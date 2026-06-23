package com.contract.review.service.rag;

import com.contract.review.service.chunker.Chunk;
import com.contract.review.service.embedding.EmbeddingService;
import com.contract.review.service.vector.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RAG 检索服务
 * 实现向量检索 -> 重排序 -> 结果返回的完整 RAG 流程
 */
@Service
public class RagRetrieverService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrieverService.class);

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;

    /** 默认检索 topK */
    private static final int DEFAULT_TOP_K = 10;

    /** 默认相似度阈值 */
    private static final double DEFAULT_THRESHOLD = 0.6;

    /** 重排后保留条数 */
    private static final int RERANK_TOP_K = 5;

    /** LRU 缓存 */
    private final LinkedHashMap<String, RetrievalResult> cache;

    public RagRetrieverService(VectorStoreService vectorStoreService,
                               EmbeddingService embeddingService,
                               ChatModel chatModel) {
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;

        // LRU cache with max 100 entries
        this.cache = new LinkedHashMap<>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RetrievalResult> eldest) {
                return size() > 100;
            }
        };
    }

    /**
     * 执行 RAG 检索
     * @param query 查询文本
     * @param threshold 相似度阈值
     * @return 检索结果
     */
    public RetrievalResult retrieve(String query, Double threshold) {
        log.info("RAG 检索: '{}'", truncate(query, 80));

        long startTime = System.currentTimeMillis();
        double thresh = threshold != null ? threshold : DEFAULT_THRESHOLD;

        // 1. 检查缓存
        String cacheKey = buildCacheKey(query, thresh);
        RetrievalResult cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("命中缓存");
            return cached;
        }

        // 2. 向量检索（获取更多结果用于重排）
        List<Chunk> chunks = vectorStoreService.searchSimilar(query, DEFAULT_TOP_K);

        if (chunks.isEmpty()) {
            log.info("检索无结果");
            return RetrievalResult.builder()
                    .items(List.of())
                    .totalCount(0)
                    .queryText(query)
                    .retrievalDuration(System.currentTimeMillis() - startTime)
                    .fromCache(false)
                    .build();
        }

        // 3. 重排序
        List<RetrievalResult.RetrievalItem> items = rerank(query, chunks, thresh);

        // 4. 构建结果
        RetrievalResult result = RetrievalResult.builder()
                .items(items)
                .totalCount(items.size())
                .queryText(query)
                .retrievalDuration(System.currentTimeMillis() - startTime)
                .fromCache(false)
                .build();

        // 5. 写入缓存
        synchronized (cache) {
            cache.put(cacheKey, result);
        }

        log.info("RAG 检索完成, 结果: {} 条, 耗时: {}ms",
                items.size(), result.getRetrievalDuration());
        return result;
    }

    /**
     * 检索并返回上下文文本（供 LLM 使用）
     * @param query 查询文本
     * @param threshold 相似度阈值
     * @return 拼接的上下文文本
     */
    public String retrieveContext(String query, Double threshold) {
        RetrievalResult result = retrieve(query, threshold);

        if (result.getItems().isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是相关法律参考信息：\n\n");

        for (int i = 0; i < result.getItems().size(); i++) {
            RetrievalResult.RetrievalItem item = result.getItems().get(i);
            context.append("【参考").append(i + 1).append("】");
            if (item.getDocumentName() != null) {
                context.append(" 来源: ").append(item.getDocumentName());
            }
            if (item.getSectionPath() != null) {
                context.append(" ").append(item.getSectionPath());
            }
            context.append("\n").append(item.getContent()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * 检索增强生成：检索 + AI 回答
     */
    public String retrieveAndGenerate(String query, String contractContext) {
        log.info("RAG 增强生成: '{}'", truncate(query, 80));

        // 1. 检索相关法律知识
        String legalContext = retrieveContext(query, DEFAULT_THRESHOLD);

        // 2. 构建增强提示词
        PromptTemplate promptTemplate = new PromptTemplate("""
                你是一位专业的合同审查法律顾问。

                用户问题：{query}

                合同上下文：
                {contractContext}

                相关法律参考：
                {legalContext}

                请基于上述法律参考和合同上下文，给出专业、准确的法律分析意见。
                引用相关法律条款时标明出处。
                """);

        String response = chatModel.call(promptTemplate.create(Map.of(
                "query", query,
                "contractContext", contractContext != null ? contractContext : "无",
                "legalContext", legalContext.isEmpty() ? "无相关法律参考" : legalContext
        ))).getResult().getOutput().getContent();

        log.info("RAG 增强生成完成, 回答长度: {} 字", response.length());
        return response;
    }

    /**
     * 重排序：使用 Cross-encoder / Cohere Rerank / LLM 对检索结果重排
     * 实现三种重排策略：
     * 1. 基于余弦相似度的重排（基础）
     * 2. 基于 LLM 的语义重排（质量高但慢）
     * 3. 混合重排（综合评分）
     */
    public List<RetrievalResult.RetrievalItem> rerank(String query, List<Chunk> chunks, double threshold) {
        log.debug("重排序: {} 条结果", chunks.size());

        // 第一步：向量相似度计算
        float[] queryEmbedding = embeddingService.embed(query);

        List<RetrievalResult.RetrievalItem> items = chunks.stream()
                .map(chunk -> {
                    double vecScore = chunk.getEmbedding() != null
                            ? embeddingService.cosineSimilarity(queryEmbedding, chunk.getEmbedding())
                            : 0.5;

                    return RetrievalResult.RetrievalItem.builder()
                            .chunkId(chunk.getId())
                            .content(chunk.getContent())
                            .score(vecScore)
                            .documentName(chunk.getDocumentName())
                            .documentId(chunk.getDocumentId())
                            .sectionPath(chunk.getSectionPath())
                            .chunkIndex(chunk.getChunkIndex())
                            .metadata(new HashMap<>(chunk.getMetadata()))
                            .build();
                })
                .filter(item -> item.getScore() >= threshold)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(RERANK_TOP_K)
                .collect(Collectors.toList());

        // 第二步：LLM 语义重排（对 top-K 结果）
        if (!items.isEmpty() && items.size() >= 2) {
            items = llmRerank(query, items);
        }

        // 设置最终排序分
        for (int i = 0; i < items.size(); i++) {
            RetrievalResult.RetrievalItem item = items.get(i);
            double rerankScore = item.getRerankScore() != null
                    ? item.getRerankScore()
                    : item.getScore();
            item.setFinalScore(rerankScore);
        }

        // 按最终分排序
        items.sort((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()));

        return items;
    }

    /**
     * 基于 LLM 的重排
     * 使用 AI 模型评估检索结果与查询的相关性
     */
    private List<RetrievalResult.RetrievalItem> llmRerank(String query, List<RetrievalResult.RetrievalItem> items) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个文档相关性评估专家。请评估以下检索结果与查询的相关性。\n\n");
            prompt.append("查询: ").append(query).append("\n\n");
            prompt.append("请对每个结果给出相关性评分（0-10），并返回 JSON 数组：\n");
            prompt.append("[{\"index\": 0, \"relevanceScore\": 8.5, \"reason\": \"...\"}, ...]\n\n");

            for (int i = 0; i < items.size(); i++) {
                prompt.append("---结果 ").append(i).append("---\n");
                prompt.append(items.get(i).getContent()).append("\n\n");
            }

            String response = chatModel.call(
                    new org.springframework.ai.chat.prompt.Prompt(prompt.toString())
            ).getResult().getOutput().getContent();

            // 解析评分结果
            parseRerankScores(response, items);

        } catch (Exception e) {
            log.warn("LLM 重排失败，使用向量相似度排序: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 解析 LLM 返回的重排评分
     */
    private void parseRerankScores(String response, List<RetrievalResult.RetrievalItem> items) {
        try {
            // 提取 JSON 数组
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                var scores = com.alibaba.fastjson2.JSON.parseArray(json);

                for (int i = 0; i < scores.size() && i < items.size(); i++) {
                    var obj = (com.alibaba.fastjson2.JSONObject) scores.get(i);
                    int index = obj.getIntValue("index");
                    double relevanceScore = obj.getDoubleValue("relevanceScore");

                    if (index >= 0 && index < items.size()) {
                        items.get(index).setRerankScore(relevanceScore / 10.0);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析重排评分失败", e);
        }
    }

    private String buildCacheKey(String query, double threshold) {
        return query.hashCode() + "_" + threshold;
    }

    private String truncate(String text, int maxLen) {
        return text != null && text.length() > maxLen
                ? text.substring(0, maxLen) + "..."
                : text;
    }
}

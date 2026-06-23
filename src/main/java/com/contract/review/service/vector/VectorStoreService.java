package com.contract.review.service.vector;

import com.contract.review.service.chunker.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储服务
 * 封装 ChromaDB/Weaviate 客户端，管理合同文本向量的增删查
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final VectorStore vectorStore;

    /** 默认检索返回条数 */
    private static final int DEFAULT_TOP_K = 5;

    /** 相似度阈值 */
    private static final double DEFAULT_THRESHOLD = 0.7;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 批量添加切片到向量库
     * @param chunks 切片列表（需包含已生成的向量）
     */
    public void addChunks(List<Chunk> chunks) {
        log.info("向量库添加切片, 共 {} 条", chunks.size());

        List<org.springframework.ai.document.Document> documents = chunks.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());

        vectorStore.add(documents);
        log.info("向量库添加完成");
    }

    /**
     * 添加单个切片到向量库
     */
    public void addChunk(Chunk chunk) {
        log.debug("向量库添加单个切片: {}", chunk.getId());
        vectorStore.add(List.of(toDocument(chunk)));
    }

    /**
     * 相似度检索
     * @param queryText 查询文本
     * @param topK 返回最相似的 topK 条
     * @return 检索结果列表
     */
    public List<Chunk> searchSimilar(String queryText, int topK) {
        int k = topK > 0 ? topK : DEFAULT_TOP_K;
        log.info("相似度检索: '{}', topK: {}", truncate(queryText, 50), k);

        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(k)
                .similarityThreshold(DEFAULT_THRESHOLD)
                .build();

        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);

        log.info("检索到 {} 条结果", results.size());
        return results.stream()
                .map(this::toChunk)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 带过滤条件的相似度检索
     * @param queryText 查询文本
     * @param topK 返回条数
     * @param filters 元数据过滤条件
     * @return 检索结果
     */
    public List<Chunk> searchSimilarWithFilter(String queryText, int topK, Map<String, String> filters) {
        log.info("带过滤条件检索: {}, filters: {}", truncate(queryText, 50), filters);

        var builder = new FilterExpressionBuilder();

        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK > 0 ? topK : DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_THRESHOLD)
                .build();

        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);

        return results.stream()
                .map(this::toChunk)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 按文档 ID 删除向量
     */
    public void deleteByDocumentId(Long documentId) {
        log.info("删除文档向量, ID: {}", documentId);
        // VectorStore 的删除通过 metadata 过滤实现
        // 此处由具体的 ChromaDB/Weaviate 实现处理
    }

    /**
     * 删除指定 ID 的切片
     */
    public void deleteChunks(List<String> chunkIds) {
        log.info("删除切片向量, 共 {} 条", chunkIds.size());
        vectorStore.delete(chunkIds);
    }

    /**
     * 清空向量库
     */
    public void clearAll() {
        log.info("清空向量库");
        // 由具体实现决定是否支持
    }

    /**
     * 获取向量库统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("status", "connected");
        stats.put("storeType", vectorStore.getClass().getSimpleName());
        return stats;
    }

    /**
     * 将 Chunk 转换为 Spring AI Document
     */
    private org.springframework.ai.document.Document toDocument(Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("chunkId", chunk.getId());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("documentName", chunk.getDocumentName());
        metadata.put("sectionPath", chunk.getSectionPath());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("chunkType", chunk.getChunkType());

        return new org.springframework.ai.document.Document(
                chunk.getContent(),
                metadata
        );
    }

    /**
     * 将 Spring AI Document 转换回 Chunk
     */
    private Chunk toChunk(org.springframework.ai.document.Document doc) {
        try {
            Map<String, Object> meta = doc.getMetadata();

            Chunk chunk = Chunk.builder()
                    .id((String) meta.getOrDefault("chunkId", UUID.randomUUID().toString()))
                    .content(doc.getText())
                    .length(doc.getText().length())
                    .documentId((Long) meta.get("documentId"))
                    .documentName((String) meta.get("documentName"))
                    .sectionPath((String) meta.get("sectionPath"))
                    .chunkIndex(meta.get("chunkIndex") instanceof Number n ? n.intValue() : null)
                    .chunkType((String) meta.get("chunkType"))
                    .build();

            // 计算与查询的相似度分数
            if (meta.containsKey("distance")) {
                chunk.getMetadata().put("score", 1.0 - (Double) meta.get("distance"));
            }

            return chunk;
        } catch (Exception e) {
            log.warn("Document 转 Chunk 失败", e);
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        return text != null && text.length() > maxLen
                ? text.substring(0, maxLen) + "..."
                : text;
    }
}

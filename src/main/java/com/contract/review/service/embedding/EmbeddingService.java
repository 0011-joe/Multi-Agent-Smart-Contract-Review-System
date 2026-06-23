package com.contract.review.service.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedding 向量化服务
 * 使用 Spring AI 的 EmbeddingModel Bean（BGE-large-zh）
 * 批量生成文本向量，支持异步处理
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    /** 批处理大小 */
    private static final int BATCH_SIZE = 10;

    /** 异步处理线程池 */
    private final ExecutorService executor;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "embedding-worker");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * 对单段文本进行向量化
     * @param text 输入文本
     * @return 向量（float[]）
     */
    public float[] embed(String text) {
        log.debug("向量化单段文本, 长度: {} 字", text.length());

        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), org.springframework.ai.embedding.EmbeddingOptions.EMPTY)
        );

        List<Embedding> results = response.getResults();
        if (results.isEmpty()) {
            log.warn("向量化返回空结果");
            return new float[0];
        }

        return convertToFloatArray(results.get(0).getVector());
    }

    /**
     * 批量对文本列表进行向量化
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        log.info("批量向量化, 共 {} 段文本", texts.size());

        List<float[]> allEmbeddings = new ArrayList<>();
        List<CompletableFuture<List<float[]>>> futures = new ArrayList<>();

        // 分批次异步处理
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);

            futures.add(CompletableFuture.supplyAsync(() -> processBatch(batch), executor));
        }

        // 收集所有结果
        for (CompletableFuture<List<float[]>>> future : futures) {
            try {
                allEmbeddings.addAll(future.join());
            } catch (Exception e) {
                log.error("批量向量化任务失败", e);
            }
        }

        log.info("批量向量化完成, 共 {} 个向量", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 异步向量化单段文本
     */
    public CompletableFuture<float[]> embedAsync(String text) {
        return CompletableFuture.supplyAsync(() -> embed(text), executor);
    }

    /**
     * 异步批量向量化
     */
    public CompletableFuture<List<float[]>> embedBatchAsync(List<String> texts) {
        return CompletableFuture.supplyAsync(() -> embedBatch(texts), executor);
    }

    /**
     * 处理一批文本的向量化
     */
    private List<float[]> processBatch(List<String> texts) {
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(texts, org.springframework.ai.embedding.EmbeddingOptions.EMPTY)
            );

            return response.getResults().stream()
                    .map(r -> convertToFloatArray(r.getVector()))
                    .toList();

        } catch (Exception e) {
            log.error("批处理向量化失败, 批次大小: {}", texts.size(), e);
            // 降级：逐条处理
            return texts.stream()
                    .map(t -> {
                        try {
                            return embed(t);
                        } catch (Exception ex) {
                            log.error("逐条向量化失败", ex);
                            return new float[0];
                        }
                    })
                    .toList();
        }
    }

    /**
     * 将 List<Double> 转换为 float[]
     */
    private float[] convertToFloatArray(List<Double> doubleList) {
        float[] result = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            result[i] = doubleList.get(i).floatValue();
        }
        return result;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public double cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA.length != vecB.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + vecA.length + " vs " + vecB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }
}

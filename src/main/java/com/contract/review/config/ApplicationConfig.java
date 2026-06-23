package com.contract.review.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI 核心配置
 * 配置 ChatModel（智谱 GLM-4.5-air）、EmbeddingModel（BGE 中文向量模型）、VectorStore（ChromaDB）
 */
@Configuration
public class ApplicationConfig {

    /**
     * 智谱 GLM-4.5-air 对话模型
     */
    @Bean
    @Primary
    public ChatModel chatModel(DashScopeChatModel dashScopeChatModel) {
        return dashScopeChatModel;
    }

    /**
     * DashScope 嵌入模型（用于文本向量化）
     * 底层模型：text-embedding-v3 或 text-embedding-v2
     */
    @Bean
    public EmbeddingModel embeddingModel(DashScopeEmbeddingModel dashScopeEmbeddingModel) {
        return dashScopeEmbeddingModel;
    }

    /**
     * ChromaDB 向量存储
     * 用于存储合同全文向量和相似度检索
     */
    @Bean
    public VectorStore vectorStore(ChromaVectorStore chromaVectorStore) {
        return chromaVectorStore;
    }
}

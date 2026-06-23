package com.contract.review.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置
 * 提供备选的 AI 模型集成能力（BGE-M3 中文嵌入模型）
 */
@Configuration
public class LangChain4jConfig {

    @Value("${spring.ai.alibaba.api-key:}")
    private String apiKey;

    /**
     * LangChain4j Chat 模型（通过兼容 OpenAI 接口对接智谱 GLM）
     */
    @Bean
    public ChatLanguageModel langChainChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .modelName("glm-4.5-air")
                .temperature(0.3)
                .build();
    }

    /**
     * BGE-M3 中文嵌入模型（LangChain4j 封装）
     * 用于合同文本的向量化表示
     */
    @Bean
    public EmbeddingModel bgeEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .modelName("embedding-3")
                .build();
    }
}

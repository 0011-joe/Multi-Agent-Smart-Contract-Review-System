package com.contract.review.mcp.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 条款分析工具
 * 使用 Spring AI @Tool 注解将方法暴露给 LLM，支持智能调用
 */
@Component
public class ContractAnalysisTool {

    private static final Logger log = LoggerFactory.getLogger(ContractAnalysisTool.class);

    private final ChatModel chatModel;

    public ContractAnalysisTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 分析合同条款风险
     */
    @Tool(description = "分析合同条款的法律风险，返回风险等级、风险描述和修改建议")
    public String analyzeClause(
            @ToolParam(description = "条款原文内容") String clauseContent,
            @ToolParam(description = "条款类型（如：付款条款、违约责任、保密条款等）") String clauseType) {
        log.info("【@Tool】分析条款 - 类型: {}, 长度: {} 字", clauseType, clauseContent.length());

        PromptTemplate promptTemplate = new PromptTemplate("""
                你是一个专业的合同审查专家。请分析以下合同条款的风险：

                条款类型：{clauseType}
                条款原文：{clauseContent}

                请以 JSON 格式返回：
                {
                    "riskLevel": "HIGH/MEDIUM/LOW/NONE",
                    "riskScore": 0-100,
                    "riskDescription": "风险详细描述",
                    "suggestion": "修改建议",
                    "legalBasis": "相关法律依据"
                }
                """);

        String response = chatModel.call(promptTemplate.create(Map.of(
                "clauseContent", clauseContent,
                "clauseType", clauseType
        ))).getResult().getOutput().getContent();

        log.debug("条款分析完成");
        return response;
    }

    /**
     * 检索相关法律条文（RAG 接口）
     */
    @Tool(description = "检索与合同条款相关的法律法规条文")
    public String searchLegalDb(
            @ToolParam(description = "检索关键词，如：违约金、不可抗力、保密义务等") String keyword) {
        log.info("【@Tool】检索法律条文: {}", keyword);
        // 实际集成 RAG 检索法律知识库
        return JSON.toJSONString(Map.of(
                "keyword", keyword,
                "results", "法律检索结果（由 RAG 服务提供）",
                "note", "该功能需集成 LegalKnowledgeSource"
        ));
    }
}

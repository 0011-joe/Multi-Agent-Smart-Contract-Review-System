package com.contract.review.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 合同审查工具
 * 对整份合同进行多维度审查，暴露为 MCP @Tool
 */
@Component
public class ContractReviewTool {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewTool.class);

    private final ChatModel chatModel;

    public ContractReviewTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(description = "对整份合同进行全面审查，包括条款分析、风险评估和合规检查")
    public String reviewContract(
            @ToolParam(description = "合同全文内容") String contractContent,
            @ToolParam(description = "合同类型（如：采购合同、劳务合同、保密协议）") String contractType) {
        log.info("【@Tool】全面审查合同 - 类型: {}, 长度: {} 字", contractType, contractContent.length());

        PromptTemplate promptTemplate = new PromptTemplate("""
                你是一位资深合同审查专家，请对以下{contractType}进行全面审查。

                合同全文：{contractContent}

                请从以下维度全面分析：
                1. 总体评价
                2. 条款逐条分析
                3. 风险评估（HIGH/MEDIUM/LOW）
                4. 合规检查
                5. 修改建议
                6. 谈判策略

                以 JSON 格式返回。
                """);

        return chatModel.call(promptTemplate.create(Map.of(
                "contractContent", contractContent,
                "contractType", contractType
        ))).getResult().getOutput().getContent();
    }
}

package com.contract.review.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具：审查报告生成
 * 基于审查结果数据生成结构化的合同审查报告
 */
@Component
public class ReportGenerationTool {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationTool.class);

    private final ChatModel chatModel;

    public ReportGenerationTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 生成审查报告
     */
    public String generateReport(Map<String, Object> arguments) {
        String contractId = (String) arguments.get("contractId");
        String reviewData = (String) arguments.get("reviewData");

        log.info("生成审查报告 - 合同ID: {}", contractId);

        PromptTemplate promptTemplate = new PromptTemplate("""
                请根据以下审查结果数据，生成一份结构化的合同审查报告。

                审查数据：{reviewData}

                报告要求：
                1. 风险评估摘要 - 总体风险等级和关键发现
                2. 条款分析详情 - 各条款的风险分析结果
                3. 合规检查清单 - 法律法规遵循情况
                4. 修改建议清单 - 按优先级排序
                5. 谈判策略建议 - 关键条款的谈判切入点

                请以 JSON 格式输出完整的审查报告。
                """);

        String response = chatModel.call(
                promptTemplate.create(Map.of("reviewData", reviewData))
        ).getResult().getOutput().getContent();

        log.info("审查报告生成完成");
        return response;
    }
}

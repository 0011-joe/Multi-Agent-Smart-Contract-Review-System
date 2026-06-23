package com.contract.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.contract.review.model.ContractClause;
import com.contract.review.model.RiskLevel;
import com.contract.review.service.rag.RagRetrieverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 风险评估 Agent
 * 多维度风险识别：权利义务不对等、定义模糊、责任无限等
 * 关联历史风险案例（通过 RAG 调用）
 */
@Component
public class RiskAgent extends AgentBase {

    private static final Logger log = LoggerFactory.getLogger(RiskAgent.class);

    private final ChatModel chatModel;
    private final RagRetrieverService ragRetrieverService;
    private final PromptManager promptManager;

    public RiskAgent(ChatModel chatModel,
                     RagRetrieverService ragRetrieverService,
                     PromptManager promptManager) {
        super("RiskAgent", "合同风险评估专家", "1.0.0");
        this.chatModel = chatModel;
        this.ragRetrieverService = ragRetrieverService;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> execute(List<String> inputs) {
        log.info("RiskAgent 执行风险评估");
        long startTime = System.currentTimeMillis();

        if (inputs == null || inputs.isEmpty()) {
            return Map.of("error", "输入为空");
        }

        String clauseContent = inputs.get(0);
        String clauseType = inputs.size() > 1 ? inputs.get(1) : "通用条款";

        // 1. 检索历史风险案例
        String riskContext = ragRetrieverService.retrieveContext(
                clauseContent + " 风险 案例", 0.5);

        // 2. 构建多维度风险分析 Prompt
        Map<String, Object> variables = Map.of(
                "clauseContent", clauseContent,
                "clauseType", clauseType,
                "riskContext", riskContext.isEmpty() ? "无相关风险案例" : riskContext
        );
        String prompt = promptManager.getPrompt("RiskAgent", variables, "cot");
        // 追加多维度分析要求
        prompt += """

            请按以下维度逐项评估风险（每项评分0-10）：
            1. 权利义务对等性 - 双方权利义务是否平衡
            2. 定义明确性 - 关键术语是否明确定义
            3. 责任限制合理性 - 赔偿限额、免责条款是否合理
            4. 潜在商业风险 - 对业务运营的潜在影响
            5. 履约可行性 - 条款在实际中能否履行

            输出JSON格式：
            {
                "riskLevel": "HIGH/MEDIUM/LOW",
                "riskScore": 总分,
                "dimensions": {
                    "rightObligationBalance": {"score": 0-10, "risk": "描述", "suggestion": "建议"},
                    "definitionClarity": {"score": 0-10, "risk": "描述", "suggestion": "建议"},
                    "liabilityLimit": {"score": 0-10, "risk": "描述", "suggestion": "建议"},
                    "businessRisk": {"score": 0-10, "risk": "描述", "suggestion": "建议"},
                    "performanceFeasibility": {"score": 0-10, "risk": "描述", "suggestion": "建议"}
                },
                "riskDescription": "总体风险描述",
                "suggestion": "总体修改建议"
            }
            """;

        // 3. 调用 LLM
        String response = chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getContent();

        // 4. 解析结果
        Map<String, Object> result = parseResult(response, clauseContent);

        long duration = System.currentTimeMillis() - startTime;
        result.put("agentName", getAgentName());
        result.put("agentRole", getAgentRole());
        result.put("duration", duration);

        log.info("RiskAgent 评估完成, 耗时: {}ms, 风险等级: {}, 评分: {}",
                duration, result.get("riskLevel"), result.get("riskScore"));
        return result;
    }

    /**
     * 分析单条条款的风险
     */
    public Map<String, Object> analyzeClause(ContractClause clause) {
        return execute(List.of(
                clause.getContent(),
                clause.getClauseType() != null ? clause.getClauseType() : "通用条款"
        ));
    }

    @Override
    public List<String> getCapabilities() {
        return List.of(
                "多维度风险评估",
                "权利义务对等性分析",
                "责任条款合理性评估",
                "历史风险案例关联"
        );
    }

    private Map<String, Object> parseResult(String response, String clauseContent) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                JSONObject parsed = JSON.parseObject(json);
                return Map.of(
                        "riskLevel", parsed.getString("riskLevel") != null
                                ? parsed.getString("riskLevel") : "MEDIUM",
                        "riskScore", parsed.getIntValue("riskScore", 50),
                        "dimensions", parsed.getJSONObject("dimensions") != null
                                ? parsed.getJSONObject("dimensions") : Map.of(),
                        "riskDescription", parsed.getString("riskDescription"),
                        "suggestion", parsed.getString("suggestion"),
                        "originalClause", clauseContent
                );
            }
        } catch (Exception e) {
            log.warn("解析风险评估结果失败", e);
        }

        return Map.of(
                "riskLevel", "MEDIUM",
                "riskScore", 50,
                "riskDescription", "解析失败",
                "originalClause", clauseContent
        );
    }
}

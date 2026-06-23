package com.contract.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.contract.review.model.ContractClause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商务条款 Agent
 * 关键信息提取、合理性审查（如违约金比例）
 * 使用 Spring AI 的 @Schema 定义 JSON 输出格式
 */
@Component
public class BusinessTermAgent extends AgentBase {

    private static final Logger log = LoggerFactory.getLogger(BusinessTermAgent.class);

    private final ChatModel chatModel;
    private final PromptManager promptManager;

    public BusinessTermAgent(ChatModel chatModel, PromptManager promptManager) {
        super("BusinessTermAgent", "商务条款分析专家", "1.0.0");
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> execute(List<String> inputs) {
        log.info("BusinessTermAgent 执行商务条款分析");
        long startTime = System.currentTimeMillis();

        if (inputs == null || inputs.isEmpty()) {
            return Map.of("error", "输入为空");
        }

        String clauseContent = inputs.get(0);
        String clauseType = inputs.size() > 1 ? inputs.get(1) : "通用条款";

        // 构建包含 Schema 定义的 Prompt
        String prompt = buildPrompt(clauseContent, clauseType);

        String response = chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getContent();

        Map<String, Object> result = parseResult(response, clauseContent);

        long duration = System.currentTimeMillis() - startTime;
        result.put("agentName", getAgentName());
        result.put("agentRole", getAgentRole());
        result.put("duration", duration);

        log.info("BusinessTermAgent 分析完成, 耗时: {}ms", duration);
        return result;
    }

    /**
     * 分析单条条款的商务要素
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
                "关键商务信息提取",
                "价格/费用合理性审查",
                "违约金比例评估",
                "期限条款可行性分析"
        );
    }

    /**
     * 构建带 Schema 输出定义的 Prompt
     */
    private String buildPrompt(String clauseContent, String clauseType) {
        // 定义输出 Schema（模拟 @Schema 注解的效果）
        String outputSchema = """
            {
                "type": "object",
                "properties": {
                    "partyA": {"type": "string", "description": "甲方名称/信息"},
                    "partyB": {"type": "string", "description": "乙方名称/信息"},
                    "contractAmount": {"type": "string", "description": "合同金额"},
                    "currency": {"type": "string", "description": "币种(如:人民币)"},
                    "paymentTerms": {"type": "string", "description": "付款条件/方式"},
                    "contractTerm": {"type": "string", "description": "合同期限"},
                    "deliveryDeadline": {"type": "string", "description": "交付/完成期限"},
                    "liquidatedDamages": {"type": "string", "description": "违约金比例/金额"},
                    "liabilityCap": {"type": "string", "description": "赔偿上限"},
                    "noticePeriod": {"type": "string", "description": "通知期限"},
                    "terminationClause": {"type": "string", "description": "解约条件"},
                    "reasonableness": {
                        "type": "object",
                        "properties": {
                            "isReasonable": {"type": "boolean"},
                            "riskLevel": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                            "reason": {"type": "string", "description": "合理性判断理由"},
                            "suggestion": {"type": "string", "description": "修改建议"}
                        }
                    }
                },
                "required": ["contractAmount", "paymentTerms", "contractTerm", "reasonableness"]
            }
            """;

        return String.format("""
            你是一位专业的商务条款分析专家。
            请分析以下合同条款中的商务要素：

            条款类型：%s
            条款原文：%s

            请提取关键商务信息并进行合理性审查。
            严格按照以下 JSON Schema 输出结果：

            %s

            注意：
            - 缺失的字段用 null 填充
            - 金额统一转为数字格式
            - 若含违约金，评估是否在法定范围内（一般不超过损失的30%%）
            """, clauseType, clauseContent, outputSchema);
    }

    private Map<String, Object> parseResult(String response, String clauseContent) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                JSONObject parsed = JSON.parseObject(json);

                Map<String, Object> result = new java.util.HashMap<>(parsed);

                // 提取合理性评估
                JSONObject reasonableness = parsed.getJSONObject("reasonableness");
                if (reasonableness != null) {
                    result.put("riskLevel", reasonableness.getString("riskLevel"));
                    result.put("isReasonable", reasonableness.getBoolean("isReasonable"));
                    result.put("reasonSuggestion", reasonableness.getString("suggestion"));
                }

                result.put("originalClause", clauseContent);
                return result;
            }
        } catch (Exception e) {
            log.warn("解析商务条款结果失败", e);
        }

        return Map.of(
                "error", "解析失败",
                "originalClause", clauseContent
        );
    }
}

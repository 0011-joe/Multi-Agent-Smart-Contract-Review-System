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
 * 合规审查 Agent
 * 审查合同条款是否符合法律法规要求
 * 集成 RAG 检索服务，使用 CoT 思维链引导推理
 */
@Component
public class ComplianceAgent extends AgentBase {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAgent.class);

    private final ChatModel chatModel;
    private final RagRetrieverService ragRetrieverService;
    private final PromptManager promptManager;

    public ComplianceAgent(ChatModel chatModel,
                           RagRetrieverService ragRetrieverService,
                           PromptManager promptManager) {
        super("ComplianceAgent", "合同合规审查专家", "1.0.0");
        this.chatModel = chatModel;
        this.ragRetrieverService = ragRetrieverService;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> execute(List<String> inputs) {
        log.info("ComplianceAgent 执行审查");
        long startTime = System.currentTimeMillis();

        if (inputs == null || inputs.isEmpty()) {
            return Map.of("error", "输入为空");
        }

        String clauseContent = inputs.size() > 0 ? inputs.get(0) : "";
        String clauseType = inputs.size() > 1 ? inputs.get(1) : "通用条款";

        // 1. RAG 检索相关法律知识
        String legalContext = ragRetrieverService.retrieveContext(clauseContent, 0.6);

        // 2. 构建 CoT Prompt
        Map<String, Object> variables = Map.of(
                "clauseContent", clauseContent,
                "clauseType", clauseType,
                "legalContext", legalContext.isEmpty() ? "无特定法律参考" : legalContext
        );
        String prompt = promptManager.getPrompt("ComplianceAgent", variables, "cot");

        // 3. 调用 LLM
        String response = chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getContent();

        // 4. 解析结果
        Map<String, Object> result = parseResult(response, clauseContent);

        long duration = System.currentTimeMillis() - startTime;
        result.put("agentName", getAgentName());
        result.put("agentRole", getAgentRole());
        result.put("duration", duration);
        result.put("ragLegalContext", legalContext);

        log.info("ComplianceAgent 审查完成, 耗时: {}ms, 风险等级: {}",
                duration, result.get("riskLevel"));
        return result;
    }

    /**
     * 分析单条条款的合规性
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
                "法律合规性审查",
                "RAG 法律知识检索",
                "CoT 思维链推理",
                "合规风险评估"
        );
    }

    /**
     * 解析 LLM 返回的结构化结果
     */
    private Map<String, Object> parseResult(String response, String clauseContent) {
        try {
            // 尝试提取 JSON
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = response.substring(start, end + 1);
                JSONObject parsed = JSON.parseObject(json);
                return Map.of(
                        "isCompliant", parsed.getBoolean("isCompliant") != Boolean.FALSE,
                        "riskLevel", parsed.getString("riskLevel") != null
                                ? parsed.getString("riskLevel") : "MEDIUM",
                        "reason", parsed.getString("riskDescription") != null
                                ? parsed.getString("riskDescription") : parsed.getString("reason"),
                        "suggestion", parsed.getString("suggestion") != null
                                ? parsed.getString("suggestion") : parsed.getString("suggestions"),
                        "legalBasis", parsed.getString("legalBasis"),
                        "originalClause", clauseContent,
                        "rawResponse", response
                );
            }
        } catch (Exception e) {
            log.warn("解析合规审查结果失败，返回原始文本", e);
        }

        return Map.of(
                "isCompliant", false,
                "riskLevel", "MEDIUM",
                "reason", "解析失败，请查看原始分析",
                "rawResponse", response,
                "originalClause", clauseContent
        );
    }
}

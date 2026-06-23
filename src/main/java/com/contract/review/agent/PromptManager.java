package com.contract.review.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 工程管理器
 * 管理各个 Agent 的 Prompt 模板，支持外部化/动态加载、变量替换、CoT和ReAct模式切换
 */
@Component
public class PromptManager {

    private static final Logger log = LoggerFactory.getLogger(PromptManager.class);

    /** Prompt 模板缓存 */
    private final Map<String, String> promptTemplates = new ConcurrentHashMap<>();

    /** 默认 CoT 指令 */
    private static final String COT_INSTRUCTION = """
        请一步一步思考（Chain-of-Thought）：
        1. 首先理解条款的核心内容和法律含义
        2. 然后分析可能存在的风险点
        3. 接着评估风险等级和影响程度
        4. 最后给出具体的修改建议
        """;

    /** 默认 ReAct 指令 */
    private static final String REACT_INSTRUCTION = """
        请使用 ReAct（思考-行动-观察）模式：
        Thought: 分析当前条款的关键问题
        Action: 调用法律知识库检索相关法规
        Observation: 分析检索结果与条款的关联
        Thought: 综合判断风险并给出建议
        """;

    public PromptManager() {
        // 初始化默认 Prompt 模板
        initDefaultTemplates();
    }

    /**
     * 获取 Prompt
     * @param agentName Agent名称
     * @param variables 变量映射
     * @return 渲染后的 Prompt
     */
    public String getPrompt(String agentName, Map<String, Object> variables) {
        return getPrompt(agentName, variables, "cot");
    }

    /**
     * 获取指定模式的 Prompt
     * @param agentName Agent名称
     * @param variables 变量映射
     * @param mode 模式（cot / react）
     * @return 渲染后的 Prompt
     */
    public String getPrompt(String agentName, Map<String, Object> variables, String mode) {
        String template = promptTemplates.get(agentName + ":" + mode);
        if (template == null) {
            template = promptTemplates.get(agentName + ":default");
        }
        if (template == null) {
            log.warn("未找到 Agent '{}' 的 Prompt 模板，使用默认模板", agentName);
            template = getDefaultTemplate(agentName);
        }

        return renderTemplate(template, variables, mode);
    }

    /**
     * 注册 Prompt 模板
     */
    public void registerTemplate(String agentName, String template, String mode) {
        String key = agentName + ":" + (mode != null ? mode : "default");
        promptTemplates.put(key, template);
        log.debug("注册 Prompt 模板: {} (模式: {})", agentName, mode);
    }

    /**
     * 从外部加载 Prompt 模板
     * @param agentName Agent名称
     * @param templateContent 模板内容
     */
    public void loadExternalTemplate(String agentName, String templateContent) {
        registerTemplate(agentName, templateContent, "default");
        log.info("外部加载 Prompt 模板: {}", agentName);
    }

    /**
     * 渲染模板：替换 {{variable}} 占位符
     */
    private String renderTemplate(String template, Map<String, Object> variables, String mode) {
        if (template == null) return "";

        String result = template;

        // 替换变量
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }

        // 追加思维链指令
        if ("cot".equals(mode)) {
            result = result + "\n\n" + COT_INSTRUCTION;
        } else if ("react".equals(mode)) {
            result = result + "\n\n" + REACT_INSTRUCTION;
        }

        return result;
    }

    /**
     * 获取默认模板
     */
    private String getDefaultTemplate(String agentName) {
        return switch (agentName) {
            case "ComplianceAgent" -> """
                你是一位专业的合同合规审查专家。
                请审查以下合同条款的合规性：

                条款原文：{{clauseContent}}
                条款类型：{{clauseType}}

                请检查是否符合中国法律法规要求，特别是：
                1. 是否符合《民法典》相关规定
                2. 是否有违反强制性法律规定的条款
                3. 是否有侵犯消费者/劳动者权益的内容
                4. 是否符合行业监管要求
                """;
            case "RiskAgent" -> """
                你是一位专业的合同风险评估专家。
                请评估以下合同条款的风险：

                条款原文：{{clauseContent}}
                条款类型：{{clauseType}}

                请从多维度评估风险：
                1. 权利义务对等性
                2. 定义明确性
                3. 责任限制合理性
                4. 潜在的商业风险
                """;
            case "BusinessTermAgent" -> """
                你是一位专业的商务条款分析专家。
                请分析以下合同条款中的商务要素：

                条款原文：{{clauseContent}}
                条款类型：{{clauseType}}

                请关注：
                1. 关键商务信息提取
                2. 价格/费用条款的合理性
                3. 交付/服务期限的可行性
                4. 违约责任的商业可接受性
                """;
            default -> """
                请审查以下合同条款：

                条款原文：{{clauseContent}}
                条款类型：{{clauseType}}
                """;
        };
    }

    /**
     * 初始化默认模板
     */
    private void initDefaultTemplates() {
        registerTemplate("ComplianceAgent", getDefaultTemplate("ComplianceAgent"), "default");
        registerTemplate("RiskAgent", getDefaultTemplate("RiskAgent"), "default");
        registerTemplate("BusinessTermAgent", getDefaultTemplate("BusinessTermAgent"), "default");
        registerTemplate("ComplianceAgent", getDefaultTemplate("ComplianceAgent") + "\n" + COT_INSTRUCTION, "cot");
        registerTemplate("RiskAgent", getDefaultTemplate("RiskAgent") + "\n" + COT_INSTRUCTION, "cot");
        registerTemplate("BusinessTermAgent", getDefaultTemplate("BusinessTermAgent") + "\n" + COT_INSTRUCTION, "cot");
    }
}

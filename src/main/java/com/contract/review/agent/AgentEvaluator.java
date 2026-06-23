package com.contract.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent 评测框架
 * 基于 CUAD 合同理解数据集子集测试，计算 Precision/Recall/F1
 */
@Component
public class AgentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluator.class);

    /** 评测结果存储 */
    private final Map<String, EvaluationResult> evaluationHistory = new ConcurrentHashMap<>();

    /** CUAD 测试数据 */
    private static final List<TestCase> CUAD_TEST_CASES = List.of(
            new TestCase("违约责任条款", "任何一方违反本合同约定，应向守约方支付合同总金额20%的违k约金。",
                    Set.of("违约", "违约金", "20%", "守约方"), "HIGH"),
            new TestCase("保密条款", "乙方应对在合作期间获知的甲方商业秘密承担无限期保密义务。",
                    Set.of("保密", "商业秘密", "无限期"), "MEDIUM"),
            new TestCase("管辖条款", "双方因本合同产生的争议，提交甲方所在地人民法院管辖。",
                    Set.of("管辖", "争议解决", "法院"), "LOW"),
            new TestCase("免责条款", "因不可抗力导致无法履约的，受影响方不承担责任。但不可抗力不包括市场价格波动。",
                    Set.of("不可抗力", "免责", "责任免除"), "LOW"),
            new TestCase("解除条款", "甲方有权在任何时候无故解除本合同，无需承担任何责任。",
                    Set.of("解除", "单方解除", "无条件"), "HIGH")
    );

    public AgentEvaluator() {
        log.info("Agent评测框架初始化完成, 内置 {} 个 CUAD 测试用例", CUAD_TEST_CASES.size());
    }

    /**
     * 评测单个 Agent
     * @param agent Agent实例
     * @return 评测结果
     */
    public EvaluationResult evaluate(AgentBase agent) {
        log.info("开始评测 Agent: {}", agent.getAgentName());

        List<TestCaseResult> caseResults = new ArrayList<>();
        int truePositives = 0, falsePositives = 0, falseNegatives = 0;

        for (TestCase testCase : CUAD_TEST_CASES) {
            try {
                Map<String, Object> result = agent.execute(
                        List.of(testCase.clauseContent, "测试条款"));

                // 提取关键词命中情况
                Set<String> mentionedTerms = extractTerms(result);
                Set<String> expectedTerms = testCase.expectedTerms;

                // 计算命中
                Set<String> hit = new HashSet<>(mentionedTerms);
                hit.retainAll(expectedTerms);

                Set<String> missed = new HashSet<>(expectedTerms);
                missed.removeAll(mentionedTerms);

                boolean isRiskCorrect = result.containsKey("riskLevel")
                        && testCase.expectedRiskLevel.equals(result.get("riskLevel"));

                caseResults.add(new TestCaseResult(
                        testCase, hit, missed, isRiskCorrect, mentionedTerms));

                truePositives += hit.size();
                falsePositives += mentionedTerms.size() - hit.size();
                falseNegatives += missed.size();

            } catch (Exception e) {
                log.error("测试用例执行失败: {}", testCase.description, e);
            }
        }

        // 计算指标
        double precision = truePositives + falsePositives > 0
                ? (double) truePositives / (truePositives + falsePositives) : 0;
        double recall = truePositives + falseNegatives > 0
                ? (double) truePositives / (truePositives + falseNegatives) : 0;
        double f1 = precision + recall > 0
                ? 2 * precision * recall / (precision + recall) : 0;

        // 计算风险等级准确率
        long riskAccuracy = caseResults.stream().filter(r -> r.riskCorrect).count();
        double riskAccuracyRate = (double) riskAccuracy / caseResults.size();

        EvaluationResult evalResult = new EvaluationResult(
                agent.getAgentName(),
                CUAD_TEST_CASES.size(),
                truePositives, falsePositives, falseNegatives,
                precision, recall, f1,
                riskAccuracyRate,
                caseResults
        );

        evaluationHistory.put(agent.getAgentName(), evalResult);

        log.info("Agent {} 评测完成: P={:.2f}, R={:.2f}, F1={:.2f}, 风险准确率={:.2f}",
                agent.getAgentName(), precision, recall, f1, riskAccuracyRate);

        return evalResult;
    }

    /**
     * 评测所有 Agent
     */
    public Map<String, EvaluationResult> evaluateAll(List<AgentBase> agents) {
        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        for (AgentBase agent : agents) {
            results.put(agent.getAgentName(), evaluate(agent));
        }
        return results;
    }

    /**
     * 获取评测报告
     */
    public String generateReport(Map<String, EvaluationResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("========================================\n");
        report.append("      Agent 评测报告\n");
        report.append("========================================\n\n");

        report.append(String.format("%-20s %8s %8s %8s %12s\n",
                "Agent", "Precision", "Recall", "F1", "RiskAcc"));
        report.append("-".repeat(60)).append("\n");

        for (Map.Entry<String, EvaluationResult> entry : results.entrySet()) {
            EvaluationResult r = entry.getValue();
            report.append(String.format("%-20s %8.3f %8.3f %8.3f %12.2f%%\n",
                    entry.getKey(), r.precision, r.recall, r.f1, r.riskAccuracyRate * 100));
        }

        report.append("\n--- 详细测试用例结果 ---\n");
        for (Map.Entry<String, EvaluationResult> entry : results.entrySet()) {
            report.append("\n[").append(entry.getKey()).append("]\n");
            for (TestCaseResult tcr : entry.getValue().caseResults) {
                report.append(String.format("  %-12s 命中:%s 遗漏:%s 风险%s\n",
                        tcr.testCase.description,
                        tcr.hitTerms.isEmpty() ? "无" : tcr.hitTerms,
                        tcr.missedTerms.isEmpty() ? "无" : tcr.missedTerms,
                        tcr.riskCorrect ? "✓" : "✗"));
            }
        }

        return report.toString();
    }

    /**
     * 从 Agent 结果中提取关键术语
     */
    private Set<String> extractTerms(Map<String, Object> result) {
        Set<String> terms = new HashSet<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString().toLowerCase() : "";
            if (entry.getKey().equals("riskLevel")) {
                terms.add(value);
            } else if (entry.getKey().equals("riskDescription")
                    || entry.getKey().equals("suggestion")
                    || entry.getKey().equals("reason")) {
                // 提取中文关键词（长度>=2的连续中文字符）
                var matcher = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{2,}")
                        .matcher(value);
                while (matcher.find()) {
                    terms.add(matcher.group());
                }
            }
        }
        return terms;
    }

    // ===== 内部类 =====

    /** 测试用例 */
    public record TestCase(String description, String clauseContent,
                           Set<String> expectedTerms, String expectedRiskLevel) {}

    /** 单用例结果 */
    public record TestCaseResult(TestCase testCase, Set<String> hitTerms,
                                 Set<String> missedTerms, boolean riskCorrect,
                                 Set<String> allMentionedTerms) {}

    /** 评测结果 */
    public record EvaluationResult(
            String agentName,
            int totalCases,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1,
            double riskAccuracyRate,
            List<TestCaseResult> caseResults
    ) {
        public String summary() {
            return String.format(
                    "[%s] TP=%d FP=%d FN=%d P=%.3f R=%.3f F1=%.3f RiskAcc=%.1f%%",
                    agentName, truePositives, falsePositives, falseNegatives,
                    precision, recall, f1, riskAccuracyRate * 100);
        }
    }
}

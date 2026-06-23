package com.contract.review.service;

import com.alibaba.fastjson2.JSON;
import com.contract.review.model.Contract;
import com.contract.review.model.Report;
import com.contract.review.model.ReviewResult;
import com.contract.review.model.RiskLevel;
import com.contract.review.repository.ContractRepository;
import com.contract.review.repository.ReviewResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 审查报告服务
 * 聚合各 Agent 审查结果，生成最终报告，支持 JSON/PDF 导出
 */
@Service
public class ReviewReportService {

    private static final Logger log = LoggerFactory.getLogger(ReviewReportService.class);

    private final ContractRepository contractRepository;
    private final ReviewResultRepository reviewResultRepository;

    public ReviewReportService(ContractRepository contractRepository,
                               ReviewResultRepository reviewResultRepository) {
        this.contractRepository = contractRepository;
        this.reviewResultRepository = reviewResultRepository;
    }

    /**
     * 生成审查报告
     * @param contractId 合同ID
     * @param agentResults 各 Agent 审查结果
     * @return Report 实体
     */
    public Report generateReport(Long contractId, Map<String, Object> agentResults) {
        log.info("生成审查报告: contractId={}", contractId);

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("合同不存在: " + contractId));

        // 构建报告
        String reportNo = "RPT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + contractId;

        Report report = Report.builder()
                .contract(contract)
                .reportNo(reportNo)
                .title(contract.getTitle() + " - 审查报告")
                .riskLevel(determineRiskLevel(agentResults))
                .overallScore(calculateOverallScore(agentResults))
                .summary(generateSummary(contract, agentResults))
                .keyFindings(extractKeyFindings(agentResults))
                .clauseDetails(JSON.toJSONString(agentResults.get("clauseResults")))
                .complianceResults(JSON.toJSONString(agentResults.get("ComplianceAgent")))
                .riskAssessment(JSON.toJSONString(agentResults.get("RiskAgent")))
                .businessAnalysis(JSON.toJSONString(agentResults.get("BusinessTermAgent")))
                .recommendations(extractRecommendations(agentResults))
                .negotiationStrategy(generateNegotiationStrategy(agentResults))
                .exportFormat("json")
                .build();

        // 更新合同审查结果
        updateContractReviewResult(contract, agentResults, report.getOverallScore());

        log.info("审查报告生成完成: reportNo={}", reportNo);
        return report;
    }

    /**
     * 生成审查摘要
     */
    private String generateSummary(Contract contract, Map<String, Object> results) {
        int highRisk = (int) results.getOrDefault("highRiskCount", 0);
        int totalClauses = (int) results.getOrDefault("totalClauses", 0);
        int duration = (int) results.getOrDefault("totalDuration", 0);

        return String.format("对合同《%s》进行了全面审查。共审查 %d 个条款，" +
                        "发现高风险条款 %d 个，审查耗时 %d 毫秒。",
                contract.getTitle(), totalClauses, highRisk, duration);
    }

    /**
     * 提取关键发现
     */
    private String extractKeyFindings(Map<String, Object> results) {
        List<String> findings = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clauseResults =
                (List<Map<String, Object>>) results.get("clauseResults");

        if (clauseResults != null) {
            for (Map<String, Object> cr : clauseResults) {
                if ("HIGH".equals(cr.get("overallRiskLevel"))) {
                    findings.add("高风险条款: " + cr.get("clauseNumber"));
                }
            }
        }

        if (findings.isEmpty()) {
            findings.add("未发现高风险条款");
        }

        return String.join("; ", findings);
    }

    /**
     * 提取修改建议
     */
    private String extractRecommendations(Map<String, Object> results) {
        List<String> suggestions = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clauseResults =
                (List<Map<String, Object>>) results.get("clauseResults");

        if (clauseResults != null) {
            for (Map<String, Object> cr : clauseResults) {
                var compliance = (Map<String, Object>) cr.get("compliance");
                var risk = (Map<String, Object>) cr.get("risk");

                if (compliance != null && compliance.get("suggestion") != null) {
                    suggestions.add("条款" + cr.get("clauseNumber") + ": "
                            + compliance.get("suggestion"));
                }
            }
        }

        return suggestions.isEmpty() ? "无具体修改建议" : String.join("\n", suggestions);
    }

    /**
     * 生成谈判策略
     */
    private String generateNegotiationStrategy(Map<String, Object> results) {
        // 基于风险分析生成谈判建议
        int highRisk = (int) results.getOrDefault("highRiskCount", 0);
        if (highRisk > 0) {
            return "建议重点谈判高风险条款，争取降低风险等级；对于无法修改的条款，评估是否接受风险。";
        }
        return "合同整体风险可控，可按标准流程签署。";
    }

    private RiskLevel determineRiskLevel(Map<String, Object> results) {
        int highRisk = (int) results.getOrDefault("highRiskCount", 0);
        if (highRisk > 2) return RiskLevel.HIGH;
        if (highRisk > 0) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private Integer calculateOverallScore(Map<String, Object> results) {
        int highRisk = (int) results.getOrDefault("highRiskCount", 0);
        int total = (int) results.getOrDefault("totalClauses", 1);

        if (total == 0) return 100;
        int score = 100 - (highRisk * 100 / total);
        return Math.max(0, Math.min(100, score));
    }

    private void updateContractReviewResult(Contract contract, Map<String, Object> results, int score) {
        ReviewResult reviewResult = ReviewResult.builder()
                .contract(contract)
                .overallScore(score)
                .riskLevel(determineRiskLevel(results))
                .highRiskCount((int) results.getOrDefault("highRiskCount", 0))
                .mediumRiskCount((int) results.getOrDefault("mediumRiskCount", 0))
                .lowRiskCount((int) results.getOrDefault("lowRiskCount", 0))
                .totalClauses((int) results.getOrDefault("totalClauses", 0))
                .summary(generateSummary(contract, results))
                .agentResults(JSON.toJSONString(results))
                .reviewDuration((Long) results.getOrDefault("totalDuration", 0L))
                .build();

        reviewResultRepository.save(reviewResult);

        contract.setOverallScore(score);
        contract.setRiskLevel(determineRiskLevel(results));
        contract.setReviewDuration((Long) results.getOrDefault("totalDuration", 0L));
        contractRepository.save(contract);
    }
}

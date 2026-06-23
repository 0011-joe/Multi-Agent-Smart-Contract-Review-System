package com.contract.review.agent;

import com.contract.review.model.Contract;
import com.contract.review.model.ContractClause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Agent 执行编排器
 * 实现多 Agent 并行执行，收集各 Agent 审查结果
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ComplianceAgent complianceAgent;
    private final RiskAgent riskAgent;
    private final BusinessTermAgent businessTermAgent;

    /** 并行执行线程池 */
    private final ExecutorService executorService;

    /** Agent 超时时间（秒） */
    private static final long AGENT_TIMEOUT_SECONDS = 60;

    public AgentOrchestrator(ComplianceAgent complianceAgent,
                             RiskAgent riskAgent,
                             BusinessTermAgent businessTermAgent) {
        this.complianceAgent = complianceAgent;
        this.riskAgent = riskAgent;
        this.businessTermAgent = businessTermAgent;

        // 创建虚拟线程池（JDK 21）或回退到平台线程
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 对合同所有条款执行全 Agent 审查
     * @param contract 合同实体
     * @return 审查结果映射
     */
    public Map<String, Object> executeAll(Contract contract) {
        log.info("Agent编排器: 开始审查合同 '{}' (ID: {}), 共 {} 个条款",
                contract.getTitle(), contract.getId(),
                contract.getClauses() != null ? contract.getClauses().size() : 0);

        long startTime = System.currentTimeMillis();

        List<ContractClause> clauses = contract.getClauses();
        if (clauses == null || clauses.isEmpty()) {
            log.warn("合同无条款可审查");
            return Map.of("error", "合同无条款", "status", "skipped");
        }

        // 对每个条款并行执行所有 Agent
        List<CompletableFuture<ClauseReviewResult>> clauseFutures = clauses.stream()
                .map(clause -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return reviewClause(clause);
                    } catch (Exception e) {
                        log.error("条款审查失败: {} - {}", clause.getClauseNumber(), e.getMessage());
                        return new ClauseReviewResult(clause, Map.of("error", e.getMessage()));
                    }
                }, executorService))
                .toList();

        // 等待所有条款审查完成
        List<ClauseReviewResult> results = clauseFutures.stream()
                .map(future -> {
                    try {
                        return future.get(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        log.warn("条款审查超时");
                        return null;
                    } catch (Exception e) {
                        log.warn("条款审查异常", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 汇总结果
        Map<String, Object> aggregatedResult = aggregateResults(contract, results);

        long duration = System.currentTimeMillis() - startTime;
        aggregatedResult.put("totalDuration", duration);
        aggregatedResult.put("totalClausesReviewed", results.size());

        log.info("Agent编排器: 审查完成, 耗时: {}ms", duration);
        return aggregatedResult;
    }

    /**
     * 对单个条款执行所有 Agent
     */
    public ClauseReviewResult reviewClause(ContractClause clause) {
        String content = clause.getContent();
        String type = clause.getClauseType() != null ? clause.getClauseType() : "通用条款";

        // 并行调用三个 Agent
        CompletableFuture<Map<String, Object>> complianceFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return complianceAgent.execute(List.of(content, type));
                    } catch (Exception e) {
                        log.error("合规Agent执行失败", e);
                        return Map.of("error", e.getMessage());
                    }
                }, executorService);

        CompletableFuture<Map<String, Object>> riskFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return riskAgent.execute(List.of(content, type));
                    } catch (Exception e) {
                        log.error("风险Agent执行失败", e);
                        return Map.of("error", e.getMessage());
                    }
                }, executorService);

        CompletableFuture<Map<String, Object>> businessFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return businessTermAgent.execute(List.of(content, type));
                    } catch (Exception e) {
                        log.error("商务Agent执行失败", e);
                        return Map.of("error", e.getMessage());
                    }
                }, executorService);

        // 等待所有 Agent 完成
        try {
            CompletableFuture.allOf(complianceFuture, riskFuture, businessFuture)
                    .get(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Agent并行执行异常", e);
        }

        Map<String, Object> complianceResult = complianceFuture.getNow(Map.of("error", "超时"));
        Map<String, Object> riskResult = riskFuture.getNow(Map.of("error", "超时"));
        Map<String, Object> businessResult = businessFuture.getNow(Map.of("error", "超时"));

        return new ClauseReviewResult(clause, complianceResult, riskResult, businessResult);
    }

    /**
     * 汇总所有条款的审查结果
     */
    private Map<String, Object> aggregateResults(Contract contract, List<ClauseReviewResult> results) {
        Map<String, Object> aggregated = new HashMap<>();
        aggregated.put("contractId", contract.getId());
        aggregated.put("contractTitle", contract.getTitle());

        // 汇总各 Agent 的总体评估
        List<Map<String, Object>> clauseResults = results.stream()
                .map(r -> {
                    Map<String, Object> clauseMap = new HashMap<>();
                    clauseMap.put("clauseId", r.clause.getId());
                    clauseMap.put("clauseNumber", r.clause.getClauseNumber());
                    clauseMap.put("clauseType", r.clause.getClauseType());
                    clauseMap.put("compliance", r.compliance);
                    clauseMap.put("risk", r.risk);
                    clauseMap.put("businessTerm", r.businessTerm);

                    // 综合风险等级
                    String riskLevel = determineOverallRisk(r);
                    clauseMap.put("overallRiskLevel", riskLevel);
                    return clauseMap;
                })
                .collect(Collectors.toList());

        aggregated.put("clauseResults", clauseResults);

        // 统计风险分布
        long highRiskCount = clauseResults.stream()
                .filter(r -> "HIGH".equals(r.get("overallRiskLevel")))
                .count();
        long mediumRiskCount = clauseResults.stream()
                .filter(r -> "MEDIUM".equals(r.get("overallRiskLevel")))
                .count();
        long lowRiskCount = clauseResults.stream()
                .filter(r -> "LOW".equals(r.get("overallRiskLevel")))
                .count();

        aggregated.put("highRiskCount", (int) highRiskCount);
        aggregated.put("mediumRiskCount", (int) mediumRiskCount);
        aggregated.put("lowRiskCount", (int) lowRiskCount);
        aggregated.put("totalClauses", results.size());

        return aggregated;
    }

    /**
     * 综合判断条款风险等级
     */
    private String determineOverallRisk(ClauseReviewResult result) {
        String[] levels = {
                (String) result.compliance.get("riskLevel"),
                (String) result.risk.get("riskLevel"),
                (String) result.businessTerm.get("riskLevel")
        };

        for (String level : levels) {
            if ("HIGH".equals(level)) return "HIGH";
        }
        for (String level : levels) {
            if ("MEDIUM".equals(level)) return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * 单条款审查结果内部类
     */
    public static class ClauseReviewResult {
        public final ContractClause clause;
        public final Map<String, Object> compliance;
        public final Map<String, Object> risk;
        public final Map<String, Object> businessTerm;

        public ClauseReviewResult(ContractClause clause,
                                  Map<String, Object> compliance,
                                  Map<String, Object> risk,
                                  Map<String, Object> businessTerm) {
            this.clause = clause;
            this.compliance = compliance;
            this.risk = risk;
            this.businessTerm = businessTerm;
        }

        public ClauseReviewResult(ContractClause clause, Map<String, Object> error) {
            this(clause, error, error, error);
        }
    }
}

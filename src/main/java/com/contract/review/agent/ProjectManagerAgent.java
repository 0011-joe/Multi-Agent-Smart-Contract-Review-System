package com.contract.review.agent;

import com.alibaba.fastjson2.JSON;
import com.contract.review.model.Contract;
import com.contract.review.model.TaskStatus;
import com.contract.review.service.AsyncTaskQueue;
import com.contract.review.service.StreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 总控 Agent（Project Manager）
 * 分配子任务给各专家 Agent，监控任务状态，汇总所有结果
 */
@Component
public class ProjectManagerAgent {

    private static final Logger log = LoggerFactory.getLogger(ProjectManagerAgent.class);

    private final ComplianceAgent complianceAgent;
    private final RiskAgent riskAgent;
    private final BusinessTermAgent businessTermAgent;
    private final AsyncTaskQueue taskQueue;
    private final StreamingService streamingService;

    private final ExecutorService executor;

    public ProjectManagerAgent(ComplianceAgent complianceAgent,
                               RiskAgent riskAgent,
                               BusinessTermAgent businessTermAgent,
                               AsyncTaskQueue taskQueue,
                               StreamingService streamingService) {
        this.complianceAgent = complianceAgent;
        this.riskAgent = riskAgent;
        this.businessTermAgent = businessTermAgent;
        this.taskQueue = taskQueue;
        this.streamingService = streamingService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 执行完整的合同审查流程
     * 分配子任务 -> 监控执行 -> 汇总结果
     */
    public Map<String, Object> executeFullReview(Contract contract, String taskId) {
        log.info("总控Agent: 启动合同审查 taskId={}, contract={}", taskId, contract.getTitle());

        long startTime = System.currentTimeMillis();

        // 1. 构建子任务列表
        List<AgentTask> subTasks = buildSubTasks(contract);

        // 2. 并行执行子任务
        List<CompletableFuture<AgentTaskResult>> futures = subTasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return executeSubTask(task, taskId);
                    } catch (Exception e) {
                        log.error("子任务执行失败: {}", task.agentName, e);
                        return new AgentTaskResult(task, Map.of("error", e.getMessage()), true);
                    }
                }, executor))
                .toList();

        // 3. 等待所有子任务完成
        List<AgentTaskResult> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get(5, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("子任务超时", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. 汇总结果
        Map<String, Object> aggregated = aggregateResults(contract, results, taskId);

        long duration = System.currentTimeMillis() - startTime;
        aggregated.put("totalDuration", duration);
        aggregated.put("taskId", taskId);
        aggregated.put("status", TaskStatus.DONE);

        // 5. 推送完成事件
        streamingService.pushComplete(taskId, aggregated);

        log.info("总控Agent: 审查完成 taskId={}, 耗时={}ms", taskId, duration);
        return aggregated;
    }

    /**
     * 执行单个子任务
     */
    private AgentTaskResult executeSubTask(AgentTask task, String taskId) {
        log.info("执行子任务: agent={}, clauseCount={}", task.agentName, task.clauses.size());

        List<Map<String, Object>> clauseResults = new ArrayList<>();

        for (int i = 0; i < task.clauses.size(); i++) {
            var clause = task.clauses.get(i);
            Map<String, Object> result;

            try {
                result = switch (task.agentName) {
                    case "ComplianceAgent" ->
                            complianceAgent.execute(List.of(clause.getContent(), clause.getClauseType()));
                    case "RiskAgent" ->
                            riskAgent.execute(List.of(clause.getContent(), clause.getClauseType()));
                    case "BusinessTermAgent" ->
                            businessTermAgent.execute(List.of(clause.getContent(), clause.getClauseType()));
                    default -> Map.of("error", "未知Agent");
                };
            } catch (Exception e) {
                result = Map.of("error", e.getMessage());
            }

            result.put("clauseIndex", i);
            result.put("clauseNumber", clause.getClauseNumber());
            clauseResults.add(result);

            // 推送进度
            taskQueue.produce(taskId, task.agentName, Map.of(
                    "progress", (i + 1) + "/" + task.clauses.size(),
                    "clauseNumber", clause.getClauseNumber(),
                    "result", result
            ));
        }

        return new AgentTaskResult(task, Map.of(
                "totalClauses", task.clauses.size(),
                "results", clauseResults
        ), false);
    }

    /**
     * 构建子任务列表（按 Agent 分组）
     */
    private List<AgentTask> buildSubTasks(Contract contract) {
        var clauses = contract.getClauses();
        if (clauses == null || clauses.isEmpty()) {
            log.warn("合同无条款，跳过子任务构建");
            return List.of();
        }

        return List.of(
                new AgentTask("ComplianceAgent", "合规审查", clauses),
                new AgentTask("RiskAgent", "风险评估", clauses),
                new AgentTask("BusinessTermAgent", "商务条款分析", clauses)
        );
    }

    /**
     * 汇总所有子任务结果
     */
    private Map<String, Object> aggregateResults(Contract contract,
                                                  List<AgentTaskResult> results,
                                                  String taskId) {
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("contractId", contract.getId());
        aggregated.put("contractTitle", contract.getTitle());

        for (AgentTaskResult result : results) {
            String agentName = result.task.agentName;
            if (!result.isError) {
                aggregated.put(agentName, result.data);
            } else {
                aggregated.put(agentName + "_error", result.data);
            }

            // 推送至 SSE
            streamingService.pushEvent(taskId, "agent-complete", Map.of(
                    "agentName", agentName,
                    "status", result.isError ? "failed" : "completed"
            ));
        }

        // 综合风险等级
        aggregated.put("overallRiskLevel", calculateOverallRisk(results));
        aggregated.put("status", "completed");

        return aggregated;
    }

    private String calculateOverallRisk(List<AgentTaskResult> results) {
        for (var result : results) {
            if (result.data != null) {
                String risk = (String) result.data.get("riskLevel");
                if ("HIGH".equals(risk)) return "HIGH";
            }
        }
        return "LOW";
    }

    /** 子任务定义 */
    public record AgentTask(String agentName, String description,
                            List<com.contract.review.model.ContractClause> clauses) {}

    /** 子任务执行结果 */
    public record AgentTaskResult(AgentTask task, Map<String, Object> data, boolean isError) {}
}

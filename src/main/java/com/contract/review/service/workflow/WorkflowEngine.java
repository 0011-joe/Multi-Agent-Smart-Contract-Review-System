package com.contract.review.service.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 工作流编排引擎
 * 支持 PARALLEL 与 SEQUENCE 任务类型，定义任务依赖关系
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** 工作流定义 */
    private final Map<String, WorkflowDefinition> workflows = new ConcurrentHashMap<>();

    /** 运行中的工作流实例 */
    private final Map<String, WorkflowInstance> instances = new ConcurrentHashMap<>();

    /**
     * 注册工作流定义
     */
    public void registerWorkflow(String name, WorkflowDefinition definition) {
        workflows.put(name, definition);
        log.info("注册工作流: {}, 共 {} 个任务", name, definition.tasks().size());
    }

    /**
     * 执行工作流
     */
    public WorkflowInstance execute(String workflowName, Map<String, Object> context) {
        WorkflowDefinition def = workflows.get(workflowName);
        if (def == null) {
            throw new IllegalArgumentException("未找到工作流: " + workflowName);
        }

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        WorkflowInstance instance = new WorkflowInstance(instanceId, workflowName, def, context);
        instances.put(instanceId, instance);

        log.info("启动工作流实例: {} -> {}", instanceId, workflowName);
        executeTasks(instance);

        return instance;
    }

    /**
     * 执行工作流任务（拓扑序）
     */
    private void executeTasks(WorkflowInstance instance) {
        WorkflowDefinition def = instance.definition;
        Map<String, CompletableFuture<Map<String, Object>>> futures = new HashMap<>();

        // 按依赖关系逐层执行
        while (futures.size() < def.tasks().size()) {
            for (WorkflowTask task : def.tasks()) {
                if (futures.containsKey(task.name())) continue;

                // 检查依赖是否全部完成
                List<String> deps = task.dependsOn() != null ? task.dependsOn() : List.of();
                boolean allDepsDone = deps.stream().allMatch(futures::containsKey);

                if (!allDepsDone) continue;

                // 收集依赖结果
                Map<String, Object> depResults = new HashMap<>();
                for (String dep : deps) {
                    CompletableFuture<Map<String, Object>> depFuture = futures.get(dep);
                    if (depFuture != null && depFuture.isDone()) {
                        try {
                            depResults.put(dep, depFuture.get());
                        } catch (Exception e) {
                            depResults.put(dep, Map.of("error", e.getMessage()));
                        }
                    }
                }

                // 提交任务
                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    log.info("执行任务: {} (类型: {})", task.name(), task.type());
                    try {
                        Map<String, Object> result = task.action().apply(
                                new TaskContext(task.name(), instance.context, depResults));
                        instance.results.put(task.name(), result);
                        instance.completedTasks.add(task.name());
                        log.info("任务完成: {}", task.name());
                        return result;
                    } catch (Exception e) {
                        log.error("任务失败: {}", task.name(), e);
                        instance.errors.put(task.name(), e.getMessage());
                        throw e;
                    }
                }, executor);

                futures.put(task.name(), future);
            }

            // 检查是否有进展
            if (futures.size() == instance.completedTasks.size() + instance.errors.size()) {
                // 所有可达任务已完成或失败
                break;
            }

            // 等待一小段时间后继续检查
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 等待所有任务完成
        futures.values().forEach(f -> {
            try { f.get(5, TimeUnit.MINUTES); } catch (Exception ignored) {}
        });

        instance.status = "completed";
        log.info("工作流实例完成: {}, 成功: {}, 失败: {}",
                instance.instanceId, instance.completedTasks.size(), instance.errors.size());
    }

    /**
     * 获取工作流实例状态
     */
    public WorkflowInstance getInstance(String instanceId) {
        return instances.get(instanceId);
    }

    /**
     * 默认的工作流：合同审查流程
     */
    public WorkflowDefinition createDefaultReviewWorkflow() {
        return new WorkflowDefinition("合同审查流程", List.of(
                new WorkflowTask("document_parse", "文档解析", "SEQUENCE", List.of(),
                        ctx -> Map.of("status", "parsed", "content", ctx.context.get("contractContent"))),
                new WorkflowTask("clause_extract", "条款提取", "SEQUENCE", List.of("document_parse"),
                        ctx -> Map.of("status", "extracted", "clauseCount", 0)),
                new WorkflowTask("compliance_check", "合规审查", "PARALLEL", List.of("clause_extract"),
                        ctx -> Map.of("status", "checked", "riskLevel", "LOW")),
                new WorkflowTask("risk_assessment", "风险评估", "PARALLEL", List.of("clause_extract"),
                        ctx -> Map.of("status", "assessed", "riskScore", 30)),
                new WorkflowTask("business_analysis", "商务分析", "PARALLEL", List.of("clause_extract"),
                        ctx -> Map.of("status", "analyzed", "amount", null)),
                new WorkflowTask("report_generation", "报告生成", "SEQUENCE",
                        List.of("compliance_check", "risk_assessment", "business_analysis"),
                        ctx -> Map.of("status", "generated", "summary", "审查完成"))
        ));
    }

    // ===== 内部类型 =====

    public record WorkflowDefinition(String name, List<WorkflowTask> tasks) {}

    public record WorkflowTask(String name, String description, String type,
                               List<String> dependsOn,
                               Function<TaskContext, Map<String, Object>> action) {}

    public record TaskContext(String taskName, Map<String, Object> context,
                              Map<String, Object> dependencyResults) {}

    public static class WorkflowInstance {
        public final String instanceId;
        public final String workflowName;
        public final WorkflowDefinition definition;
        public final Map<String, Object> context;
        public final Map<String, Object> results = new ConcurrentHashMap<>();
        public final Set<String> completedTasks = ConcurrentHashMap.newKeySet();
        public final Map<String, String> errors = new ConcurrentHashMap<>();
        public String status = "running";

        public WorkflowInstance(String instanceId, String workflowName,
                                WorkflowDefinition definition, Map<String, Object> context) {
            this.instanceId = instanceId;
            this.workflowName = workflowName;
            this.definition = definition;
            this.context = context;
        }
    }
}

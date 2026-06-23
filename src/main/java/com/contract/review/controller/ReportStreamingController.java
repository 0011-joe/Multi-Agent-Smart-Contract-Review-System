package com.contract.review.controller;

import com.contract.review.model.Contract;
import com.contract.review.model.TaskStatus;
import com.contract.review.repository.ContractRepository;
import com.contract.review.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式输出控制器
 * 使用 Server-Sent Events 实现审查结果流式推送
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "流式审查", description = "SSE 流式审查接口")
public class ReportStreamingController {

    private static final Logger log = LoggerFactory.getLogger(ReportStreamingController.class);

    private final ContractRepository contractRepository;
    private final StreamingService streamingService;

    /** 任务状态缓存（生产环境应使用 Redis） */
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    public ReportStreamingController(ContractRepository contractRepository,
                                     StreamingService streamingService) {
        this.contractRepository = contractRepository;
        this.streamingService = streamingService;
    }

    /**
     * 提交审查任务（异步）
     * POST /api/v1/review/{contractId}
     */
    @PostMapping("/review/{contractId}")
    @Operation(summary = "提交审查任务", description = "异步启动合同审查，返回 taskId")
    public ResponseEntity<Map<String, Object>> startReview(@PathVariable Long contractId) {
        log.info("提交审查任务: contractId={}", contractId);

        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }

        // 生成任务 ID
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        taskStatusMap.put(taskId, TaskStatus.RUNNING);

        log.info("审查任务已创建: taskId={}, contractId={}", taskId, contractId);

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "contractId", contractId,
                "status", TaskStatus.RUNNING,
                "streamUrl", "/api/v1/stream/" + taskId
        ));
    }

    /**
     * SSE 流式获取审查结果
     * GET /api/v1/stream/{taskId}
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 流式获取审查结果", description = "通过 Server-Sent Events 实时获取审查进度和结果")
    public SseEmitter streamReview(@PathVariable String taskId) {
        log.info("SSE 流式连接: taskId={}", taskId);

        TaskStatus status = taskStatusMap.getOrDefault(taskId, TaskStatus.PENDING);

        if (status == TaskStatus.DONE || status == TaskStatus.FAILED) {
            log.warn("任务已结束: taskId={}, status={}", taskId, status);
            SseEmitter done = new SseEmitter(0L);
            try {
                done.send(SseEmitter.event()
                        .name("status")
                        .data(Map.of("taskId", taskId, "status", status)));
                done.complete();
            } catch (Exception e) {
                // ignore
            }
            return done;
        }

        return streamingService.registerEmitter(taskId);
    }

    /**
     * 获取任务状态
     * GET /api/v1/tasks/{taskId}/status
     */
    @GetMapping("/tasks/{taskId}/status")
    @Operation(summary = "获取任务状态", description = "查询审查任务的当前状态")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status = taskStatusMap.getOrDefault(taskId, TaskStatus.PENDING);
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", status
        ));
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, TaskStatus status) {
        taskStatusMap.put(taskId, status);
        log.info("任务状态更新: taskId={}, status={}", taskId, status);
    }
}

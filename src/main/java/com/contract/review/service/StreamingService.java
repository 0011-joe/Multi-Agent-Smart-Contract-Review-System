package com.contract.review.service;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 推送服务
 * 订阅任务队列中的新结果，实时推送给前端（JSON格式）
 * 处理连接断开与重连
 */
@Service
public class StreamingService {

    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);

    private final AsyncTaskQueue taskQueue;

    /** SSE 连接池 <taskId, SseEmitter> */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 推送线程 */
    private final ExecutorService streamingExecutor;

    /** SSE 超时时间（30分钟） */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    public StreamingService(AsyncTaskQueue taskQueue) {
        this.taskQueue = taskQueue;
        this.streamingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-push-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 注册 SSE 连接
     */
    public SseEmitter registerEmitter(String taskId) {
        log.info("注册 SSE 连接: taskId={}", taskId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(taskId, emitter);

        // 完成回调
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成: taskId={}", taskId);
            emitters.remove(taskId);
        });

        // 超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: taskId={}", taskId);
            emitters.remove(taskId);
        });

        // 错误回调
        emitter.onError(e -> {
            log.error("SSE 连接错误: taskId={}, error={}", taskId, e.getMessage());
            emitters.remove(taskId);
        });

        // 发送初始连接确认
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(JSON.toJSONString(Map.of(
                            "taskId", taskId,
                            "status", "connected",
                            "timestamp", System.currentTimeMillis()
                    ))));
        } catch (IOException e) {
            log.error("SSE 初始连接发送失败", e);
            emitters.remove(taskId);
        }

        // 启动推送监听
        startPushListener(taskId, emitter);

        return emitter;
    }

    /**
     * 启动推送监听线程
     */
    private void startPushListener(String taskId, SseEmitter emitter) {
        streamingExecutor.submit(() -> {
            try {
                while (true) {
                    // 从队列消费
                    AsyncTaskQueue.TaskMessage message = taskQueue.consume();
                    if (message != null && message.taskId().equals(taskId)) {
                        // 推送结果
                        emitter.send(SseEmitter.event()
                                .name("agent-result")
                                .data(JSON.toJSONString(Map.of(
                                        "taskId", taskId,
                                        "agentName", message.agentName(),
                                        "result", message.result(),
                                        "sequence", message.sequence(),
                                        "timestamp", System.currentTimeMillis()
                                ))));
                        log.debug("SSE 推送: agent={}, seq={}", message.agentName(), message.sequence());
                    }

                    // 检查是否完成
                    if (taskQueue.isEmpty()) {
                        Thread.sleep(100); // 避免空转
                    }
                }
            } catch (IOException e) {
                log.warn("SSE 推送 IO 异常: {}", e.getMessage());
                emitters.remove(taskId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("SSE 推送线程中断");
            } catch (Exception e) {
                log.error("SSE 推送异常", e);
                emitters.remove(taskId);
            }
        });
    }

    /**
     * 主动推送消息
     */
    public void pushEvent(String taskId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(JSON.toJSONString(data)));
            } catch (IOException e) {
                log.warn("推送失败, 移除连接: taskId={}", taskId);
                emitters.remove(taskId);
            }
        }
    }

    /**
     * 推送审查完成事件
     */
    public void pushComplete(String taskId, Map<String, Object> finalResult) {
        pushEvent(taskId, "complete", Map.of(
                "taskId", taskId,
                "status", "complete",
                "result", finalResult
        ));
        log.info("SSE 推送完成事件: taskId={}", taskId);
    }

    /**
     * 推送错误事件
     */
    public void pushError(String taskId, String error) {
        pushEvent(taskId, "error", Map.of(
                "taskId", taskId,
                "error", error
        ));
    }

    /**
     * 断开连接
     */
    public void disconnect(String taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("断开 SSE 连接异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }
}

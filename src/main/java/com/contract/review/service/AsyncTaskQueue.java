package com.contract.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步任务队列
 * 使用 ConcurrentLinkedQueue 存储审查结果，生产者-消费者模式
 */
@Service
public class AsyncTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskQueue.class);

    /** 任务结果队列 */
    private final ConcurrentLinkedQueue<TaskMessage> queue = new ConcurrentLinkedQueue<>();

    /** 计数器 */
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * 异步生产任务结果（生产者）
     */
    @Async("taskExecutor")
    public void produce(String taskId, String agentName, Map<String, Object> result) {
        long seq = counter.incrementAndGet();
        TaskMessage message = new TaskMessage(taskId, agentName, result, seq);
        queue.offer(message);
        log.debug("任务入队: taskId={}, agent={}, seq={}", taskId, agentName, seq);
    }

    /**
     * 消费任务结果（消费者）
     */
    public TaskMessage consume() {
        TaskMessage message = queue.poll();
        if (message != null) {
            log.debug("任务出队: taskId={}, agent={}", message.taskId, message.agentName);
        }
        return message;
    }

    /**
     * 批量消费
     */
    public java.util.List<TaskMessage> drainAll() {
        java.util.List<TaskMessage> messages = new java.util.ArrayList<>();
        TaskMessage msg;
        while ((msg = queue.poll()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    /**
     * 队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 任务消息体
     */
    public record TaskMessage(String taskId, String agentName,
                              Map<String, Object> result, long sequence) {}
}

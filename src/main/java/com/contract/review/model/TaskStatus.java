package com.contract.review.model;

/**
 * 任务状态枚举
 * 用于跟踪审查任务的生命周期
 */
public enum TaskStatus {
    /** 已创建、待分配 */
    PENDING,
    /** 运行中 */
    RUNNING,
    /** 已完成 */
    DONE,
    /** 失败 */
    FAILED,
    /** 超时 */
    TIMEOUT,
    /** 已取消 */
    CANCELLED
}

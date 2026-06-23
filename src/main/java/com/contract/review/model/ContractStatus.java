package com.contract.review.model;

public enum ContractStatus {
    /** 待审查 */
    PENDING,
    /** 审查中 */
    REVIEWING,
    /** 审查完成 */
    COMPLETED,
    /** 审查失败 */
    FAILED
}

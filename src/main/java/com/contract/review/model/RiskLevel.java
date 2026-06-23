package com.contract.review.model;

/**
 * 风险等级枚举
 */
public enum RiskLevel {

    /** 高风险 - 必须修改 */
    HIGH("高风险", "条款存在重大法律/商业风险，必须修改"),
    /** 中风险 - 建议修改 */
    MEDIUM("中风险", "条款存在一定风险，建议修改或进一步确认"),
    /** 低风险 - 可接受 */
    LOW("低风险", "条款风险较低，可接受或仅需微小调整"),
    /** 无风险 - 合规 */
    NONE("无风险", "条款符合标准规范，无风险");

    private final String displayName;
    private final String description;

    RiskLevel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

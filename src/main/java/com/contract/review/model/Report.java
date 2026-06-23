package com.contract.review.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审查报告实体
 * 聚合各 Agent 审查结果，支持 JSON/PDF 导出
 */
@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联合同 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @ToString.Exclude
    private Contract contract;

    /** 报告编号 */
    @Column(unique = true)
    private String reportNo;

    /** 报告标题 */
    private String title;

    /** 总体风险等级 */
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /** 综合评分 (0-100) */
    private Integer overallScore;

    /** 审查摘要 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 关键发现 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String keyFindings;

    /** 条款审查详情 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String clauseDetails;

    /** 合规检查结果 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String complianceResults;

    /** 风险评估详情 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String riskAssessment;

    /** 商务条款分析 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String businessAnalysis;

    /** 修改建议汇总 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String recommendations;

    /** 谈判策略 */
    @Column(columnDefinition = "TEXT")
    private String negotiationStrategy;

    /** 报告格式 (json/pdf) */
    private String exportFormat;

    /** 报告版本 */
    @Builder.Default
    private String version = "1.0";

    /** 创建时间 */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 更新时间 */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

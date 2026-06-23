package com.contract.review.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审查结果实体
 */
@Entity
@Table(name = "review_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联合同 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @ToString.Exclude
    private Contract contract;

    /** 综合评分 (0-100) */
    private Integer overallScore;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /** 高风险条款数 */
    private Integer highRiskCount;

    /** 中风险条款数 */
    private Integer mediumRiskCount;

    /** 低风险条款数 */
    private Integer lowRiskCount;

    /** 总审查条款数 */
    private Integer totalClauses;

    /** 审查摘要 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 总体评价 */
    @Column(columnDefinition = "TEXT")
    private String overallAssessment;

    /** 关键发现 */
    @Column(columnDefinition = "TEXT")
    private String keyFindings;

    /** 修改建议汇总 */
    @Column(columnDefinition = "TEXT")
    private String recommendations;

    /** 谈判策略建议 */
    @Column(columnDefinition = "TEXT")
    private String negotiationStrategy;

    /** 各 Agent 审查结果 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String agentResults;

    /** 合规检查结果 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String complianceCheck;

    /** 审查耗时（秒） */
    private Long reviewDuration;

    /** 审查完成时间 */
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
}

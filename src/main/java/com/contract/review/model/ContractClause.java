package com.contract.review.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 合同条款实体
 */
@Entity
@Table(name = "contract_clauses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "contract")
public class ContractClause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属合同 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @ToString.Exclude
    private Contract contract;

    /** 条款编号（如：第1条、第2.1条） */
    private String clauseNumber;

    /** 条款标题 */
    private String title;

    /** 条款原文 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 条款类型（如：付款条款、违约责任、保密条款、管辖条款等） */
    private String clauseType;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /** 风险描述 */
    @Column(columnDefinition = "TEXT")
    private String riskDescription;

    /** 修改建议 */
    @Column(columnDefinition = "TEXT")
    private String suggestion;

    /** 推荐修改后文本 */
    @Column(columnDefinition = "TEXT")
    private String recommendedText;

    /** 相关法律依据 */
    @Column(columnDefinition = "TEXT")
    private String legalBasis;

    /** AI 分析原始结果 */
    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;

    /** 分析时间 */
    @Builder.Default
    private LocalDateTime analyzedAt = LocalDateTime.now();
}

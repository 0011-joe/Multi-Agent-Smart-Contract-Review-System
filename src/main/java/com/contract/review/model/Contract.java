package com.contract.review.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同主实体
 */
@Entity
@Table(name = "contracts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "clauses")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 合同标题 */
    @Column(nullable = false)
    private String title;

    /** 合同编号 */
    @Column(unique = true)
    private String contractNo;

    /** 合同原文内容 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 合同类型（如：采购合同、劳务合同、保密协议、租赁合同等） */
    private String contractType;

    /** 合同方信息 */
    private String partyA;
    private String partyB;

    /** 合同金额 */
    private String contractAmount;

    /** 签署日期 */
    private LocalDateTime signDate;

    /** 生效日期 */
    private LocalDateTime effectiveDate;

    /** 到期日期 */
    private LocalDateTime expiryDate;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件格式（pdf / docx / txt / html） */
    private String fileFormat;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ContractStatus status = ContractStatus.PENDING;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /** 综合评分 (0-100) */
    private Integer overallScore;

    /** 审查耗时（秒） */
    private Long reviewDuration;

    /** 创建时间 */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 更新时间 */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** 合同条款列表 */
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<ContractClause> clauses = new ArrayList<>();

    /** 审查结果 */
    @OneToOne(mappedBy = "contract", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private ReviewResult reviewResult;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 添加条款 */
    public void addClause(ContractClause clause) {
        if (clauses == null) {
            clauses = new ArrayList<>();
        }
        clauses.add(clause);
        clause.setContract(this);
    }
}

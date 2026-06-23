package com.contract.review.dto;

import com.contract.review.model.ContractStatus;
import com.contract.review.model.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审查结果响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long contractId;
    private String title;
    private String contractType;
    private ContractStatus status;
    private RiskLevel riskLevel;
    private Integer overallScore;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
    private Integer totalClauses;
    private String summary;
    private String keyFindings;
    private String recommendations;
    private String negotiationStrategy;
    private String complianceCheck;
    private Long reviewDuration;
    private LocalDateTime completedAt;
    private String message;
}

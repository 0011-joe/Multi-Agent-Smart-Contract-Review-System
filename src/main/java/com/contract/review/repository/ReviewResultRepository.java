package com.contract.review.repository;

import com.contract.review.model.ReviewResult;
import com.contract.review.model.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审查结果数据仓库
 */
@Repository
public interface ReviewResultRepository extends JpaRepository<ReviewResult, Long> {

    /** 按合同 ID 查询审查结果 */
    Optional<ReviewResult> findByContractId(Long contractId);

    /** 按风险等级查询 */
    List<ReviewResult> findByRiskLevel(RiskLevel riskLevel);

    /** 按评分范围查询 */
    List<ReviewResult> findByOverallScoreBetween(Integer min, Integer max);
}

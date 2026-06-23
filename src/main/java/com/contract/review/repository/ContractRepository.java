package com.contract.review.repository;

import com.contract.review.model.Contract;
import com.contract.review.model.ContractStatus;
import com.contract.review.model.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 合同数据仓库
 */
@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    /** 按状态查询合同 */
    List<Contract> findByStatus(ContractStatus status);

    /** 按风险等级查询合同 */
    List<Contract> findByRiskLevel(RiskLevel riskLevel);

    /** 按合同类型查询 */
    List<Contract> findByContractType(String contractType);

    /** 按合同编号查询 */
    Optional<Contract> findByContractNo(String contractNo);

    /** 按标题模糊查询 */
    List<Contract> findByTitleContaining(String keyword);

    /** 按合同方查询 */
    List<Contract> findByPartyAContainingOrPartyBContaining(String partyA, String partyB);

    /** 查询最近的合同 */
    List<Contract> findTop20ByOrderByCreatedAtDesc();

    /** 统计各风险等级的合同数 */
    @Query("SELECT c.riskLevel, COUNT(c) FROM Contract c GROUP BY c.riskLevel")
    List<Object[]> countByRiskLevel();

    /** 统计各状态的合同数 */
    @Query("SELECT c.status, COUNT(c) FROM Contract c GROUP BY c.status")
    List<Object[]> countByStatus();
}

package com.contract.review.controller;

import com.contract.review.dto.ContractUploadRequest;
import com.contract.review.dto.ReviewResponse;
import com.contract.review.model.*;
import com.contract.review.repository.ContractRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同审查 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同审查", description = "合同上传、审查、报告获取 API")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractRepository contractRepository;

    public ContractController(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * 1. 合同上传
     * POST /api/v1/contracts/upload
     */
    @PostMapping("/upload")
    @Operation(summary = "上传合同", description = "上传合同内容进行审查")
    public ResponseEntity<ReviewResponse> uploadContract(@Valid @RequestBody ContractUploadRequest request) {
        log.info("接收到合同上传请求: {}", request.getTitle());

        // 创建合同实体
        Contract contract = Contract.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .contractType(request.getContractType())
                .partyA(request.getPartyA())
                .partyB(request.getPartyB())
                .contractAmount(request.getContractAmount())
                .originalFileName(request.getOriginalFileName())
                .fileFormat(request.getFileFormat())
                .status(ContractStatus.PENDING)
                .build();

        contract = contractRepository.save(contract);

        log.info("合同上传成功, ID: {}", contract.getId());

        ReviewResponse response = ReviewResponse.builder()
                .contractId(contract.getId())
                .title(contract.getTitle())
                .contractType(contract.getContractType())
                .status(contract.getStatus())
                .message("合同上传成功")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. 启动合同审查
     * POST /api/v1/contracts/{id}/review
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "启动审查", description = "对指定合同启动多 Agent 智能审查")
    public ResponseEntity<ReviewResponse> startReview(@PathVariable Long id) {
        log.info("启动合同审查, ID: {}", id);

        Contract contract = contractRepository.findById(id)
                .orElse(null);

        if (contract == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReviewResponse.builder()
                            .message("合同不存在, ID: " + id)
                            .build());
        }

        // 更新状态为审查中
        contract.setStatus(ContractStatus.REVIEWING);
        contractRepository.save(contract);

        // 审查将在后续功能中实现完整流程
        ReviewResponse response = ReviewResponse.builder()
                .contractId(contract.getId())
                .title(contract.getTitle())
                .status(ContractStatus.REVIEWING)
                .message("审查已启动，审查任务正在执行中")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 3. 获取审查报告
     * GET /api/v1/reports/{contractId}
     */
    @GetMapping("/{id}/report")
    @Operation(summary = "获取审查报告", description = "获取指定合同的审查报告")
    public ResponseEntity<ReviewResponse> getReport(@PathVariable Long id) {
        log.info("获取审查报告, 合同ID: {}", id);

        Contract contract = contractRepository.findById(id)
                .orElse(null);

        if (contract == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReviewResponse.builder()
                            .message("合同不存在, ID: " + id)
                            .build());
        }

        ReviewResult result = contract.getReviewResult();

        ReviewResponse response = ReviewResponse.builder()
                .contractId(contract.getId())
                .title(contract.getTitle())
                .contractType(contract.getContractType())
                .status(contract.getStatus())
                .riskLevel(contract.getRiskLevel())
                .overallScore(contract.getOverallScore())
                .message(result != null ? "审查报告已生成" : "审查尚未完成")
                .build();

        if (result != null) {
            response.setHighRiskCount(result.getHighRiskCount());
            response.setMediumRiskCount(result.getMediumRiskCount());
            response.setLowRiskCount(result.getLowRiskCount());
            response.setTotalClauses(result.getTotalClauses());
            response.setSummary(result.getSummary());
            response.setKeyFindings(result.getKeyFindings());
            response.setRecommendations(result.getRecommendations());
            response.setNegotiationStrategy(result.getNegotiationStrategy());
            response.setComplianceCheck(result.getComplianceCheck());
            response.setReviewDuration(result.getReviewDuration());
            response.setCompletedAt(result.getCompletedAt());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取合同列表
     */
    @GetMapping
    @Operation(summary = "获取合同列表", description = "获取所有已上传的合同列表")
    public ResponseEntity<List<Contract>> listContracts() {
        List<Contract> contracts = contractRepository.findTop20ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(contracts);
    }
}

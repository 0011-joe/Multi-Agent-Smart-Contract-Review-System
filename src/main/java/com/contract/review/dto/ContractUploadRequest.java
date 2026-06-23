package com.contract.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合同上传请求 DTO
 */
@Data
public class ContractUploadRequest {

    @NotBlank(message = "合同标题不能为空")
    private String title;

    @NotBlank(message = "合同内容不能为空")
    private String content;

    /** 合同类型（采购合同/劳务合同/保密协议/租赁合同等） */
    private String contractType;

    /** 甲方 */
    private String partyA;

    /** 乙方 */
    private String partyB;

    /** 合同金额 */
    private String contractAmount;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件格式 */
    private String fileFormat;
}

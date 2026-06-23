package com.contract.review.service.chunker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 文本切片实体
 * 包含切片ID、内容、元数据，用于向量化存储和检索
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chunk {

    /** 切片唯一ID */
    private String id;

    /** 切片内容 */
    private String content;

    /** 切片长度（字符数） */
    private Integer length;

    /** 来源文档ID */
    private Long documentId;

    /** 来源文档名称 */
    private String documentName;

    /** 章节路径（如：第三章/第二节） */
    private String sectionPath;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 切片类型（paragraph/section/article） */
    private String chunkType;

    /** 向量值（Float数组，由EmbeddingService填充） */
    private float[] embedding;

    /** 扩展元数据 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}

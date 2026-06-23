package com.contract.review.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索结果
 * 包含相关文本、原始文档元数据和相关性评分
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievalResult {

    /** 检索结果条目 */
    private List<RetrievalItem> items;

    /** 检索总数 */
    private Integer totalCount;

    /** 查询文本 */
    private String queryText;

    /** 检索耗时(ms) */
    private Long retrievalDuration;

    /** 是否命中缓存 */
    private boolean fromCache;

    /** 扩展信息 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetrievalItem {

        /** 切片ID */
        private String chunkId;

        /** 相关文本内容 */
        private String content;

        /** 相关性评分 (0.0 ~ 1.0) */
        private Double score;

        /** 重排后得分 (如有) */
        private Double rerankScore;

        /** 最终排序分 */
        private Double finalScore;

        /** 来源文档名称 */
        private String documentName;

        /** 来源文档ID */
        private Long documentId;

        /** 章节路径 */
        private String sectionPath;

        /** 切片序号 */
        private Integer chunkIndex;

        /** 扩展元数据 */
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }
}

package com.contract.review.service.chunker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档切块工具
 * 实现语义切分策略（按段落、章节），支持重叠块（Overlap）配置
 */
@Component
public class TextChunker {

    private static final Logger log = LoggerFactory.getLogger(TextChunker.class);

    /** 默认切片大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认重叠大小（字符数） */
    private static final int DEFAULT_OVERLAP = 50;

    /** 段落正则（中文章节标题） */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "((第[一二三四五六七八九十百千]+[章节条]|[条])[^。！？\\n]*[。！？])",
            Pattern.MULTILINE
    );

    /** 段落分割符 */
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "([^。！？\\n]+[。！？\\n])",
            Pattern.MULTILINE
    );

    /**
     * 按章节拆分文档
     * @param text 文档全文
     * @param chunkSize 切片大小（默认500）
     * @param overlap 重叠字符数（默认50）
     * @return 切片列表
     */
    public List<Chunk> splitBySection(String text, Integer chunkSize, Integer overlap) {
        int cs = chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
        int ol = overlap != null ? overlap : DEFAULT_OVERLAP;

        log.debug("按章节切分文档, 切片大小: {}, 重叠: {}", cs, ol);

        List<Chunk> chunks = new ArrayList<>();
        List<String> sections = extractSections(text);

        int globalIndex = 0;
        for (String section : sections) {
            List<Chunk> sectionChunks = splitText(section, cs, ol, globalIndex);
            chunks.addAll(sectionChunks);
            globalIndex = chunks.size();
        }

        log.info("文档切分完成, 共 {} 个切片", chunks.size());
        return chunks;
    }

    /**
     * 按段落拆分文档
     */
    public List<Chunk> splitByParagraph(String text, Long documentId, String documentName) {
        log.debug("按段落切分文档, ID: {}", documentId);

        List<Chunk> chunks = new ArrayList<>();
        Matcher matcher = PARAGRAPH_PATTERN.matcher(text);

        int index = 0;
        int pos = 0;
        StringBuilder currentChunk = new StringBuilder();

        while (matcher.find()) {
            String sentence = matcher.group(1).trim();
            if (sentence.isEmpty()) continue;

            // 遇到空行或章节标题时切分
            if (isSectionTitle(sentence) && currentChunk.length() > 0) {
                chunks.add(buildChunk(currentChunk.toString(), index++, documentId, documentName, "paragraph"));
                currentChunk = new StringBuilder();
            }

            currentChunk.append(sentence);
        }

        // 最后一块
        if (currentChunk.length() > 0) {
            chunks.add(buildChunk(currentChunk.toString(), index, documentId, documentName, "paragraph"));
        }

        log.info("段落切分完成, 共 {} 个切片", chunks.size());
        return chunks;
    }

    /**
     * 通用文本切分（按大小切割，支持重叠）
     */
    public List<Chunk> splitText(String text, int chunkSize, int overlap, int startIndex) {
        List<Chunk> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;
        int index = startIndex;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            // 尽量在句子边界切分
            if (end < length) {
                int sentenceEnd = findSentenceBoundary(text, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(Chunk.builder()
                        .id(UUID.randomUUID().toString())
                        .content(content)
                        .length(content.length())
                        .chunkIndex(index++)
                        .chunkType("text")
                        .build());
            }

            start = end - overlap;
            if (start >= end) break;
        }

        return chunks;
    }

    /**
     * 提取文档中的章节
     */
    public List<String> extractSections(String text) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = SECTION_PATTERN.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sections.add(text.substring(lastEnd, matcher.start()).trim());
            }
            lastEnd = matcher.start();
        }

        // 剩余尾部
        if (lastEnd < text.length()) {
            sections.add(text.substring(lastEnd).trim());
        }

        return sections.stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 判断是否为章节标题
     */
    private boolean isSectionTitle(String text) {
        return text.matches("^\\s*(第[一二三四五六七八九十百千]+[章节条]|\\d+\\.\\d*[\\s　]).*");
    }

    /**
     * 查找句子边界（。！？\n）
     */
    private int findSentenceBoundary(String text, int from) {
        String boundaryChars = "。！？\n";
        for (int i = from; i < text.length() && i < from + 100; i++) {
            if (boundaryChars.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return from;
    }

    /**
     * 构建 Chunk 对象
     */
    private Chunk buildChunk(String content, int index, Long docId, String docName, String type) {
        Chunk chunk = Chunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content.trim())
                .length(content.trim().length())
                .chunkIndex(index)
                .chunkType(type)
                .documentId(docId)
                .documentName(docName)
                .build();

        chunk.getMetadata().put("source", docName);
        chunk.getMetadata().put("chunkType", type);
        return chunk;
    }
}

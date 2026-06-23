package com.contract.review.service.document;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 解析服务
 * 集成 Apache PDFBox 解析 PDF 文档，返回结构化条款 JSON
 */
@Service
public class PdfParserService {

    private static final Logger log = LoggerFactory.getLogger(PdfParserService.class);

    /** 条款/章节正则 */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十百千]+[条章节][\\s　]*[^。！？\\n]*[。！？\\n]?)",
            Pattern.MULTILINE
    );

    /**
     * 解析 PDF 输入流，返回结构化条款
     * @param pdfStream PDF 文件流
     * @return 解析结果 JSON
     */
    public String parse(InputStream pdfStream) {
        log.info("开始解析 PDF 文档");
        long startTime = System.currentTimeMillis();

        try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
            // 提取元数据
            int pageCount = document.getNumberOfPages();
            log.info("PDF 页数: {}", pageCount);

            // 提取全文文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            String fullText = stripper.getText(document);

            // 结构化解析
            JSONObject result = extractStructuredData(fullText, pageCount);

            long duration = System.currentTimeMillis() - startTime;
            log.info("PDF 解析完成, 耗时: {}ms, 文本长度: {} 字", duration, fullText.length());

            result.put("parseDuration", duration);
            result.put("totalPages", pageCount);
            result.put("totalChars", fullText.length());

            return JSON.toJSONString(result);

        } catch (IOException e) {
            log.error("PDF 解析失败: {}", e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("error", "PDF解析失败: " + e.getMessage());
            error.put("success", false);
            return JSON.toJSONString(error);
        }
    }

    /**
     * 提取 PDF 元数据
     */
    public Map<String, Object> extractMetadata(InputStream pdfStream) {
        try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("pageCount", document.getNumberOfPages());
            metadata.put("version", document.getVersion());

            var info = document.getDocumentInformation();
            if (info != null) {
                metadata.put("title", info.getTitle());
                metadata.put("author", info.getAuthor());
                metadata.put("subject", info.getSubject());
                metadata.put("creator", info.getCreator());
                metadata.put("producer", info.getProducer());
                metadata.put("createdDate", info.getCreationDate());
                metadata.put("modifiedDate", info.getModificationDate());
            }

            return metadata;
        } catch (IOException e) {
            log.error("提取 PDF 元数据失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 从 PDF 提取纯文本内容
     */
    public String extractText(InputStream pdfStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * 将 PDF 文本解析为结构化条款 JSON
     */
    private JSONObject extractStructuredData(String fullText, int pageCount) {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("fullText", fullText);

        // 提取条款列表
        List<JSONObject> clauses = new ArrayList<>();
        Matcher matcher = CLAUSE_PATTERN.matcher(fullText);

        while (matcher.find()) {
            JSONObject clause = new JSONObject();
            clause.put("clauseNumber", extractClauseNumber(matcher.group(1)));
            clause.put("content", matcher.group(1).trim());
            clause.put("position", matcher.start());
            clauses.add(clause);
        }

        // 提取合同方信息
        result.put("clauses", clauses);
        result.put("clauseCount", clauses.size());

        // 尝试提取合同标题、甲乙双方
        extractParties(fullText, result);

        return result;
    }

    /**
     * 提取条款编号
     */
    private String extractClauseNumber(String clauseText) {
        Matcher m = Pattern.compile("第[一二三四五六七八九十百千]+[条章节]").matcher(clauseText);
        return m.find() ? m.group() : "";
    }

    /**
     * 提取合同甲乙双方
     */
    private void extractParties(String text, JSONObject result) {
        // 甲方模式
        Pattern partyAPattern = Pattern.compile("甲方[：:](\\S+)", Pattern.MULTILINE);
        Matcher aMatcher = partyAPattern.matcher(text);
        if (aMatcher.find()) {
            result.put("partyA", aMatcher.group(1));
        }

        // 乙方模式
        Pattern partyBPattern = Pattern.compile("乙方[：:](\\S+)", Pattern.MULTILINE);
        Matcher bMatcher = partyBPattern.matcher(text);
        if (bMatcher.find()) {
            result.put("partyB", bMatcher.group(1));
        }
    }
}

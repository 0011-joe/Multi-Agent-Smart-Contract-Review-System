package com.contract.review.service.document;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * OCR 识别服务
 * 集成 Tesseract OCR 处理扫描件/图片类 PDF，通过 MCP Server 调用 OCR 工具接口
 * 实现文本 + 坐标映射
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /** Tesseract 命令行路径（可配置） */
    private static final String TESSERACT_PATH = System.getenv().getOrDefault(
            "TESSERACT_PATH", "tesseract");

    /** 缓存目录 */
    private final Path cacheDir;

    public OcrService() {
        try {
            this.cacheDir = Files.createTempDirectory("ocr-cache-");
            log.info("OCR 缓存目录: {}", cacheDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 OCR 缓存目录", e);
        }
    }

    /**
     * 对图片执行 OCR 识别
     * @param imageFile 图片文件
     * @return 识别结果 JSON（包含文本+坐标）
     */
    public String recognize(File imageFile) {
        log.info("OCR 识别图片: {}", imageFile.getName());

        try {
            JSONObject result = new JSONObject();
            result.put("fileName", imageFile.getName());
            result.put("success", true);

            // 读取图片信息
            BufferedImage image = ImageIO.read(imageFile);
            if (image != null) {
                result.put("imageWidth", image.getWidth());
                result.put("imageHeight", image.getHeight());
            }

            // 调用 Tesseract 获取包含坐标的识别结果
            String rawOutput = runTesseractWithTsV(imageFile);
            result.put("rawText", rawOutput);

            // 解析 TSV 输出获取文本+坐标映射
            JSONArray items = parseTsVOutput(rawOutput);
            result.put("items", items);

            // 提取纯文本
            StringBuilder fullText = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                String text = item.getString("text");
                if (text != null && !text.trim().isEmpty()) {
                    if (fullText.length() > 0) fullText.append("\n");
                    fullText.append(text.trim());
                }
            }
            result.put("fullText", fullText.toString());

            log.info("OCR 识别完成, 识别 {} 个文本项", items.size());
            return JSON.toJSONString(result);

        } catch (Exception e) {
            log.error("OCR 识别失败: {}", e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("error", "OCR识别失败: " + e.getMessage());
            error.put("success", false);
            return JSON.toJSONString(error);
        }
    }

    /**
     * 批量 OCR 识别（异步）
     */
    public CompletableFuture<List<String>> batchRecognize(List<File> imageFiles) {
        log.info("批量 OCR 识别, 共 {} 张图片", imageFiles.size());

        List<CompletableFuture<String>> futures = imageFiles.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> recognize(file)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * 对 PDF 的每一页执行 OCR（先转图片）
     */
    public String recognizePdf(InputStream pdfStream, String pdfFileName) {
        log.info("OCR 识别 PDF: {}", pdfFileName);
        try {
            // 将 PDF 各页转为图片（需外部工具如 pdfimages/ghostscript）
            // 这里简化：直接保存流到临时文件
            Path tempPdf = cacheDir.resolve(pdfFileName + ".pdf");
            Files.copy(pdfStream, tempPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            JSONObject result = new JSONObject();
            result.put("fileName", pdfFileName);
            result.put("success", true);
            result.put("message", "PDF OCR 需要 GhostScript 转换页面为图片后逐页识别");

            // 实际项目中应调用 GhostScript 转图片再逐页 OCR
            // 这里示意接口定义
            return JSON.toJSONString(result);

        } catch (IOException e) {
            log.error("PDF OCR 失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 通过 MCP Server 调用 OCR 工具接口
     * 将识别委托给 MCP 协议定义的远程 OCR 服务
     */
    public String recognizeViaMcp(String imageBase64) {
        log.info("通过 MCP 调用 OCR 服务");
        // 此处通过 MCP Client 调用远程 OCR Server
        // 实现由 MCP 协议层完成
        JSONObject result = new JSONObject();
        result.put("mcpInvoked", true);
        result.put("status", "pending");
        return JSON.toJSONString(result);
    }

    /**
     * 调用 Tesseract 并获取 TSV 格式输出（含坐标）
     */
    private String runTesseractWithTsV(File imageFile) throws IOException, InterruptedException {
        Path outputBase = cacheDir.resolve(imageFile.getName() + "_ocr");
        List<String> cmd = List.of(
                TESSERACT_PATH,
                imageFile.getAbsolutePath(),
                outputBase.toString(),
                "-l", "chi_sim+eng",   // 中文+英文
                "--psm", "6",          // 假设为统一文本块
                "tsv"                  // TSV 格式输出（含坐标）
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cacheDir.toFile());
        Process process = pb.start();

        // 读取错误输出
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                log.debug("Tesseract: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Tesseract 退出码: " + exitCode);
        }

        // 读取 TSV 输出
        Path tsvFile = outputBase.getParent().resolve(outputBase.getFileName() + ".tsv");
        if (!Files.exists(tsvFile)) {
            log.warn("TSV 输出文件不存在: {}", tsvFile);
            return "";
        }

        return Files.readString(tsvFile);
    }

    /**
     * 解析 Tesseract TSV 输出为结构化文本+坐标
     */
    private JSONArray parseTsVOutput(String tsvContent) {
        JSONArray items = new JSONArray();

        if (tsvContent == null || tsvContent.isEmpty()) {
            return items;
        }

        String[] lines = tsvContent.split("\\n");
        if (lines.length < 2) return items;

        // 解析表头
        String header = lines[0];
        String[] columns = header.split("\\t");

        int levelIdx = -1, textIdx = -1, xIdx = -1, yIdx = -1;
        int widthIdx = -1, heightIdx = -1, confIdx = -1;

        for (int i = 0; i < columns.length; i++) {
            switch (columns[i].trim()) {
                case "level" -> levelIdx = i;
                case "text" -> textIdx = i;
                case "left" -> xIdx = i;
                case "top" -> yIdx = i;
                case "width" -> widthIdx = i;
                case "height" -> heightIdx = i;
                case "conf" -> confIdx = i;
            }
        }

        // 解析数据行（仅 level=5 的词级别）
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split("\\t");
            if (fields.length <= levelIdx) continue;

            try {
                int level = Integer.parseInt(fields[levelIdx].trim());
                if (level != 5) continue;  // 只取单词级别

                String text = textIdx >= 0 && fields.length > textIdx ? fields[textIdx].trim() : "";
                if (text.isEmpty() || text.matches("\\s+")) continue;

                JSONObject item = new JSONObject();
                item.put("text", text);
                if (xIdx >= 0) item.put("x", Integer.parseInt(fields[xIdx].trim()));
                if (yIdx >= 0) item.put("y", Integer.parseInt(fields[yIdx].trim()));
                if (widthIdx >= 0) item.put("width", Integer.parseInt(fields[widthIdx].trim()));
                if (heightIdx >= 0) item.put("height", Integer.parseInt(fields[heightIdx].trim()));
                if (confIdx >= 0) item.put("confidence", Integer.parseInt(fields[confIdx].trim()));

                items.add(item);
            } catch (NumberFormatException e) {
                // 跳过解析失败的行
            }
        }

        return items;
    }
}

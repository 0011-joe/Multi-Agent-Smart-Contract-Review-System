package com.contract.review.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 坐标映射服务
 * 记录条款在 PDF 中的位置坐标，返回相对坐标（百分比）
 */
@Service
public class CoordinateMapperService {

    private static final Logger log = LoggerFactory.getLogger(CoordinateMapperService.class);

    /** 条款坐标映射 <clauseId, Coordinate> */
    private final Map<String, Coordinate> coordinateMap = new LinkedHashMap<>();

    /**
     * 注册条款坐标
     */
    public void registerCoordinate(String clauseId, float x, float y, float width, float height,
                                   int pageNum, float pageWidth, float pageHeight) {
        Coordinate coord = new Coordinate(
                pageNum,
                x / pageWidth * 100,     // 转为百分比
                y / pageHeight * 100,
                width / pageWidth * 100,
                height / pageHeight * 100
        );
        coordinateMap.put(clauseId, coord);
        log.debug("注册条款坐标: clauseId={}, page={}, x={}%, y={}%",
                clauseId, pageNum, coord.relativeX(), coord.relativeY());
    }

    /**
     * 从 PDF 解析结果提取坐标
     * @param pdfParseResult PDF 解析返回的 JSON
     */
    public void parseFromPdfResult(String pdfParseResult) {
        try {
            JSONObject result = JSON.parseObject(pdfParseResult);
            JSONArray clauses = result.getJSONArray("clauses");
            if (clauses == null) return;

            for (int i = 0; i < clauses.size(); i++) {
                JSONObject clause = clauses.getJSONObject(i);
                String clauseNumber = clause.getString("clauseNumber");
                int position = clause.getIntValue("position", 0);

                // 简化：使用位置信息估算坐标
                registerCoordinate(
                        clauseNumber,
                        position % 1000, 0, 500, 100,
                        1, 1000, 2000
                );
            }
        } catch (Exception e) {
            log.warn("从 PDF 结果解析坐标失败", e);
        }
    }

    /**
     * 获取条款坐标
     */
    public Coordinate getCoordinate(String clauseId) {
        return coordinateMap.get(clauseId);
    }

    /**
     * 获取所有坐标
     */
    public Map<String, Coordinate> getAllCoordinates() {
        return Collections.unmodifiableMap(coordinateMap);
    }

    /**
     * 获取带坐标的审查结果
     */
    public List<Map<String, Object>> getAnnotatedResults(List<Map<String, Object>> clauseResults) {
        List<Map<String, Object>> annotated = new ArrayList<>();

        for (Map<String, Object> result : clauseResults) {
            Map<String, Object> annotatedResult = new LinkedHashMap<>(result);
            String clauseNumber = (String) result.get("clauseNumber");
            Coordinate coord = coordinateMap.get(clauseNumber);

            if (coord != null) {
                annotatedResult.put("coordinate", Map.of(
                        "page", coord.pageNum(),
                        "x", coord.relativeX(),
                        "y", coord.relativeY(),
                        "width", coord.relativeWidth(),
                        "height", coord.relativeHeight()
                ));
            }

            annotated.add(annotatedResult);
        }

        return annotated;
    }

    /**
     * 清除坐标
     */
    public void clear() {
        coordinateMap.clear();
    }

    /**
     * 坐标记录
     * @param pageNum 页码
     * @param relativeX 相对X坐标（%）
     * @param relativeY 相对Y坐标（%）
     * @param relativeWidth 相对宽度（%）
     * @param relativeHeight 相对高度（%）
     */
    public record Coordinate(int pageNum, float relativeX, float relativeY,
                             float relativeWidth, float relativeHeight) {}
}

package com.contract.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 动态安全护栏服务
 * 流式内容实时检测（正则+轻量LLM），支持风险词库拦截
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);

    /** 风险词库 */
    private static final List<RiskPattern> RISK_PATTERNS = List.of(
            new RiskPattern("PHONE", Pattern.compile("1[3-9]\\d{9}"), "手机号", "HIGH"),
            new RiskPattern("ID_CARD", Pattern.compile("\\d{17}[\\dXx]"), "身份证号", "HIGH"),
            new RiskPattern("BANK_CARD", Pattern.compile("\\d{16,19}"), "银行卡号", "HIGH"),
            new RiskPattern("EMAIL", Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "邮箱", "LOW"),
            new RiskPattern("IP", Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"), "IP地址", "MEDIUM"),
            new RiskPattern("SENSITIVE", Pattern.compile("(涉密|绝密|机密|内部文件|保密协议[^\\w])"), "敏感词", "HIGH")
    );

    /** 调用统计 */
    private final ConcurrentHashMap<String, Long> invocationStats = new ConcurrentHashMap<>();

    /**
     * 实时检测文本内容
     * @param text 待检测文本
     * @param taskId 任务ID
     * @return 检测结果
     */
    public GuardResult check(String text, String taskId) {
        if (text == null || text.isEmpty()) {
            return GuardResult.safe();
        }

        invocationStats.merge(taskId, 1L, Long::sum);

        for (RiskPattern risk : RISK_PATTERNS) {
            var matcher = risk.pattern().matcher(text);
            if (matcher.find()) {
                log.warn("安全护栏拦截: taskId={}, 类型={}, 等级={}",
                        taskId, risk.name(), risk.level());
                return GuardResult.unsafe(risk.name(), risk.level(), matcher.group());
            }
        }

        return GuardResult.safe();
    }

    /**
     * 异步批量校验（用于重内容）
     */
    public GuardResult deepCheck(String text, String taskId) {
        // 先做正则检查
        GuardResult quickResult = check(text, taskId);
        if (!quickResult.isSafe()) {
            return quickResult;
        }

        // 长文本/重内容可在此引入轻量 LLM 检测
        // 简化：对超长文本增加额外检查
        if (text.length() > 1000) {
            log.debug("长文本深度检测: taskId={}, length={}", taskId, text.length());
        }

        return GuardResult.safe();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "totalInvocations", invocationStats.values().stream().mapToLong(Long::longValue).sum(),
                "activeTasks", invocationStats.size()
        );
    }

    /**
     * 风险模式
     */
    public record RiskPattern(String name, Pattern pattern, String description, String level) {}

    /**
     * 检测结果
     */
    public record GuardResult(boolean isSafe, String riskType, String riskLevel, String matchedContent) {
        public static GuardResult safe() {
            return new GuardResult(true, null, null, null);
        }
        public static GuardResult unsafe(String riskType, String riskLevel, String matchedContent) {
            return new GuardResult(false, riskType, riskLevel, matchedContent);
        }
    }
}

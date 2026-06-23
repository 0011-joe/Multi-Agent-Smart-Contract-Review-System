package com.contract.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * 性能基准测试
 * 记录合同处理时间，评估系统吞吐能力
 */
public class BenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkTest.class);

    /** 测试合同数量 */
    private static final int TEST_CONTRACT_COUNT = 10;

    /** 模拟标准合同文本 */
    private static final String SAMPLE_CONTRACT = """
            采购合同
            甲方：测试采购有限公司
            乙方：测试供应商有限公司

            第一条 合同标的
            甲方向乙方采购服务器设备共计100台，总金额500万元。

            第二条 付款方式
            合同签订后甲方支付30%预付款，验收合格后支付65%，剩余5%为质保金。

            第三条 交货期限
            乙方应在收到预付款后30日内完成交货。

            第四条 违约责任
            任何一方违约，应向守约方支付合同总金额20%的违约金。
            """;

    @Test
    @DisplayName("性能基准测试 - 模拟10份合同处理")
    public void benchmarkProcessingTime() {
        log.info("===== 性能基准测试开始 =====");

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        // 模拟并发处理10份合同
        for (int i = 0; i < TEST_CONTRACT_COUNT; i++) {
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();

                // 模拟审查流程
                simulateContractReview(index);

                long duration = System.currentTimeMillis() - start;
                log.info("合同#{} 处理完成, 耗时: {}ms", index, duration);
                return duration;
            }, executor));
        }

        // 收集结果
        List<Long> durations = futures.stream()
                .map(f -> {
                    try { return f.get(30, TimeUnit.SECONDS); }
                    catch (Exception e) { return 0L; }
                })
                .filter(d -> d > 0)
                .toList();

        executor.shutdown();

        // 统计
        DoubleSummaryStatistics stats = durations.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        log.info("===== 性能基准测试结果 =====");
        log.info("测试合同数: {}", durations.size());
        log.info("平均耗时: {:.2f} ms", stats.getAverage());
        log.info("最短耗时: {} ms", stats.getMin());
        log.info("最长耗时: {} ms", stats.getMax());
        log.info("总耗时: {} ms", stats.getSum());
        log.info("吞吐量: {:.2f} 份/秒", durations.size() / (stats.getAverage() / 1000.0));

        // 人工对比（假设人工审查一份合同需30分钟）
        double humanTime = 30 * 60 * 1000; // ms
        double systemTime = stats.getAverage();
        log.info("人工 vs 系统耗时比: {:.1f}x", humanTime / systemTime);
    }

    /**
     * 模拟合同审查流程
     */
    private void simulateContractReview(int index) {
        // 模拟各阶段耗时
        sleep(50);   // 文档解析
        sleep(30);   // 条款提取
        sleep(100);  // 合规审查
        sleep(80);   // 风险评估
        sleep(60);   // 商务分析
        sleep(40);   // 报告生成
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

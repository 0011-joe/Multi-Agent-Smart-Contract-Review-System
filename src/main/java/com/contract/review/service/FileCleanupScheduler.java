package com.contract.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;

/**
 * 定时清理任务
 * 定期清理临时文件，释放服务器存储空间
 */
@Component
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    @Value("${app.cleanup.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${app.cleanup.temp-dir:./data/temp}")
    private String tempDir;

    /**
     * 每天凌晨3点执行清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCleanup() {
        log.info("定时清理任务开始 (保留 {} 天内文件)", retentionDays);

        try {
            // 清理上传目录
            int uploadedDeleted = cleanupDirectory(Paths.get(uploadDir));
            // 清理临时目录
            int tempDeleted = cleanupDirectory(Paths.get(tempDir));

            log.info("定时清理完成: 上传目录 {} 个文件, 临时目录 {} 个文件",
                    uploadedDeleted, tempDeleted);

        } catch (Exception e) {
            log.error("定时清理任务失败", e);
        }
    }

    /**
     * 清理指定目录中的过期文件
     */
    private int cleanupDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            log.debug("目录不存在，跳过清理: {}", dir);
            return 0;
        }

        final int[] count = {0};
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                    if (lastModified.isBefore(cutoff)) {
                        Files.delete(file);
                        count[0]++;
                        log.debug("已删除过期文件: {}", file);
                    }
                } catch (IOException e) {
                    log.warn("删除文件失败: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return count[0];
    }
}

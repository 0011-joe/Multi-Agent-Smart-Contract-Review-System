package com.contract.review.service;

import com.contract.review.model.Contract;
import com.contract.review.model.ContractStatus;
import com.contract.review.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件上传服务
 * 支持多文件上传，文件类型校验（仅PDF），临时文件管理
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final ContractRepository contractRepository;

    /** 上传目录 */
    private static final String UPLOAD_DIR = "./uploads/contracts";

    /** 允许的文件类型 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "doc", "txt", "md");

    /** 最大文件大小（50MB） */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    public FileUploadService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
        initUploadDir();
    }

    /**
     * 上传单个文件
     */
    public Contract uploadFile(MultipartFile file, String title, String contractType) {
        validateFile(file);

        try {
            // 保存文件
            String fileName = storeFile(file);

            // 创建合同记录
            Contract contract = Contract.builder()
                    .title(title != null ? title : file.getOriginalFilename())
                    .content(new String(file.getBytes()))
                    .contractType(contractType)
                    .originalFileName(file.getOriginalFilename())
                    .fileFormat(getExtension(file.getOriginalFilename()))
                    .status(ContractStatus.PENDING)
                    .build();

            contract = contractRepository.save(contract);
            log.info("文件上传成功: id={}, file={}", contract.getId(), fileName);

            return contract;

        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传文件
     */
    public List<Contract> uploadFiles(MultipartFile[] files) {
        log.info("批量上传 {} 个文件", files.length);
        List<Contract> contracts = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                Contract contract = uploadFile(file, null, null);
                contracts.add(contract);
            } catch (Exception e) {
                log.warn("文件上传失败: {} - {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        return contracts;
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制（最大50MB）");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension
                    + "，仅支持: " + ALLOWED_EXTENSIONS);
        }
    }

    /**
     * 存储文件到磁盘
     */
    private String storeFile(MultipartFile file) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        String storedName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        Path targetPath = Paths.get(UPLOAD_DIR, storedName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.debug("文件已保存: {}", targetPath);
        return storedName;
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "txt";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 初始化上传目录
     */
    private void initUploadDir() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            log.info("上传目录已初始化: {}", UPLOAD_DIR);
        } catch (IOException e) {
            log.error("初始化上传目录失败", e);
        }
    }
}

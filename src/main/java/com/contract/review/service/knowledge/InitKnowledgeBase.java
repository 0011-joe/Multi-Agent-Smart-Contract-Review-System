package com.contract.review.service.knowledge;

import com.contract.review.service.chunker.Chunk;
import com.contract.review.service.chunker.TextChunker;
import com.contract.review.service.embedding.EmbeddingService;
import com.contract.review.service.vector.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 知识库初始化脚本
 * 应用启动时加载预设法律知识到向量库
 */
@Component
@Order(1)
public class InitKnowledgeBase implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitKnowledgeBase.class);

    private final LegalKnowledgeSource legalKnowledgeSource;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /** 法律知识文件路径 */
    private static final String KNOWLEDGE_DIR = "./data/legal-knowledge";

    public InitKnowledgeBase(LegalKnowledgeSource legalKnowledgeSource,
                             TextChunker textChunker,
                             EmbeddingService embeddingService,
                             VectorStoreService vectorStoreService) {
        this.legalKnowledgeSource = legalKnowledgeSource;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public void run(String... args) {
        log.info("===== 开始初始化法律知识库 =====");

        try {
            // 1. 检查是否已有知识库
            if (isKnowledgeBaseInitialized()) {
                log.info("知识库已初始化，跳过");
                return;
            }

            // 2. 从 API 获取最新的法律数据
            List<String> laws = legalKnowledgeSource.fetchLatestLaws();

            // 3. 如果 API 没有数据，从本地文件加载
            if (laws.isEmpty()) {
                laws = loadFromLocalFiles();
            }

            // 4. 如果本地也没有，使用预设数据
            if (laws.isEmpty()) {
                laws = List.copyOf(getPresetKnowledge());
            }

            log.info("共加载 {} 条法律知识数据", laws.size());

            // 5. 处理并存储到向量库
            legalKnowledgeSource.processAndStoreLaws(laws);

            // 6. 保存初始化标记
            saveInitMarker();

            log.info("===== 法律知识库初始化完成 =====");

        } catch (Exception e) {
            log.error("知识库初始化失败", e);
        }
    }

    /**
     * 从本地法律知识文件加载
     */
    private List<String> loadFromLocalFiles() {
        log.info("从本地文件加载法律知识: {}", KNOWLEDGE_DIR);
        List<String> contents = new ArrayList<>();

        Path dir = Paths.get(KNOWLEDGE_DIR);
        if (!Files.exists(dir)) {
            log.warn("法律知识目录不存在: {}", KNOWLEDGE_DIR);
            return contents;
        }

        try (Stream<Path> paths = Files.walk(dir, 1)) {
            List<Path> files = paths
                    .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                    .collect(Collectors.toList());

            for (Path file : files) {
                try {
                    String content = Files.readString(file);
                    if (!content.isBlank()) {
                        contents.add(content);
                        log.debug("加载法律知识文件: {}", file.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("读取文件失败: {}", file.getFileName(), e);
                }
            }
        } catch (Exception e) {
            log.warn("遍历法律知识目录失败", e);
        }

        log.info("从本地加载 {} 个法律知识文件", contents.size());
        return contents;
    }

    /**
     * 预设法律知识数据
     */
    private Set<String> getPresetKnowledge() {
        return Set.of(
                "《中华人民共和国民法典》合同编 第四百六十四条：合同是民事主体之间设立、变更、终止民事法律关系的协议。婚姻、收养、监护等有关身份关系的协议，适用有关该身份关系的法律规定；没有规定的，可以根据其性质参照适用本编规定。",
                "《中华人民共和国民法典》第四百六十五条：依法成立的合同，受法律保护。依法成立的合同，仅对当事人具有法律约束力，但是法律另有规定的除外。",
                "《中华人民共和国民法典》第五百七十七条：当事人一方不履行合同义务或者履行合同义务不符合约定的，应当承担继续履行、采取补救措施或者赔偿损失等违约责任。",
                "《中华人民共和国民法典》第五百八十五条：当事人可以约定一方违约时应当根据违约情况向对方支付一定数额的违约金，也可以约定因违约产生的损失赔偿额的计算方法。约定的违约金低于造成的损失的，当事人可以请求人民法院或者仲裁机构予以增加；约定的违约金过分高于造成的损失的，当事人可以请求人民法院或者仲裁机构予以适当减少。",
                "《中华人民共和国劳动法》第十七条：订立和变更劳动合同，应当遵循平等自愿、协商一致的原则，不得违反法律、行政法规的规定。劳动合同依法订立即具有法律约束力，当事人必须履行劳动合同规定的义务。",
                "《中华人民共和国劳动合同法》第十九条：劳动合同期限三个月以上不满一年的，试用期不得超过一个月；劳动合同期限一年以上不满三年的，试用期不得超过二个月；三年以上固定期限和无固定期限的劳动合同，试用期不得超过六个月。同一用人单位与同一劳动者只能约定一次试用期。",
                "《中华人民共和国招标投标法》第三条：在中华人民共和国境内进行下列工程建设项目包括项目的勘察、设计、施工、监理以及与工程建设有关的重要设备、材料等的采购，必须进行招标。",
                "《中华人民共和国数据安全法》第三十条：重要数据的处理者应当按照规定对其数据处理活动定期开展风险评估，并向有关主管部门报送风险评估报告。风险评估报告应当包括处理的重要数据的种类、数量，开展数据处理活动的情况，面临的数据安全风险及其应对措施等。",
                "《中华人民共和国民法典》第七百零三条：租赁合同是出租人将租赁物交付承租人使用、收益，承租人支付租金的合同。",
                "《中华人民共和国民法典》第九百一十九条：委托合同是委托人和受托人约定，由受托人处理委托人事务的合同。"
        );
    }

    /**
     * 检查知识库是否已初始化
     */
    private boolean isKnowledgeBaseInitialized() {
        Path marker = Paths.get(KNOWLEDGE_DIR, ".initialized");
        return Files.exists(marker);
    }

    /**
     * 保存初始化标记
     */
    private void saveInitMarker() {
        try {
            Path dir = Paths.get(KNOWLEDGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Files.writeString(Paths.get(KNOWLEDGE_DIR, ".initialized"),
                    "initialized at " + java.time.LocalDateTime.now());
        } catch (Exception e) {
            log.warn("保存初始化标记失败", e);
        }
    }
}

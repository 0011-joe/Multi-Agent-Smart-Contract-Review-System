package com.contract.review.service.knowledge;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.contract.review.service.chunker.Chunk;
import com.contract.review.service.chunker.TextChunker;
import com.contract.review.service.embedding.EmbeddingService;
import com.contract.review.service.vector.VectorStoreService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 法律知识数据源
 * 从公开法律数据库获取《民法典》等法规文本，定时更新知识库
 */
@Service
@EnableScheduling
public class LegalKnowledgeSource {

    private static final Logger log = LoggerFactory.getLogger(LegalKnowledgeSource.class);

    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /** 法律知识本地存储路径 */
    @Value("${legal.knowledge.path:./data/legal-knowledge}")
    private String knowledgePath;

    /** 法律数据库 API 地址（示例） */
    private static final String LAW_API_BASE = "https://flk.npc.gov.cn/api";

    /** HTTP 客户端 */
    private final HttpClient httpClient;

    /** 预设法律知识库 */
    private static final Map<String, String> PRESET_LAWS = new LinkedHashMap<>();

    static {
        PRESET_LAWS.put("民法典-合同编", "民法典-合同编 是中国民事法律的重要组成部分，规定了合同的订立、效力、履行、变更、转让、终止以及违约责任等基本制度。");
        PRESET_LAWS.put("民法典-物权编", "民法典-物权编 规定了物权的基本制度，包括所有权、用益物权、担保物权等。");
        PRESET_LAWS.put("劳动法", "中华人民共和国劳动法 保护劳动者的合法权益，调整劳动关系，建立和维护适应社会主义市场经济的劳动制度。");
        PRESET_LAWS.put("劳动合同法", "中华人民共和国劳动合同法 完善劳动合同制度，明确劳动合同双方当事人的权利和义务，保护劳动者的合法权益。");
        PRESET_LAWS.put("招标投标法", "中华人民共和国招标投标法 规范招标投标活动，保护国家利益、社会公共利益和招标投标活动当事人的合法权益。");
        PRESET_LAWS.put("公司法", "中华人民共和国公司法 规范公司的组织和行为，保护公司、股东、职工和债权人的合法权益。");
        PRESET_LAWS.put("消费者权益保护法", "中华人民共和国消费者权益保护法 保护消费者的合法权益，维护社会经济秩序。");
        PRESET_LAWS.put("知识产权法-专利", "中华人民共和国专利法 保护专利权人的合法权益，鼓励发明创造，推动科技进步和经济社会发展。");
        PRESET_LAWS.put("知识产权法-商标", "中华人民共和国商标法 加强商标管理，保护商标专用权，促使生产经营者保证商品和服务质量。");
        PRESET_LAWS.put("数据安全法", "中华人民共和国数据安全法 规范数据处理活动，保障数据安全，促进数据开发利用，保护个人和组织的合法权益。");
    }

    public LegalKnowledgeSource(TextChunker textChunker,
                                EmbeddingService embeddingService,
                                VectorStoreService vectorStoreService) {
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    /**
     * 初始化：加载预设法律知识到本地文件
     */
    @PostConstruct
    public void init() {
        log.info("初始化法律知识数据源");
        try {
            Path dir = Paths.get(knowledgePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("创建法律知识目录: {}", dir);
            }
            savePresetLaws(dir);
            log.info("法律知识数据源初始化完成");
        } catch (IOException e) {
            log.error("初始化法律知识源失败", e);
        }
    }

    /**
     * 定时任务：每日凌晨2点更新法律知识库
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledUpdate() {
        log.info("定时任务：开始更新法律知识库");
        try {
            List<String> updatedLaws = fetchLatestLaws();
            if (!updatedLaws.isEmpty()) {
                processAndStoreLaws(updatedLaws);
            }
            log.info("法律知识库更新完成");
        } catch (Exception e) {
            log.error("定时更新法律知识库失败", e);
        }
    }

    /**
     * 手动触发知识库更新
     */
    public void manualUpdate() {
        log.info("手动触发法律知识库更新");
        scheduledUpdate();
    }

    /**
     * 从 API 获取最新法律数据
     */
    public List<String> fetchLatestLaws() {
        log.info("从法律数据库 API 获取最新法规");
        List<String> laws = new ArrayList<>();

        try {
            // 调用全国人大法律数据库 API
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LAW_API_BASE + "/laws?limit=10"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = JSON.parseObject(response.body());
                JSONArray data = json.getJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.size(); i++) {
                        JSONObject law = data.getJSONObject(i);
                        laws.add(law.getString("content"));
                    }
                }
                log.info("API 获取到 {} 条法律数据", laws.size());
            } else {
                log.warn("API 返回状态码: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.warn("从 API 获取法律数据失败，使用预设数据: {}", e.getMessage());
            // API 不可用时使用预设数据
            laws.addAll(PRESET_LAWS.values());
        }

        return laws;
    }

    /**
     * 处理并存储法律知识到向量库
     */
    public void processAndStoreLaws(List<String> lawTexts) {
        log.info("处理并存储法律知识, 共 {} 条", lawTexts.size());

        List<Chunk> allChunks = new ArrayList<>();

        for (int i = 0; i < lawTexts.size(); i++) {
            String text = lawTexts.get(i);
            if (text == null || text.isBlank()) continue;

            // 切块
            List<Chunk> chunks = textChunker.splitBySection(text, 300, 30);
            String lawName = "法律-" + (i + 1);
            for (Chunk chunk : chunks) {
                chunk.setDocumentId((long) i);
                chunk.setDocumentName(lawName);
            }
            allChunks.addAll(chunks);
        }

        if (allChunks.isEmpty()) {
            log.warn("没有需要处理的法律知识");
            return;
        }

        // 批量生成向量
        List<String> texts = allChunks.stream()
                .map(Chunk::getContent)
                .collect(Collectors.toList());

        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 填充向量
        for (int i = 0; i < allChunks.size() && i < embeddings.size(); i++) {
            allChunks.get(i).setEmbedding(embeddings.get(i));
        }

        // 存储到向量库
        vectorStoreService.addChunks(allChunks);

        // 保存到本地文件
        saveToLocalFiles(allChunks);

        log.info("法律知识处理完成, 共 {} 个切片存入向量库", allChunks.size());
    }

    /**
     * 将预设法律知识保存到本地文件
     */
    private void savePresetLaws(Path dir) throws IOException {
        for (Map.Entry<String, String> entry : PRESET_LAWS.entrySet()) {
            String fileName = entry.getKey().replaceAll("[\\\\/:*?\"<>|]", "_") + ".md";
            Path filePath = dir.resolve(fileName);

            if (!Files.exists(filePath)) {
                String content = String.format("# %s\n\n%s\n\n> 最后更新: %s\n",
                        entry.getKey(), entry.getValue(),
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                log.debug("保存法律知识文件: {}", fileName);
            }
        }
        log.info("预设法律知识保存完成, 共 {} 个文件", PRESET_LAWS.size());
    }

    /**
     * 保存法律知识切片到本地
     */
    private void saveToLocalFiles(List<Chunk> chunks) {
        try {
            Path dir = Paths.get(knowledgePath, "chunks");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path outputFile = dir.resolve("knowledge-chunks-" + timestamp + ".json");

            List<Map<String, Object>> simpleChunks = chunks.stream()
                    .map(c -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", c.getId());
                        m.put("content", c.getContent());
                        m.put("documentName", c.getDocumentName());
                        m.put("chunkIndex", c.getChunkIndex());
                        m.put("metadata", c.getMetadata());
                        return m;
                    })
                    .collect(Collectors.toList());

            Files.writeString(outputFile, JSON.toJSONString(simpleChunks), StandardCharsets.UTF_8);
            log.info("法律知识切片已保存到: {}", outputFile);

        } catch (IOException e) {
            log.error("保存法律知识切片失败", e);
        }
    }
}

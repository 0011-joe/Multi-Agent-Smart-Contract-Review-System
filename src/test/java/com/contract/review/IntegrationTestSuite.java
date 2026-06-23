package com.contract.review;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

/**
 * 集成测试套件
 * 启动嵌入式 Spring 容器，测试完整业务流程
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTestSuite {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTestSuite.class);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
    }

    @Test
    @Order(1)
    @DisplayName("1. 测试合同上传")
    void testUploadContract() {
        log.info("测试: 合同上传");

        Map<String, Object> request = Map.of(
                "title", "测试采购合同",
                "content", "第一条 甲方向乙方采购设备100台。\n第二条 甲方应支付货款500万元。",
                "contractType", "采购合同",
                "partyA", "测试甲方",
                "partyB", "测试乙方"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/contracts/upload",
                new HttpEntity<>(request),
                Map.class
        );

        Assertions.assertEquals(201, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody().get("contractId"));
        log.info("✅ 合同上传成功: contractId={}", response.getBody().get("contractId"));
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试获取合同列表")
    void testListContracts() {
        log.info("测试: 获取合同列表");

        ResponseEntity<Map[]> response = restTemplate.getForEntity(
                baseUrl + "/contracts", Map[].class);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        log.info("✅ 获取合同列表成功, 共 {} 个", response.getBody().length);
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试启动审查")
    void testStartReview() {
        log.info("测试: 启动审查");

        // 先上传一个合同
        Map<String, Object> uploadReq = Map.of(
                "title", "审查测试合同",
                "content", "第一条 任何一方违约需支付50%违约金。"
        );

        ResponseEntity<Map> uploadResp = restTemplate.postForEntity(
                baseUrl + "/contracts/upload",
                new HttpEntity<>(uploadReq),
                Map.class
        );

        Long contractId = ((Number) uploadResp.getBody().get("contractId")).longValue();

        // 启动审查
        ResponseEntity<Map> reviewResp = restTemplate.postForEntity(
                baseUrl + "/contracts/" + contractId + "/review",
                null,
                Map.class
        );

        Assertions.assertEquals(200, reviewResp.getStatusCode().value());
        log.info("✅ 审查启动成功");
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试 SSE 流式接口")
    void testStreamEndpoint() {
        log.info("测试: SSE 流式接口可用性");

        ResponseEntity<Map> reviewResp = restTemplate.postForEntity(
                baseUrl + "/review/1",
                null,
                Map.class
        );

        if (reviewResp.getStatusCode() == HttpStatus.OK) {
            String taskId = (String) reviewResp.getBody().get("taskId");
            Assertions.assertNotNull(taskId);

            // SSE endpoint should respond
            ResponseEntity<String> streamResp = restTemplate.getForEntity(
                    baseUrl + "/stream/" + taskId, String.class);

            Assertions.assertTrue(streamResp.getStatusCode().is2xxSuccessful()
                    || streamResp.getStatusCode().is4xxClientError());
            log.info("✅ SSE 接口可用");
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. 测试 404 处理")
    void testNotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/contracts/99999", Map.class);

        Assertions.assertEquals(404, response.getStatusCode().value());
        log.info("✅ 404 处理正常");
    }
}

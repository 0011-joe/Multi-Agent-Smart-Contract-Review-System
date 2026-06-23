package com.contract.review.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.contract.review.mcp.tool.ContractAnalysisTool;
import com.contract.review.mcp.tool.ContractReviewTool;
import com.contract.review.mcp.tool.ReportGenerationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.McpServer;
import org.springframework.ai.mcp.server.McpServerFeatures;
import org.springframework.ai.mcp.server.transport.StdioServerTransport;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.ServerCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * MCP 协议服务器配置
 * 注册 MCP Server 到 Nacos 配置中心，支持 REST/gRPC 协议适配
 */
@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String nacosAddr;

    @Value("${nacos.namespace:public}")
    private String nacosNamespace;

    private final ContractAnalysisTool contractAnalysisTool;
    private final ContractReviewTool contractReviewTool;
    private final ReportGenerationTool reportGenerationTool;

    public McpServerConfig(ContractAnalysisTool contractAnalysisTool,
                           ContractReviewTool contractReviewTool,
                           ReportGenerationTool reportGenerationTool) {
        this.contractAnalysisTool = contractAnalysisTool;
        this.contractReviewTool = contractReviewTool;
        this.reportGenerationTool = reportGenerationTool;
    }

    /**
     * 注册 MCP Server 工具列表
     */
    @Bean
    public List<McpServerFeatures.SyncToolRegistration> toolRegistrations() {
        return List.of(
                new McpServerFeatures.SyncToolRegistration(
                        new McpSchema.Tool("analyze_clause",
                                "分析合同条款风险，返回条款的风险等级、风险描述和修改建议",
                                Map.of("type", "object",
                                        "properties", Map.of(
                                                "clauseContent", Map.of("type", "string", "description", "条款原文内容"),
                                                "clauseType", Map.of("type", "string", "description", "条款类型")
                                        ),
                                        "required", List.of("clauseContent"))),
                        contractAnalysisTool::analyzeClause
                ),
                new McpServerFeatures.SyncToolRegistration(
                        new McpSchema.Tool("review_contract",
                                "对整份合同进行全面审查，包括条款分析、风险评估和合规检查",
                                Map.of("type", "object",
                                        "properties", Map.of(
                                                "contractContent", Map.of("type", "string", "description", "合同全文内容"),
                                                "contractType", Map.of("type", "string", "description", "合同类型")
                                        ),
                                        "required", List.of("contractContent"))),
                        contractReviewTool::executeReview
                ),
                new McpServerFeatures.SyncToolRegistration(
                        new McpSchema.Tool("generate_report",
                                "生成审查报告，包括风险摘要、评分和建议",
                                Map.of("type", "object",
                                        "properties", Map.of(
                                                "contractId", Map.of("type", "string", "description", "合同ID"),
                                                "reviewData", Map.of("type", "string", "description", "审查结果 JSON 数据")
                                        ),
                                        "required", List.of("contractId", "reviewData"))),
                        reportGenerationTool::generateReport
                )
        );
    }

    /**
     * MCP Server Bean - 使用 Stdio 传输协议
     * 注册到 Nacos 配置中心，支持服务发现
     */
    @Bean
    public McpServer mcpServer(List<McpServerFeatures.SyncToolRegistration> toolRegistrations,
                               NacosDiscoveryProperties nacosProperties) {
        log.info("初始化 MCP Server: contract-review-mcp-server");
        log.info("注册到 Nacos: {} / {}", nacosAddr, nacosNamespace);

        // 构建 MCP Server
        McpServer server = McpServer.using(new StdioServerTransport())
                .serverInfo("contract-review-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)    // 启用工具调用
                        .resources(false)
                        .prompts(false)
                        .build())
                .tools(toolRegistrations)
                .build();

        log.info("MCP Server 启动完成，已注册 {} 个工具", toolRegistrations.size());

        // 注册到 Nacos（实际注册由 NacosDiscoveryProperties 自动完成）
        nacosProperties.setService("contract-review-mcp-server");
        nacosProperties.setGroup("MCP_SERVERS");

        return server;
    }
}

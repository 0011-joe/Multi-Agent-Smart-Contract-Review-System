# Multi-Agent Contract Review System

<p align="center">
  <strong>基于 Spring AI + MCP 的多 Agent 智能合同审查系统</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--M4-blue" alt="Spring AI">
  <img src="https://img.shields.io/badge/JDK-17-red" alt="JDK 17">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License">
</p>

## 📋 项目介绍

多 Agent 智能合同审查系统，通过多个专业 AI Agent 协作，自动完成合同审查流程。

### 核心架构

```
┌──────────────────────────────────────────────────────┐
│                  REST API / SSE Layer                 │
├──────────────────────────────────────────────────────┤
│                   Agent Orchestrator                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │
│  │Compliance│  │   Risk   │  │  Business Term   │     │
│  │  Agent   │  │  Agent   │  │      Agent       │     │
│  └────┬─────┘  └────┬─────┘  └────────┬─────────┘     │
│       └──────────────┼─────────────────┘               │
│                      ▼                                 │
│          ProjectManagerAgent (总控)                      │
├──────────────────────────────────────────────────────┤
│  MCP Protocol Layer / Spring AI / LangChain4j          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │
│  │ ChatModel│  │Embedding │  │  Vector Store    │     │
│  │(GLM-4.5) │  │(BGE-large)│  │  (ChromaDB)      │     │
│  └──────────┘  └──────────┘  └──────────────────┘     │
├──────────────────────────────────────────────────────┤
│  RAG Knowledge Base / Document Parsing / OCR           │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │
│  │   PDF    │  │   OCR    │  │  Text Chunker    │     │
│  │  Parser  │  │(Tesseract)│  │  (语义切分)       │     │
│  └──────────┘  └──────────┘  └──────────────────┘     │
└──────────────────────────────────────────────────────┘
```

### Agent 组成

| Agent | 角色 | 能力 |
|-------|------|------|
| **ComplianceAgent** | 合规审查专家 | 法律合规性审查、RAG 法律检索、CoT 推理 |
| **RiskAgent** | 风险评估专家 | 多维度风险识别、权利义务分析、历史案例关联 |
| **BusinessTermAgent** | 商务条款专家 | 关键信息提取、合理性审查、违约金评估 |
| **ProjectManagerAgent** | 总控 Agent | 任务分配、状态监控、结果汇总 |

## 🚀 技术栈

- **JDK 17**
- **Spring Boot 3.3.5**
- **Spring AI 1.0.0-M4** (Alibaba DashScope)
- **Model: 智谱 GLM-4.5-air**
- **AgentScope** 多 Agent 框架
- **LangChain4j** + **BGE-M3** 向量化
- **ChromaDB** 向量数据库
- **Nacos** 服务注册与发现
- **MCP** 模型上下文协议
- **Apache PDFBox** PDF 解析
- **Tesseract OCR** 文字识别
- **H2/MySQL** 数据库
- **SpringDoc OpenAPI** 接口文档

## 🏗️ 项目结构

```
src/main/java/com/contract/review/
├── agent/               # Agent 智能体
│   ├── AgentBase.java           # 抽象基类
│   ├── ComplianceAgent.java     # 合规审查 Agent
│   ├── RiskAgent.java           # 风险评估 Agent
│   ├── BusinessTermAgent.java   # 商务条款 Agent
│   ├── ProjectManagerAgent.java # 总控 Agent
│   ├── AgentOrchestrator.java   # Agent 编排器
│   ├── AgentEvaluator.java      # Agent 评测框架
│   └── PromptManager.java       # Prompt 管理器
├── config/              # 配置类
├── controller/          # REST API 控制器
├── dto/                 # 数据传输对象
├── mcp/tool/            # MCP 工具定义
├── model/               # 数据实体
├── repository/          # 数据仓库
└── service/             # 业务服务
    ├── document/        # 文档解析 (PDF/OCR)
    ├── chunker/         # 文本切块
    ├── embedding/       # 向量化
    ├── vector/          # 向量存储
    ├── rag/             # RAG 检索
    ├── knowledge/       # 法律知识库
    └── workflow/        # 工作流引擎
```

## 🛠️ 本地部署

### 前置条件

- JDK 17+
- Maven 3.8+
- ChromaDB (可选，可使用 H2 内存模式)
- Nacos (可选，可使用本地配置)
- Tesseract OCR (可选，用于扫描件识别)

### 环境变量

```bash
# 必需
export ZHIPU_API_KEY=your-zhipu-api-key

# 可选
export NACOS_ADDR=127.0.0.1:8848
export CHROMADB_HOST=localhost
export CHROMADB_PORT=8000
export TESSERACT_PATH=/usr/bin/tesseract
```

### 启动

```bash
# 克隆项目
git clone https://github.com/your-username/contract-review-system.git
cd contract-review-system

# 编译
mvn clean package -DskipTests

# 运行
java -jar target/contract-review-system-1.0.0.jar

# 或使用 Maven
mvn spring-boot:run
```

### Docker Compose

```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - ZHIPU_API_KEY=${ZHIPU_API_KEY}
    depends_on:
      - chromadb
  chromadb:
    image: chromadb/chroma:latest
    ports:
      - "8000:8000"
```

## 📖 API 文档

启动后访问 Swagger UI：

- **Swagger**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs

### 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/contracts/upload` | 上传合同 |
| GET | `/api/v1/contracts` | 合同列表 |
| POST | `/api/v1/contracts/{id}/review` | 启动审查 |
| GET | `/api/v1/contracts/{id}/report` | 获取报告 |
| POST | `/api/v1/review/{contractId}` | 提交异步审查 |
| GET | `/api/v1/stream/{taskId}` | SSE 流式结果 |
| GET | `/api/v1/tasks/{taskId}/status` | 任务状态 |

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行集成测试
mvn test -Dtest=IntegrationTestSuite

# 性能基准测试
mvn test -Dtest=BenchmarkTest

# 准确率评估
mvn test -Dtest=AccuracyEvaluation
```

## 📈 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 单合同处理时间 | < 30s | 10 条款标准合同 |
| 并发处理能力 | 50+ | 同时审查合同数 |
| SSE 首包延迟 | < 2s | 首次流式推送 |
| 风险召回率 | > 95% | 高风险条款识别 |
| F1 分数 | > 0.95 | 综合准确率 |

## 🔄 CI/CD

```yaml
# GitHub Actions 配置
name: CI/CD
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: mvn clean package
      - run: mvn test
```

## 📜 开源协议

本项目采用 **MIT License** 开源协议。

## 🎥 演示视频

[演示视频链接 - 待上传]

## 📝 项目信息

- 项目名称：Contract Review System
- 版本：1.0.0
- 技术栈：Spring AI + MCP + AgentScope
- AI 模型：智谱 GLM-4.5-air
- 向量模型：BGE-large-zh

# Agent RAG Observability Server

## 项目概述

Agent RAG Observability Server 是面向 AI Agent 体系的**可观测性后端服务**，负责采集、查询、分析与 RAG / Agent 相关的全链路 Trace 数据，并提供在线质量评分与离线评测能力。它采用 DDD（领域驱动设计）架构，对接 MySQL、Elasticsearch、Redis、RocketMQ，基于 OpenTelemetry 与 Spring AI 构建，是整个 Agent 体系的「监控与评测中枢」。

### 核心特性

- **全链路采集**：统一收集 Agent 决策、RAG 检索、对话结果、工具调用、记忆召回五类 Span，支持单条上报与批量上报
- **Trace 查询**：按 traceId / sessionId / userId / tenantId 多维检索，聚合完整调用链
- **在线质量评分**：零 LLM 成本实时计算检索质量（retrievalQuality）、忠实度（faithfulness）、答案相关性（answerRelevance）
- **离线评测**：数据集管理、评测任务、多维度指标（Recall/Precision/F1/MRR/NDCG/MAP、忠实度、幻觉检测、工具选择/参数/调用、推理、分支决策）
- **看板仪表盘**：概览、趋势、分支分布、工具使用、错误排行，支持 Redis 缓存加速
- **MQ 异步消费**：通过 RocketMQ 消费 `observability-trace` 主题，削峰填谷

## 使用功能

### 1. 数据采集（CollectController — `/api/v1/collect`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/collect/agent_decision` | 采集 Agent 决策 |
| POST | `/api/v1/collect/rag_retrieval` | 采集 RAG 检索结果 |
| POST | `/api/v1/collect/chat_result` | 采集对话结果 |
| POST | `/api/v1/collect/tool_call` | 采集工具调用日志 |
| POST | `/api/v1/collect/memory_recall` | 采集记忆召回日志 |
| POST | `/api/v1/collect/batch` | 批量上报（校验同 batch traceId 一致） |

### 2. Trace 查询（QueryController — `/api/v1/query`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/query/trace/{traceId}` | 查询单条完整 Trace（含在线质量评分） |
| POST | `/api/v1/query/trace/list` | 分页查询 Trace 列表 |
| GET | `/api/v1/query/session/{sessionId}` | 按会话查询 |
| GET | `/api/v1/query/user/{userId}/traces?tenantId=` | 按用户查询 |

### 3. 仪表盘（DashboardController — `/api/v1/dashboard`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/dashboard/overview?days=` | 概览（总量/成功率/平均耗时/空召回率） |
| GET | `/api/v1/dashboard/trend?days=&interval=` | 趋势（hour/day） |
| GET | `/api/v1/dashboard/branch_distribution?days=` | 分支分布 |
| GET | `/api/v1/dashboard/tool_usage?days=` | 工具使用统计 |
| GET | `/api/v1/dashboard/error_ranking?days=` | 错误排行 |

### 4. 评测（EvaluateController — `/api/v1/eval`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/eval/dataset` | 创建评测数据集 |
| GET | `/api/v1/eval/dataset/list` | 数据集列表 |
| POST | `/api/v1/eval/task` | 创建评测任务 |
| GET | `/api/v1/eval/task/list` | 任务列表 |
| GET | `/api/v1/eval/task/{taskId}` | 任务详情 |
| POST | `/api/v1/eval/task/{taskId}/run` | 运行评测任务 |
| POST | `/api/v1/eval/result` | 保存单条评测结果 |
| GET | `/api/v1/eval/result/{taskId}` | 查询任务结果 |
| GET | `/api/v1/eval/result/compare?task1=&task2=` | 两个任务结果对比 |
| GET | `/api/v1/eval/quality_overview?limit=` | 全局质量概览（加权均值） |

### 5. 在线质量评分模型

针对每条 Trace，`TraceQualityCalculator` 实时派生三项指标：

- **retrievalQuality**：综合 rerankScores 召回质量分与召回充分性
- **faithfulness**：基于答案长度与来源文档的忠实度
- **answerRelevance**：答案与问题的相关性

## 设计思路

### DDD 架构设计

```
agent-rag-observability-server/
├── agent-rag-observability-server-api/              # API 层：DTO、服务接口
├── agent-rag-observability-server-types/            # 类型层：响应、枚举、异常
├── agent-rag-observability-server-domain/           # 领域层：核心业务
│   ├── observe/    # 观测领域（采集、查询、看板、质量计算）
│   └── evaluate/   # 评测领域（数据集、任务、结果）
├── agent-rag-observability-server-infrastructure/   # 基础设施层：DAO、Redis、ES、MQ
├── agent-rag-observability-server-trigger/          # 触发器层：HTTP Controller、拦截器、异常处理
├── agent-rag-observability-server-app/              # 应用层：启动配置
└── agent-rag-observability-server-client/          # 对外 SDK（供其他服务上报）
```

### Trace 数据模型

一次完整 Agent 调用以 `traceId` 串联，包含：

- **AgentDecision**：意图、分支、选中工具、计划步骤、状态、耗时
- **RagRetrieval**：查询文本、改写、召回文档、rerank 分数、检索阶段
- **ChatResult**：问题、答案、Token 用量、最终状态
- **ToolCallLog**：工具名、入参、出参、耗时、调用顺序（支持 spanId/parentSpanId 嵌套）
- **MemoryRecallLog**：会话/Agent 记忆召回数量、分数、注入内容

## 使用技术

### 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.1 | 应用框架 |
| Java | 17 | 编程语言 |
| MyBatis | 3.0.4 | ORM |
| OpenTelemetry | 1.39.0 | 链路追踪 |
| Spring AI | 2.0.1 | AI 集成（评测用） |

### 数据存储与中间件

| 技术 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.0.33 | 关系数据库（Trace 主存储） |
| Elasticsearch | 8.13.4 | 全文检索与聚合 |
| Redis | - | 看板缓存 |
| RocketMQ | 2.3.1 | 异步消费 trace 主题 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| JJWT | 0.11.5 | 鉴权 |
| FastJSON | 2.0.28 | JSON 处理 |
| Guava | 32.1.3 | 工具库 |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.x、Elasticsearch 8.x、Redis、RocketMQ

### 启动步骤

1. **准备配置**

复制 `agent-rag-observability-server-app/src/main/resources/application.yml.example` 为 `application-dev.yml`，填写 MySQL、ES、Redis、RocketMQ 连接信息与 `observability.auth-key`。默认服务端口 `8092`。

2. **构建**

```bash
mvn clean package -DskipTests
```

3. **启动**

```bash
java -Dspring.profiles.active=dev \
     -jar agent-rag-observability-server-app/target/agent-rag-observability-server-app.jar
```

### 验证服务

```bash
# 概览看板
curl -H "auth-key: <your-auth-key>" \
     "http://localhost:8092/api/v1/dashboard/overview?days=7"

# 查询 Trace
curl -H "auth-key: <your-auth-key>" \
     "http://localhost:8092/api/v1/query/trace/<traceId>"
```

> 注意：所有接口受 `AuthKeyInterceptor` 保护，请求头需携带 `auth-key`（非 `x-auth-key`），否则默认按评测开关拦截。

## 项目结构

```
agent-rag-observability-server/
├── agent-rag-observability-server-api/
├── agent-rag-observability-server-types/
├── agent-rag-observability-server-domain/
├── agent-rag-observability-server-infrastructure/
├── agent-rag-observability-server-trigger/
│   └── src/main/java/cn/chyuan/ai/observability/trigger/
│       ├── http/                 # Collect/Query/Dashboard/Evaluate Controller
│       └── config/                # AuthKeyInterceptor、GlobalExceptionHandler
├── agent-rag-observability-server-app/
│   └── src/main/resources/
│       ├── application.yml.example
│       └── application-dev.yml
└── agent-rag-observability-server-client/   # 对外上报 SDK
```

## 在 Agent 体系中的位置

本服务接收来自 `mcp-gateway-agent`、`aggregation-support-agent`、`agent-add-oil` 等上游服务上报的 Trace 与工具调用数据，并提供查询/看板/评测能力给 `agent-rag-observability-web` 前端展示。

## 许可证

Apache License, Version 2.0

## 联系方式

- 开发者：chyuan
- 邮箱：184172133@qq.com

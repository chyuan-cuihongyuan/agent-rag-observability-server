# CHANGELOG — agent-rag-observability-server

> 由 `scripts/gen_changelog.py` 从 conventional commits 生成（移植自主仓 loop-92，AUTOLOOP al-13 推广）；手动修改会被下次生成覆盖。

## 未归属循环的历史提交

### ✨ 新增
- JDK 21 升级 + 遗留依赖清理（工单 0003）（dec8543，2026-08-26）
- TraceQualityCalculator 增强 + WebMvcConfig 调整（295291e，2026-07-01）
- eval 种子数据 + trace 在线质量评分（8bb1b7d，2026-07-01）
- 增加工具调用和记忆回忆日志的支持，更新相关数据模型和持久化逻辑（b5e1266，2026-06-10）
- 增强 ES 批量索引、MQ 消费及控制器校验（cc60341，2026-05-30）
- 新增 quality_overview 接口 + 评测任务透传进度字段（c51d46e，2026-06-30）
- 新增评测引擎核心实现（LLM Judge、检索指标计算、答案来源路由）（a787658，2026-06-08）
- 添加可观测性服务的数据收集和存储功能（89652e5，2026-05-28）
- 添加数据种子服务和控制器，优化日志存储与查询（35f2b68，2026-06-11）

### 🐛 修复
- repair rocketmq broker compose config（a7ad9f9，2026-05-30）
- 修复Maven打包插件版本兼容性问题（c5b36b6，2026-06-16）
- 修复P0数据可靠性+P1配置安全+P2功能缺陷（ff1fd7c，2026-06-04）

### ♻️ 重构
- 去除所有@Value注解默认值（5625061，2026-06-03）

### ✅ 测试
- 添加Spring Boot测试依赖项（9af9be9，2026-05-30）
- 添加监控评估后端控制器单元测试（98264c3，2026-05-30）

### 📝 文档
- 更新文档中的IP地址配置并完善数据库表结构（b4c8740，2026-06-30）
- 添加 Claude 项目指导文档（6f8a53e，2026-06-10）
- 添加 Kibana 中使用 Elasticsearch SQL 的详细文档（781ec8a，2026-05-31）
- 添加Agent全链路可观测性增强开发实施计划与详细实施方案（9254106，2026-06-08）
- 补全 README 项目说明文档（d8a8d7c，2026-07-02）

### 🔧 杂务
- add sanitized config templates (application.yml.example, application-test.yml.example)（87621ad，2026-07-01）
- 从 Git 索引和历史中移除所有 target/ 目录，更新 .gitignore 匹配子模块（f2597cd，2026-05-30）
- 公开仓库去除真实 IP（改 127.0.0.1）（c946e6f，2026-08-26）
- 更新配置文件中的默认服务器地址和密码（e237958，2026-06-06）
- 添加 Docker Compose 部署配置和 Dockerfile（e61e9e1，2026-06-15）
- 配置文件支持环境变量并删除无用模板文件（997f773，2026-05-31）

## loop-204（2026-09-13~2026-09-13，1 项）

### ✅ 测试
- 指标契约测试——ObserveMetrics/EvalMetricsAdapter 11 例锁定命名与 null 默认（工单 0207/0208，SELFLOOP2）（3e87da3）

## loop-202（2026-09-13~2026-09-13，1 项）

### ✨ 新增
- 全局异常处理精确化——6 类客户端异常映射 + 契约测试锁定（工单 0203/0204，SELFLOOP2）（15e92d9）

## loop-27（2026-09-12~2026-09-12，1 项）

### ✨ 新增
- 探针+优雅停机契约与模板守卫（D07，工单 0096）（03bddca）

## loop-20（2026-09-12~2026-09-12，3 项）

### ✨ 新增
- 栈升级 Spring Boot 4.1.1 + Spring AI 2.0.1（B08，ADR 决策 8 收敛第一步）（48c2ec6）
- traceId 日志贯穿——HTTP 入口 MDC 过滤器 + pattern 输出（D08 第一仓，工单 0068/0069）（67fa74f）

### 🔧 杂务
- 清除误入库的 .mimosa 扫描器会话状态 + gitignore 防复发（c95cb8f）

## loop-13（2026-09-12~2026-09-12，1 项）

### ✨ 新增
- springdoc-openapi 文档接入（D06 第一仓，工单 0053/0054）（60ebc2b）

## al-10（2026-09-13~2026-09-13，1 项）

### 🔧 杂务
- .editorconfig 编辑器格式基线（工单 1010，AUTOLOOP）（ce42454）

## al-09（2026-09-13~2026-09-13，1 项）

### 🔧 杂务
- Issue/PR 模板（工单 1009，AUTOLOOP）（b90bff7）

## al-06（2026-09-13~2026-09-13，1 项）

### ✨ 新增
- ArchUnit 七模块分层守卫（工单 1006，AUTOLOOP）（2b5c3f1）

## al-05（2026-09-13~2026-09-13，1 项）

### 🔧 杂务
- SECURITY.md 安全策略（工单 1005，AUTOLOOP）（93fe0ac）

## al-02（2026-09-13~2026-09-13，1 项）

### 🔧 杂务
- CODEOWNERS 评审路由（工单 1002，AUTOLOOP）（b1bbf62）

## al-01（2026-09-13~2026-09-13，1 项）

### 🔧 杂务
- dependabot 依赖自动化配置（工单 1001，AUTOLOOP）（61d779b）

## loop-01（2026-09-12~2026-09-12，1 项）

### ✨ 新增
- LLM-as-Judge 注入防御守卫样例正式入库（Spotlighting+Canary+REFRAIN，默认关闭）（def3970）

-- =============================================================================
-- 观测服务建表脚本（PostgreSQL 版）
-- 工单 0123（三期 O3：PG 全量 DDL 翻译）补账：为从未入库过 DDL 的表补建脚本，2026-09-10。
-- 工单 0133/0134（三期 R1/R2）：eval_dataset 增列 version/pool/source/frozen 并入建表
--   （既有库手工执行表 6 后注释中的 ALTER）；新增第 9 表 eval_rubric，2026-09-10。
-- 工单 0135/0136（三期 R3/R4）：eval_task 增列 trials/pass_threshold/pass_rate/score_std_dev/gate_id、
--   eval_result 增列 trial_no 并入建表（既有库手工执行表 7/8 后注释中的 ALTER）；
--   新增第 10/11 表 eval_gate、eval_gate_record，2026-09-10。
-- 口径：
--   (1) agent_decision_log / rag_retrieval_log / chat_result_log 三表以
--       docs/02-agent-rag-observability-server/09-补充技术细节.md 第 1040-1128 行
--       的 MySQL DDL 为准（已去除全部反引号）；mapper INSERT 中存在而文档缺失的
--       演进列逐列补齐（详见各表注释中的「补列」标记）。
--       特别说明：rag_retrieval_log 文档中的 cost_time_ms 列在 mapper 中已演进为
--       retrieval_cost_ms（同语义：检索耗时），本脚本按 mapper 实际写入列命名，
--       类型沿用文档 BIGINT；文档原 cost_time_ms 列一并保留以防回滚双写。
--   (2) tool_call_log / memory_recall_log / eval_dataset / eval_task / eval_result
--       五表从 agent-rag-observability-server-app 的 mapper XML 全部 SQL 列集
--       + infrastructure dao/po 对应 PO 字段类型反推。
-- 类型映射（对齐 dev-ops/postgresql/01-gateway-seed.sql 口径）：
--   AUTO_INCREMENT → BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY；
--   DATETIME → TIMESTAMP；ON UPDATE CURRENT_TIMESTAMP 移除（双方言统一改应用层
--   维护，mapper UPDATE 语句已显式写 update_time = NOW()）；
--   TINYINT（PO Integer 状态/开关）→ SMALLINT（保持 JDBC Integer 映射）；
--   JSON 列（PO 为 String，以 JSON 文本读写）→ TEXT（保持 MyBatis 字符串读写兼容，
--   不用 jsonb/json）；eval 分数列 PO 为 Double → DECIMAL(10,6) 双方言同名。
-- 唯一键反推依据（重点注明）：
--   eval_dataset  uk_dataset_id：selectByDatasetId 按 dataset_id 精确定位单数据集。
--   eval_task     uk_task_id   ：selectByTaskId / updateStatus / updateProgress /
--                                updateTotalCount 均按 task_id 精确定位单任务。
--   日志类表与 eval_result：纯追加账本，无唯一键；eval_result 同一 task_id 下
--   多条明细，仅建普通索引。
-- =============================================================================

-- 1. Agent 决策日志表（以 docs/02/09 L1040-1062 为准；mapper 补 4 列：
--    tool_call_times / tool_retry_times / model_version / error_message）
CREATE TABLE IF NOT EXISTS agent_decision_log (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id           VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  DEFAULT '',
    owner_user_id      VARCHAR(64)  DEFAULT '',
    session_id         VARCHAR(64)  DEFAULT '',
    agent_id           VARCHAR(64)  NOT NULL,
    source_service     VARCHAR(32)  NOT NULL,
    user_query         TEXT,
    intent_type        VARCHAR(64)  DEFAULT '',
    branch_type        VARCHAR(32)  NOT NULL,
    selected_tool_list TEXT,
    plan_steps         TEXT,
    decision_reason    TEXT,
    tool_call_times    INT          DEFAULT 0,
    tool_retry_times   INT          DEFAULT 0,
    model_version      VARCHAR(64)  DEFAULT NULL,
    agent_status       VARCHAR(32)  NOT NULL,
    error_message      TEXT,
    cost_time_ms       BIGINT       DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE agent_decision_log IS 'Agent决策日志表';
COMMENT ON COLUMN agent_decision_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN agent_decision_log.tenant_id IS '租户ID';
COMMENT ON COLUMN agent_decision_log.owner_user_id IS '所属用户ID';
COMMENT ON COLUMN agent_decision_log.session_id IS '会话ID';
COMMENT ON COLUMN agent_decision_log.agent_id IS '智能体ID';
COMMENT ON COLUMN agent_decision_log.source_service IS '来源服务';
COMMENT ON COLUMN agent_decision_log.user_query IS '用户查询';
COMMENT ON COLUMN agent_decision_log.intent_type IS '意图类型';
COMMENT ON COLUMN agent_decision_log.branch_type IS '分支类型';
COMMENT ON COLUMN agent_decision_log.selected_tool_list IS '选择的工具列表（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN agent_decision_log.plan_steps IS '规划步骤（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN agent_decision_log.decision_reason IS '决策原因';
COMMENT ON COLUMN agent_decision_log.tool_call_times IS '工具调用次数（mapper 演进补列）';
COMMENT ON COLUMN agent_decision_log.tool_retry_times IS '工具重试次数（mapper 演进补列）';
COMMENT ON COLUMN agent_decision_log.model_version IS '模型版本（mapper 演进补列）';
COMMENT ON COLUMN agent_decision_log.agent_status IS 'Agent状态';
COMMENT ON COLUMN agent_decision_log.error_message IS '错误信息（mapper 演进补列）';
COMMENT ON COLUMN agent_decision_log.cost_time_ms IS '耗时(毫秒)';
COMMENT ON COLUMN agent_decision_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_adl_trace_id ON agent_decision_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_adl_session_id ON agent_decision_log (session_id);
CREATE INDEX IF NOT EXISTS idx_adl_agent_id ON agent_decision_log (agent_id);
CREATE INDEX IF NOT EXISTS idx_adl_create_time ON agent_decision_log (create_time);

-- 2. RAG 检索日志表（以 docs/02/09 L1068-1089 为准；mapper 补 3 列：source_service /
--    retrieval_cost_ms（取代文档 cost_time_ms，同语义）/ rag_strategy_version）
CREATE TABLE IF NOT EXISTS rag_retrieval_log (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id             VARCHAR(64) NOT NULL,
    tenant_id            VARCHAR(64) DEFAULT '',
    owner_user_id        VARCHAR(64) DEFAULT '',
    session_id           VARCHAR(64) DEFAULT '',
    agent_id             VARCHAR(64) NOT NULL,
    source_service       VARCHAR(32) NOT NULL,
    query_text           TEXT,
    rewrite_text         TEXT,
    retrieval_topk       INT         DEFAULT 0,
    retrieval_count      INT         DEFAULT 0,
    source_docs          TEXT,
    rerank_scores        TEXT,
    empty_retrieval      SMALLINT    DEFAULT 0,
    retrieval_stages     TEXT,
    retrieval_cost_ms    BIGINT      DEFAULT 0,
    rag_strategy_version VARCHAR(64) DEFAULT NULL,
    cost_time_ms         BIGINT      DEFAULT 0,
    create_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE rag_retrieval_log IS 'RAG检索日志表';
COMMENT ON COLUMN rag_retrieval_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN rag_retrieval_log.tenant_id IS '租户ID';
COMMENT ON COLUMN rag_retrieval_log.owner_user_id IS '所属用户ID';
COMMENT ON COLUMN rag_retrieval_log.session_id IS '会话ID';
COMMENT ON COLUMN rag_retrieval_log.agent_id IS '智能体ID';
COMMENT ON COLUMN rag_retrieval_log.source_service IS '来源服务（mapper 演进补列）';
COMMENT ON COLUMN rag_retrieval_log.query_text IS '原始查询';
COMMENT ON COLUMN rag_retrieval_log.rewrite_text IS '改写查询';
COMMENT ON COLUMN rag_retrieval_log.retrieval_topk IS '检索TopK';
COMMENT ON COLUMN rag_retrieval_log.retrieval_count IS '召回数量';
COMMENT ON COLUMN rag_retrieval_log.source_docs IS '来源文档（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN rag_retrieval_log.rerank_scores IS '重排序分数（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN rag_retrieval_log.empty_retrieval IS '是否空召回：0-否，1-是（PO Integer→SMALLINT）';
COMMENT ON COLUMN rag_retrieval_log.retrieval_stages IS '检索阶段（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN rag_retrieval_log.retrieval_cost_ms IS '检索耗时(毫秒)（文档列 cost_time_ms 的 mapper 实名）';
COMMENT ON COLUMN rag_retrieval_log.rag_strategy_version IS 'RAG策略版本（mapper 演进补列）';
COMMENT ON COLUMN rag_retrieval_log.cost_time_ms IS '总耗时(毫秒)（文档保留列，mapper 现未写入）';
COMMENT ON COLUMN rag_retrieval_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_rrl_trace_id ON rag_retrieval_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_rrl_session_id ON rag_retrieval_log (session_id);
CREATE INDEX IF NOT EXISTS idx_rrl_create_time ON rag_retrieval_log (create_time);

-- 3. 问答结果日志表（以 docs/02/09 L1095-1113 为准；mapper 补 2 列：
--    source_service / model_version）
CREATE TABLE IF NOT EXISTS chat_result_log (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id           VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) DEFAULT '',
    owner_user_id      VARCHAR(64) DEFAULT '',
    session_id         VARCHAR(64) DEFAULT '',
    agent_id           VARCHAR(64) NOT NULL,
    source_service     VARCHAR(32) NOT NULL,
    question           TEXT,
    answer             TEXT,
    prompt_tokens      BIGINT      DEFAULT 0,
    completion_tokens  BIGINT      DEFAULT 0,
    total_cost_time_ms BIGINT      DEFAULT 0,
    final_status       VARCHAR(32) NOT NULL,
    model_version      VARCHAR(64) DEFAULT NULL,
    create_time        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_result_log IS '问答结果日志表';
COMMENT ON COLUMN chat_result_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN chat_result_log.tenant_id IS '租户ID';
COMMENT ON COLUMN chat_result_log.owner_user_id IS '所属用户ID';
COMMENT ON COLUMN chat_result_log.session_id IS '会话ID';
COMMENT ON COLUMN chat_result_log.agent_id IS '智能体ID';
COMMENT ON COLUMN chat_result_log.source_service IS '来源服务（mapper 演进补列）';
COMMENT ON COLUMN chat_result_log.question IS '用户问题';
COMMENT ON COLUMN chat_result_log.answer IS '模型回答';
COMMENT ON COLUMN chat_result_log.prompt_tokens IS 'Prompt Token';
COMMENT ON COLUMN chat_result_log.completion_tokens IS 'Completion Token';
COMMENT ON COLUMN chat_result_log.total_cost_time_ms IS '总耗时(毫秒)';
COMMENT ON COLUMN chat_result_log.final_status IS '最终状态';
COMMENT ON COLUMN chat_result_log.model_version IS '模型版本（mapper 演进补列）';
COMMENT ON COLUMN chat_result_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_crl_trace_id ON chat_result_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_crl_session_id ON chat_result_log (session_id);
CREATE INDEX IF NOT EXISTS idx_crl_create_time ON chat_result_log (create_time);

-- 4. 工具调用日志表（反推：tool_call_log_mapper.xml INSERT 列集 + ToolCallLogPO）
CREATE TABLE IF NOT EXISTS tool_call_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id       VARCHAR(64)  NOT NULL,
    span_id        VARCHAR(64)  DEFAULT NULL,
    parent_span_id VARCHAR(64)  DEFAULT NULL,
    tool_name      VARCHAR(128) NOT NULL,
    tool_input     TEXT,
    tool_output    TEXT,
    status         VARCHAR(32)  NOT NULL,
    cost_time_ms   INT          DEFAULT 0,
    error_message  TEXT,
    call_order     INT          DEFAULT 0,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tool_call_log IS '工具调用日志表';
COMMENT ON COLUMN tool_call_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN tool_call_log.span_id IS '跨度ID';
COMMENT ON COLUMN tool_call_log.parent_span_id IS '父跨度ID';
COMMENT ON COLUMN tool_call_log.tool_name IS '工具名称';
COMMENT ON COLUMN tool_call_log.tool_input IS '工具入参（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN tool_call_log.tool_output IS '工具输出（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN tool_call_log.status IS '调用状态（SUCCESS/FAILURE 等）';
COMMENT ON COLUMN tool_call_log.cost_time_ms IS '耗时(毫秒)（PO Integer→INT）';
COMMENT ON COLUMN tool_call_log.error_message IS '错误信息';
COMMENT ON COLUMN tool_call_log.call_order IS '调用序号（同链路内的顺序）';
COMMENT ON COLUMN tool_call_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_tcl_trace_id ON tool_call_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_tcl_create_time ON tool_call_log (create_time);

-- 5. 记忆召回日志表（反推：memory_recall_log_mapper.xml INSERT 列集 + MemoryRecallLogPO）
CREATE TABLE IF NOT EXISTS memory_recall_log (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id              VARCHAR(64) NOT NULL,
    query_text            TEXT,
    session_memory_count  INT DEFAULT 0,
    agent_memory_count    INT DEFAULT 0,
    session_memory_scores TEXT,
    agent_memory_scores   TEXT,
    inject_content        TEXT,
    cost_time_ms          INT DEFAULT 0,
    create_time           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE memory_recall_log IS '记忆召回日志表';
COMMENT ON COLUMN memory_recall_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN memory_recall_log.query_text IS '原始查询';
COMMENT ON COLUMN memory_recall_log.session_memory_count IS '会话记忆召回条数（PO Integer→INT）';
COMMENT ON COLUMN memory_recall_log.agent_memory_count IS '智能体记忆召回条数（PO Integer→INT）';
COMMENT ON COLUMN memory_recall_log.session_memory_scores IS '会话记忆评分列表（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN memory_recall_log.agent_memory_scores IS '智能体记忆评分列表（JSON 文本，PO String→TEXT）';
COMMENT ON COLUMN memory_recall_log.inject_content IS '注入上下文内容';
COMMENT ON COLUMN memory_recall_log.cost_time_ms IS '耗时(毫秒)（PO Integer→INT）';
COMMENT ON COLUMN memory_recall_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_mrl_trace_id ON memory_recall_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_mrl_create_time ON memory_recall_log (create_time);

-- 6. 评测数据集表（反推：eval_dataset_mapper.xml INSERT/SELECT 列集 + EvalDatasetPO；
--    工单 0134 R2 增列 version/pool/source/frozen 已并入建表）
CREATE TABLE IF NOT EXISTS eval_dataset (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_id   VARCHAR(64)  NOT NULL,
    dataset_name VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    item_count   INT          DEFAULT 0,
    items_json   TEXT,
    version      INT          NOT NULL DEFAULT 1,
    pool         VARCHAR(16)  DEFAULT NULL,
    source       VARCHAR(16)  DEFAULT NULL,
    frozen       SMALLINT     NOT NULL DEFAULT 0,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dataset_id UNIQUE (dataset_id)
);
COMMENT ON TABLE eval_dataset IS '评测数据集表';
COMMENT ON COLUMN eval_dataset.dataset_id IS '数据集业务ID（唯一）';
COMMENT ON COLUMN eval_dataset.dataset_name IS '数据集名称（版本化锚点，同 name 多版本）';
COMMENT ON COLUMN eval_dataset.description IS '数据集描述';
COMMENT ON COLUMN eval_dataset.item_count IS '条目数（PO Integer→INT）';
COMMENT ON COLUMN eval_dataset.items_json IS '数据集条目 JSON 数组（文本读写，PO String→TEXT）';
COMMENT ON COLUMN eval_dataset.version IS '版本号（同 dataset_name 递增，快照复制产生新版本）';
COMMENT ON COLUMN eval_dataset.pool IS '样本池：golden/challenge/wrong；NULL=未分类（存量兼容）';
COMMENT ON COLUMN eval_dataset.source IS '来源标记：trace/manual/seed';
COMMENT ON COLUMN eval_dataset.frozen IS '版本冻结位：0-可编辑，1-条目不可改（PO Integer→SMALLINT）';
COMMENT ON COLUMN eval_dataset.create_time IS '创建时间';
COMMENT ON COLUMN eval_dataset.update_time IS '更新时间（应用层维护）';
CREATE INDEX IF NOT EXISTS idx_eval_dataset_create_time ON eval_dataset (create_time);
CREATE INDEX IF NOT EXISTS idx_eval_dataset_name_version ON eval_dataset (dataset_name, version);
CREATE INDEX IF NOT EXISTS idx_eval_dataset_pool ON eval_dataset (pool);

-- 既有库增量升级（手工执行；本文件以幂等 CREATE IF NOT EXISTS 为主，ALTER 仅对已存在的旧表）：
-- ALTER TABLE eval_dataset
--     ADD COLUMN version INT NOT NULL DEFAULT 1,
--     ADD COLUMN pool VARCHAR(16) DEFAULT NULL,
--     ADD COLUMN source VARCHAR(16) DEFAULT NULL,
--     ADD COLUMN frozen SMALLINT NOT NULL DEFAULT 0;
-- CREATE INDEX IF NOT EXISTS idx_eval_dataset_name_version ON eval_dataset (dataset_name, version);
-- CREATE INDEX IF NOT EXISTS idx_eval_dataset_pool ON eval_dataset (pool);

-- 7. 评测任务表（反推：eval_task_mapper.xml 全部 SQL 列集 + EvalTaskPO；
--    工单 0135 R3 增列 trials/pass_threshold/pass_rate/score_std_dev、工单 0136 R4 增列 gate_id 已并入建表）
CREATE TABLE IF NOT EXISTS eval_task (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id              VARCHAR(64)   NOT NULL,
    task_name            VARCHAR(128)  NOT NULL,
    eval_type            VARCHAR(32)   NOT NULL,
    dataset_id           VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    total_count          INT           DEFAULT 0,
    completed_count      INT           DEFAULT 0,
    model_version        VARCHAR(64)   DEFAULT NULL,
    rag_strategy_version VARCHAR(64)   DEFAULT NULL,
    avg_overall_score    DECIMAL(10,6) DEFAULT NULL,
    trials               INT           NOT NULL DEFAULT 1,
    pass_threshold       DECIMAL(10,6) DEFAULT NULL,
    pass_rate            DECIMAL(10,6) DEFAULT NULL,
    score_std_dev        DECIMAL(10,6) DEFAULT NULL,
    gate_id              VARCHAR(64)   DEFAULT NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_id UNIQUE (task_id)
);
COMMENT ON TABLE eval_task IS '评测任务表';
COMMENT ON COLUMN eval_task.task_id IS '任务业务ID（唯一）';
COMMENT ON COLUMN eval_task.task_name IS '任务名称';
COMMENT ON COLUMN eval_task.eval_type IS '评测类型（RAG/AGENT 等）';
COMMENT ON COLUMN eval_task.dataset_id IS '关联数据集业务ID';
COMMENT ON COLUMN eval_task.status IS '任务状态（PENDING/RUNNING/COMPLETED/FAILED）';
COMMENT ON COLUMN eval_task.total_count IS '总条目数（样本数×trials，PO Integer→INT）';
COMMENT ON COLUMN eval_task.completed_count IS '已完成条目数（含全部 trial 累计，PO Integer→INT）';
COMMENT ON COLUMN eval_task.model_version IS '模型版本';
COMMENT ON COLUMN eval_task.rag_strategy_version IS 'RAG策略版本';
COMMENT ON COLUMN eval_task.avg_overall_score IS '平均总分（全部 trial 全部样本均值，PO Double→DECIMAL(10,6)）';
COMMENT ON COLUMN eval_task.trials IS '试验次数 k（工单 0135 R3 Pass@k；1=旧行为）';
COMMENT ON COLUMN eval_task.pass_threshold IS '样本达标阈值（NULL=应用默认 0.5，沿用 Rubric 达标线）';
COMMENT ON COLUMN eval_task.pass_rate IS '通过率 Pass@k（k 次 trial 至少 1 次达标的样本占比，完成时回写）';
COMMENT ON COLUMN eval_task.score_std_dev IS 'per-trial 综合分均值的标准差（完成时回写，总体口径）';
COMMENT ON COLUMN eval_task.gate_id IS '绑定的门禁规则 ID（工单 0136 R4 回测；非空时完成回调判定）';
COMMENT ON COLUMN eval_task.create_time IS '创建时间';
COMMENT ON COLUMN eval_task.update_time IS '更新时间（应用层维护）';
CREATE INDEX IF NOT EXISTS idx_eval_task_status_update ON eval_task (status, update_time);
CREATE INDEX IF NOT EXISTS idx_eval_task_gate_id ON eval_task (gate_id);

-- 既有库增量升级（手工执行；工单 0135/0136 R3/R4，对已存在的旧 eval_task）：
-- ALTER TABLE eval_task
--     ADD COLUMN trials INT NOT NULL DEFAULT 1,
--     ADD COLUMN pass_threshold DECIMAL(10,6) DEFAULT NULL,
--     ADD COLUMN pass_rate DECIMAL(10,6) DEFAULT NULL,
--     ADD COLUMN score_std_dev DECIMAL(10,6) DEFAULT NULL,
--     ADD COLUMN gate_id VARCHAR(64) DEFAULT NULL;
-- CREATE INDEX IF NOT EXISTS idx_eval_task_gate_id ON eval_task (gate_id);

-- 8. 评测结果表（反推：eval_result_mapper.xml INSERT/batchInsert/SELECT 全部 31 业务列
--    逐列核对 + EvalResultPO；分数列 PO 均 Double → DECIMAL(10,6)；
--    工单 0135 R3 增列 trial_no 已并入建表）
CREATE TABLE IF NOT EXISTS eval_result (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id              VARCHAR(64)   NOT NULL,
    trial_no             INT           NOT NULL DEFAULT 1,
    trace_id             VARCHAR(64)   DEFAULT NULL,
    query_text           TEXT,
    standard_answer      TEXT,
    actual_answer        TEXT,
    recall_score         DECIMAL(10,6) DEFAULT NULL,
    precision_score      DECIMAL(10,6) DEFAULT NULL,
    f1_score             DECIMAL(10,6) DEFAULT NULL,
    top3_hit_rate        DECIMAL(10,6) DEFAULT NULL,
    mrr_score            DECIMAL(10,6) DEFAULT NULL,
    ndcg_score           DECIMAL(10,6) DEFAULT NULL,
    map_score            DECIMAL(10,6) DEFAULT NULL,
    answer_similarity    DECIMAL(10,6) DEFAULT NULL,
    context_precision    DECIMAL(10,6) DEFAULT NULL,
    context_recall       DECIMAL(10,6) DEFAULT NULL,
    context_relevance    DECIMAL(10,6) DEFAULT NULL,
    faithfulness_score   DECIMAL(10,6) DEFAULT NULL,
    relevance_score      DECIMAL(10,6) DEFAULT NULL,
    hallucination_flag   SMALLINT      DEFAULT 0,
    completeness_score   DECIMAL(10,6) DEFAULT NULL,
    answer_correctness   DECIMAL(10,6) DEFAULT NULL,
    overall_score        DECIMAL(10,6) DEFAULT NULL,
    eval_detail          TEXT,
    tool_selection_score DECIMAL(10,6) DEFAULT NULL,
    tool_param_score     DECIMAL(10,6) DEFAULT NULL,
    tool_call_score      DECIMAL(10,6) DEFAULT NULL,
    intent_score         DECIMAL(10,6) DEFAULT NULL,
    branch_score         DECIMAL(10,6) DEFAULT NULL,
    reasoning_score      DECIMAL(10,6) DEFAULT NULL,
    agent_decision_score DECIMAL(10,6) DEFAULT NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE eval_result IS '评测结果表';
COMMENT ON COLUMN eval_result.task_id IS '评测任务业务ID（一任务多条明细）';
COMMENT ON COLUMN eval_result.trial_no IS '试验序号（1..k；k=1 时恒为 1，工单 0135 R3）';
COMMENT ON COLUMN eval_result.trace_id IS '链路追踪ID';
COMMENT ON COLUMN eval_result.query_text IS '评测问题';
COMMENT ON COLUMN eval_result.standard_answer IS '标准答案';
COMMENT ON COLUMN eval_result.actual_answer IS '实际回答';
COMMENT ON COLUMN eval_result.recall_score IS '召回率';
COMMENT ON COLUMN eval_result.precision_score IS '精确率';
COMMENT ON COLUMN eval_result.f1_score IS 'F1 分数';
COMMENT ON COLUMN eval_result.top3_hit_rate IS 'Top3 命中率';
COMMENT ON COLUMN eval_result.mrr_score IS 'MRR 分数';
COMMENT ON COLUMN eval_result.ndcg_score IS 'NDCG 分数';
COMMENT ON COLUMN eval_result.map_score IS 'MAP 分数';
COMMENT ON COLUMN eval_result.answer_similarity IS '答案相似度';
COMMENT ON COLUMN eval_result.context_precision IS '上下文精确率';
COMMENT ON COLUMN eval_result.context_recall IS '上下文召回率';
COMMENT ON COLUMN eval_result.context_relevance IS '上下文相关性';
COMMENT ON COLUMN eval_result.faithfulness_score IS '忠实度';
COMMENT ON COLUMN eval_result.relevance_score IS '相关性';
COMMENT ON COLUMN eval_result.hallucination_flag IS '幻觉标记：0-无，1-有（PO Integer→SMALLINT）';
COMMENT ON COLUMN eval_result.completeness_score IS '完整性';
COMMENT ON COLUMN eval_result.answer_correctness IS '答案正确性';
COMMENT ON COLUMN eval_result.overall_score IS '总分';
COMMENT ON COLUMN eval_result.eval_detail IS '评测明细 JSON 文本（PO String→TEXT）';
COMMENT ON COLUMN eval_result.tool_selection_score IS '工具选择分项（结构化落库）';
COMMENT ON COLUMN eval_result.tool_param_score IS '工具参数分项（结构化落库）';
COMMENT ON COLUMN eval_result.tool_call_score IS '工具调用分项（结构化落库）';
COMMENT ON COLUMN eval_result.intent_score IS '意图识别分项（结构化落库）';
COMMENT ON COLUMN eval_result.branch_score IS '分支决策分项（结构化落库）';
COMMENT ON COLUMN eval_result.reasoning_score IS '推理过程分项（结构化落库）';
COMMENT ON COLUMN eval_result.agent_decision_score IS 'Agent 决策总分项（结构化落库）';
COMMENT ON COLUMN eval_result.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_eval_result_task_time ON eval_result (task_id, create_time);
CREATE INDEX IF NOT EXISTS idx_eval_result_task_trial ON eval_result (task_id, trial_no);

-- 既有库增量升级（手工执行；工单 0135 R3，对已存在的旧 eval_result）：
-- ALTER TABLE eval_result
--     ADD COLUMN trial_no INT NOT NULL DEFAULT 1;
-- CREATE INDEX IF NOT EXISTS idx_eval_result_task_trial ON eval_result (task_id, trial_no);

-- 9. 评测 Rubric 评判标准表（工单 0133 R1：eval_rubric_mapper.xml 全部 SQL 列集 + EvalRubricPO）
CREATE TABLE IF NOT EXISTS eval_rubric (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rubric_id   VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    eval_type   VARCHAR(32)  NOT NULL,
    version     INT          NOT NULL DEFAULT 1,
    dimensions  TEXT,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    builtin     SMALLINT     NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rubric_id UNIQUE (rubric_id),
    CONSTRAINT uk_rubric_name UNIQUE (name)
);
COMMENT ON TABLE eval_rubric IS '评测Rubric评判标准表';
COMMENT ON COLUMN eval_rubric.rubric_id IS 'Rubric业务ID（唯一）';
COMMENT ON COLUMN eval_rubric.name IS 'Rubric名称（唯一，内置种子 builtin-* 前缀）';
COMMENT ON COLUMN eval_rubric.eval_type IS '评测类型（RAG_RETRIEVAL/ANSWER_QUALITY/CONTEXT_QUALITY/TOOL_CALL/AGENT_DECISION）';
COMMENT ON COLUMN eval_rubric.version IS '版本号';
COMMENT ON COLUMN eval_rubric.dimensions IS '维度JSON数组 [{key,label,weight,judgePrompt,binary}]（PO String→TEXT）';
COMMENT ON COLUMN eval_rubric.enabled IS '是否启用：0-停用，1-启用（PO Integer→SMALLINT）';
COMMENT ON COLUMN eval_rubric.builtin IS '内置种子标记：0-用户自建，1-内置（不可删改）（PO Integer→SMALLINT）';
COMMENT ON COLUMN eval_rubric.create_time IS '创建时间';
COMMENT ON COLUMN eval_rubric.update_time IS '更新时间（应用层维护）';
CREATE INDEX IF NOT EXISTS idx_eval_rubric_eval_type_enabled ON eval_rubric (eval_type, enabled);
-- 既有库为空表时直接执行上方 CREATE；dimensions JSON 由应用层（RubricService）校验
-- 维度 key 唯一 + 权重和=1，库层不额外建 JSON 约束。

-- 10. 评测门禁规则表（工单 0136 R4：分层门禁——安全维度一票否决 + 质量分阈值）
CREATE TABLE IF NOT EXISTS eval_gate (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gate_id          VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    safety_dims      TEXT,
    score_thresholds TEXT,
    trials           INT          NOT NULL DEFAULT 1,
    enabled          SMALLINT     NOT NULL DEFAULT 1,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gate_id UNIQUE (gate_id),
    CONSTRAINT uk_gate_name UNIQUE (name)
);
COMMENT ON TABLE eval_gate IS '评测门禁规则表';
COMMENT ON COLUMN eval_gate.gate_id IS '门禁规则业务ID（唯一）';
COMMENT ON COLUMN eval_gate.name IS '门禁名称（唯一）';
COMMENT ON COLUMN eval_gate.safety_dims IS '安全维度 JSON 对象 {dim: minSafety}（任一低于下限即一票否决；正向安全分口径，hallucination 配 0.8 等价幻觉率上限 0.2）（PO String→TEXT）';
COMMENT ON COLUMN eval_gate.score_thresholds IS '质量分阈值 JSON 对象 {metric: min}（metric 可为 overall/passRate 或 Rubric 维度 key）（PO String→TEXT）';
COMMENT ON COLUMN eval_gate.trials IS '回测评测试验次数 k（取自门禁配置）';
COMMENT ON COLUMN eval_gate.enabled IS '是否启用：0-停用，1-启用（PO Integer→SMALLINT）';
COMMENT ON COLUMN eval_gate.create_time IS '创建时间';
COMMENT ON COLUMN eval_gate.update_time IS '更新时间（应用层维护）';
-- safety_dims/score_thresholds JSON 结构与维度存在性由应用层（GateService）校验。

-- 11. 评测门禁记录表（工单 0136 R4：回测判定结论落账——PASS/BLOCK + 触发明细）
CREATE TABLE IF NOT EXISTS eval_gate_record (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_id      VARCHAR(64) NOT NULL,
    gate_id        VARCHAR(64) NOT NULL,
    task_id        VARCHAR(64) NOT NULL,
    result         VARCHAR(16) NOT NULL,
    trigger_detail TEXT,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_record_id UNIQUE (record_id)
);
COMMENT ON TABLE eval_gate_record IS '评测门禁记录表';
COMMENT ON COLUMN eval_gate_record.record_id IS '门禁记录业务ID（唯一）';
COMMENT ON COLUMN eval_gate_record.gate_id IS '门禁规则业务ID';
COMMENT ON COLUMN eval_gate_record.task_id IS '回测评测任务业务ID';
COMMENT ON COLUMN eval_gate_record.result IS '门禁结论：PASS-放行，BLOCK-拦截（安全越限/分数越限/任务失败）';
COMMENT ON COLUMN eval_gate_record.trigger_detail IS '触发明细 JSON 数组 [{ruleType,dim,actual,threshold,note}]（PO String→TEXT）';
COMMENT ON COLUMN eval_gate_record.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_eval_gate_record_gate_time ON eval_gate_record (gate_id, create_time);
CREATE INDEX IF NOT EXISTS idx_eval_gate_record_task ON eval_gate_record (task_id);

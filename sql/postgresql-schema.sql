-- =============================================================================
-- 观测服务建表脚本（PostgreSQL 版）
-- 工单 0123（三期 O3：PG 全量 DDL 翻译）补账：为从未入库过 DDL 的表补建脚本，2026-09-10。
-- 工单 0133/0134（三期 R1/R2）：eval_dataset 增列 version/pool/source/frozen 并入建表
--   （既有库手工执行表 6 后注释中的 ALTER）；新增第 9 表 eval_rubric，2026-09-10。
-- 工单 0135/0136（三期 R3/R4）：eval_task 增列 trials/pass_threshold/pass_rate/score_std_dev/gate_id、
--   eval_result 增列 trial_no 并入建表（既有库手工执行表 7/8 后注释中的 ALTER）；
--   新增第 10/11 表 eval_gate、eval_gate_record，2026-09-10。
-- 工单 0137（三期 S1）：新增第 12 表 patrol_record（巡检拨测记录），2026-09-11。
-- 工单 0138（三期 S2）：新增第 13 表 eval_case_candidate（Case 挖掘候选），2026-09-11。
-- 工单 0148（四期 U2）：新增第 14 表 model_pricing（模型计价），2026-09-11。
-- 工单 0150（四期 U4）：新增第 15 表 trace_annotation（人工评分注解），2026-09-11。
-- 工单 0154（四期 U8）：新增第 16 表 drift_event（检索分数漂移事件），2026-09-11。
-- 工单 0170（四期 X1）：新增第 17 表 eval_pairwise_record（pairwise 对局记录），2026-09-11。
-- 工单 0176（四期 X7）：新增第 18 表 judge_cache（judge 判定缓存），2026-09-11。
-- 工单 0179（四期 Y3）：新增第 19 表 alert_silence（告警静默窗口），2026-09-11。
-- 工单 0180（四期 Y4）：新增第 20 表 dlq_record（死信记录），2026-09-11。
-- 工单 0182（四期 Y6）：新增第 21 表 config_change_event（配置变更审计），2026-09-11。
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

-- 12. 巡检拨测记录表（工单 0137 S1：在线评测三手段之巡检——定时回放拨测 + 三态判定 + 轻量分）
CREATE TABLE IF NOT EXISTS patrol_record (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    round_id      VARCHAR(64)  NOT NULL,
    task_ref      VARCHAR(128) NOT NULL,
    query         TEXT         NOT NULL,
    agent_id      VARCHAR(64),
    status        VARCHAR(16)  NOT NULL,
    score         DECIMAL(10,6),
    duration_ms   BIGINT       NOT NULL DEFAULT 0,
    error_summary VARCHAR(512),
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE patrol_record IS '巡检拨测记录表（追加写日志表，无唯一业务键：round_id 每轮新生成）';
COMMENT ON COLUMN patrol_record.round_id IS '轮次ID（同轮共享，/patrol/latest 取最近一轮聚合）';
COMMENT ON COLUMN patrol_record.task_ref IS '种子任务标识（固定集为 q-N 序号；golden 池为 ds-N 序号）';
COMMENT ON COLUMN patrol_record.query IS '拨测查询原文';
COMMENT ON COLUMN patrol_record.agent_id IS '目标智能体ID（空=在线回放 provider 默认）';
COMMENT ON COLUMN patrol_record.status IS '结果三态：SUCCESS-成功，FAIL-失败，TIMEOUT-超时';
COMMENT ON COLUMN patrol_record.score IS '轻量质量分（0-1，TraceQualityCalculator 启发式非空维度均值；仅成功样本可评估时有值）';
COMMENT ON COLUMN patrol_record.duration_ms IS '拨测耗时（毫秒；TIMEOUT 记录为超时预算值）';
COMMENT ON COLUMN patrol_record.error_summary IS '错误摘要（FAIL/TIMEOUT 原因，截断存储）';
COMMENT ON COLUMN patrol_record.trace_id IS '在线回放关联 traceId（可查链路详情）';
COMMENT ON COLUMN patrol_record.create_time IS '创建时间（追加写，无更新）';
CREATE INDEX IF NOT EXISTS idx_patrol_round_id ON patrol_record (round_id);
CREATE INDEX IF NOT EXISTS idx_patrol_time ON patrol_record (create_time);

-- 13. Case 候选表（工单 0138 S2：三来源挖掘统一入池——低分评测/失败链路/巡检失败；回填错题集）
CREATE TABLE IF NOT EXISTS eval_case_candidate (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source              VARCHAR(24)  NOT NULL,
    source_ref          VARCHAR(128) NOT NULL,
    trace_id            VARCHAR(64),
    query               TEXT,
    answer_summary      TEXT,
    hit_doc_count       INT,
    tool_list           TEXT,
    reason              VARCHAR(512),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    promoted_dataset_id VARCHAR(64),
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 工单 0139 S3 归因四列（既有库手工执行：
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution VARCHAR(24);
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_note VARCHAR(512);
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_by VARCHAR(64);
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_at TIMESTAMP;）
    attribution         VARCHAR(24),
    attribution_note    VARCHAR(512),
    attribution_by      VARCHAR(64),
    attribution_at      TIMESTAMP,
    CONSTRAINT uk_case_source_ref UNIQUE (source, source_ref)
);
COMMENT ON TABLE eval_case_candidate IS 'Case 候选表（挖掘枢纽——线上问题自动沉淀为评测资产）';
COMMENT ON COLUMN eval_case_candidate.source IS '来源：EVAL_LOW_SCORE-低分评测结果，TRACE_FAIL-失败超时链路，PATROL_FAIL-巡检失败';
COMMENT ON COLUMN eval_case_candidate.source_ref IS '来源内唯一引用（评测=taskId:trialNo:traceId、链路=traceId、巡检=pid-记录ID 或 traceId），与 source 组成幂等键';
COMMENT ON COLUMN eval_case_candidate.trace_id IS '关联 traceId（可关查链路详情，可空）';
COMMENT ON COLUMN eval_case_candidate.query IS '查询原文（回填错题集时作为 prompt）';
COMMENT ON COLUMN eval_case_candidate.answer_summary IS '答案摘要（截断存储的上下文快照）';
COMMENT ON COLUMN eval_case_candidate.hit_doc_count IS '命中文档数（检索上下文快照）';
COMMENT ON COLUMN eval_case_candidate.tool_list IS '工具调用列表 JSON 数组原文（工具上下文快照，可空）';
COMMENT ON COLUMN eval_case_candidate.reason IS '入池原因（分数值/失败状态/巡检错误摘要）';
COMMENT ON COLUMN eval_case_candidate.status IS '处置状态：PENDING-待处置，PROMOTED-已回填错题集，IGNORED-已忽略';
COMMENT ON COLUMN eval_case_candidate.promoted_dataset_id IS '回填目标数据集 ID（PROMOTED 时有值）';
COMMENT ON COLUMN eval_case_candidate.create_time IS '创建时间';
COMMENT ON COLUMN eval_case_candidate.attribution IS '归因四分层：PLANNING-规划错，TOOL-工具错，ENVIRONMENT-环境错，SKILL-知识错（未标注 NULL）';
COMMENT ON COLUMN eval_case_candidate.attribution_note IS '归因备注（判定依据）';
COMMENT ON COLUMN eval_case_candidate.attribution_by IS '标注人（操作留痕）';
COMMENT ON COLUMN eval_case_candidate.attribution_at IS '标注时间（操作留痕）';
CREATE INDEX IF NOT EXISTS idx_case_status ON eval_case_candidate (status, create_time);
CREATE INDEX IF NOT EXISTS idx_case_attribution ON eval_case_candidate (attribution, attribution_at);

-- 14. 模型计价表（工单 0148 U2：成本看板读时派生——cost = tokens/1000 × 单价）
CREATE TABLE IF NOT EXISTS model_pricing (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model              VARCHAR(128)  NOT NULL,
    input_price_per_1k DECIMAL(18,6) NOT NULL DEFAULT 0,
    output_price_per_1k DECIMAL(18,6) NOT NULL DEFAULT 0,
    remark             VARCHAR(256),
    update_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pricing_model UNIQUE (model)
);
COMMENT ON TABLE model_pricing IS '模型计价表（成本读时派生口径：cost = tokens/1000 × 单价）';
COMMENT ON COLUMN model_pricing.model IS '模型名（与 chat_result_log.model_version 匹配，唯一）';
COMMENT ON COLUMN model_pricing.input_price_per_1k IS '输入单价：每 1K prompt token';
COMMENT ON COLUMN model_pricing.output_price_per_1k IS '输出单价：每 1K completion token';
COMMENT ON COLUMN model_pricing.remark IS '备注（币种/生效口径）';
COMMENT ON COLUMN model_pricing.update_time IS '更新时间（应用层维护）';
-- 幂等种子（示例价，运营按需修正）：INSERT ... ON CONFLICT (model) DO NOTHING
-- INSERT INTO model_pricing (model, input_price_per_1k, output_price_per_1k, remark)
-- VALUES ('deepseek-v4-pro', 0.002, 0.008, '示例价') ON CONFLICT (model) DO NOTHING;

-- 15. 人工注解表（工单 0150 U4：trace 人工 1-5 分评分 + 依据；借鉴 Langfuse annotations）
CREATE TABLE IF NOT EXISTS trace_annotation (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id    VARCHAR(64)  NOT NULL,
    score       INT          NOT NULL,
    note        VARCHAR(1024),
    operator    VARCHAR(64)  NOT NULL DEFAULT 'unknown',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_annotation UNIQUE (trace_id, operator)
);
COMMENT ON TABLE trace_annotation IS '人工评分注解表（主观质量资产化：1-5 分 + 依据，重评即改判）';
COMMENT ON COLUMN trace_annotation.trace_id IS '关联链路 traceId';
COMMENT ON COLUMN trace_annotation.score IS '评分 1-5（1=很差，5=很好）';
COMMENT ON COLUMN trace_annotation.note IS '判定依据备注';
COMMENT ON COLUMN trace_annotation.operator IS '标注人（操作留痕）';
COMMENT ON COLUMN trace_annotation.create_time IS '首次标注时间';
COMMENT ON COLUMN trace_annotation.update_time IS '最近标注时间（重评即改判）';

-- 16. 漂移事件表（工单 0154 U8：检索分数分布漂移留痕）
CREATE TABLE IF NOT EXISTS drift_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metric         VARCHAR(64)   NOT NULL,
    current_value  DECIMAL(18,6),
    previous_value DECIMAL(18,6),
    threshold      DECIMAL(18,6),
    detail         TEXT,
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE drift_event IS '漂移事件表（检索分数分布漂移留痕：本周 vs 上周）';
COMMENT ON COLUMN drift_event.metric IS '指标名（当前固定 rerank_mean）';
COMMENT ON COLUMN drift_event.detail IS '上下文 JSON（样本数/空检索率/窗口）';
CREATE INDEX IF NOT EXISTS idx_drift_time ON drift_event (create_time);

-- 17. pairwise 对局记录表（工单 0170 X1：两任务同题 A/B 判定——X2 Elo 重算数据源）
CREATE TABLE IF NOT EXISTS eval_pairwise_record (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_a      VARCHAR(64)  NOT NULL,
    task_b      VARCHAR(64)  NOT NULL,
    dataset_id  VARCHAR(64),
    query       TEXT,
    outcome     VARCHAR(16)  NOT NULL,
    pair_no     INT          NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE eval_pairwise_record IS 'pairwise 对局记录表（两任务同题 A/B 判定）';
COMMENT ON COLUMN eval_pairwise_record.outcome IS '判定：A_WIN / B_WIN / TIE';
CREATE INDEX IF NOT EXISTS idx_pair_tasks ON eval_pairwise_record (task_a, task_b);

-- 18. judge 判定缓存表（工单 0176 X7：等价输入复用判定结果，省 LLM 调用成本）
CREATE TABLE IF NOT EXISTS judge_cache (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cache_key   VARCHAR(64)   NOT NULL,
    output      TEXT          NOT NULL,
    rubric_id   VARCHAR(64),
    hit_count   BIGINT        NOT NULL DEFAULT 0,
    update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_judge_key UNIQUE (cache_key)
);
COMMENT ON TABLE judge_cache IS 'judge 判定缓存表（等价输入复用判定结果）';
COMMENT ON COLUMN judge_cache.cache_key IS '缓存键（判定 prompt 归一化 sha256）';
COMMENT ON COLUMN judge_cache.hit_count IS '命中次数';

-- 19. 告警静默表（工单 0179 Y3）
CREATE TABLE IF NOT EXISTS alert_silence (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    silence_key VARCHAR(128) NOT NULL,
    starts_at   TIMESTAMP    NOT NULL,
    ends_at     TIMESTAMP    NOT NULL,
    created_by  VARCHAR(64)  NOT NULL DEFAULT 'unknown',
    reason      VARCHAR(256),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE alert_silence IS '告警静默表（精确+前缀通配匹配，窗口惰性失效）';
COMMENT ON COLUMN alert_silence.silence_key IS '静默键（精确匹配；* 结尾为前缀通配）';
CREATE INDEX IF NOT EXISTS idx_silence_key ON alert_silence (silence_key);

-- 20. 死信记录表（工单 0180 Y4：MQ 消费失败消息留痕与重放）
CREATE TABLE IF NOT EXISTS dlq_record (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_tag   VARCHAR(32)  NOT NULL,
    payload     TEXT,
    retry_count INT          NOT NULL DEFAULT 0,
    last_error  VARCHAR(512),
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dlq_record IS '死信记录表（MQ 消费失败留痕与重放）';
COMMENT ON COLUMN dlq_record.topic_tag IS '消息标签（decision/retrieval/chat_result/tool_call/memory_recall）';
COMMENT ON COLUMN dlq_record.payload IS '消息原文（截断 8KB）';
COMMENT ON COLUMN dlq_record.status IS '状态：PENDING-待重放，REPLAYED-已成功重放';
CREATE INDEX IF NOT EXISTS idx_dlq_status ON dlq_record (status, create_time);

-- 21. 配置变更事件表（工单 0182 Y6：关键配置 update 前后 diff 留痕，敏感值脱敏）
CREATE TABLE IF NOT EXISTS config_change_event (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name   VARCHAR(64)  NOT NULL,
    biz_key      VARCHAR(128),
    changes_json TEXT,
    operator     VARCHAR(64)  NOT NULL DEFAULT 'unknown',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE config_change_event IS '配置变更事件表（漂移审计：字段级 from→to，敏感值脱敏）';
CREATE INDEX IF NOT EXISTS idx_config_table ON config_change_event (table_name, create_time);

-- 22. 消费延迟快照表（工单 0220 AD1：topic lag 采样 + 水位分级）
CREATE TABLE IF NOT EXISTS lag_snapshot (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic       VARCHAR(128) NOT NULL,
    lag         BIGINT       NOT NULL DEFAULT 0,
    level       VARCHAR(16)  NOT NULL,
    sampled_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE lag_snapshot IS '消费延迟快照（AD1：水位分级 OK/WARN/CRITICAL）';
CREATE INDEX IF NOT EXISTS idx_lag_sampled ON lag_snapshot (sampled_at);

-- 23. 数据质量规则表（工单 0221 AD2：期望规则，借鉴 Great Expectations）
CREATE TABLE IF NOT EXISTS quality_rule (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    target     VARCHAR(128),
    field      VARCHAR(128) NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    params_json TEXT,
    enabled    SMALLINT     NOT NULL DEFAULT 1,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_rule_name UNIQUE (name)
);
COMMENT ON TABLE quality_rule IS '数据质量期望规则（NOT_NULL/RANGE/ENUM/FRESHNESS）';

-- 24. 数据质量校验结果表（工单 0221 AD2：断言运行留痕）
CREATE TABLE IF NOT EXISTS quality_result (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_name   VARCHAR(128) NOT NULL,
    pass        SMALLINT     NOT NULL,
    checked     INT          NOT NULL DEFAULT 0,
    violated    INT          NOT NULL DEFAULT 0,
    samples_json TEXT,
    ran_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE quality_result IS '数据质量校验结果（失败样本 JSON 封顶 5 条）';
CREATE INDEX IF NOT EXISTS idx_quality_result_rule ON quality_result (rule_name, ran_at);

-- 25. 回填任务表（工单 0222 AD3：范围分片 + 已完成集，幂等重跑）
CREATE TABLE IF NOT EXISTS backfill_job (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id       VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    range_start  BIGINT       NOT NULL,
    range_end    BIGINT       NOT NULL,
    shard_count  INT          NOT NULL,
    completed_json TEXT,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_backfill_job_id UNIQUE (job_id)
);
COMMENT ON TABLE backfill_job IS '回填任务（AD3：仅 pending 分片执行，完成即落档）';

-- 26. SLA 错过记录表（工单 0223 AD4：预计 vs 实际超时留痕）
CREATE TABLE IF NOT EXISTS sla_miss (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task        VARCHAR(128) NOT NULL,
    expected_ms BIGINT       NOT NULL,
    actual_ms   BIGINT       NOT NULL,
    overdue_ms  BIGINT       NOT NULL,
    detected_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sla_miss IS 'SLA 错过记录（AD4：调度任务超预计完成时长）';
CREATE INDEX IF NOT EXISTS idx_sla_miss_task ON sla_miss (task, detected_at);

-- 27. 资产实体表（工单 0285 AK1：dataset/job/model 统一 URN 注册）
CREATE TABLE IF NOT EXISTS asset_entity (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    urn         VARCHAR(256) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    qualifier   VARCHAR(256) NOT NULL,
    display_name VARCHAR(256),
    owner       VARCHAR(64),
    note        VARCHAR(512),
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_entity_urn UNIQUE (urn)
);
COMMENT ON TABLE asset_entity IS '资产实体（AK1：URN 全局唯一身份）';

-- 28. 血缘边表（工单 0286 AK2：表级上下游邻接，manual|auto 来源）
CREATE TABLE IF NOT EXISTS lineage_edge (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_urn    VARCHAR(256) NOT NULL,
    to_urn      VARCHAR(256) NOT NULL,
    source      VARCHAR(16)  NOT NULL DEFAULT 'manual',
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lineage_edge UNIQUE (from_urn, to_urn, source)
);
COMMENT ON TABLE lineage_edge IS '血缘边（AK2：auto 来源由 RUN_COMPLETE 事件幂等补边）';

-- 29. 血缘运行表（工单 0287 AK3：run 事件留档，event_key 幂等）
CREATE TABLE IF NOT EXISTS lineage_run (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_key   VARCHAR(256) NOT NULL,
    event_type  VARCHAR(32)  NOT NULL,
    job_urn     VARCHAR(256) NOT NULL,
    inputs_json TEXT,
    outputs_json TEXT,
    at_ms       BIGINT       NOT NULL,
    error       VARCHAR(512),
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lineage_run_event UNIQUE (event_key)
);
COMMENT ON TABLE lineage_run IS '血缘运行（AK3：RUN_START/COMPLETE/FAIL 事件，OpenLineage run 模型思想）';

-- 30. 资产 schema 变更表（工单 0289 AK5：字段级变更时间线，破坏性标记）
CREATE TABLE IF NOT EXISTS asset_schema_change (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_urn   VARCHAR(256) NOT NULL,
    at_ms       BIGINT       NOT NULL,
    field       VARCHAR(128) NOT NULL,
    change      VARCHAR(32)  NOT NULL,
    before      VARCHAR(128),
    after       VARCHAR(128),
    breaking    BOOLEAN      NOT NULL DEFAULT FALSE,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE asset_schema_change IS '资产 schema 变更（AK5：ADDED/REMOVED/TYPE_CHANGED/NULLABLE_CHANGED）';
CREATE INDEX IF NOT EXISTS idx_schema_change_asset ON asset_schema_change (asset_urn, at_ms);

-- 31. 通知表（工单 0293 AL1：事件通知中心，状态机 + 指纹去重）
CREATE TABLE IF NOT EXISTS notification (
    id          VARCHAR(64)  PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    severity    VARCHAR(16)  NOT NULL,
    title       VARCHAR(256) NOT NULL,
    payload_json TEXT,
    fingerprint VARCHAR(256),
    subscriber  VARCHAR(128) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at_ms BIGINT     NOT NULL,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE notification IS '通知中心（AL1：PENDING/SENT/FAILED/SUPPRESSED/DIGESTED/DEDUPED；INBOX 渠道落库）';
CREATE INDEX IF NOT EXISTS idx_notification_subscriber ON notification (subscriber, status);

-- 32. 提示优化实验表（工单 0329 AO7：签名/数据集指纹 + 候选与得分曲线快照）
CREATE TABLE IF NOT EXISTS prompt_optimization_experiment (
    id                    VARCHAR(64)  PRIMARY KEY,
    name                  VARCHAR(128) NOT NULL,
    signature_fingerprint VARCHAR(64),
    dataset_fingerprint   VARCHAR(64),
    candidates_json       TEXT,
    score_curve_json      TEXT,
    winner                TEXT,
    status                VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    created_at_ms         BIGINT       NOT NULL,
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE prompt_optimization_experiment IS '提示优化实验（AO7：RUNNING/DONE/FAILED；候选快照与得分曲线 JSON 留痕）';
CREATE INDEX IF NOT EXISTS idx_optim_experiment_created ON prompt_optimization_experiment (created_at_ms);

-- 33. 异常事件表（工单 0412 AX8：检测器异常全生命周期 OPEN→RESOLVED）
CREATE TABLE IF NOT EXISTS anomaly_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       VARCHAR(64)  NOT NULL,
    detector       VARCHAR(64)  NOT NULL,
    metric_name    VARCHAR(128) NOT NULL,
    side           VARCHAR(8)   NOT NULL,
    score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    triggered_at   BIGINT       NOT NULL,
    resolved_at    BIGINT,
    context_json   TEXT,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_anomaly_event_id UNIQUE (event_id)
);
COMMENT ON TABLE anomaly_event IS '异常事件（AX8：OPEN→RESOLVED 全生命周期，连续 N 点回归带内判定恢复）';
COMMENT ON COLUMN anomaly_event.update_time IS '更新时间（应用层维护）';
CREATE INDEX IF NOT EXISTS idx_anomaly_event_metric ON anomaly_event (metric_name, status);

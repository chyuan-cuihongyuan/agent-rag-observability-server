-- =============================================================================
-- 观测服务建表脚本（MySQL 版）
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
-- 口径：
--   (1) agent_decision_log / rag_retrieval_log / chat_result_log 三表以
--       docs/02-agent-rag-observability-server/09-补充技术细节.md 第 1040-1128 行
--       的 MySQL DDL 为准（已去除全部反引号）；mapper INSERT 中存在而文档缺失的
--       演进列逐列补齐（详见各表注释中的「补列」标记）。
--       特别说明：rag_retrieval_log 文档中的 cost_time_ms 列在 mapper 中已演进为
--       retrieval_cost_ms（同语义：检索耗时），本脚本按 mapper 实际写入列命名，
--       类型沿用文档 BIGINT。
--   (2) tool_call_log / memory_recall_log / eval_dataset / eval_task / eval_result
--       五表从 agent-rag-observability-server-app 的 mapper XML 全部 SQL 列集
--       + infrastructure dao/po 对应 PO 字段类型反推。
-- 类型口径：TINYINT 对应 PO Integer 状态/开关列；eval 分数列 PO 为 Double →
--   DECIMAL(10,6)；JSON 列（PO 为 String，以 JSON 文本读写）MySQL 侧保留 JSON 类型；
--   反推表的 *_json 文本列用 TEXT。不使用 ON UPDATE CURRENT_TIMESTAMP，updated
--   时间统一由应用层维护（mapper UPDATE 语句已显式写 update_time = NOW()）。
-- 唯一键反推依据（重点注明）：
--   eval_dataset  uk_dataset_id：selectByDatasetId 按 dataset_id 精确定位单数据集。
--   eval_task     uk_task_id   ：selectByTaskId / updateStatus / updateProgress /
--                                updateTotalCount 均按 task_id 精确定位单任务。
--   日志类 4 表（agent_decision_log / rag_retrieval_log / chat_result_log /
--   tool_call_log / memory_recall_log）与 eval_result：纯追加账本，无唯一键；
--   eval_result 同一 task_id 下多条明细，仅建普通索引。
-- =============================================================================

-- 1. Agent 决策日志表（以 docs/02/09 L1040-1062 为准；mapper 补 4 列：
--    tool_call_times / tool_retry_times / model_version / error_message）
CREATE TABLE IF NOT EXISTS agent_decision_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id          VARCHAR(64)  NOT NULL COMMENT '链路追踪ID',
    tenant_id         VARCHAR(64)  DEFAULT '' COMMENT '租户ID',
    owner_user_id     VARCHAR(64)  DEFAULT '' COMMENT '所属用户ID',
    session_id        VARCHAR(64)  DEFAULT '' COMMENT '会话ID',
    agent_id          VARCHAR(64)  NOT NULL COMMENT '智能体ID',
    source_service    VARCHAR(32)  NOT NULL COMMENT '来源服务',
    user_query        TEXT         COMMENT '用户查询',
    intent_type       VARCHAR(64)  DEFAULT '' COMMENT '意图类型',
    branch_type       VARCHAR(32)  NOT NULL COMMENT '分支类型',
    selected_tool_list JSON        COMMENT '选择的工具列表（JSON 文本，PO String）',
    plan_steps        JSON         COMMENT '规划步骤（JSON 文本，PO String）',
    decision_reason   TEXT         COMMENT '决策原因',
    tool_call_times   INT          DEFAULT 0 COMMENT '工具调用次数（mapper 演进补列）',
    tool_retry_times  INT          DEFAULT 0 COMMENT '工具重试次数（mapper 演进补列）',
    model_version     VARCHAR(64)  DEFAULT NULL COMMENT '模型版本（mapper 演进补列）',
    agent_status      VARCHAR(32)  NOT NULL COMMENT 'Agent状态',
    error_message     TEXT         COMMENT '错误信息（mapper 演进补列）',
    cost_time_ms      BIGINT       DEFAULT 0 COMMENT '耗时(毫秒)',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent决策日志表';

-- 2. RAG 检索日志表（以 docs/02/09 L1068-1089 为准；mapper 补 3 列：source_service /
--    retrieval_cost_ms（取代文档 cost_time_ms，同语义）/ rag_strategy_version）
CREATE TABLE IF NOT EXISTS rag_retrieval_log (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id           VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    tenant_id          VARCHAR(64) DEFAULT '' COMMENT '租户ID',
    owner_user_id      VARCHAR(64) DEFAULT '' COMMENT '所属用户ID',
    session_id         VARCHAR(64) DEFAULT '' COMMENT '会话ID',
    agent_id           VARCHAR(64) NOT NULL COMMENT '智能体ID',
    source_service     VARCHAR(32) NOT NULL COMMENT '来源服务（mapper 演进补列）',
    query_text         TEXT        COMMENT '原始查询',
    rewrite_text       TEXT        COMMENT '改写查询',
    retrieval_topk     INT         DEFAULT 0 COMMENT '检索TopK',
    retrieval_count    INT         DEFAULT 0 COMMENT '召回数量',
    source_docs        JSON        COMMENT '来源文档（JSON 文本，PO String）',
    rerank_scores      JSON        COMMENT '重排序分数（JSON 文本，PO String）',
    empty_retrieval    TINYINT     DEFAULT 0 COMMENT '是否空召回：0-否，1-是（PO Integer）',
    retrieval_stages   JSON        COMMENT '检索阶段（JSON 文本，PO String）',
    retrieval_cost_ms  BIGINT      DEFAULT 0 COMMENT '检索耗时(毫秒)（文档列 cost_time_ms 的 mapper 实名）',
    rag_strategy_version VARCHAR(64) DEFAULT NULL COMMENT 'RAG策略版本（mapper 演进补列）',
    cost_time_ms       BIGINT      DEFAULT 0 COMMENT '总耗时(毫秒)（文档保留列，mapper 现未写入）',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG检索日志表';

-- 3. 问答结果日志表（以 docs/02/09 L1095-1113 为准；mapper 补 2 列：
--    source_service / model_version）
CREATE TABLE IF NOT EXISTS chat_result_log (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id           VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    tenant_id          VARCHAR(64) DEFAULT '' COMMENT '租户ID',
    owner_user_id      VARCHAR(64) DEFAULT '' COMMENT '所属用户ID',
    session_id         VARCHAR(64) DEFAULT '' COMMENT '会话ID',
    agent_id           VARCHAR(64) NOT NULL COMMENT '智能体ID',
    source_service     VARCHAR(32) NOT NULL COMMENT '来源服务（mapper 演进补列）',
    question           TEXT        COMMENT '用户问题',
    answer             TEXT        COMMENT '模型回答',
    prompt_tokens      BIGINT      DEFAULT 0 COMMENT 'Prompt Token',
    completion_tokens  BIGINT      DEFAULT 0 COMMENT 'Completion Token',
    total_cost_time_ms BIGINT      DEFAULT 0 COMMENT '总耗时(毫秒)',
    final_status       VARCHAR(32) NOT NULL COMMENT '最终状态',
    model_version      VARCHAR(64) DEFAULT NULL COMMENT '模型版本（mapper 演进补列）',
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答结果日志表';

-- 4. 工具调用日志表（反推：tool_call_log_mapper.xml INSERT 列集 + ToolCallLogPO）
CREATE TABLE IF NOT EXISTS tool_call_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id        VARCHAR(64)  NOT NULL COMMENT '链路追踪ID',
    span_id         VARCHAR(64)  DEFAULT NULL COMMENT '跨度ID',
    parent_span_id  VARCHAR(64)  DEFAULT NULL COMMENT '父跨度ID',
    tool_name       VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_input      TEXT         COMMENT '工具入参（JSON 文本，PO String）',
    tool_output     TEXT         COMMENT '工具输出（JSON 文本，PO String）',
    status          VARCHAR(32)  NOT NULL COMMENT '调用状态（SUCCESS/FAILURE 等）',
    cost_time_ms    INT          DEFAULT 0 COMMENT '耗时(毫秒)（PO Integer）',
    error_message   TEXT         COMMENT '错误信息',
    call_order      INT          DEFAULT 0 COMMENT '调用序号（同链路内的顺序）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志表';

-- 5. 记忆召回日志表（反推：memory_recall_log_mapper.xml INSERT 列集 + MemoryRecallLogPO）
CREATE TABLE IF NOT EXISTS memory_recall_log (
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id             VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    query_text           TEXT        COMMENT '原始查询',
    session_memory_count INT         DEFAULT 0 COMMENT '会话记忆召回条数（PO Integer）',
    agent_memory_count   INT         DEFAULT 0 COMMENT '智能体记忆召回条数（PO Integer）',
    session_memory_scores TEXT       COMMENT '会话记忆评分列表（JSON 文本，PO String）',
    agent_memory_scores  TEXT        COMMENT '智能体记忆评分列表（JSON 文本，PO String）',
    inject_content       TEXT        COMMENT '注入上下文内容',
    cost_time_ms         INT         DEFAULT 0 COMMENT '耗时(毫秒)（PO Integer）',
    create_time          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆召回日志表';

-- 6. 评测数据集表（反推：eval_dataset_mapper.xml INSERT/SELECT 列集 + EvalDatasetPO；
--    工单 0134 R2 增列 version/pool/source/frozen 已并入建表）
CREATE TABLE IF NOT EXISTS eval_dataset (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    dataset_id   VARCHAR(64)  NOT NULL COMMENT '数据集业务ID（唯一）',
    dataset_name VARCHAR(128) NOT NULL COMMENT '数据集名称（版本化锚点，同 name 多版本）',
    description  VARCHAR(512) DEFAULT NULL COMMENT '数据集描述',
    item_count   INT          DEFAULT 0 COMMENT '条目数（PO Integer）',
    items_json   TEXT         COMMENT '数据集条目 JSON 数组（文本读写，PO String）',
    version      INT          NOT NULL DEFAULT 1 COMMENT '版本号（同 dataset_name 递增，快照复制产生新版本）',
    pool         VARCHAR(16)  DEFAULT NULL COMMENT '样本池：golden/challenge/wrong；NULL=未分类（存量兼容）',
    source       VARCHAR(16)  DEFAULT NULL COMMENT '来源标记：trace/manual/seed',
    frozen       TINYINT      NOT NULL DEFAULT 0 COMMENT '版本冻结位：0-可编辑，1-条目不可改',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_id (dataset_id),
    INDEX idx_create_time (create_time),
    INDEX idx_dataset_name_version (dataset_name, version),
    INDEX idx_pool (pool)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测数据集表';

-- 既有库增量升级（手工执行；本文件以幂等 CREATE IF NOT EXISTS 为主，ALTER 仅对已存在的旧表）：
-- ALTER TABLE eval_dataset
--     ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号（同 dataset_name 递增）',
--     ADD COLUMN pool VARCHAR(16) DEFAULT NULL COMMENT '样本池：golden/challenge/wrong；NULL=未分类',
--     ADD COLUMN source VARCHAR(16) DEFAULT NULL COMMENT '来源标记：trace/manual/seed',
--     ADD COLUMN frozen TINYINT NOT NULL DEFAULT 0 COMMENT '版本冻结位：0-可编辑，1-条目不可改',
--     ADD INDEX idx_dataset_name_version (dataset_name, version),
--     ADD INDEX idx_pool (pool);

-- 7. 评测任务表（反推：eval_task_mapper.xml 全部 SQL 列集 + EvalTaskPO；
--    工单 0135 R3 增列 trials/pass_threshold/pass_rate/score_std_dev、工单 0136 R4 增列 gate_id 已并入建表）
CREATE TABLE IF NOT EXISTS eval_task (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_id             VARCHAR(64)   NOT NULL COMMENT '任务业务ID（唯一）',
    task_name           VARCHAR(128)  NOT NULL COMMENT '任务名称',
    eval_type           VARCHAR(32)   NOT NULL COMMENT '评测类型（RAG/AGENT 等）',
    dataset_id          VARCHAR(64)   NOT NULL COMMENT '关联数据集业务ID',
    status              VARCHAR(32)   NOT NULL COMMENT '任务状态（PENDING/RUNNING/COMPLETED/FAILED）',
    total_count         INT           DEFAULT 0 COMMENT '总条目数（样本数×trials，PO Integer）',
    completed_count     INT           DEFAULT 0 COMMENT '已完成条目数（含全部 trial 累计，PO Integer）',
    model_version       VARCHAR(64)   DEFAULT NULL COMMENT '模型版本',
    rag_strategy_version VARCHAR(64)  DEFAULT NULL COMMENT 'RAG策略版本',
    avg_overall_score   DECIMAL(10,6) DEFAULT NULL COMMENT '平均总分（全部 trial 全部样本均值，PO Double）',
    trials              INT           NOT NULL DEFAULT 1 COMMENT '试验次数 k（工单 0135 R3 Pass@k；1=旧行为）',
    pass_threshold      DECIMAL(10,6) DEFAULT NULL COMMENT '样本达标阈值（NULL=应用默认 0.5，沿用 Rubric 达标线）',
    pass_rate           DECIMAL(10,6) DEFAULT NULL COMMENT '通过率 Pass@k（k 次 trial 至少 1 次达标的样本占比，完成时回写）',
    score_std_dev       DECIMAL(10,6) DEFAULT NULL COMMENT 'per-trial 综合分均值的标准差（完成时回写，总体口径）',
    gate_id             VARCHAR(64)   DEFAULT NULL COMMENT '绑定的门禁规则 ID（工单 0136 R4 回测；非空时完成回调判定）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_status_update (status, update_time),
    INDEX idx_gate_id (gate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测任务表';

-- 既有库增量升级（手工执行；工单 0135/0136 R3/R4，对已存在的旧 eval_task）：
-- ALTER TABLE eval_task
--     ADD COLUMN trials INT NOT NULL DEFAULT 1 COMMENT '试验次数 k（Pass@k；1=旧行为）',
--     ADD COLUMN pass_threshold DECIMAL(10,6) DEFAULT NULL COMMENT '样本达标阈值（NULL=默认 0.5）',
--     ADD COLUMN pass_rate DECIMAL(10,6) DEFAULT NULL COMMENT '通过率 Pass@k（完成时回写）',
--     ADD COLUMN score_std_dev DECIMAL(10,6) DEFAULT NULL COMMENT 'per-trial 综合分标准差（完成时回写）',
--     ADD COLUMN gate_id VARCHAR(64) DEFAULT NULL COMMENT '绑定门禁规则 ID（回测）',
--     ADD INDEX idx_gate_id (gate_id);

-- 8. 评测结果表（反推：eval_result_mapper.xml INSERT/batchInsert/SELECT 全部 31 业务列
--    逐列核对 + EvalResultPO；分数列 PO 均 Double → DECIMAL(10,6)；
--    工单 0135 R3 增列 trial_no 已并入建表）
CREATE TABLE IF NOT EXISTS eval_result (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_id              VARCHAR(64)   NOT NULL COMMENT '评测任务业务ID（一任务多条明细）',
    trial_no             INT           NOT NULL DEFAULT 1 COMMENT '试验序号（1..k；k=1 时恒为 1，工单 0135 R3）',
    trace_id             VARCHAR(64)   DEFAULT NULL COMMENT '链路追踪ID',
    query_text           TEXT          COMMENT '评测问题',
    standard_answer      TEXT          COMMENT '标准答案',
    actual_answer        TEXT          COMMENT '实际回答',
    recall_score         DECIMAL(10,6) DEFAULT NULL COMMENT '召回率',
    precision_score      DECIMAL(10,6) DEFAULT NULL COMMENT '精确率',
    f1_score             DECIMAL(10,6) DEFAULT NULL COMMENT 'F1 分数',
    top3_hit_rate        DECIMAL(10,6) DEFAULT NULL COMMENT 'Top3 命中率',
    mrr_score            DECIMAL(10,6) DEFAULT NULL COMMENT 'MRR 分数',
    ndcg_score           DECIMAL(10,6) DEFAULT NULL COMMENT 'NDCG 分数',
    map_score            DECIMAL(10,6) DEFAULT NULL COMMENT 'MAP 分数',
    answer_similarity    DECIMAL(10,6) DEFAULT NULL COMMENT '答案相似度',
    context_precision    DECIMAL(10,6) DEFAULT NULL COMMENT '上下文精确率',
    context_recall       DECIMAL(10,6) DEFAULT NULL COMMENT '上下文召回率',
    context_relevance    DECIMAL(10,6) DEFAULT NULL COMMENT '上下文相关性',
    faithfulness_score   DECIMAL(10,6) DEFAULT NULL COMMENT '忠实度',
    relevance_score      DECIMAL(10,6) DEFAULT NULL COMMENT '相关性',
    hallucination_flag   TINYINT       DEFAULT 0 COMMENT '幻觉标记：0-无，1-有（PO Integer）',
    completeness_score   DECIMAL(10,6) DEFAULT NULL COMMENT '完整性',
    answer_correctness   DECIMAL(10,6) DEFAULT NULL COMMENT '答案正确性',
    overall_score        DECIMAL(10,6) DEFAULT NULL COMMENT '总分',
    eval_detail          TEXT          COMMENT '评测明细 JSON 文本（PO String）',
    tool_selection_score DECIMAL(10,6) DEFAULT NULL COMMENT '工具选择分项（结构化落库）',
    tool_param_score     DECIMAL(10,6) DEFAULT NULL COMMENT '工具参数分项（结构化落库）',
    tool_call_score      DECIMAL(10,6) DEFAULT NULL COMMENT '工具调用分项（结构化落库）',
    intent_score         DECIMAL(10,6) DEFAULT NULL COMMENT '意图识别分项（结构化落库）',
    branch_score         DECIMAL(10,6) DEFAULT NULL COMMENT '分支决策分项（结构化落库）',
    reasoning_score      DECIMAL(10,6) DEFAULT NULL COMMENT '推理过程分项（结构化落库）',
    agent_decision_score DECIMAL(10,6) DEFAULT NULL COMMENT 'Agent 决策总分项（结构化落库）',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_task_time (task_id, create_time),
    INDEX idx_task_trial (task_id, trial_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测结果表';

-- 既有库增量升级（手工执行；工单 0135 R3，对已存在的旧 eval_result）：
-- ALTER TABLE eval_result
--     ADD COLUMN trial_no INT NOT NULL DEFAULT 1 COMMENT '试验序号（1..k；k=1 时恒为 1）',
--     ADD INDEX idx_task_trial (task_id, trial_no);

-- 9. 评测 Rubric 评判标准表（工单 0133 R1：eval_rubric_mapper.xml 全部 SQL 列集 + EvalRubricPO）
CREATE TABLE IF NOT EXISTS eval_rubric (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rubric_id   VARCHAR(64)  NOT NULL COMMENT 'Rubric业务ID（唯一）',
    name        VARCHAR(128) NOT NULL COMMENT 'Rubric名称（唯一，内置种子 builtin-* 前缀）',
    eval_type   VARCHAR(32)  NOT NULL COMMENT '评测类型（RAG_RETRIEVAL/ANSWER_QUALITY/CONTEXT_QUALITY/TOOL_CALL/AGENT_DECISION）',
    version     INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    dimensions  TEXT         COMMENT '维度JSON数组 [{key,label,weight,judgePrompt,binary}]（PO String）',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0-停用，1-启用',
    builtin     TINYINT      NOT NULL DEFAULT 0 COMMENT '内置种子标记：0-用户自建，1-内置（不可删改）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rubric_id (rubric_id),
    UNIQUE KEY uk_rubric_name (name),
    INDEX idx_eval_type_enabled (eval_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测Rubric评判标准表';
-- 既有库为空表时直接执行上方 CREATE；dimensions JSON 由应用层（RubricService）校验
-- 维度 key 唯一 + 权重和=1，库层不额外建 JSON 约束。

-- 10. 评测门禁规则表（工单 0136 R4：分层门禁——安全维度一票否决 + 质量分阈值）
CREATE TABLE IF NOT EXISTS eval_gate (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    gate_id            VARCHAR(64)  NOT NULL COMMENT '门禁规则业务ID（唯一）',
    name               VARCHAR(128) NOT NULL COMMENT '门禁名称（唯一）',
    safety_dims        TEXT         COMMENT '安全维度 JSON 对象 {dim: minSafety}（任一低于下限即一票否决；正向安全分口径，hallucination 配 0.8 等价幻觉率上限 0.2）',
    score_thresholds   TEXT         COMMENT '质量分阈值 JSON 对象 {metric: min}（metric 可为 overall/passRate 或 Rubric 维度 key）',
    trials             INT          NOT NULL DEFAULT 1 COMMENT '回测评测试验次数 k（取自门禁配置）',
    enabled            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0-停用，1-启用',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gate_id (gate_id),
    UNIQUE KEY uk_gate_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测门禁规则表';
-- safety_dims/score_thresholds JSON 结构与维度存在性由应用层（GateService）校验，
-- 库层不额外建 JSON 约束。

-- 11. 评测门禁记录表（工单 0136 R4：回测判定结论落账——PASS/BLOCK + 触发明细）
CREATE TABLE IF NOT EXISTS eval_gate_record (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    record_id      VARCHAR(64) NOT NULL COMMENT '门禁记录业务ID（唯一）',
    gate_id        VARCHAR(64) NOT NULL COMMENT '门禁规则业务ID',
    task_id        VARCHAR(64) NOT NULL COMMENT '回测评测任务业务ID',
    result         VARCHAR(16) NOT NULL COMMENT '门禁结论：PASS-放行，BLOCK-拦截（安全越限/分数越限/任务失败）',
    trigger_detail TEXT        COMMENT '触发明细 JSON 数组 [{ruleType,dim,actual,threshold,note}]（PO String）',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_id (record_id),
    INDEX idx_gate_time (gate_id, create_time),
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测门禁记录表';

-- 12. 巡检拨测记录表（工单 0137 S1：在线评测三手段之巡检——定时回放拨测 + 三态判定 + 轻量分）
CREATE TABLE IF NOT EXISTS patrol_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    round_id      VARCHAR(64)  NOT NULL COMMENT '轮次ID（同轮共享，/patrol/latest 取最近一轮聚合）',
    task_ref      VARCHAR(128) NOT NULL COMMENT '种子任务标识（固定集为 q-N 序号；golden 池为 ds-N 序号）',
    query         TEXT         NOT NULL COMMENT '拨测查询原文',
    agent_id      VARCHAR(64)  COMMENT '目标智能体ID（空=在线回放 provider 默认）',
    status        VARCHAR(16)  NOT NULL COMMENT '结果三态：SUCCESS-成功，FAIL-失败，TIMEOUT-超时',
    score         DECIMAL(10,6) COMMENT '轻量质量分（0-1，TraceQualityCalculator 启发式非空维度均值；仅成功样本可评估时有值）',
    duration_ms   BIGINT       NOT NULL DEFAULT 0 COMMENT '拨测耗时（毫秒；TIMEOUT 记录为超时预算值）',
    error_summary VARCHAR(512) COMMENT '错误摘要（FAIL/TIMEOUT 原因，截断存储）',
    trace_id      VARCHAR(64)  COMMENT '在线回放关联 traceId（可查链路详情）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（追加写，无更新）',
    PRIMARY KEY (id),
    INDEX idx_round_id (round_id),
    INDEX idx_patrol_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检拨测记录表（追加写日志表，无唯一业务键：round_id 每轮新生成）';

-- 13. Case 候选表（工单 0138 S2：三来源挖掘统一入池——低分评测/失败链路/巡检失败；回填错题集）
CREATE TABLE IF NOT EXISTS eval_case_candidate (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    source              VARCHAR(24)  NOT NULL COMMENT '来源：EVAL_LOW_SCORE-低分评测结果，TRACE_FAIL-失败超时链路，PATROL_FAIL-巡检失败',
    source_ref          VARCHAR(128) NOT NULL COMMENT '来源内唯一引用（评测=taskId:trialNo:traceId、链路=traceId、巡检=pid-记录ID 或 traceId），与 source 组成幂等键',
    trace_id            VARCHAR(64)  COMMENT '关联 traceId（可关查链路详情，可空）',
    query               TEXT         COMMENT '查询原文（回填错题集时作为 prompt）',
    answer_summary      TEXT         COMMENT '答案摘要（截断存储的上下文快照）',
    hit_doc_count       INT          COMMENT '命中文档数（检索上下文快照）',
    tool_list           TEXT         COMMENT '工具调用列表 JSON 数组原文（工具上下文快照，可空）',
    reason              VARCHAR(512) COMMENT '入池原因（分数值/失败状态/巡检错误摘要）',
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处置状态：PENDING-待处置，PROMOTED-已回填错题集，IGNORED-已忽略',
    promoted_dataset_id VARCHAR(64)  COMMENT '回填目标数据集 ID（PROMOTED 时有值）',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 工单 0139 S3 归因四列（既有库手工执行：
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution VARCHAR(24) NULL COMMENT '归因四分层：PLANNING/TOOL/ENVIRONMENT/SKILL';
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_note VARCHAR(512) NULL COMMENT '归因备注';
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_by VARCHAR(64) NULL COMMENT '标注人';
    --   ALTER TABLE eval_case_candidate ADD COLUMN attribution_at DATETIME NULL COMMENT '标注时间';）
    attribution         VARCHAR(24)  COMMENT '归因四分层：PLANNING-规划错，TOOL-工具错，ENVIRONMENT-环境错，SKILL-知识错（未标注 NULL）',
    attribution_note    VARCHAR(512) COMMENT '归因备注（判定依据）',
    attribution_by      VARCHAR(64)  COMMENT '标注人（操作留痕）',
    attribution_at      DATETIME     COMMENT '标注时间（操作留痕）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_ref (source, source_ref),
    INDEX idx_case_status (status, create_time),
    INDEX idx_case_attribution (attribution, attribution_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Case 候选表（挖掘枢纽——线上问题自动沉淀为评测资产）';

-- 14. 模型计价表（工单 0148 U2：成本看板读时派生——cost = tokens/1000 × 单价）
CREATE TABLE IF NOT EXISTS model_pricing (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    model              VARCHAR(128)  NOT NULL COMMENT '模型名（与 chat_result_log.model_version 匹配，唯一）',
    input_price_per_1k DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '输入单价：每 1K prompt token',
    output_price_per_1k DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '输出单价：每 1K completion token',
    remark             VARCHAR(256)  COMMENT '备注（币种/生效口径）',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（应用层维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pricing_model (model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型计价表（成本读时派生口径）';
-- 幂等种子（示例价，运营按需修正；upsert 语义可重复执行）：
-- INSERT INTO model_pricing (model, input_price_per_1k, output_price_per_1k, remark)
-- VALUES ('deepseek-v4-pro', 0.002, 0.008, '示例价') ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 15. 人工注解表（工单 0150 U4：trace 人工 1-5 分评分 + 依据；借鉴 Langfuse annotations）
CREATE TABLE IF NOT EXISTS trace_annotation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id    VARCHAR(64)  NOT NULL COMMENT '关联链路 traceId',
    score       INT          NOT NULL COMMENT '评分 1-5（1=很差，5=很好）',
    note        VARCHAR(1024) COMMENT '判定依据备注',
    operator    VARCHAR(64)  NOT NULL DEFAULT 'unknown' COMMENT '标注人（操作留痕）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次标注时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近标注时间（重评即改判）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_annotation (trace_id, operator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工评分注解表（主观质量资产化）';

-- 16. 漂移事件表（工单 0154 U8：检索分数分布漂移留痕——本周 vs 上周均值漂移/空检索率增量）
CREATE TABLE IF NOT EXISTS drift_event (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    metric         VARCHAR(64)   NOT NULL COMMENT '指标名（当前固定 rerank_mean）',
    current_value  DECIMAL(18,6) COMMENT '当前窗值',
    previous_value DECIMAL(18,6) COMMENT '对比窗值',
    threshold      DECIMAL(18,6) COMMENT '触发阈值',
    detail         TEXT          COMMENT '上下文 JSON（样本数/空检索率/窗口）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (id),
    INDEX idx_drift_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漂移事件表';

-- 17. pairwise 对局记录表（工单 0170 X1：两任务同题 A/B 判定——X2 Elo 重算数据源）
CREATE TABLE IF NOT EXISTS eval_pairwise_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_a      VARCHAR(64)  NOT NULL COMMENT '任务 A 业务ID',
    task_b      VARCHAR(64)  NOT NULL COMMENT '任务 B 业务ID',
    dataset_id  VARCHAR(64)  COMMENT '对齐所用数据集',
    query       TEXT         COMMENT '查询原文（对齐键 queryText）',
    outcome     VARCHAR(16)  NOT NULL COMMENT '判定：A_WIN / B_WIN / TIE',
    pair_no     INT          NOT NULL DEFAULT 0 COMMENT '同对任务内的题序',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '判定时间',
    PRIMARY KEY (id),
    INDEX idx_pair_tasks (task_a, task_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='pairwise 对局记录表';

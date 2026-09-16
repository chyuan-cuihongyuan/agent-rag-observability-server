SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `eval_result` (
  `id`                  bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id`             varchar(64)     NOT NULL,
  `trace_id`            varchar(64)     NOT NULL DEFAULT '',
  `query_text`          text            NOT NULL,
  `standard_answer`     text,
  `actual_answer`       text,
  -- ========== 检索类确定性指标（规则计算，零 LLM） ==========
  `recall_score`        double COMMENT '召回率：命中标准 chunk 数 / 标准 chunk 总数',
  `precision_score`     double COMMENT '精确率：命中标准的实际 chunk 数 / 实际检索 chunk 数',
  `f1_score`            double COMMENT 'F1：召回率与精确率的调和平均',
  `top3_hit_rate`       double COMMENT 'Top3 命中率：前 3 条命中任一标准 chunk 则为 1',
  `mrr_score`           double COMMENT 'MRR 平均倒数排名：第一条命中标准 chunk 的实际 chunk 排名倒数，无命中=0',
  `ndcg_score`          double COMMENT 'NDCG 归一化折损累计增益：位置加权检索质量（含排序感知）',
  `map_score`           double COMMENT 'MAP 平均精度均值：命中位置 precision@k 累计均值',
  `answer_similarity`   double COMMENT '答案词面相似度（Jaccard），无 LLM 时作为语义相似度兜底',
  -- ========== 上下文维度指标（RAGAS 式，LLM-as-Judge） ==========
  `context_precision`   double COMMENT '上下文精确率：检索 chunk 逐条对回答 query 是否相关，按位置加权',
  `context_recall`      double COMMENT '上下文召回率：标准答案逐句能否从检索上下文推断，可推断句/总句',
  `context_relevance`   double COMMENT '上下文相关性：检索内容与用户查询的整体相关度 0-1',
  -- ========== 生成类指标（LLM-as-Judge） ==========
  `faithfulness_score`  double COMMENT '忠实度：答案是否完全基于检索内容（量化幻觉程度）',
  `relevance_score`     double COMMENT '答案相关性：最终答案是否直接完整回答了原始问题',
  `hallucination_flag`  tinyint COMMENT '幻觉标记：hallucination_rate >= 0.3 置 1',
  `completeness_score`  double COMMENT '信息完整性：答案覆盖标准答案要点的程度',
  `answer_correctness`  double COMMENT '答案正确性：实际答案相对标准答案的事实正确性 0-1',
  -- ========== 工具调用评测分项（修复断层：原仅写 eval_detail JSON，现结构化落库） ==========
  `tool_selection_score`  double COMMENT '工具选择正确率：期望工具命中数 / 期望工具数',
  `tool_param_score`      double COMMENT '工具参数正确率：参数命中数 / 期望参数数',
  `tool_call_score`       double COMMENT '工具调用综合分：选择 0.5 + 参数 0.5',
  -- ========== Agent 决策评测分项（修复断层：原仅写 eval_detail JSON，现结构化落库） ==========
  `intent_score`          double COMMENT '意图识别正确率',
  `branch_score`          double COMMENT '分支选择正确率',
  `reasoning_score`       double COMMENT '推理质量分：推理步骤匹配度',
  `agent_decision_score`  double COMMENT 'Agent 决策综合分：意图 0.3 + 分支 0.3 + 推理 0.4',
  -- ========== 汇总与明细 ==========
  `overall_score`       double COMMENT '综合评分（按评测类型加权）',
  `eval_detail`         json COMMENT '评测明细 JSON（judge 降级/权重版本/原始摘要等）',
  `create_time`         datetime        NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测结果';

# -- ========== 增量迁移：已有环境补列（IF NOT EXISTS 兼容 MySQL 8.0.29+，低版本请手动忽略报错列） ==========
# -- 新增检索排序感知指标
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `mrr_score`  double COMMENT 'MRR 平均倒数排名' AFTER `top3_hit_rate`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `ndcg_score` double COMMENT 'NDCG 归一化折损累计增益' AFTER `mrr_score`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `map_score`  double COMMENT 'MAP 平均精度均值' AFTER `ndcg_score`;
# -- 新增上下文维度指标
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `context_precision` double COMMENT '上下文精确率' AFTER `answer_similarity`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `context_recall`    double COMMENT '上下文召回率' AFTER `context_precision`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `context_relevance` double COMMENT '上下文相关性' AFTER `context_recall`;
# -- 新增答案正确性
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `answer_correctness` double COMMENT '答案正确性' AFTER `completeness_score`;
# -- 修复断层：工具调用分项
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `tool_selection_score` double COMMENT '工具选择正确率' AFTER `answer_correctness`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `tool_param_score`     double COMMENT '工具参数正确率' AFTER `tool_selection_score`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `tool_call_score`      double COMMENT '工具调用综合分' AFTER `tool_param_score`;
# -- 修复断层：Agent 决策分项
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `intent_score`         double COMMENT '意图识别正确率' AFTER `tool_call_score`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `branch_score`         double COMMENT '分支选择正确率' AFTER `intent_score`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `reasoning_score`      double COMMENT '推理质量分' AFTER `branch_score`;
# ALTER TABLE `eval_result` ADD COLUMN IF NOT EXISTS `agent_decision_score` double COMMENT 'Agent 决策综合分' AFTER `reasoning_score`;

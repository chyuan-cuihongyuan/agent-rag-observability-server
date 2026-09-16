SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `eval_task` (
  `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id`           varchar(64)     NOT NULL COMMENT '评测任务ID',
  `task_name`         varchar(256)    NOT NULL COMMENT '任务名称',
  `eval_type`         varchar(32)     NOT NULL COMMENT '类型: RAG_RETRIEVAL/ANSWER_QUALITY/AGENT_DECISION',
  `dataset_id`        varchar(64)     NOT NULL COMMENT '数据集ID',
  `status`            varchar(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
  `total_count`       int             NOT NULL DEFAULT 0,
  `completed_count`   int             NOT NULL DEFAULT 0,
  `model_version`     varchar(64)     NOT NULL DEFAULT '',
  `rag_strategy_version` varchar(64)  NOT NULL DEFAULT '',
  `avg_overall_score` double          COMMENT '平均综合评分',
  `dataset_content_hash` char(64)     NOT NULL DEFAULT '' COMMENT '数据集内容快照哈希(版本漂移检测,SELFLOOP3 loop-334)',
  `create_time`       datetime        NOT NULL,
  `update_time`       datetime        NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测任务';

-- 老库增量迁移（CREATE TABLE IF NOT EXISTS 不补列）：
-- ALTER TABLE `eval_task` ADD COLUMN `dataset_content_hash` char(64) NOT NULL DEFAULT '' COMMENT '数据集内容快照哈希' AFTER `avg_overall_score`;

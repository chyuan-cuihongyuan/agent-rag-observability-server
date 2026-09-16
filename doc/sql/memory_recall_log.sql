CREATE TABLE IF NOT EXISTS `memory_recall_log` (
  `id`                      bigint unsigned NOT NULL AUTO_INCREMENT,
  `trace_id`                varchar(64)     NOT NULL COMMENT '关联全局 Trace',
  `query_text`              text            COMMENT '检索查询文本',
  `session_memory_count`    int             NOT NULL DEFAULT 0 COMMENT '会话级记忆命中数',
  `agent_memory_count`      int             NOT NULL DEFAULT 0 COMMENT 'Agent 级记忆命中数',
  `session_memory_scores`   json            COMMENT '会话级记忆分数',
  `agent_memory_scores`     json            COMMENT 'Agent 级记忆分数',
  `inject_content`          text            COMMENT '注入到 prompt 的记忆内容',
  `cost_time_ms`            int             NOT NULL DEFAULT 0 COMMENT '记忆检索耗时(ms)',
  `create_time`             datetime        NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆检索追踪日志';

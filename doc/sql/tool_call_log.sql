CREATE TABLE IF NOT EXISTS `tool_call_log` (
  `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
  `trace_id`          varchar(64)     NOT NULL COMMENT '关联全局 Trace',
  `span_id`           varchar(64)     NOT NULL COMMENT '本次调用的唯一标识',
  `parent_span_id`    varchar(64)     NOT NULL DEFAULT '' COMMENT '父 Span',
  `tool_name`         varchar(128)    NOT NULL COMMENT '工具名称',
  `tool_input`        text            COMMENT '入参 JSON（脱敏）',
  `tool_output`       text            COMMENT '出参 JSON（脱敏）',
  `status`            varchar(32)     NOT NULL DEFAULT '' COMMENT '状态: SUCCESS/FAIL/TIMEOUT',
  `cost_time_ms`      int             NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
  `error_message`     text            COMMENT '错误信息',
  `call_order`        int             NOT NULL DEFAULT 0 COMMENT '调用顺序',
  `create_time`       datetime        NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_tool_name` (`tool_name`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用追踪日志';

SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `eval_dataset` (
  `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
  `dataset_id`        varchar(64)     NOT NULL,
  `dataset_name`      varchar(256)    NOT NULL,
  `description`       text,
  `item_count`        int             NOT NULL DEFAULT 0,
  `items_json`        longtext        COMMENT 'JSON [{query,standardAnswer,standardChunks}]',
  `create_time`       datetime        NOT NULL,
  `update_time`       datetime        NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测数据集';

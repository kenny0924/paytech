-- Local bootstrap marker for the vetted upstream XXL-Job schema.

USE xxl_job;

CREATE TABLE IF NOT EXISTS `xxl_job_schema_version`
(
    `version`       varchar(32) NOT NULL,
    `source_commit` char(40)    NOT NULL,
    `installed_at`  datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`version`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO `xxl_job_schema_version` (`version`, `source_commit`)
VALUES ('3.4.2', 'c2bbb46c9a3af8e2a69246728a452c606240b80e')
ON DUPLICATE KEY UPDATE source_commit = VALUES(source_commit);

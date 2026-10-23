-- Platform Support 工作流：新增 10 张表（待执行，需授权）。审阅后手工执行。
-- 需求：requirements/20261021/platform-support-工作流/technique.md 第 4.2 节（t_workflow_callback）与第 6 节（其余 9 张）；
--       决定：agent-docs/decisions/2026-10-22-workflow-into-platform-support.md。
-- 目标库：Platform Support（mor_platform；本地自动测试库按同一 DDL 另行准备）。
-- 前提：目标库不存在同名表。预检：
--   SELECT COUNT(*) FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 't\_workflow\_%';
--   结果须为 0；否则说明已执行，跳过本文件。
-- 执行顺序：先执行本文件，再部署读写这些表的 Platform；之后执行初始化 DML 中工作流的注册块（块号在阶段 3 方案为 127，执行前按 live mor_platform 复核）。
--          旧 Platform 不读写这些表，先建表不影响其运行。
-- 影响：只建空表，不修改任何已有表或数据；应用与测试不执行本文件。
-- 执行后核对：SHOW CREATE TABLE 逐表对照 platform-support-schema.sql 与 platform-support.pdma.json（图组 workflow）的字段、默认值、索引与注释。
-- 回退（仅限尚无需要保留的业务数据时；执行前先回退读写这些表的 Platform）：
--   DROP TABLE `t_workflow_callback`; DROP TABLE `t_workflow_transfer`; DROP TABLE `t_workflow_approval`;
--   DROP TABLE `t_workflow_verification`; DROP TABLE `t_workflow_action_run`; DROP TABLE `t_workflow_node_run`;
--   DROP TABLE `t_workflow_instance`; DROP TABLE `t_workflow_action_template`;
--   DROP TABLE `t_workflow_definition_version`; DROP TABLE `t_workflow_definition`;
-- 2026-10-22 修订：t_workflow_action_run 的表注释与 required、status 列注释按动作状态 PENDING / COMPLETED / CANCELLED 同步为当前模型；
--   已按旧注释建表的环境由 requirements/20261022/platform-support-工作流体验优化/sql/10-action-run-status.sql 迁移数据并改注释。
SET NAMES utf8mb4;

CREATE TABLE `t_workflow_definition` (
  `definition_id` BIGINT NOT NULL COMMENT '流程定义的 Snowflake 主键。',
  `definition_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义的业务编码；创建后不可修改，全局唯一。',
  `definition_name` VARCHAR(128) NOT NULL COMMENT '流程定义的显示名称，随草稿保存修改。',
  `description` VARCHAR(512) NULL COMMENT '流程定义的用途说明；未填写时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '流程定义是否允许发起新实例；INACTIVE 不影响在途实例。允许值：ACTIVE、INACTIVE。',
  `current_version_no` INT NULL COMMENT '最新一次发布的版本号，发起实例使用该版本；从未发布时为空。',
  `draft_version_no` INT NULL COMMENT '当前唯一草稿的版本号；没有草稿时为空，保证每个定义最多一份草稿。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建流程定义的 Operations 账号 ID（t_user_account.account_id）。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改流程定义或其草稿的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`definition_id`),
  UNIQUE KEY `uk_workflow_def_code` (`definition_code`) COMMENT '保证业务唯一性：definition_code',
  KEY `idx_workflow_def_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Operations 用流程图配置的流程模板；草稿可改，发布生成不可变版本，再修改即生成新版本；停用后不能发起新实例，在途实例照常运行';

CREATE TABLE `t_workflow_definition_version` (
  `definition_version_id` BIGINT NOT NULL COMMENT '流程版本的 Snowflake 主键。',
  `definition_id` BIGINT NOT NULL COMMENT '该版本所属的流程定义 ID。',
  `version_no` INT NOT NULL COMMENT '定义内从 1 开始递增的版本号；草稿发布后号码不变。',
  `status` VARCHAR(32) NOT NULL COMMENT 'DRAFT 可随草稿保存覆盖；PUBLISHED 后内容不可变。允许值：DRAFT、PUBLISHED。',
  `graph_json` JSON NOT NULL COMMENT '节点、连线、排他条件、审批、动作引用与通知规则，结构见工作流技术方案 3.1；不超过 48 KiB。',
  `start_form_json` JSON NOT NULL COMMENT '启动表单字段定义，与表单动作共用结构，见工作流技术方案 3.2；最多 50 个字段，不超过 12 KiB。',
  `action_snapshot_json` JSON NULL COMMENT '发布时冻结的引用动作模板：模板 ID、模板版本、类型与配置；草稿为空。',
  `published_by` BIGINT NULL COMMENT '发布该版本的 Operations 账号 ID；草稿为空。',
  `published_time` DATETIME(6) NULL COMMENT '版本发布的 UTC 微秒时间；草稿为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该版本（新建定义或从发布版本复制草稿）的 Operations 账号 ID。',
  `updated_by` BIGINT NOT NULL COMMENT '最后保存该草稿或发布该版本的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`definition_version_id`),
  UNIQUE KEY `uk_workflow_def_ver` (`definition_id`, `version_no`) COMMENT '保证业务唯一性：definition_id、version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='流程定义的草稿或已发布版本；发布后流程图、启动表单与动作模板快照不可变，实例发起时绑定版本';

CREATE TABLE `t_workflow_action_template` (
  `action_template_id` BIGINT NOT NULL COMMENT '动作模板的 Snowflake 主键。',
  `template_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '动作模板的业务编码；创建后不可修改，全局唯一。',
  `template_name` VARCHAR(128) NOT NULL COMMENT '动作模板的显示名称。',
  `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式；DATA_QUERY 本期只预留，不能被发布的流程引用。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。',
  `description` VARCHAR(512) NULL COMMENT '动作模板的用途说明；未填写时为空。',
  `config_json` JSON NOT NULL COMMENT '按动作类型的配置，结构见工作流技术方案 3.3。',
  `status` VARCHAR(32) NOT NULL COMMENT '动作模板是否允许被新发布的流程引用。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次修改加一；发布时作为模板版本冻结进版本快照。',
  `created_by` BIGINT NOT NULL COMMENT '创建动作模板的 Operations 账号 ID。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改动作模板的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`action_template_id`),
  UNIQUE KEY `uk_workflow_tpl_code` (`template_code`) COMMENT '保证业务唯一性：template_code',
  KEY `idx_workflow_tpl_type` (`action_type`, `status`) COMMENT '支持访问路径：action_type、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作目录中可复用的节点动作配置：类型、输入、输出与验证方式；停用后新发布的流程不能引用，已发布版本使用冻结快照照常运行';

CREATE TABLE `t_workflow_instance` (
  `instance_id` BIGINT NOT NULL COMMENT '流程实例的 Snowflake 主键。',
  `definition_id` BIGINT NOT NULL COMMENT '实例所属的流程定义 ID。',
  `definition_version_id` BIGINT NOT NULL COMMENT '发起时绑定的已发布流程版本 ID；实例全程按该版本执行。',
  `title` VARCHAR(128) NOT NULL COMMENT '发起人填写的实例标题。',
  `business_key` VARCHAR(128) NULL COMMENT '可选的业务关联键（如 CHANNEL:GEIDEA-SA），只用于检索和展示，工作流不解释其含义；未填写时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '实例运行状态；COMPLETED、TERMINATED 为终态，终态后只读。允许值：RUNNING、COMPLETED、TERMINATED。',
  `start_values_json` JSON NOT NULL COMMENT '启动表单提交值，作为 start.* 实例变量；发起后不可修改。',
  `assignment_json` JSON NOT NULL COMMENT '发起时冻结的各节点审批人与各动作执行人（版本默认值加发起时的修改）。',
  `started_by` BIGINT NOT NULL COMMENT '发起实例的 Operations 账号 ID。',
  `start_time` DATETIME(6) NOT NULL COMMENT '实例发起的 UTC 微秒时间。',
  `end_time` DATETIME(6) NULL COMMENT '实例完成或终止的 UTC 微秒时间；运行中为空。',
  `terminated_by` BIGINT NULL COMMENT '终止实例的 Operations 账号 ID；未终止时为空。',
  `termination_reason` VARCHAR(512) NULL COMMENT '终止实例时填写的原因；未终止时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`instance_id`),
  KEY `idx_workflow_inst_status` (`status`, `start_time`) COMMENT '支持访问路径：status、start_time',
  KEY `idx_workflow_inst_def` (`definition_id`, `status`) COMMENT '支持访问路径：definition_id、status',
  KEY `idx_workflow_inst_starter` (`started_by`, `start_time`) COMMENT '支持访问路径：started_by、start_time',
  KEY `idx_workflow_inst_bizkey` (`business_key`) COMMENT '支持访问路径：business_key'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次具体的流程运行；绑定发起时的已发布版本，节点、动作与配置全程不变，运行中只能经转交变更人员';

CREATE TABLE `t_workflow_node_run` (
  `node_run_id` BIGINT NOT NULL COMMENT '节点运行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '节点运行所属的流程实例 ID。',
  `node_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '绑定流程版本中的节点 key。',
  `node_type` VARCHAR(32) NOT NULL COMMENT '节点在流程图中的类型。允许值：START、END、TASK、EXCLUSIVE、PARALLEL_SPLIT、PARALLEL_JOIN。',
  `round_no` INT NOT NULL COMMENT '同一实例同一节点的轮次，从 1 开始；新一轮为该节点已有最大轮次加一。',
  `branch_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '所在并行分支，取并行分叉出边的 key；不在并行分支内时为空。',
  `previous_node_run_id` BIGINT NULL COMMENT '实际来源的节点运行 ID；START 与并行汇合节点为空。',
  `status` VARCHAR(32) NOT NULL COMMENT 'TASK 依次为 ACTIVE（执行动作）、APPROVING（待审批），结束于 PASSED、RETURNED（退回修改）、REJECTED（被驳回）或 CANCELLED；并行汇合为 WAITING 直到全部分支完成；START、END、排他网关与并行分叉写入即 PASSED。允许值：ACTIVE、APPROVING、WAITING、PASSED、RETURNED、REJECTED、CANCELLED。',
  `selected_edge_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '排他网关按条件选中的出边 key；其他节点为空。',
  `activated_time` DATETIME(6) NOT NULL COMMENT '节点运行创建即激活的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NULL COMMENT '节点运行进入 PASSED、RETURNED、REJECTED 或 CANCELLED 的 UTC 微秒时间；进行中为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`node_run_id`),
  UNIQUE KEY `uk_workflow_node_round` (`instance_id`, `node_key`, `round_no`) COMMENT '保证业务唯一性：instance_id、node_key、round_no',
  KEY `idx_workflow_node_status` (`instance_id`, `status`) COMMENT '支持访问路径：instance_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='实例中一个节点的一轮运行；退回修改与驳回重新激活时新建一轮，previous_node_run_id 记录实际来源，用于驳回定位上一节点';

CREATE TABLE `t_workflow_action_run` (
  `action_run_id` BIGINT NOT NULL COMMENT '动作运行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '动作运行所属的流程实例 ID。',
  `node_run_id` BIGINT NOT NULL COMMENT '动作运行所属的节点运行 ID，每轮节点运行各有一份。',
  `action_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '绑定流程版本中的动作 key，同时是输出变量前缀。',
  `action_template_id` BIGINT NOT NULL COMMENT '引用的动作模板 ID；执行配置以流程版本的模板快照为准。',
  `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式，取自版本快照。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM。',
  `required` TINYINT NOT NULL COMMENT '1 表示必选，节点进入审批或放行前必须 COMPLETED；0 表示可选，不阻塞放行。',
  `assignee_id` BIGINT NOT NULL COMMENT '当前指派的执行人 Operations 账号 ID；转交后更新。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 待处理；COMPLETED 已完成；CANCELLED 节点结束或实例终止时仍未完成。允许值：PENDING、COMPLETED、CANCELLED。',
  `output_json` JSON NULL COMMENT '执行人最近一次提交的输出，按动作类型与配置校验；未提交时为空。',
  `submitted_by` BIGINT NULL COMMENT '最近一次提交的 Operations 账号 ID；未提交时为空。',
  `submitted_time` DATETIME(6) NULL COMMENT '最近一次提交的 UTC 微秒时间；未提交时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`action_run_id`),
  UNIQUE KEY `uk_workflow_action_node` (`node_run_id`, `action_key`) COMMENT '保证业务唯一性：node_run_id、action_key',
  KEY `idx_workflow_action_assignee` (`assignee_id`, `status`) COMMENT '支持访问路径：assignee_id、status',
  KEY `idx_workflow_action_inst` (`instance_id`) COMMENT '支持访问路径：instance_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='节点运行中一个动作的执行情况与最近一次完成的输出；必选动作在节点进入审批后锁定，变量取自最新一轮 COMPLETED 的输出';

CREATE TABLE `t_workflow_verification` (
  `verification_id` BIGINT NOT NULL COMMENT '验证记录的 Snowflake 主键。',
  `action_run_id` BIGINT NOT NULL COMMENT '被验证的动作运行 ID。',
  `instance_id` BIGINT NOT NULL COMMENT '验证所属的流程实例 ID。',
  `result` VARCHAR(32) NOT NULL COMMENT '本次验证的结论。允许值：PASSED、FAILED。',
  `evidence_json` JSON NULL COMMENT '本次验证依据的证据，如文件 ID 列表、扫描状态或对象类型、ID、版本与状态；没有证据时为空。',
  `failure_reason` VARCHAR(512) NULL COMMENT '验证未通过的中文原因；通过时为空。',
  `verified_by` BIGINT NOT NULL COMMENT '提交或重新验证的 Operations 账号 ID。',
  `verified_time` DATETIME(6) NOT NULL COMMENT '本次验证完成的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`verification_id`),
  KEY `idx_workflow_verify_action` (`action_run_id`, `verified_time`) COMMENT '支持访问路径：action_run_id、verified_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作每一次验证的结论、证据与未通过原因；追加后不可变，验证调用失败或超时记为 FAILED，不会误判为通过';

CREATE TABLE `t_workflow_approval` (
  `approval_id` BIGINT NOT NULL COMMENT '审批行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '审批所属的流程实例 ID。',
  `node_run_id` BIGINT NOT NULL COMMENT '审批所属的节点运行 ID，每轮节点运行各有一组。',
  `approver_id` BIGINT NOT NULL COMMENT '当前审批人 Operations 账号 ID；转交后更新，同一节点运行内不重复。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 待决定；APPROVED 同意；RETURNED 退回修改；REJECTED 驳回；WITHDRAWN 因他人决定或实例终止而撤回。允许值：PENDING、APPROVED、RETURNED、REJECTED、WITHDRAWN。',
  `decision_comment` VARCHAR(512) NULL COMMENT '审批人填写的意见；退回修改与驳回必填，同意可空。',
  `reject_target_node_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '汇合后第一个节点驳回时指定的目标分支末 TASK 节点 key；其他情形为空。',
  `decided_time` DATETIME(6) NULL COMMENT '审批决定或撤回的 UTC 微秒时间；待决定时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`approval_id`),
  UNIQUE KEY `uk_workflow_approval` (`node_run_id`, `approver_id`) COMMENT '保证业务唯一性：node_run_id、approver_id',
  KEY `idx_workflow_approval_user` (`approver_id`, `status`) COMMENT '支持访问路径：approver_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='节点运行中每位审批人的一行审批；ANY 任一人同意即放行，ALL 需全部同意，任一人退回修改或驳回即按该决定处理，其余行撤回';

CREATE TABLE `t_workflow_transfer` (
  `transfer_id` BIGINT NOT NULL COMMENT '转交记录的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '转交所属的流程实例 ID。',
  `target_type` VARCHAR(32) NOT NULL COMMENT '被转交的是动作运行还是审批行。允许值：ACTION_RUN、APPROVAL。',
  `target_id` BIGINT NOT NULL COMMENT '被转交的动作运行 ID 或审批 ID，由 target_type 区分。',
  `from_user_id` BIGINT NOT NULL COMMENT '转交前的执行人或审批人 Operations 账号 ID。',
  `to_user_id` BIGINT NOT NULL COMMENT '转交后的执行人或审批人 Operations 账号 ID。',
  `reason` VARCHAR(512) NULL COMMENT '转交时填写的说明；未填写时为空。',
  `transferred_by` BIGINT NOT NULL COMMENT '执行转交的 Operations 账号 ID：原处理人本人或有实例管理权限的人。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`transfer_id`),
  KEY `idx_workflow_transfer_inst` (`instance_id`, `create_time`) COMMENT '支持访问路径：instance_id、create_time',
  KEY `idx_workflow_transfer_target` (`target_type`, `target_id`) COMMENT '支持访问路径：target_type、target_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作执行人或审批人的转交历史；追加后不可变，不能转给自己，也不能转给同一节点的其他审批人';

CREATE TABLE `t_workflow_callback` (
  `callback_id` BIGINT NOT NULL COMMENT '通知或回调记录的 Snowflake 主键，也是补偿任务与回调事件的业务键。',
  `instance_id` BIGINT NOT NULL COMMENT '触发通知或回调的流程实例 ID。',
  `node_run_id` BIGINT NULL COMMENT '节点级触发的节点运行 ID；实例级触发为空。',
  `trigger_code` VARCHAR(32) NOT NULL COMMENT '触发规则的状态变化；STARTED、COMPLETED、TERMINATED 为实例级，其余为节点级。允许值：ACTIVATED、APPROVAL_OPENED、PASSED、RETURNED_FOR_REVISION、REJECTED、VERIFICATION_FAILED、TRANSFERRED、STARTED、COMPLETED、TERMINATED。',
  `callback_type` VARCHAR(32) NOT NULL COMMENT 'NOTIFY 通知人员（邮件）；CALLBACK 向 topic_platform_workflow_event 发布业务事件。允许值：NOTIFY、CALLBACK。',
  `target` JSON NOT NULL COMMENT 'NOTIFY 为解析后的收件人账号 ID 列表与渠道；CALLBACK 为回调编码；登记后不可变。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 待发送或重试中；SENT 已送达；FAILED 超过重试上限，可人工重试。允许值：PENDING、SENT、FAILED。',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已执行的发送次数，初始为 0。',
  `last_error` VARCHAR(512) NULL COMMENT '最近一次失败的脱敏错误摘要；未失败时为空。',
  `sent_time` DATETIME(6) NULL COMMENT '发送成功的 UTC 微秒时间；未送达时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`callback_id`),
  KEY `idx_workflow_cb_inst` (`instance_id`, `create_time`) COMMENT '支持访问路径：instance_id、create_time',
  KEY `idx_workflow_cb_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='状态变化按规则触发的一次通知（邮件）或业务回调（MQ 事件）及其投递状态；与状态变化同事务登记，提交后由补偿任务执行';

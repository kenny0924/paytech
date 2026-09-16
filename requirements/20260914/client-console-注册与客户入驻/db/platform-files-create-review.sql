-- DRAFT FOR REVIEW ONLY: not an approved model or an executable migration authorization.
-- Candidate: add seven currently absent tables in the isolated local Platform validation schema.
-- Requires approval of file-upload-closure-proposal.md and synchronization of the canonical Platform PDManer/Schema first.
-- Never execute the whole predecessor platform-additions-review.sql: authentication_challenge already exists with later amendments.
-- Recheck exact database identity and all seven tables absent immediately before any separately authorized execution.
-- No IF NOT EXISTS, DROP, seed, external credentials, physical CHECK or FOREIGN KEY.

CREATE TABLE `t_file_upload` (
  `upload_id` BIGINT NOT NULL COMMENT '文件上传会话的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `uploader_id` BIGINT NOT NULL COMMENT '上传用户的稳定业务标识。',
  `expected_size` BIGINT NOT NULL COMMENT '预期文件大小的长整数值。',
  `expected_mime` VARCHAR(255) NOT NULL COMMENT '预期媒体类型的业务值。',
  `owner_system` VARCHAR(64) NOT NULL COMMENT '上传目标所属业务系统代码，来自 Platform 受控代码目录；Client 入驻由 CCT 授权结果固定，浏览器不得提交。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '上传目标的受控资源类型；Client 入驻固定为 CLIENT_APPLICATION_SUBJECT，不允许上传后变更目标类型。',
  `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '来源 Owner 定义的不可变目标坐标；Client 入驻为 CCT 的 applicationId:subjectPartyId，两段均为规范正整数 ID，不复制 Client 名称或其他可变事实。',
  `display_name` VARCHAR(255) NOT NULL COMMENT '去除路径和控制字符后的文件展示名，最大 255 Unicode 字符；仅授权页面展示，不作为对象存储 Key 或鉴权依据。',
  `storage_provider` VARCHAR(64) NOT NULL COMMENT '此次上传使用的对象存储适配器代码，来自受控配置；创建后固定，恢复不得改用当前默认适配器。',
  `storage_container_ref` VARCHAR(255) NOT NULL COMMENT '此次上传的目标存储容器引用；不含凭据，创建后固定，完成和恢复必须使用原容器。',
  `storage_object_ref` VARCHAR(255) NOT NULL COMMENT 'Platform 创建的高熵上传对象引用，不含原始文件名或个人资料；与适配器和容器组成上传目标，不能由浏览器替换。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '本次上传在开始时冻结的 Platform File Scan Policy 修订 ID；完成后由 File 继承，不因等待期间默认策略变化而重选。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '上传到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '上传状态的稳定业务代码。允许值：CREATED、UPLOADING、COMPLETED、EXPIRED、CANCELLED。',
  `version` INT NOT NULL COMMENT '上传并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`upload_id`),
  UNIQUE KEY `uk_upload_storage_target` (`storage_provider`,`storage_container_ref`,`storage_object_ref`) COMMENT '同一存储对象上传目标只归属一条 Upload，防止不同申请共享可写目标',
  KEY `idx_file_upload_tenant` (`tenant_id`, `status`, `create_time`) COMMENT '支持访问路径：tenant_id、status、create_time',
  KEY `idx_file_upload_due` (`status`, `expires_time`) COMMENT '支持访问路径：status、expires_time',
  KEY `idx_file_upload_uploader_id` (`uploader_id`) COMMENT '支持访问路径：uploader_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存受控上传的准确 Owner 目标、租户与上传人、展示名、原存储目标和冻结扫描策略；上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';

CREATE TABLE `t_file_scan_policy` (
  `policy_id` BIGINT NOT NULL COMMENT '文件扫描策略修订的稳定业务标识。',
  `policy_code` VARCHAR(64) NOT NULL COMMENT '扫描策略代码的业务值。',
  `revision_no` INT NOT NULL COMMENT '扫描策略修订号的整数值。',
  `rule_schema_version` INT NOT NULL COMMENT '规则结构版本的整数值。',
  `allowed_mime_rules` JSON NOT NULL COMMENT '允许媒体类型规则的有界版本化 JSON 内容。',
  `max_size_bytes` BIGINT NOT NULL COMMENT '最大文件字节数的长整数值。',
  `effective_from` DATETIME(6) NULL COMMENT '生效开始时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '生效结束时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '扫描策略修订状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `active_slot` INT NULL COMMENT '活动修订占位的整数值。',
  `version` INT NOT NULL COMMENT '扫描策略并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`policy_id`),
  UNIQUE KEY `uk_file_scan_revision` (`policy_code`, `revision_no`) COMMENT '保证业务唯一性：policy_code、revision_no',
  UNIQUE KEY `uk_file_scan_active` (`policy_code`, `active_slot`) COMMENT '保证业务唯一性：policy_code、active_slot',
  KEY `idx_file_scan_effective` (`status`, `effective_from`, `effective_to`) COMMENT '支持访问路径：status、effective_from、effective_to'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一行保存一个扫描策略修订；激活时同策略代码仅允许一个 ACTIVE 修订且内容随后冻结';

CREATE TABLE `t_file_scan_requirement` (
  `requirement_id` BIGINT NOT NULL COMMENT '文件扫描要求的稳定业务标识。',
  `policy_id` BIGINT NOT NULL COMMENT '扫描策略修订的稳定业务标识。',
  `requirement_no` INT NOT NULL COMMENT '扫描要求序号的整数值。',
  `scanner_code` VARCHAR(64) NOT NULL COMMENT '扫描器代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `scan_type` VARCHAR(64) NOT NULL COMMENT '扫描类型的稳定业务代码。允许值：ANTI_MALWARE、DLP、FORMAT。',
  `minimum_scanner_version` VARCHAR(64) NOT NULL COMMENT '最低扫描器版本的业务值。',
  `accepted_result_codes` JSON NOT NULL COMMENT '接受结果代码集合的有界版本化 JSON 内容。',
  `version` INT NOT NULL COMMENT '扫描要求并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`requirement_id`),
  UNIQUE KEY `uk_file_scan_req_no` (`policy_id`, `requirement_no`) COMMENT '保证业务唯一性：policy_id、requirement_no',
  UNIQUE KEY `uk_file_scan_req` (`policy_id`, `scanner_code`, `scan_type`) COMMENT '保证业务唯一性：policy_id、scanner_code、scan_type',
  UNIQUE KEY `uk_file_scan_req_owner` (`requirement_id`, `policy_id`) COMMENT '保证业务唯一性：requirement_id、policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一个扫描策略修订必须执行的扫描器和接受结果；策略激活后要求随策略一起冻结';

CREATE TABLE `t_file_object` (
  `file_id` BIGINT NOT NULL COMMENT '文件对象的稳定业务标识。',
  `creation_source` VARCHAR(64) NOT NULL COMMENT '文件创建来源的稳定业务代码。允许值：UPLOAD、DOCUMENT_RENDER、INTERNAL。',
  `upload_id` BIGINT NULL COMMENT '文件上传的稳定业务标识。',
  `storage_provider` VARCHAR(64) NOT NULL COMMENT '存储提供方的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `storage_container_ref` VARCHAR(255) NOT NULL COMMENT '存储容器引用的业务值。',
  `storage_object_ref` VARCHAR(255) NOT NULL COMMENT '存储对象引用的业务值。',
  `object_version` VARCHAR(128) NOT NULL COMMENT '存储对象版本的业务值。',
  `content_hash` CHAR(64) NOT NULL COMMENT '文件内容哈希的 SHA-256 小写十六进制值。',
  `size_bytes` BIGINT NOT NULL COMMENT '文件字节数的长整数值。',
  `mime_type` VARCHAR(255) NOT NULL COMMENT '对象内容的规范媒体类型。取值来源：IANA Media Types 与 Platform 允许列表；非固定枚举。',
  `storage_encryption_key_ref` VARCHAR(255) NOT NULL COMMENT '存储加密密钥引用的业务值。',
  `classification` VARCHAR(64) NOT NULL COMMENT '文件分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL、RESTRICTED。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '冻结扫描策略修订的稳定业务标识。',
  `scan_status` VARCHAR(64) NOT NULL COMMENT '扫描状态的稳定业务代码。允许值：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。',
  `object_status` VARCHAR(64) NOT NULL COMMENT '对象状态的稳定业务代码。允许值：ACTIVE、UNAVAILABLE、RETIRED。',
  `available_time` DATETIME(6) NULL COMMENT '文件可用时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '文件并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_storage_object` (`storage_provider`, `storage_container_ref`, `storage_object_ref`, `object_version`) COMMENT '保证业务唯一性：storage_provider、storage_container_ref、storage_object_ref、object_version',
  UNIQUE KEY `uk_file_upload` (`upload_id`) COMMENT '保证业务唯一性：upload_id',
  UNIQUE KEY `uk_file_content_owner` (`file_id`, `content_hash`) COMMENT '保证业务唯一性：file_id、content_hash',
  UNIQUE KEY `uk_file_policy_owner` (`file_id`, `scan_policy_id`) COMMENT '保证业务唯一性：file_id、scan_policy_id',
  KEY `idx_file_scan_status` (`scan_status`, `create_time`) COMMENT '支持访问路径：scan_status、create_time',
  KEY `idx_file_object_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存不可变对象存储引用、内容摘要和创建时冻结的扫描策略修订';

CREATE TABLE `t_file_owner_ref` (
  `owner_ref_row_id` BIGINT NOT NULL COMMENT '文件所有者引用的稳定业务标识。',
  `file_id` BIGINT NOT NULL COMMENT '文件的稳定业务标识。',
  `owner_system` VARCHAR(64) NOT NULL COMMENT '所有者系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '所有者资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '所有者资源标识的业务值。',
  `purpose_code` VARCHAR(64) NOT NULL COMMENT '文件用途代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`owner_ref_row_id`),
  UNIQUE KEY `uk_file_owner` (`file_id`, `owner_system`, `owner_type`, `owner_ref_id`, `purpose_code`) COMMENT '保证业务唯一性：file_id、owner_system、owner_type、owner_ref_id、purpose_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存文件与来源系统业务资源之间的最小安全引用';

CREATE TABLE `t_file_scan_result` (
  `scan_result_id` BIGINT NOT NULL COMMENT '文件扫描结果的稳定业务标识。',
  `file_id` BIGINT NOT NULL COMMENT '文件的稳定业务标识。',
  `scan_requirement_id` BIGINT NOT NULL COMMENT '扫描要求的稳定业务标识。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '冻结扫描策略修订的稳定业务标识。',
  `attempt_no` INT NOT NULL COMMENT '扫描尝试序号的整数值。',
  `scanner_runtime_version` VARCHAR(64) NOT NULL COMMENT '扫描器运行版本的业务值。',
  `result_code` VARCHAR(64) NOT NULL COMMENT '扫描结果代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `scanned_time` DATETIME(6) NOT NULL COMMENT '扫描完成时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`scan_result_id`),
  UNIQUE KEY `uk_file_scan_attempt` (`file_id`, `scan_requirement_id`, `attempt_no`) COMMENT '保证业务唯一性：file_id、scan_requirement_id、attempt_no',
  KEY `idx_scan_file_policy` (`file_id`, `scan_policy_id`) COMMENT '支持访问路径：file_id、scan_policy_id',
  KEY `idx_scan_req_policy` (`scan_requirement_id`, `scan_policy_id`) COMMENT '支持访问路径：scan_requirement_id、scan_policy_id',
  KEY `idx_file_scan_re_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存文件对其冻结策略修订中每项扫描要求的运行版本与判定';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿执行记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT 唯一 Compensation Handler Catalog；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的业务子类型；无业务子类型时使用短横线占位。取值来源：所属 Handler 的子类型目录，无业务子类型时固定短横线占位；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可空或最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿任务已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前 Claim 补偿任务的执行实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前补偿任务执行租约到期的 UTC 微秒精度时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT 'Unique index for compensation_event on event_key, event_type, sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT 'Access index for compensation_event on execute_status, next_execute_at, id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT 'Access index for compensation_event on execute_status, lock_until, id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='本服务 Handler 的可恢复任务；uk_comp_event_identity(event_key,event_type,sub_event_type)；状态 INIT/PROCESSING/ERROR/FAILED；可选最小 JSON Object 最大 4 KiB UTF-8，禁止敏感值、Vault Token 和已落库上下文；Claim/Lease 使用 lock_version，安全默认值与执行协议服从现行补偿指南；Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。';

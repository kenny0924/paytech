# 任务卡 A：Platform 文件多存储并存（第一段后端）

> 需求：[requirement.md](../requirement.md) v2（D1—D4、R1—R8、B1—B6、AC-01—AC-12）；方案：[technique.md](../technique.md) v2（Q6—Q11 已按建议确认）。本卡只补方案未定的细节，冲突时以本卡为准并在报告中说明。

## 0. 工作方式

- **只在** `/Users/kenny/Desktop/Workspace/Code-MoR-filestore`（detached worktree）中修改；不得改主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`，不建分支、不提交、不推送。需求文档只读取主工作区 `requirements/20261022/platform-support-文件多存储并存/`。
- **范围**：`paytech/platform-support/**`、`agent-docs/TECHNOLOGY_STACK.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`local-environment/README.md`。**不改** Operations BFF / Web（任务卡 B）、Vault 服务、CCT、Client BFF、`test-environment/`。如发现必须改范围外文件，停下并在报告里说明。
- **开工前必读**（按 AGENTS.md 要求实际读取，报告中列出已读路径）：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（完成前按第 11 节逐项自查）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`paytech/platform-support/README.md`。
- **参照实现**：
  - 凭据 Vault：`delivery/service/processor/ProtectNotificationCredentialProcessor.java`、`delivery/integration/provider/ses/SesCredentialVault.java`、`t_notification_provider.credential_vault_token`；
  - 管理写接口：币种维护（`CurrencySpecManagementApi`、`CreateCurrencySpecProcessor` 等，`AbstractLockProcessor` + `OperationsManagementPolicyService.authorizeManagement` + `OperationsAuditService`）；
  - 进程内快照定时刷新：币种快照的做法；
  - 现有文件链路：`paytech-platform-support-file-document`、core 中 `*Onboarding*Processor`、`ReviewFileDownloadProcessor`、`FileScanHandler`。
- **构建与测试**：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，Maven 本地库 `~/.m3/repository`，可离线（`-o`）。测试凭据只从 `local-environment/.env.local` 读取所需键，不打印。本机库被多个会话共用：**只跑 platform-support Reactor**，不跑全仓库测试。
- **命令在前台运行**，不要等待后台事件或 Monitor；长命令设足够超时。

## 1. 数据库（`mor_platform`）

按 technique.md 第 3 节建 3 张表、改 2 张表，补充：

- `t_file_storage_instance`：`instance_code` 唯一键 `uk_file_storage_instance_code`；`credential_mode` 为 `IAM_ROLE` 时 `credential_vault_token` 必须为空，`STATIC` 时必填（应用层校验，不建 CHECK）。去掉方案中的 `last_check_*` 两列（实例不单独测试，见第 3 节）。
- `t_file_storage_bucket`：唯一键 `uk_file_storage_bucket (instance_id, bucket_name)`。
- `t_file_storage_route`：唯一键 `uk_file_storage_route_source (source_system)`；默认路由 `source_system='*'`。
- `t_file_upload` 加 `source_system`、`reason_code`；`uploader_id`、`tenant_id` 改为可空。`t_file_object` 加 `source_system`、`reason_code` 与索引 `idx_file_source (source_system, reason_code, create_time)`。新列：加列（可空）→ 回填 → 改 NOT NULL。回填值：`source_system='platform-support'`、`reason_code='CLIENT_ONBOARDING_MATERIAL'`。
- 同一变更同步：PDManer `docs/database/platform-support.pdma.json`（完整中文表 / 列注释）、`docs/database/sql/platform-support-schema.sql`、新增增量 `docs/database/sql/changes/2026-10-22-file-storage-management.sql`（含执行顺序、回退语句、核验查询，文件头注明"未执行"）、测试 `src/test/resources/init/ddl.sql`、`PLATFORM_SUPPORT_DATABASE.md`。
- 不建 FK / CHECK；表名 `t_`、唯一 `uk_`、普通 `idx_`。
- **执行授权**：只允许把 DDL 应用到 Platform auto 测试实际使用的本机测试库（按 UNIT_TEST_GUIDE 和现有测试配置确认是哪个库），在报告中记录执行了什么、对哪个库。**不得**连接 mor-test，也不改本机运行库 `mor_platform`（若它与测试库是同一个，先停下报告）。

## 2. 存储运行时（file-document）

- 删除 `MinioStorageConfiguration`、`S3StorageConfiguration`、`MinioStorageProperties`、`S3StorageProperties` 与两个 `PROVIDER` 常量；`MinioFileStorage` / `S3FileStorage` 改为由"实例快照 + 本次凭据"构建，`providerCode()` 返回实例代码。客户端构造（超时、禁止重定向、私有 CA 只信任该证书、path-style、默认凭据链）逻辑保留。私有 CA 改为从 `trusted_certificate_pem` 文本加载（`StorageTrust` 增加从 PEM 文本构建）。
- 新增 `FileStorageRegistry`：
  - 持有实例 / 桶 / 路由快照，启动加载、每 30 秒刷新（可配置，`files.storage.snapshot-refresh`），本进程管理写提交后立即刷新；
  - `forWrite(sourceSystem)`：按 `source_system` 精确匹配，否则 `*`；要求路由桶 `ACTIVE`、实例 `ACTIVE`；返回实例、桶、`scan_policy_code`、`max_upload_seconds`；无路由 → 503；
  - `forRead(provider, container)`：实例 `ACTIVE` 且桶 `ACTIVE` / `READ_ONLY`，否则 503，且不发起存储调用；
  - 取得适配器时：`STATIC` 实例先经 `StorageCredentialVault` Reveal（类型 `FILE_STORAGE_CREDENTIAL_V1`，值 `{"accessKeyId","secretAccessKey"}`，用途 `FILE_STORAGE_ACCESS`，不缓存原值，原值和解析错误不进日志 / 异常），`IAM_ROLE` 用默认凭据链；每次操作构建客户端，用完关闭；
  - 替换现有 6 处 `providerCode()` 过滤（`StartOnboardingUploadProcessor`、`DownloadOnboardingFileProcessor`、`ReviewFileDownloadProcessor`、`CompletedUploadVerifier`、`FormatFileScanner` 等）。
- Vault 侧：先核对通知账号的 `NOTIFICATION_PROVIDER_CREDENTIAL_V1` 在 Vault 服务 / 本机 / mor-test 是否需要登记数据类型或用途策略。如需要，**不要改 Vault**，在报告中列出需要登记的内容。
- 配置：删除 `files.onboarding.storage-provider`、`storage-container-ref`、`scan-policy-code`、`upload-seconds`、`minio.*`、`s3.*`（主配置、测试配置、README）。启动时若这些旧键仍存在，失败并提示"存储配置已改为运营存储管理"（AC-11）。
- 开关：新增 `files.enabled`（默认 false）控制内部文件接口、`FILE_SCAN` 与格式检查器；`files.onboarding.enabled=true` 要求 `files.enabled=true`。补偿执行器默认值从 `files.onboarding.enabled` 改为 `files.enabled`。
- 入驻链路（第一段不改行为）：写入目标改为 `forWrite("platform-support")`；扫描策略与上传期限取自路由；上传写 `source_system='platform-support'`、`reason_code='CLIENT_ONBOARDING_MATERIAL'`，`uploader_id` / `tenant_id` 照旧写入。

## 3. 存储管理（管理写接口，供 Operations BFF）

`platform-support-api` 新增 `FileStorageManagementApi`，`@HttpExchange("/storage-admin/v1")`，请求头同 `CurrencySpecManagementApi`（`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`）。权限：`FILE_STORAGE_READ`（查询）、`FILE_STORAGE_MANAGE`（写）。所有写：`AbstractLockProcessor` 锁实际资源 → 短事务内授权、锁行 + `expectedVersion`、状态校验、条件更新、`OperationsAuditService` 审计（审计不含密钥）。外部调用（Vault、存储）全部在事务外。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/instances` | 列表：实例 + 其桶 + 引用该实例的路由数；**不返回** Vault Token，只返回 `credentialConfigured` |
| POST | `/instances` | `instanceCode`、`storageType`、`endpoint`、`region`、`pathStyleAccess`、`trustedCertificatePem`、`credentialMode`、`accessKey`、`secretKey`、超时、`reason`；STATIC 先 Protect（用途 `FILE_STORAGE_SETUP`）再写库，建为 `DRAFT`；MINIO 只允许 STATIC 且 endpoint 必填 |
| PUT | `/instances/{instanceCode}` | 改 endpoint / region / pathStyle / 证书 / 超时；`expectedVersion` |
| PUT | `/instances/{instanceCode}/credential` | 更换密钥（或切换凭据方式）：Protect 新值 → 短事务替换 Token、`credential_update_time`；`expectedVersion` |
| PATCH | `/instances/{instanceCode}/status` | `DRAFT→ACTIVE`（要求至少一个桶 `ACTIVE`）、`ACTIVE→DISABLED`（有路由指向其桶时拒绝）、`DISABLED→ACTIVE`；`reason` 必填 |
| POST | `/instances/{instanceCode}/buckets` | 登记桶 `bucketName`，`DRAFT` |
| POST | `/instances/{instanceCode}/buckets/{bucketName}/check` | 事务外检查：存在、版本控制 Enabled、默认加密开启、写入 / 读取 / 删除一个探测对象（高熵 Key，前缀 `.platform-readiness/`）；短事务回写 `last_check_*`；实例为 DRAFT 也可检查 |
| PATCH | `/instances/{instanceCode}/buckets/{bucketName}/status` | `DRAFT→ACTIVE`（要求最近一次检查 `OK` 且在 10 分钟内）、`ACTIVE→READ_ONLY`、`READ_ONLY→ACTIVE`、`ACTIVE|READ_ONLY→DISABLED`；有路由指向该桶时不能 `READ_ONLY` / `DISABLED` |
| GET | `/routes` | 路由列表（含实例代码、桶名） |
| POST | `/routes` | `sourceSystem`（服务名格式或 `*`）、`instanceCode`、`bucketName`、`scanPolicyCode`（必须有 ACTIVE 修订）、`maxUploadSeconds`（60—900） |
| PUT | `/routes/{sourceSystem}` | 改目标桶 / 策略 / 期限；`expectedVersion` |
| DELETE | `/routes/{sourceSystem}` | `*` 不能删；`expectedVersion` |

- 实例代码、桶名创建后不可改；被文件或上传记录引用的实例 / 桶不能物理删除（本期不提供删除接口）。
- `sourceSystem='*'` 在 URL 中按 `*` 传递（或另定 `DEFAULT` 别名，报告中说明选择）。
- 错误：业务校验 400、版本冲突 409、Vault / 存储不可用 503，沿用 RFC 9457 体系与现有错误码风格。

## 4. 内部文件接口

`platform-support-api` 新增 `InternalFileApi`，`@HttpExchange("/internal/files/v1")`，按 technique.md 4.1 实现 4 个接口，补充：

- 调用身份：使用 Foundation internal-auth 已解析的调用方身份（查 `InternalAuthFilter` / `CallerIdentity` 如何暴露）；无身份 → 拒绝（401/403 按现有约定）。`source_system` = 身份中的服务名。
- 开始上传入参校验：`reasonCode` 格式 `[A-Z][A-Z0-9_]{0,63}`；`displayName` 沿用现有清洗规则；`mimeType` / `sizeBytes` 受路由扫描策略约束；可选 `uploaderAccountId`。
- 完成上传：只有原 `source_system` 可完成；复用现有完成核对（`CompletedUploadVerifier`）与 `FILE_SCAN` 登记；幂等返回原结果。
- 下载：文件 `scan_status=AVAILABLE` 且 `object_status=ACTIVE`、桶可读；60 秒原版本地址。
- 同名 Service 入口（file-document 模块的 Domain Service 或 core 层可复用的 Service 方法，按 JAVA_PROJECT_STRUCTURE 决定位置）供 Platform 内部模块（工作流）直接调用，`source_system` 由入口参数传入并限定为 `platform-support`。在报告中给出最终的接口名、方法签名，供通知工作流会话。
- 内部路径加入 metadata-only 日志配置（参照现有 `/internal/onboarding-files`）。

## 5. 测试（auto，真实本机 MySQL / Redis / MinIO，Vault 用现有测试端点）

按 technique.md 第 7 节和 AC-01—AC-12。补充：

- `StorageInstanceMocker` 直接插入实例 / 桶 / 路由行；Vault 测试端点返回配置的凭据 DTO（参照 `NotificationVaultMock` / `VaultMockController`），并能断言 Protect / Reveal 调用次数与用途。
- 并存场景：同一上下文登记 MINIO 类型实例（MinIO SDK）和 S3 类型实例（AWS SDK，path-style），都指向本机 MinIO（19400）的 `mor-platform-files-test`。
- 桶检查失败场景：不存在的桶；未开版本控制 / 未开加密的桶如本机无法构造，用 MinIO 管理 API 在测试中临时建一个不开版本控制的桶并在 AfterEach 删除，做不到时在报告中说明并保留其余场景。
- 日志与表中无密钥原值（断言）。
- 现有文件测试全部迁到库中配置；`OnboardingFileTestBase` 不再通过属性切换供应商。
- 使用枚举 / 常量，不手写业务代码字面量（JSON 样例除外）。
- 收尾：`./mvnw -o -pl paytech/platform-support -am clean verify`（或该 Reactor 的等价命令）全部通过，保持 90% 指令 / 分支覆盖率门槛；记录用例数与覆盖率。

## 6. 文档

`TECHNOLOGY_STACK.md:50` 对象存储一行、`local-environment/README.md:67`、`paytech/platform-support/README.md`、`PLATFORM_SUPPORT_DATABASE.md` 改为库中管理与新开关说明。`CURRENT_IMPLEMENTATION.md` 不改（由主会话在合入后更新）。

## 7. 交付报告

写到 `/Users/kenny/Desktop/Workspace/Code-MoR-filestore/requirements/20261022/platform-support-文件多存储并存/reports/A-platform-report.md`（目录不存在则创建），包括：已读路径；改动文件清单；DDL 执行记录；接口与 Service 入口最终签名；WRITE_API_EXAMPLE 第 11 节逐项自查；测试命令、用例数、覆盖率；与本卡 / 方案的偏差及原因；Vault 侧需要的登记；遗留项。

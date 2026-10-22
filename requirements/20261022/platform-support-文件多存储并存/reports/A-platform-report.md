# 任务卡 A：Platform 文件多存储并存（第一段后端）· 交付报告

- 日期：2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-filestore`（detached，基于 `281a217d`）；未建分支、未提交、未推送、未部署，未连接 mor-test。
- 结论：代码、数据库文档与测试完成；最终 Platform auto 全量 **791/791** 通过。本次新增与改动代码行的分支覆盖率 **85.4%**（379/444，主会话接受的口径）；新增 / 修改类整体分支 72.0%、指令 93.9%。整体覆盖率门禁（Platform Phase A runtime，90%）**未通过**：指令 76.0%、分支 55.9%（拆模块后已知未达标，见第 6 节）。

## 1. 已读路径

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`paytech/platform-support/README.md`、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`（第 9 节）、`local-environment/README.md`；需求 `requirement.md`、`technique.md`、`delivery.md`、`tasks/A-platform.md`；工作流 `requirements/20261021/platform-support-工作流/technique.md`（模块位置）。参照实现：币种维护全链路、通知账号密钥 Protect / Reveal、ledger 币种快照刷新、Webhook / Notification 内部调用身份、现有入驻文件 Processor 与适配器测试。

本次结构：
- 所属模块：持久化、规则、存储运行时归 `paytech-platform-support-file-document`；鉴权 + 事务 + 审计的 Processor 归 `paytech-platform-support-core`（同通知管理：file-document 不依赖鉴权与审计模块）；协议适配在 `app`。
- 本地原子写入：每个管理写操作一个短事务（权限重检 → file-document Service 锁行、核对版本与状态 → 条件更新 → 审计事实）；内部文件完成为一个短事务（锁上传行 → 写文件 → 登记 FILE_SCAN）。
- 事务外步骤：Vault Protect / Reveal、存储桶检查、签发上传 / 下载授权、读取对象核对；均在事务前后，不在事务内。

## 2. 改动文件

**数据库与文档**
- `paytech/platform-support/docs/database/sql/changes/2026-10-22-file-storage-management.sql`（新，含顺序、回退、核验）
- `paytech/platform-support/docs/database/sql/platform-support-schema.sql`、`paytech-platform-support-test/src/test/resources/init/ddl.sql`（3 新表、2 表新列与可空）
- `paytech/platform-support/docs/database/platform-support.pdma.json`：`t_file_upload`、`t_file_object`、3 张新表与 `file_document` 图组取自生成器输出（其余实体与图组保持 HEAD 原样，不覆盖既有漂移）
- PDManer 生成输入（主会话批准）：`scripts/pdmaner/platform-support/manifests/02-file-document.mjs`（3 表定义；两表新列、可空与注释；`idx_file_source`）、`scripts/pdmaner/platform-support/manifest.mjs`（`file_document` 12→15，连带修改）、`scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`（图组加 3 表、组大小 15、总数 63）
- `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`agent-docs/TECHNOLOGY_STACK.md`、`local-environment/README.md`、`paytech/platform-support/README.md`
- 经批准的范围外：`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`（脚本重生成，+3 表、注释计数 46→49；模型更新后重跑无变化）、`scripts/pdmaner/platform-file-upload-model.test.mjs`、`scripts/pdmaner/platform-support-model.test.mjs`（见第 9 节）

**api**：`api/filestorage/**`（`FileStorageManagementApi` + 8 请求 + 6 响应）、`api/files/**`（`InternalFileApi` + 1 请求 + 5 响应）、`api/enums/FileStorage{Type,CredentialMode,InstanceStatus,BucketStatus,CheckResult}`

**file-document**
- 新：DataObject `FileStorageInstance/Bucket/Route`、3 Mapper + XML、3 Repository + Impl、`FileStorageManagementService(+Impl)`、`domain/storage/*`（命令、变更结果、视图、`FileStorageCatalog`、`StorageWriteTarget`）、`domain/file/{FileDisplayName,FileSourceCodes,StoredFileView}`、`integration/storage/{FileStorageRegistry,FileObjectStorage,StorageCredentialVault,StorageCredential,ProbeObject}`、`service/processor/{ProtectStorageCredentialProcessor,CheckStorageBucketProcessor}`、`ext/job/FileStorageSnapshotRefreshJob`、`core/configuration/FileStorageProperties`
- 改：`MinioFileStorage` / `S3FileStorage`（按实例 + 本次凭据构建、`checkBucket`、`close`）、`StorageTrust`（`fromPemText`，删路径加载）、`CompletedUploadVerifier`、`FormatFileScanner`（注册表；`files.enabled`）、`FileUploadService(Impl)`、`FileLifecycleService(Impl)`、`UploadModels`、`FileUpload`、两个 Mapper 与 XML、两个 Repository、`FileDownloadStorage.Download`（`usableUntil`）、pom（vault-client、validation）
- 删：`MinioStorageConfiguration`、`MinioStorageProperties`、`S3StorageConfiguration`、`S3StorageProperties`

**core**：新 `service/processor/files/*`（3 Processor + `FileTransactions` + 请求 / 响应）、`service/processor/filestorage/*`（12 Processor + `FileStorageManagementSupport` + 请求 / 响应）；改 `StartOnboardingUploadProcessor`、`DownloadOnboardingFileProcessor`、`ReviewFileDownloadProcessor`、`AbstractOnboardingFileAccessProcessor`、`OnboardingFileLocks`、`StartOnboardingUploadRequest`、`FileScanHandler`（`files.enabled`）

**audit-security**：`OperationsAuditFact.ConfigurationField` 增加 13 个存储字段

**app**：新 `ext/rest/files/InternalFileController|Converter`、`ext/rest/filestorage/FileStorageManagementController|Converter`、`configuration/FileStorageConfiguration`；改 `OnboardingFileConfiguration`、`PersistenceConfiguration`（5 个枚举 TypeHandler）、`application.yml`

**test**：新 `FileStorageInstanceManagementTest`、`FileStorageRouteManagementTest`、`InternalFileApiTest`、`FileStorageConfigurationTest`、`FileStorageManagementTestBase`、`StorageInstanceMocker`、`TempBucketMocker`、`StorageVaultMocker`、`FileStorageTestMapper`、`StorageInstanceRequestBuilder`；改适配器 / 入驻 / 扫描测试与基类、`VaultMockController`、`PlatformDataCleaner`（+3 表）、`LocalMockEnvironmentInitializer`（MinIO 管理员账号）、`StorageObjectMocker`、`FileTestMapper`、`OnboardingFileBuilder`、`OperationsCatalog`、`application-mock.yml`

## 3. DDL 执行记录（用户授权：仅这一份增量、仅本机 mor_platform）

- 目标：`127.0.0.1:13306` / `mor_platform`，账号 `platform_app`。
- 执行前：`t_file_upload` 0 行、`t_file_object` 0 行，`t_file_storage%` 不存在（2026-10-21T22:23:18Z）。
- 执行：`mysql ... mor_platform < changes/2026-10-22-file-storage-management.sql`，2026-10-21T22:23:27Z（UTC，即 2026-10-22 本地），一次完成：建 3 表 → 加列可空 → 回填（0 行）→ NOT NULL + `idx_file_source` → 放宽 `tenant_id`、`uploader_id`、`owner_*`。
- 核验：3 张新表存在；`source_system`、`reason_code` 两表均 `IS_NULLABLE=NO`；`tenant_id`、`uploader_id`、`owner_system`、`owner_type`、`owner_ref_id` 为 `YES`；两表空值计数 0；索引 `idx_file_source(source_system,reason_code,create_time)`、`uk_file_storage_instance_code`、`uk_file_storage_bucket(instance_id,bucket_name)`、`uk_file_storage_route_source`、`idx_file_storage_route_bucket` 均在。
- 未执行其他 SQL；未执行初始化 DML（注册块 130 以任务卡 B 的版本为准，我起草的那一份已撤回）。
- 执行后按生成器统一了 4 个列注释（只改注释、结构不变）：`t_file_upload.source_system`、`t_file_object.source_system`、`t_file_storage_route.source_system` 末尾补“取值来源：内部调用身份的服务名；非固定枚举。”（生成器要求来源系统字段声明取值来源）；`t_file_storage_instance.credential_mode` 改为“凭据方式；MINIO 只允许 STATIC。允许值：STATIC、IAM_ROLE。”。增量 SQL、schema、测试 DDL 已同步；**本机 `mor_platform` 这 4 列仍是旧注释**（未授权再执行 SQL，对应用与测试无影响），mor-test 按新文件执行即一致。

## 4. 接口与 Service 入口

**存储管理** `api/filestorage/FileStorageManagementApi`，`@HttpExchange("/storage-admin/v1")`，Header `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；查询 `FILE_STORAGE_READ|FILE_STORAGE_MANAGE`，写入 `FILE_STORAGE_MANAGE`：
`listInstances`、`createInstance`、`updateInstance`、`replaceCredential`、`changeInstanceStatus`、`registerBucket`、`checkBucket`、`changeBucketStatus`、`listRoutes`、`createRoute`、`updateRoute`、`deleteRoute`（路径与 DTO 见已发给主会话的合同清单）。默认路由在 URL 中按 `*` 传递（或 `%2A`），未另设别名。写入返回 `ConfigurationMutationResponseBody`。

**内部文件接口** `api/files/InternalFileApi`，`@HttpExchange("/internal/files/v1")`：
- `FileUploadResponseBody startUpload(StartFileUploadRequestBody)` — `POST /uploads`，201
- `FileUploadCompletionResponseBody completeUpload(String uploadId)` — `POST /uploads/{uploadId}/completion`
- `StoredFileResponseBody getFile(String fileId)` — `GET /files/{fileId}`
- `FileDownloadResponseBody authorizeDownload(String fileId)` — `POST /files/{fileId}/download`
- 调用身份：`CallerIdentityHolder.current()`；无身份 → 401 `INTERNAL_TOKEN_INVALID`；来源系统 = `identity.caller()`。

**Platform 内部模块（工作流）直接调用的同名入口**（`source_system` 传 `FileSourceCodes.PLATFORM_SUPPORT` = `platform-support`）：
- `com.paytech.platform.support.service.processor.files.StartFileUploadProcessor#process(StartFileUploadRequest(String sourceSystem, String reasonCode, String filename, String mimeType, long sizeBytes, Long uploaderAccountId))` → `StartFileUploadResponse(uploadId, Grant grant, Instant expiresAt)`
- `com.paytech.platform.support.service.processor.files.CompleteFileUploadProcessor#process(CompleteFileUploadRequest(String sourceSystem, long uploadId))` → `CompleteFileUploadResponse(uploadId, fileId, completedAt)`
- `com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService#describe(long fileId)` → `StoredFileView`（fileId、sourceSystem、reasonCode、displayName、mimeType、sizeBytes、contentHash、scanStatus、objectStatus、uploaderId、createTime）
- `com.paytech.platform.support.service.processor.files.AuthorizeFileDownloadProcessor#process(AuthorizeFileDownloadRequest(long fileId))` → `AuthorizeFileDownloadResponse(url, expiresAt)`

**存储运行时**：`FileStorageRegistry#forWrite(String sourceSystem)`、`#openForWrite(provider, container)`、`#openForRead(provider, container)`、`#connect(FileStorageInstance)`、`#refresh()`。

## 5. WRITE_API_EXAMPLE 第 11 节自查

- API 与 Body：class + 中文字段注释 + Hibernate Validator；内部 Request 注解由 `validateBasic` 触发；仅依赖输入的组合（地址 HTTPS 根、证书可解析、凭据组合、文件名）在 `validate`。✔
- Controller / Converter 只适配；每个写操作一个 Processor（core `service/processor/filestorage|files`），主方法分段可读。✔
- Processor 查询：存储管理的行锁 / 版本 / 状态由 file-document Service 在同一事务内完成（core 不能持有 file-document DataObject，沿用通知管理的做法，见第 7 节偏差）；内部完成 Processor 先读原上传并核对来源再推进。⚠ 有说明
- Context：file-document 内部以 DataObject 直接承载；跨模块用命令 / 变更结果，不传 DataObject。✔
- Service 不调用其他 Service、Processor 或外部依赖；Vault / 存储只在 Processor 与注册表中、事务外。✔
- 原子写入与事务位置：管理写入一事务（权限 + 写 + 审计）；Vault Protect 与桶检查在事务外，结果另开短事务。✔
- Repository 按表；条件 SQL（版本 + 原状态）与行数检查后同步原对象。✔
- 锁：实例及其桶共用 `file-storage:instance:{code}`，路由 `file-storage:route:{source}`；内部完成与入驻完成共用 `file:upload:complete:{uploadId}`；数据库行锁按 实例 → 桶 → 路由 顺序，跨表不变量由行锁串行。✔
- 原请求比较：完成上传以 uploadId 为身份，已完成返回原回执；其他写入依当前状态与版本处理。✔
- 未接触 Idempotency-Key / 公共命令幂等。✔
- 多表组合查询使用 View（`FileStorageInstanceView`、`FileStorageRouteView`、`StoredFileView`）；DataObject 未越过模块与 HTTP 边界。✔
- 配置集中在 `FileStorageProperties`（`files.storage.*`）；无生产 Mock；生产代码无测试分支。✔
- 验证：见第 6 节。✔

## 6. 测试

- 命令（`paytech/` 下，JDK 25，离线）：
  `./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -pl platform-support/paytech-platform-support-test -am clean verify -Pcoverage-gate -Dtest='com/paytech/platform/support/auto/**/*' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`
- 最终结果：**791/791 通过**，0 失败 / 错误 / 跳过。最终全量在主会话协调的独占窗口运行：2026-10-21T23:39:36Z → 23:47:08Z（UTC），开跑前确认无 `Code-MoR-workflow` surefire 进程；`clean verify -Pcoverage-gate`，排除 manual，未 install。
- 上一轮全量（补测前）：2026-10-21T23:00:40Z → 23:08:04Z（UTC），780/780 通过。之前两轮出现非本需求用例的残留数据 / 主键冲突，原因是工作流会话同时在本机 `mor_platform` 跑测试；等它结束后独占重跑，全部通过。
- 文件相关 157 项（单独一轮）：适配器 MinIO 12 / S3 12、存储管理实例 11 / 路由 4、入驻开始 23 / 读取 16 / 审核下载 17 / 完成 15 / 下载 13 / 核验 12、内部文件 7、扫描 Handler 7、启动配置 8。
- 覆盖率（最终全量，`-Pcoverage-gate`，bundle Platform Phase A runtime）：
  | 口径 | 补测前 | 最终 |
  |---|---|---|
  | 本次新增与改动代码行的分支（新文件全部行 + 修改文件的 diff 行） | 70.5%（313/444） | **85.4%（379/444）** |
  | 新增 / 修改类整体的分支（140 个源文件） | 64.1%（537/838） | 72.0%（603/838） |
  | 新增 / 修改类整体的指令 | 92.9% | 93.9% |
  | 整体门禁 Platform Phase A runtime（门槛 90%） | 指令 75.9% / 分支 55.0% | 指令 76.0% / 分支 55.9%，**未通过** |

  主会话接受按改动代码行口径达到 85%；类整体口径的差距来自这些类中本需求未改动的既有保护分支（扫描策略 / 结果校验、策略 JSON 校验、其他审计类型等），按主会话决定不补（范围扩大）。整体门禁在拆模块后已知未达标（README 已记载）。早先报告中的“68.1%”为另一种统计范围的数字，以本表为准。
- 验收覆盖：
  - AC-01 `FileStorageInstanceManagementTest.test_normalFlow`：两类实例同时可用；密钥只在 Vault 请求中；表、审计属性、响应与日志（OutputCapture）均无原值。
  - AC-02 `test_bucketCheckFailures`：NOT_FOUND / VERSIONING_DISABLED / ENCRYPTION_DISABLED / ACCESS_DENIED 均用本机 MinIO 真实构造（管理员账号临时建桶，AfterEach 删除），启用被拒；适配器测试也覆盖 OK / ACCESS_DENIED。
  - AC-03 `InternalFileApiTest.test_routeBySource`、`FileStorageRouteManagementTest.test_normalFlow`。
  - AC-04 `InternalFileApiTest.test_multipleStorages`（改路由后旧 MINIO 上传仍完成、扫描、下载；新上传进 S3）、`FileScanHandlerTest.test_readsRecordedInstance`。
  - AC-05 只读桶可读（`test_multipleStorages`、`test_disabledStorageRetries`），不接新上传（`StartOnboardingUploadTest.test_routedByPlatformSource`）。
  - AC-06 停用桶 / 实例：下载 503、扫描可重试失败，Vault Reveal 次数不变（无存储调用）。
  - AC-07 `test_replaceCredential`：新 Token 生效，之后只 Reveal 新 Token。
  - AC-08 `test_vaultUnavailableOnCreate`、`test_iamRoleUnaffectedByVault`（IAM_ROLE 不读 Vault 仍检查成功；STATIC 503）、`InternalFileApiTest.test_vaultUnavailable`。
  - AC-09 `InternalFileApiTest.test_callerIdentity`（无身份 401；Token 身份决定来源系统）、`test_businessRuleViolation`（跨来源完成 404）。
  - AC-10 入驻开始 / 完成断言 `source_system=platform-support`、`reason_code=CLIENT_ONBOARDING_MATERIAL`；入驻全部回归通过。
  - AC-11 `FileStorageConfigurationTest`（6 个旧键、环境变量形式、新键不误报）。
  - AC-12 既有适配器 / 上传 / 完成 / 检查 / 审核下载测试改为库中配置后全部通过（`OnboardingFileTestBase` 不再用属性切换供应商）。
- 其他门禁：`ArchitectureAutoTest` 通过（table-ownership 重生成后）。
- PDManer：`node --test scripts/pdmaner/platform-file-upload-model.test.mjs scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-schema.test.mjs`

  | 时点 | 通过 / 总数 | 失败项 |
  |---|---|---|
  | 改动前（HEAD 基线） | 35 / 42 | #8、#9、#21、#34、#38、#40、#41 |
  | 只改 SQL / 模型、未改生成输入时 | — | 基线 7 项 + #1、#2（列数、可空）+ #10（表集合）、#32（表总数 60≠63） |
  | 最终 | 35 / 42 | #8、#9、#21、#34、#38、#40、#41（与基线相同，无新增） |

  基线 7 项与本次无关：#8 / #9 由 `t_api_credential`、通知两表、`t_currency_spec`、`audit_security` 图组的既有漂移及过期摘要钉造成；#21 是 `t_api_credential` 一个非 v5 ID；#34 是 55 表硬编码清单（已缺 `t_currency_spec`、`t_tenant_invitation`，现在也列出 3 张新表）；#38、#40、#41 是 API 凭证、初始目录 127 行、通知增量的既有差异。

### 6.1 覆盖率补测（主会话第二轮要求）

- 新增 / 扩充的业务场景（均从真实入口进入，无 stub）：
  - 存储管理 HTTP（`FileStorageInstanceManagementTest`）：`test_credentialModeSwitch`（STATIC→IAM_ROLE 清除 Token、IAM_ROLE→IAM_ROLE、IAM_ROLE→STATIC；MINIO 切 IAM_ROLE、密钥不完整、IAM_ROLE 带密钥拒绝；Protect 503 不改库；实例不存在 404；列表显示未配置密钥与未检查的桶）、`test_statusTransitionsRejected`（实例与桶的非法转换、只读桶恢复启用、停用实例重新启用、版本冲突 409、不存在 404）；`test_bucketCheckFailures` 增加 S3 适配器的 NOT_FOUND / VERSIONING_DISABLED / ENCRYPTION_DISABLED（MinIO 管理员账号）；`test_checkUnavailable` 增加 Vault 返回格式不合法的密钥、MINIO 配 IAM_ROLE、STATIC 无 Token（数据不一致）返回 503，以及实例不存在 404；`test_invalidParameter` 增加地址带用户信息 / 查询 / 片段 / 无主机、密钥空白；`test_vaultUnavailableOnCreate` 增加 IAM_ROLE 不填证书、地址为根路径或留空；`test_statusTransitions` 增加“检查结果 OK 但无检查时间”不能启用。
  - 写入路由 HTTP（`FileStorageRouteManagementTest`）：路由指向不存在的实例或桶拒绝；`test_danglingRoute`（桶已不存在时列表按空目标返回）。
  - 内部文件 HTTP（`InternalFileApiTest`）：`test_routeUnavailable`（来源路由悬空、默认路由所指实例未启用、没有任何路由时开始上传 503 且不登记）；不存在的上传 / 文件 404、对象不可用的文件下载 409、纯系统上传查询上传人为空、记录的实例未登记时下载 503、文件名 "." 与超长。
  - 入驻完成（`CompleteOnboardingUploadTest.test_noDataFound`）：不存在的上传 404。
  - 启动检查：`FileStorageConfigurationTest.test_enabledDependencies`（files.enabled 缺 Redis 资源协调或本地补偿执行器拒绝启动）、新 `FileStoragePropertiesTest`（默认值、刷新间隔不足 1 秒、负等待、非正租约、超过 Integer.MAX_VALUE 毫秒）。
- 文件相关测试单独运行（-Dtest 只选文件相关类）：2026-10-21T23:33:56Z → 23:35:56Z（UTC），168/168 通过，改动行分支 85.1%；随后最终全量 791/791，改动行分支 85.4%（见上表）。
- 确实覆盖不到的改动分支及原因：
  - 签发授权的二次核对（`UploadModels.usableFor`、`FileDownloadStorage.usableUntil`、`StartFileUploadProcessor` / `AuthorizeFileDownloadProcessor` 的 grant 判空）：适配器已按上限签发，真实 SDK 不会返回越界或非 HTTPS 授权。
  - 并发竞态：`CompleteFileUploadProcessor` 锁内的“已完成”再判断；`FileStorageRegistry.openForWrite` 在 `forWrite` 之后同一快照内再次判定不可写。
  - 快照刷新失败与首次加载失败（`FileStorageRegistry.refresh/catalog`）：需要数据库不可用。
  - 三个存储 Repository 的“唯一键冲突但不是本表唯一索引”与“乐观条件更新影响行数不为 1”：行锁与版本已在同一事务内核对。
  - `FileStorageManagementServiceImpl.requireCredentialAllowed` 的 Token / 方式组合：处理器已在输入校验中拒绝，Service 是第二道防线。
  - S3 不填地址或不填证书（会访问真实 AWS，或需 JDK 默认信任本机自签名证书）；存储返回空加密头或读回内容不一致；S3 错误分类中 404 的其余错误码组合与非 403 / 404 状态。
  - `FileStorageProperties` 的空值：Spring 绑定空值时保留默认值，不会绑定为 null。
  - `StorageCredentialVault` / `ProtectStorageCredentialProcessor` 的 Vault 客户端未装配；解析结果为 null 节点（Jackson 对空内容返回 MissingNode）。
  - 两个 Converter 的路径变量 / 必填文本为 null：在到达 Converter 前已被路由或 Bean Validation 拒绝。

## 7. 与任务卡 / 方案的偏差及原因

1. **`t_file_upload.owner_system/owner_type/owner_ref_id` 也改为可空**（任务卡只写了 `uploader_id`、`tenant_id`）：内部文件接口的上传没有 Owner；填伪值会污染第二段要删的字段。入驻查询仍按 `owner_system='CCT'` 过滤，不受影响。
2. **默认加密检查方式**：不调用 GetBucketEncryption，而是确认探测对象写入后带有服务端加密头。本机 Platform 账号的 MinIO 策略没有 `GetEncryptionConfiguration` 权限，按方案调用会把加密已开的桶误判为 ACCESS_DENIED；现做法验证的是“默认加密实际生效”，且不需要改 `local-environment/minio/init`。
3. **内部接口身份**：接受 Foundation 识别的任何调用身份——DUAL 模式下无 Token 时回退 `X-Caller-Service`，与 Webhook / Notification 内部入口一致；`internal-auth.mode=OFF` 时全部 401。若要求必须是签名 Token，需另行决定。
4. **存储管理规则放在 file-document Service**（锁行、版本、状态转换、引用保护），core Processor 负责鉴权、事务、锁、Vault / 存储与审计：core 不能持有 file-document 的 DataObject，沿用通知管理的分工。
5. **实例不单独测试连接**（任务卡第 1 节去掉 `last_check_*`）；连接是否可用由桶检查体现。
6. **适配器端口**：保留四个原端口接口，新增组合接口 `FileObjectStorage`（继承四者，另加 `checkBucket`、`close`），注册表按操作构建、调用方 try-with-resources 关闭。
7. **`files.onboarding.enabled` 写入主配置**（默认 false），与 `files.enabled` 一起说明；`compensation.enabled` 默认改为跟随 `files.enabled`。
8. **入驻上传期限**：改为取默认 / 精确路由的 `max_upload_seconds`（标准测试为 900），旧的 `upload-seconds` 配置已删除。
9. **既有 DataObject `FileUpload` 仍为 record**：只增加字段，未改为 class（MyBatis 构造器映射与多处调用会连带变化）。
10. **注册块 130 DML**：曾按币种做法起草，主会话确认以 B 的版本为准，已撤回。测试目录 `OperationsCatalog` 使用权限 ID 900000000000130101（READ）、900000000000130102（MANAGE），需与 B 的版本一致。
11. **误操作：向共用本地 Maven 仓库安装 SNAPSHOT**。时间 2026-10-21T23:26:44Z（UTC）；命令（`paytech/` 下）`./mvnw -o -q -Dmaven.repo.local=$HOME/.m3/repository -pl platform-support/paytech-platform-support-test -am install -DskipTests`（本应只编译）。影响：把本工作目录（281a217d + 本需求改动）构建的 SNAPSHOT 装入共用 `~/.m3`，覆盖模块包括 platform-support 全部模块（parent、api、access-control、audit-security、delivery、file-document、core、app），以及 -am 带上的上游模块：paytech-parent、paytech-dependencies、paytech-foundation 下各模块（common-core、mvc-web-starter、http-problem、http-client-support、internal-auth、mybatis-support、redis-starter、caffeine-starter、xxl-job-starter、snowflake-id、json-canonicalization、messaging / messaging-api、compensation-event、test-support）、arch-rules、vault / vault-client、client-master / client-master-api。用 -am 在 Reactor 内构建的会话不受影响；不带 -am 从 `~/.m3` 解析的构建会拿到本版本。已当即报告主会话，由主会话通知其他会话各自重新 install；本任务之后只用 verify，未尝试恢复。

## 8. Vault 需要登记（未改 Vault）

- 数据类型 `FILE_STORAGE_CREDENTIAL_V1`（值格式 JSON：`{"accessKeyId","secretAccessKey"}`）。
- 规则 `platform-support-service / PROTECT / FILE_STORAGE_CREDENTIAL_V1 / FILE_STORAGE_SETUP`。
- 规则 `platform-support-service / REVEAL / FILE_STORAGE_CREDENTIAL_V1 / FILE_STORAGE_ACCESS`。
- 本机与 mor-test 都需要登记（参照通知账号：mor-test 经 Vault 管理接口添加规则）。主会话已在需求目录写 `db/vault/dml.sql` 并在本工作目录同步 `local-environment/vault/policy.local.json`（非本任务卡改动）。

## 9. 遗留项

1. **PDManer 模型测试**：基线已失败的 7 项未动（见第 6 节表格）。本次修改的测试断言：`platform-file-upload-model.test.mjs` 中 `t_file_upload` 列数 18 → 20、`owner_*` 三列 `notNull` 期望 true → false、注释期望补“；内部文件接口的上传为空（第二段删除）。”；`platform-support-model.test.mjs` #14 的文件组表清单补 3 张新表（标题 twelve → fifteen）。模型中 3 张新表的实体 / 字段 / 索引 ID 改为生成器的稳定 ID（替换此前手工补丁的 ID），Java 与 SQL 不引用模型 ID，无影响。#8 / #9 的摘要钉与总数期望属既有过期状态，本次未更新。
2. **覆盖率门禁**未通过（第 6 节），属于拆模块后的既有状况，本次未降低门槛。
3. **本机 e2e Platform**（`local-environment/e2e`）启动参数仍带 `files.onboarding.storage-provider` / `minio.*` 等旧键；换新 jar 会按 AC-11 启动失败，需要更新 e2e 脚本并在本机登记实例 / 桶 / 路由。mor-test compose 同理（删除 `FILES_ONBOARDING_STORAGE_*`、`FILES_ONBOARDING_MINIO_*`，阶段 6—7）。
4. **共享 `mor_platform`**：本机库已执行新 DDL，其他会话（如工作流）基于旧代码、直接往 `t_file_upload` / `t_file_object` 插入数据的测试会因新 NOT NULL 列失败，需要合入本变更后的测试映射。自动测试把 3 张存储表列为测试独占表：本机若在 `mor_platform` 手工登记实例，auto 测试会拒绝运行（与其他独占表一致）。
5. **注册块 130** 的菜单 / 路由路径以任务卡 B 为准；BFF 路由需与本合同的 12 个接口一一对应。
6. Vault 登记（第 8 节）完成前，STATIC 实例的新建与存储操作在真实环境会返回 503。
7. `CURRENT_IMPLEMENTATION.md` 未改（由主会话合入后更新）。
8. **类口径覆盖率**：新增 / 修改类整体分支 72.0%；既有保护分支不补测（主会话决定）。

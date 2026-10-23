# A 卡交付报告：Platform 文件公开桶、替换、检查策略与文件记录

日期：2026-10-22。工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-plat`（分离 HEAD；最初基于 main `42879401`，后按 team lead 要求变基到 `22ede435`，见第 7 节；未提交、未推送、未建分支，未在 mor-test 执行任何操作，见第 2 节最后一条）。

## 0. 阶段说明

**已读路径**：`tasks/A-platform.md`、`tasks/common.md`、`requirement.md`、`technique.md`；`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`paytech/platform-support/README.md` 文件章节，以及文件多存储并存第一段、第二段的方案与现有文件模块源码。

**各写接口的 Processor、Context，本地原子写入与事务外步骤**：

| 写接口 | Processor（模块：core） | 锁 | 本地原子写入（一次短事务） | 事务外步骤 |
| --- | --- | --- | --- | --- |
| 开始上传（新增 `accessLevel`） | `StartFileUploadProcessor` | 无（沿用既有） | 登记 Upload（冻结按 `(source, level)` 选出的路由、桶与策略修订） | 签发 PUT 授权 |
| 开始替换 `POST /internal/files/v1/files/{fileId}/replacements` | `StartFileReplacementProcessor` | TRY_LOCK `file:replace:{fileId}` | 登记替换 Upload；公开文件同时登记 `FILE_PUBLIC_SYNC` | 事务前读取存储最新版本（公开文件须仍对外提供当前版本）；事务后签发只签 Content-Type 的 PUT |
| 完成上传（替换分支） | `CompleteFileUploadProcessor` | LOCK（沿用既有 upload 锁） | 0 个新版本→409；>1 个→作废该替换（短事务）并 409；恰好 1 个→登记候选 `t_file_version` + Upload COMPLETED + 登记 `FILE_SCAN`（键 `{fileId}:v{n}`） | 事务前列出比基准新的版本并按准确版本打开、复核 |
| `FILE_SCAN` 补偿 | `FileScanHandler` → 既有扫描链 | 沿用 | 记录结果；候选通过时同一事务切换 CURRENT / SUPERSEDED 并刷新 `t_file_object` 镜像；不通过为 REJECTED | 读取版本字节、执行检查器 |
| `FILE_PUBLIC_SYNC` 补偿 | `FilePublicSyncHandler` → `SyncPublicFileProcessor` | TRY_LOCK `file:replace:{fileId}`（与开始替换互斥） | 无数据库写入（只判断） | 读取最新版本；需要时把当前版本服务端复制为最新版本 |
| 修改存储桶 `PUT .../buckets/{bucket}` | `UpdateStorageBucketProcessor` | 沿用实例/桶锁 | 更新 `public_base_url`、清空检查结果、版本 CAS | 无 |
| 存储桶检查（新增匿名核对） | `CheckStorageBucketProcessor` | 沿用 | 记录检查结果 | 探测对象写入、匿名读取、删除 |
| 写入路由新增 / 修改 / 删除（路径加 `/{accessLevel}`） | `Create/Update/DeleteStorageRouteProcessor` | 沿用（按来源系统，见偏差 4） | 路由行 CAS；校验同级别桶、锁定策略的 ACTIVE 修订存在 | 无 |
| 检查策略 8 个接口 | `CreateScanPolicy` / `CreateScanPolicyRevision` / `UpdateScanPolicyRevision` / `DiscardScanPolicyRevision` / `ActivateScanPolicyRevision` / `RetireScanPolicy` Processor（写）；`ListScanPolicies` / `GetScanPolicy`（读） | LOCK `file-scan-policy:{code}` | 修订行 + 检查要求行；激活时旧 ACTIVE→RETIRED（active_slot 唯一键兜底）；停用前核对路由引用 | 无 |
| 文件记录下载 `POST /storage-admin/v1/files/{fileId}/download` | `DownloadFileRecordProcessor` → `AuthorizeFileDownloadProcessor` | 无 | 签发成功后写 `FILE_DOWNLOAD_AUTHORIZE` 审计（`version_no`） | 签发下载地址 |

Context：文件模块沿用既有领域记录（`Replaceable`、`Downloadable`、`PublicSync`、`ReplacementStart`、`FileModels` / `UploadModels`），没有新建具名 Context 类，见偏差 9。

## 1. 改动文件清单

变基后共 209 个路径（117 个修改、92 个新增），完整列表见附录。按区域：

- api（30）：`InternalFileApi.startReplacement`；`StartFileUploadRequestBody.accessLevel`；`StartFileReplacementRequestBody`（`accessLevel`、`reasonCode` 为 `@Null`）；完成回执 / 元数据 / 下载响应新增字段；`FileStorageCheckResult` 增加 `PUBLIC_ACCESS_ALLOWED`、`PUBLIC_READ_FAILED`；`FileStorageManagementApi` 修改存储桶、路由新路径、检查策略 8 个、文件记录 3 个接口及其 DTO。
- file-document（85）：枚举 `FileAccessLevel`、`FileVersionStatus`（`core/enums`）；DataObject `FileVersion`、`FileDescription`、`FileScanPolicyRevision`、`FileScanPolicyRequirement`；Mapper / XML / Repository；`FileLifecycleService`、`FileUploadService`、`FileScanPolicyService`、`FileRecordService`、`FileStorageManagementService`；存储端口 `latest` / `restore` / `newerVersions` / `open(upload, version)` / `checkBucket(name, level, url)` 及 MinIO、S3 适配器；`AnonymousHttp`（OkHttp）、`LatestVersion`、`ProbeObject.anonymousResult`；`FileStorageRegistry.forWrite(source, level)`。
- audit-security（1）：`ConfigurationField` 增加 `ACCESS_LEVEL`、`PUBLIC_BASE_URL`、`REVISION_NO`、`ALLOWED_MIME_TYPES`、`MAX_SIZE_BYTES`。
- core（41）：上表 Processor、`FileScanHandler` 版本化、`FilePublicSyncHandler`、`FileStorageManagementSupport`。
- app（8）：`InternalFileController` / Converter、`FileStorageManagementController`（含 `@Audit`）、`FileScanPolicyConverter`、`FileRecordConverter`、`WorkflowAttachmentController`、`PersistenceConfiguration`。
- test（23）：新测试类 `InternalFilePublicApiTest`、`InternalFileReplacementApiTest`、`FilePublicSyncHandlerTest`、`FileStorageBucketAccessTest`、`FileScanPolicyManagementTest`、`FileRecordManagementTest`；更新 Builder / Mocker / TestMapper / 测试库 `init/ddl.sql` 与既有测试。
- 数据库与文档：`platform-support.pdma.json`、`platform-support-schema.sql`、`sql/changes/2026-10-22-file-public-bucket.sql`、`compensation-event-catalog.yaml`、`README.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`。
- 卡片范围外（见偏差 3）：`scripts/pdmaner/platform-support/manifests/02-file-document.mjs`、`manifest.mjs`、`platform-support-test-oracle/schema.oracle.mjs`、`platform-support-model.test.mjs`、`platform-file-upload-model.test.mjs`；`paytech/tools/arch-rules` 的 `table-ownership.yaml`、`TableOwnershipAutoTest`。
- 主工作树（按卡片允许）：本需求 `db/ddl.sql`、`db/dml.sql` 与本报告。
- 场景文档：`agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md` 增加 `t_file_version` 场景行，并给文件各表场景行补齐新增的 NOT NULL 字段（场景文档测试要求每张批准表都有一行）。
- **跨系统编译修复（team lead 授权，只做机械改动）**：
  - `card-test` `PlatformMocker`：`StoredFileResponseBody` 全参构造末尾补 `"PRIVATE", 1, null, null, null`。
  - `card-test` `MockPlatformFileController`：实现 `startReplacement`，与 `startUpload` 一样按意外调用抛出。
  - `client-master-test` `OnboardingOwnerMocker:79`：同上补构造参数。
  - `client-master-test` `MockPlatformInternalFileApi`：实现 `startReplacement`，按意外调用抛出。
  - `operations-console-bff` `BinLibraryFileClientImpl`：`accessLevel` 传 null（行为不变，默认 PRIVATE）。
  - `operations-console-bff` `PlatformFileStorageClientImpl`：`updateRoute` / `deleteRoute` 的新路径参数传 `"PRIVATE"`，保持旧行为。ops-bff 两处只为本工作树编译通过，**合并时以 B 卡版本为准**。
  - `client-console-bff` `ClientOnboardingUploadProcessor` 不需要改：它用无参构造加 setter，`accessLevel` 保持 null（Platform 缺省 PRIVATE）；该模块测试不直接构造被改的 DTO。
  - 没有运行 client-master、card 的测试（它们使用共享库）。

## 2. DDL / DML 执行记录（本机 `mor_platform`）

- 执行前核对：本机库已是 `42879401` 结构（`t_file_upload.owner_*` 与 `t_file_owner_ref` 已不存在，第二段删除 DDL 已执行），无须补执行。
- 备份：`/Users/kenny/Code-MoR-backups/pubbucket-20261022T174356Z/mor_platform-full.sql`（目录 0700、文件 600，含 73 个 CREATE TABLE）。第一次 mysqldump 因缺少 FLUSH TABLES 权限失败，加 `--set-gtid-purged=OFF` 后重试成功。
- 执行：`db/ddl.sql` 于 2026-10-22T17:44:22Z，`db/dml.sql` 于 2026-10-22T17:44:23Z。本机文件表 0 行，回填 0 行。
- 执行后核对：与 PDManer 模型对比，只剩两处原有注释差异（`t_file_object.source_system`、`t_file_upload.source_system` 缺少“取值来源”后缀，属上一需求的已知差异，不是本次引入）。
- 回退语句写在 `ddl.sql` 文件头（前提：没有 PUBLIC 桶或路由、没有 version_no>1 的版本、没有 replacement_no>0 的上传）。
- mor-test 未执行任何 SQL。变基后做第 5 步结构核对时，我用 `docker ps | grep mysql | head -1` 选容器，误选中 `mor-test-mysql-1`。那条命令是只读的 information_schema SELECT，以 `platform_app` 登录，结果 ERROR 1045 拒绝登录，没有任何语句执行。已当即报告 team lead；之后改为按名称 `mor-local-mysql-1` 核对。
- 变基后核对本机库（只读）：`mor_platform` 共 74 张表，已有 card 会话的 `t_country_region` 和本需求全部 DDL（`t_file_version` 与各新列）。本需求 DDL 不涉及 `t_country_region`，无须重跑。

## 3. 测试命令与结果

Java（在 `paytech/` 执行，`JAVA_HOME=jdk-25`，离线）：

```
./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -pl platform-support/paytech-platform-support-test -am test \
  -Dtest='ArchitectureAutoTest,FileAddressTest,FilePublicSyncHandlerTest,FileRecordManagementTest,FileScanHandlerTest,FileScanPolicyManagementTest,FileStorageBucketAccessTest,FileStorageConfigurationTest,FileStorageInstanceManagementTest,FileStoragePropertiesTest,FileStorageRouteManagementTest,InternalFileApiTest,InternalFilePublicApiTest,InternalFileReplacementApiTest,LocalMockEnvironmentInitializerTest,MinioFileStorageTest,S3FileStorageTest,WorkflowAttachmentApiTest,WorkflowAttachmentValidationApiTest,WorkflowInstanceApiTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

最终结果：**171 个，失败 0**。各类数量：FileAddress 22、FileStorageConfiguration 9、FileStorageProperties 6、LocalMockEnvironmentInitializer 9、S3FileStorage 15、MinioFileStorage 15、FileStorageInstanceManagement 13、FileStorageRouteManagement 6、FileScanPolicyManagement 4、FileStorageBucketAccess 5、FileRecordManagement 5、WorkflowInstanceApi 12、WorkflowAttachmentValidationApi 3、WorkflowAttachmentApi 4、InternalFilePublicApi 6、InternalFileReplacementApi 10、InternalFileApi 10、FilePublicSyncHandler 6、FileScanHandler 10、ArchitectureAuto 1。

- 过程中修复的问题：① `ArchitectureAutoTest` R15 拦截 `AnonymousHttp` 直接调用 JDK `HttpClient.send`，改用 file-document 已登记出口例外（O5）的 OkHttp 后通过；② `InternalFileReplacementApiTest.test_publicReplacementWaitsForRestore` 偶发 409 `CONCURRENT_REQUEST`：`FILE_PUBLIC_SYNC` 提交后立即执行一次，与测试的下一个请求争用替换锁。生产上这是正确行为（可重试），测试改为先等首次执行结束（`FileTestMapper.settledAttempts`），之后连续 2 次 22/22 通过；③ 文件记录详情的上传记录改按 `replacement_no DESC, upload_id DESC` 排序，原先按创建时间排序，同一时间创建的记录顺序不确定。
- 一次重跑中有 15 个用例在全局 BeforeEach 报“测试独占表存在数据”（`t_permission`、`t_role` 等）。原因是另一会话同时使用共享的本机测试库，不是本卡代码问题；单独重跑后通过。
- arch-rules：`./mvnw -o ... -pl tools/arch-rules -am test -Dtest=TableOwnershipAutoTest` 7/7 通过。
- PDManer Node 测试（`node --test scripts/pdmaner/<name>.test.mjs`）：与基线 `42879401`（git archive 到 scratchpad）逐项比较失败用例名，platform-file-upload-model、platform-support-model、platform-support-scenario-doc、platform-support-schema 的失败集合与基线**完全一致**（基线本来就有 17 / 3 / 9 个已知失败），pdmaner-canonical-paths 3/3。本次新增的 4 个失败已通过同步快照断言消除（upload 21 列、唯一键含 `replacement_no`、file_document 15 表、总数 73、Hash 白名单增加 `file_version.content_hash`）。
- 全仓 `test-compile`：变基并完成第 1 节的跨系统修复后，`./mvnw -o -q -fae test-compile` 通过（exit 0）。

**最终结果（变基到 `22ede435` 后，加入 AC-14 专项测试，2026-10-22）**：开跑前查询本机 `mor_platform` 的守卫表（`t_permission`、`t_role`、`t_jwt_signing_key`、`t_role_permission`），合计 0 行，没有其他会话在跑。同一条命令加 `ScanPolicyRevisionFreezeTest` 共 21 个类串行执行：**181 个，失败 0**。各类数量：FileAddress 22、FileStorageConfiguration 9、FileStorageProperties 6、LocalMockEnvironmentInitializer 9、S3FileStorage 17、MinioFileStorage 17、FileStorageInstanceManagement 13、FileStorageRouteManagement 6、FileScanPolicyManagement 4、FileStorageBucketAccess 5、FileRecordManagement 5、WorkflowInstanceApi 12、WorkflowAttachmentValidationApi 3、WorkflowAttachmentApi 4、ScanPolicyRevisionFreeze 2、InternalFilePublicApi 6、InternalFileReplacementApi 10、InternalFileApi 12、FilePublicSyncHandler 6、FileScanHandler 12、ArchitectureAuto 1。main 新增的 CSV 用例使 Minio、S3、InternalFileApi、FileScanHandler 各多 2 个。

**变基到 `22ede435` 之后（中间过程）**：
- 同一组 20 个类：179 个，178 通过。唯一失败是 `FileScanPolicyManagementTest.test_invalidParameter`：它断言 text/csv 不受支持，而 main 的格式检查器已能识别 CSV。改用 image/gif 后该类 4/4 通过。
- `TableOwnershipAutoTest` 7/7。
- PDManer Node 测试与 `22ede435` 基线逐项比较，失败集合完全一致；原先新增的 scenario-doc 失败已通过补场景行消除。
- 另跑了 main 新增的 `CountryRegionQueryApiTest` 与 `GatewayChannelCallbackRouteTest` 作回归。`CountryRegionQueryApiTest` 两次都失败：第一次是 `t_access_tenant` 主键冲突，第二次是全局 BeforeEach 报“测试独占表存在数据”（`t_permission`、`t_role`、`t_jwt_signing_key`）。这说明另一会话正在用共享测试库，失败与本卡改动无关；已停止跑数据库测试并报告 team lead，待其放行后再重跑。

## 4. WRITE_API_EXAMPLE 第 11 节逐项核对

- **API 与 Body 最小合同**：新 DTO 都是带字段注释与 Hibernate Validator 的 class（如 `StartFileReplacementRequestBody` 的 `@Null accessLevel/reasonCode`，`UpdateStorageBucketRequestBody.expectedVersion` 为 `@NotNull`）；补偿入口在 Handler 中解析并校验事件身份，身份损坏返回终局失败（`FilePublicSyncHandlerTest.test_invalidParameter`）。
- **Controller / Converter 只做适配**：`InternalFileController`、`FileStorageManagementController` 只做转换并委托 Processor；写接口的 Processor 位于 core 模块，主方法按“校验 → 锁 → 读取 → 短事务 → 事务外”顺序展开。
- **Processor 只经 Service / Repository 访问数据**：没有直接调用 Mapper；同源与状态核对在 `FileLifecycleService.replaceable` 中完成。
- **Context**：未新建具名 Context，沿用文件模块既有领域记录，见偏差 9。
- **Service 不互调**：`StartFileReplacementProcessor` 组合 `FileLifecycleService`、`FileUploadService`、`FileStorageRegistry` 与 `CompensationEventRegistrar`；Service 之间没有互相调用。
- **事务**：`FileTransactions.write` 包住的只有数据库写入与补偿登记；存储 `latest`、`authorize`、`newerVersions`、`restore` 都在事务外。开始替换时存储客户端在短事务期间保持打开，但事务内没有 I/O（偏差 6）。
- **Repository 与并发保护**：按表维护；版本切换与路由、桶、策略修订都用条件 SQL 加影响行数判断；`uk_upload_storage_target` 含 `replacement_no`、策略 `active_slot` 唯一，作为数据库兜底。
- **锁针对实际资源**：替换锁锁定文件，开始替换与公开同步共用；策略锁锁定策略代码。都用 Foundation `AbstractLockProcessor` 的 TRY_LOCK / LOCK，没有叠加其他锁机制。
- **重放**：开始替换没有业务操作身份，重复请求被“已有未结束的替换”拦截并返回 409；完成上传沿用既有按 uploadId 的幂等恢复。补偿任务键 `{fileId}:{uploadId}` 与 `{fileId}:v{n}` 保证重复登记不重复执行。
- **Gateway 与业务防重**：没有使用 `Idempotency-Key`，也没有新增命令幂等组件。
- **View**：文件记录列表与详情使用 `FileRecordView` / `FileRecordDetailView` 多表查询；写接口没有新增 View；DataObject 不跨出 HTTP 边界。
- **公共包与配置**：复用 `FileDisplayName`、审计注解与补偿组件；没有生产 Mock 或伪造成功。
- **验证**：见第 3 节，覆盖首次处理、跨来源 404、状态冲突 409、并发开始替换只有一个成功、多余 PUT 作废、过期、候选检查中与被拒绝、公开恢复、锁占用重试、权限 403、下载审计与失败不写审计、回填式 v1 行的存量行为。未覆盖的项见第 6 节。

## 5. 与 common.md / technique.md 的偏差

1. 枚举 `FileAccessLevel`、`FileVersionStatus` 按卡片放在 file-document 的 `core/enums`。开发规范倾向把线上编码枚举放在 `api/enums`，API 字段仍用字符串编码。
2. `StartFileReplacementRequestBody` 增加 `@Null` 的 `accessLevel`、`reasonCode`，以满足 AC-12 的“改级别 422”。
3. 改动了卡片列出范围外的文件：PDManer 生成脚本与快照断言、arch-rules 表归属、audit-security `ConfigurationField`。
4. 路由锁键仍按来源系统（比 `(source, level)` 更粗），两个级别的路由维护会互相串行，不影响正确性。
5. 替换上传期限取 `(source, level)` 路由的上传期限，没有路由时为 900 秒。
6. 开始替换时，存储客户端在短事务期间保持打开，事务内没有存储 I/O。
7. 匿名读取的信任证书取实例私有证书，未登记时使用 JDK 默认信任库；实现改用 OkHttp 以满足 R15。
8. 文件描述的 `uploaderAccountId` 取首次上传人，`displayName` 取当前版本的上传；详情中每个版本另带各自的上传人。
9. 没有新建具名 Context 类，沿用文件模块既有的领域记录写法；如需对齐 Payment 式 Context，应单独立项迁移。
10. 测试目录中 `FILE_RECORD_DOWNLOAD` 的动作暂按 `DOWNLOAD` 登记，正式权限行由 B 卡的 block 134 DML 决定。
11. 文件记录详情的上传记录按替换序号倒序（原 DTO 注释写的是按创建时间倒序，已改注释），时间相同时顺序稳定。
12. `PLATFORM_SUPPORT_DATABASE.md` 第 3、4.4 节的表数和 Hash 数是历史统计，没有重算，只补了本次新增项的说明。

## 6. 遗留项

1. 跨系统编译破坏已按 team lead 授权在本工作树修复（见第 1 节）。ops-bff 两处合并时以 B 卡版本为准。变基后的 Platform 回归里，`CountryRegionQueryApiTest` 因共享测试库被占用未能确认，需待放行后重跑。
2. **残余风险**：公开桶被外部改写，且没有待执行的同步任务时（最新版本与当前版本不一致），开始替换会一直返回 409，需要人工恢复（如重新登记同步或人工复制）。
3. **AC-14 已有专项测试** `ScanPolicyRevisionFreezeTest`（`auto/ext/http/files`），策略、修订、路由都经运营接口维护：
   - 修订 1 只允许 PDF。在修订 1 下开始一个 PDF 上传，随后新建、修改并激活修订 2（只允许 PNG），修订 1 变为 RETIRED。
   - 再完成这个 PDF 上传：上传、v1 版本与检查结果都冻结在修订 1，并按修订 1 检查通过。
   - 之后新开始的 PDF 上传按修订 2 返回 422；新的 PNG 上传冻结修订 2，并按修订 2 检查通过。
   - 被路由引用的策略停用返回 400，修订保持不变。
4. **部署前提**：公开桶的桶策略需允许匿名 `s3:GetObject`；Platform 账号需要 `ListBucketVersions`、`GetObjectVersion` 与 CopyObject 权限；CORS 需允许不带 `If-None-Match` 的 PUT。真实 S3 或 CDN 环境没有验证过（本地只验证了 MinIO，S3 适配器通过 MinIO 路径风格验证）。
5. mor-test 的 DDL / DML 与发布未执行。block 134 权限、路由的 SQL 由 B 卡负责。

## 7. 变基到 `22ede435`（team lead 要求）

- 备份：`~/Code-MoR-backups/pubbucket-A-20261022T193316Z/`（`pubbucket-A.patch` 为 `git diff HEAD --binary`；`untracked.tar` 与 `untracked.txt` 为 91 个未跟踪文件）。没有用 stash，没有提交。只在本工作树执行 `git reset --hard 22ede435`，恢复未跟踪文件后 `git apply --3way`；最后 `git reset` 把索引还原为 HEAD。
- 未跟踪文件与 main 新增文件没有重名。15 个重叠文件中 10 个干净合入，5 个冲突，处理如下：
  - `platform-support.pdma.json`：以 main 的模型为底，重新拼入本需求的 6 个文件实体（`t_file_version` 放在 `t_file_scan_result` 之后）和 `file_document` 图。核对过：拼接结果与当前生成器输出相比，只有 main 自己的 `audit_security` 图不同。
  - `TableOwnershipAutoTest`：合并两边的加项（main 的 +2 +1 +4，加本卡 +1），`mor_platform` 64 张。
  - `FileMocker`：保留 main 的 CSV 允许类型，统一用 `MIME_RULES`（含 text/csv）。
  - `FileScanHandlerTest` 类注释：合并两边的说明。
  - `InternalFileApiTest`：保留 main 的按文件名、类型上传的重载，补上 `accessLevel=null` 参数。
- 冲突以外的同步修改：
  - `platform-support-schema.sql` 表头改为 64 张（main 已从 62 改到 63）。
  - `schema.oracle.mjs` 总数改为 74。
  - 用生成脚本重新生成 `table-ownership.yaml`（只改了表头注释的数字）。
  - 测试库 `init/ddl.sql` 已同时包含 `t_country_region` 与 `t_file_version`（64 个 CREATE TABLE）。
- 支持的类型：`StoredContentType` 新增 `PDF_TYPE`、`PNG_TYPE`、`JPEG_TYPE` 常量，`detect` 返回值与 `SUPPORTED`（PDF、PNG、JPEG、CSV）共用这些常量；`FormatFileScanner.SUPPORTED_MIME_TYPES` 引用 `SUPPORTED`，不另写一份列表。
- 发布兼容：`FileUploadCompletionResponseBody.versionNo`、`StoredFileResponseBody.versionNo` 由 `int` 改为 `Integer`。这是在既有响应类上新增的仅有两个基本类型字段，其他新增字段本来就是 String 或 Integer，新接口的新类不受影响。这样消费方可以先于 Platform 升级。Platform 仍然总是填写这两个字段。

## 附录：完整改动路径

（`M` 修改，`A` 新增；`<模块>(main|test):` 前缀省略了 `paytech/platform-support/paytech-platform-support-<模块>/src/<main|test>/java/com/paytech/platform/support/`）

```
M  agent-docs/database/PLATFORM_SUPPORT_DATABASE.md
M  agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md
M  paytech/card/paytech-card-test/src/test/java/com/paytech/card/test/mock/MockPlatformFileController.java
M  paytech/card/paytech-card-test/src/test/java/com/paytech/card/test/mocker/PlatformMocker.java
M  paytech/client-master/paytech-client-master-test/src/test/java/com/paytech/client/master/test/mock/external/onboarding/MockPlatformInternalFileApi.java
M  paytech/client-master/paytech-client-master-test/src/test/java/com/paytech/client/master/test/mocker/onboarding/OnboardingOwnerMocker.java
M  paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/BinLibraryFileClientImpl.java
M  paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/PlatformFileStorageClientImpl.java
M  paytech/platform-support/README.md
M  paytech/platform-support/compensation-event-catalog.yaml
M  paytech/platform-support/docs/database/platform-support.pdma.json
M  paytech/platform-support/docs/database/sql/platform-support-schema.sql
M  api(main): api/enums/FileStorageCheckResult.java
M  api(main): api/files/InternalFileApi.java
M  api(main): api/files/request/StartFileUploadRequestBody.java
M  api(main): api/files/response/FileDownloadResponseBody.java
M  api(main): api/files/response/FileUploadCompletionResponseBody.java
M  api(main): api/files/response/StoredFileResponseBody.java
M  api(main): api/filestorage/FileStorageManagementApi.java
M  api(main): api/filestorage/request/CreateStorageRouteRequestBody.java
M  api(main): api/filestorage/request/RegisterStorageBucketRequestBody.java
M  api(main): api/filestorage/response/StorageBucket.java
M  api(main): api/filestorage/response/StorageRouteResponseBody.java
M  app(main): configuration/PersistenceConfiguration.java
M  app(main): ext/rest/files/InternalFileController.java
M  app(main): ext/rest/files/InternalFileConverter.java
M  app(main): ext/rest/filestorage/FileStorageManagementController.java
M  app(main): ext/rest/filestorage/FileStorageManagementConverter.java
M  app(main): ext/rest/workflow/WorkflowAttachmentController.java
M  audit-security(main): auditsecurity/domain/audit/OperationsAuditFact.java
M  core(main): service/handler/FileScanHandler.java
M  core(main): service/processor/files/AuthorizeFileDownloadProcessor.java
M  core(main): service/processor/files/CompleteFileUploadProcessor.java
M  core(main): service/processor/files/StartFileUploadProcessor.java
M  core(main): service/processor/files/request/AuthorizeFileDownloadRequest.java
M  core(main): service/processor/files/request/StartFileUploadRequest.java
M  core(main): service/processor/files/response/CompleteFileUploadResponse.java
M  core(main): service/processor/filestorage/CreateStorageRouteProcessor.java
M  core(main): service/processor/filestorage/DeleteStorageRouteProcessor.java
M  core(main): service/processor/filestorage/FileStorageManagementSupport.java
M  core(main): service/processor/filestorage/RegisterStorageBucketProcessor.java
M  core(main): service/processor/filestorage/UpdateStorageRouteProcessor.java
M  core(main): service/processor/filestorage/request/DeleteStorageRouteRequest.java
M  core(main): service/processor/filestorage/request/RegisterStorageBucketRequest.java
M  core(main): service/processor/filestorage/request/SaveStorageRouteRequest.java
M  core(main): service/processor/workflowattachment/StartWorkflowAttachmentUploadProcessor.java
M  file-document(main): filedocument/core/dal/dataobject/CompletedUploadFile.java
M  file-document(main): filedocument/core/dal/dataobject/FileObject.java
M  file-document(main): filedocument/core/dal/dataobject/FileStorageBucket.java
M  file-document(main): filedocument/core/dal/dataobject/FileStorageRoute.java
M  file-document(main): filedocument/core/dal/dataobject/FileUpload.java
M  file-document(main): filedocument/core/dal/mapper/FileLifecycleMapper.java
M  file-document(main): filedocument/core/dal/mapper/FileStorageBucketMapper.java
M  file-document(main): filedocument/core/dal/mapper/FileStorageRouteMapper.java
M  file-document(main): filedocument/core/dal/mapper/FileUploadMapper.java
M  file-document(main): filedocument/domain/file/FileModels.java
M  file-document(main): filedocument/domain/file/StoredFileView.java
M  file-document(main): filedocument/domain/file/UploadModels.java
M  file-document(main): filedocument/domain/storage/FileStorageBucketView.java
M  file-document(main): filedocument/domain/storage/FileStorageCatalog.java
M  file-document(main): filedocument/domain/storage/FileStorageChange.java
M  file-document(main): filedocument/domain/storage/FileStorageField.java
M  file-document(main): filedocument/domain/storage/FileStorageRouteView.java
M  file-document(main): filedocument/domain/storage/RegisterStorageBucketCommand.java
M  file-document(main): filedocument/domain/storage/SaveStorageRouteCommand.java
M  file-document(main): filedocument/domainservice/repository/FileLifecycleRepository.java
M  file-document(main): filedocument/domainservice/repository/FileStorageBucketRepository.java
M  file-document(main): filedocument/domainservice/repository/FileStorageRouteRepository.java
M  file-document(main): filedocument/domainservice/repository/FileUploadRepository.java
M  file-document(main): filedocument/domainservice/repository/impl/FileLifecycleRepositoryImpl.java
M  file-document(main): filedocument/domainservice/repository/impl/FileStorageBucketRepositoryImpl.java
M  file-document(main): filedocument/domainservice/repository/impl/FileStorageRouteRepositoryImpl.java
M  file-document(main): filedocument/domainservice/repository/impl/FileUploadRepositoryImpl.java
M  file-document(main): filedocument/domainservice/service/FileLifecycleService.java
M  file-document(main): filedocument/domainservice/service/FileStorageManagementService.java
M  file-document(main): filedocument/domainservice/service/FileUploadService.java
M  file-document(main): filedocument/domainservice/service/impl/FileLifecycleServiceImpl.java
M  file-document(main): filedocument/domainservice/service/impl/FileStorageManagementServiceImpl.java
M  file-document(main): filedocument/domainservice/service/impl/FileUploadServiceImpl.java
M  file-document(main): filedocument/integration/scanning/FormatFileScanner.java
M  file-document(main): filedocument/integration/storage/CompletedUploadStorage.java
M  file-document(main): filedocument/integration/storage/CompletedUploadVerifier.java
M  file-document(main): filedocument/integration/storage/FileObjectStorage.java
M  file-document(main): filedocument/integration/storage/FileStorageRegistry.java
M  file-document(main): filedocument/integration/storage/ProbeObject.java
M  file-document(main): filedocument/integration/storage/StoredContentType.java
M  file-document(main): filedocument/integration/storage/UploadStorage.java
M  file-document(main): filedocument/integration/storage/minio/MinioFileStorage.java
M  file-document(main): filedocument/integration/storage/s3/S3FileStorage.java
M  file-document(main): filedocument/service/processor/CheckStorageBucketProcessor.java
M  file-document(main res): mapper/file-document/FileLifecycleMapper.xml
M  file-document(main res): mapper/file-document/FileStorageBucketMapper.xml
M  file-document(main res): mapper/file-document/FileStorageRouteMapper.xml
M  file-document(main res): mapper/file-document/FileUploadMapper.xml
M  test(test): auto/ext/eventhandler/files/FileScanHandlerTest.java
M  test(test): auto/ext/http/files/InternalFileApiTest.java
M  test(test): auto/ext/http/filestorage/FileStorageInstanceManagementTest.java
M  test(test): auto/ext/http/filestorage/FileStorageRouteManagementTest.java
M  test(test): auto/integration/storage/MinioFileStorageTest.java
M  test(test): auto/integration/storage/S3FileStorageTest.java
M  test(test): test/base/FileStorageAdapterTestBase.java
M  test(test): test/builder/FileBuilder.java
M  test(test): test/builder/OperationsCatalog.java
M  test(test): test/dal/FileStorageTestMapper.java
M  test(test): test/dal/FileTestMapper.java
M  test(test): test/mocker/local/FileMocker.java
M  test(test): test/mocker/local/PlatformDataCleaner.java
M  test(test): test/mocker/local/StorageInstanceMocker.java
M  test(test): test/mocker/local/TempBucketMocker.java
M  test(test res): init/ddl.sql
M  paytech/tools/arch-rules/src/main/resources/table-ownership.yaml
M  paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java
M  scripts/pdmaner/platform-file-upload-model.test.mjs
M  scripts/pdmaner/platform-support-model.test.mjs
M  scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs
M  scripts/pdmaner/platform-support/manifest.mjs
M  scripts/pdmaner/platform-support/manifests/02-file-document.mjs
A  paytech/platform-support/docs/database/sql/changes/2026-10-22-file-public-bucket.sql
A  api(main): api/files/request/StartFileReplacementRequestBody.java
A  api(main): api/filestorage/request/ActivateScanPolicyRevisionRequestBody.java
A  api(main): api/filestorage/request/CreateScanPolicyRequestBody.java
A  api(main): api/filestorage/request/CreateScanPolicyRevisionRequestBody.java
A  api(main): api/filestorage/request/DiscardScanPolicyRevisionRequestBody.java
A  api(main): api/filestorage/request/RetireScanPolicyRequestBody.java
A  api(main): api/filestorage/request/UpdateScanPolicyRevisionRequestBody.java
A  api(main): api/filestorage/request/UpdateStorageBucketRequestBody.java
A  api(main): api/filestorage/response/FileRecordDetailResponseBody.java
A  api(main): api/filestorage/response/FileRecordSummary.java
A  api(main): api/filestorage/response/FileScanRecord.java
A  api(main): api/filestorage/response/FileUploadRecord.java
A  api(main): api/filestorage/response/FileVersionRecord.java
A  api(main): api/filestorage/response/ScanPolicyDetailResponseBody.java
A  api(main): api/filestorage/response/ScanPolicyListResponseBody.java
A  api(main): api/filestorage/response/ScanPolicyRequirement.java
A  api(main): api/filestorage/response/ScanPolicyRevision.java
A  api(main): api/filestorage/response/ScanPolicyRouteRef.java
A  api(main): api/filestorage/response/ScanPolicySummary.java
A  app(main): ext/rest/filestorage/FileRecordConverter.java
A  app(main): ext/rest/filestorage/FileScanPolicyConverter.java
A  core(main): service/handler/FilePublicSyncHandler.java
A  core(main): service/processor/files/StartFileReplacementProcessor.java
A  core(main): service/processor/files/SyncPublicFileProcessor.java
A  core(main): service/processor/files/request/StartFileReplacementRequest.java
A  core(main): service/processor/files/request/SyncPublicFileRequest.java
A  core(main): service/processor/files/response/SyncPublicFileResponse.java
A  core(main): service/processor/filestorage/ActivateScanPolicyRevisionProcessor.java
A  core(main): service/processor/filestorage/CreateScanPolicyProcessor.java
A  core(main): service/processor/filestorage/CreateScanPolicyRevisionProcessor.java
A  core(main): service/processor/filestorage/DiscardScanPolicyRevisionProcessor.java
A  core(main): service/processor/filestorage/DownloadFileRecordProcessor.java
A  core(main): service/processor/filestorage/GetFileRecordProcessor.java
A  core(main): service/processor/filestorage/GetScanPolicyProcessor.java
A  core(main): service/processor/filestorage/ListFileRecordsProcessor.java
A  core(main): service/processor/filestorage/ListScanPoliciesProcessor.java
A  core(main): service/processor/filestorage/RetireScanPolicyProcessor.java
A  core(main): service/processor/filestorage/UpdateScanPolicyRevisionProcessor.java
A  core(main): service/processor/filestorage/UpdateStorageBucketProcessor.java
A  core(main): service/processor/filestorage/request/ChangeScanPolicyRevisionRequest.java
A  core(main): service/processor/filestorage/request/FileRecordRequest.java
A  core(main): service/processor/filestorage/request/ListFileRecordsRequest.java
A  core(main): service/processor/filestorage/request/SaveScanPolicyRequest.java
A  core(main): service/processor/filestorage/request/ScanPolicyQueryRequest.java
A  core(main): service/processor/filestorage/request/UpdateStorageBucketRequest.java
A  core(main): service/processor/filestorage/response/ScanPolicyCatalog.java
A  file-document(main): filedocument/core/dal/dataobject/FileDescription.java
A  file-document(main): filedocument/core/dal/dataobject/FileScanPolicyRequirement.java
A  file-document(main): filedocument/core/dal/dataobject/FileScanPolicyRevision.java
A  file-document(main): filedocument/core/dal/dataobject/FileVersion.java
A  file-document(main): filedocument/core/dal/mapper/FileRecordMapper.java
A  file-document(main): filedocument/core/dal/mapper/FileScanPolicyMapper.java
A  file-document(main): filedocument/core/dal/mapper/FileScanRequirementMapper.java
A  file-document(main): filedocument/core/dal/mapper/FileVersionMapper.java
A  file-document(main): filedocument/core/enums/FileAccessLevel.java
A  file-document(main): filedocument/core/enums/FileVersionStatus.java
A  file-document(main): filedocument/domain/file/FileRecordDetailView.java
A  file-document(main): filedocument/domain/file/FileRecordQuery.java
A  file-document(main): filedocument/domain/file/FileRecordView.java
A  file-document(main): filedocument/domain/file/PublicFileUrl.java
A  file-document(main): filedocument/domain/storage/ChangeScanPolicyRevisionCommand.java
A  file-document(main): filedocument/domain/storage/FileScanPolicyDetailView.java
A  file-document(main): filedocument/domain/storage/FileScanPolicyView.java
A  file-document(main): filedocument/domain/storage/SaveScanPolicyRevisionCommand.java
A  file-document(main): filedocument/domain/storage/UpdateStorageBucketCommand.java
A  file-document(main): filedocument/domainservice/repository/FileRecordRepository.java
A  file-document(main): filedocument/domainservice/repository/FileScanPolicyRepository.java
A  file-document(main): filedocument/domainservice/repository/FileScanRequirementRepository.java
A  file-document(main): filedocument/domainservice/repository/FileVersionRepository.java
A  file-document(main): filedocument/domainservice/repository/impl/FileRecordRepositoryImpl.java
A  file-document(main): filedocument/domainservice/repository/impl/FileScanPolicyRepositoryImpl.java
A  file-document(main): filedocument/domainservice/repository/impl/FileScanRequirementRepositoryImpl.java
A  file-document(main): filedocument/domainservice/repository/impl/FileVersionRepositoryImpl.java
A  file-document(main): filedocument/domainservice/service/FileRecordService.java
A  file-document(main): filedocument/domainservice/service/FileScanPolicyService.java
A  file-document(main): filedocument/domainservice/service/impl/FileRecordServiceImpl.java
A  file-document(main): filedocument/domainservice/service/impl/FileScanPolicyServiceImpl.java
A  file-document(main): filedocument/integration/storage/AnonymousHttp.java
A  file-document(main): filedocument/integration/storage/LatestVersion.java
A  file-document(main res): mapper/file-document/FileRecordMapper.xml
A  file-document(main res): mapper/file-document/FileScanPolicyMapper.xml
A  file-document(main res): mapper/file-document/FileScanRequirementMapper.xml
A  file-document(main res): mapper/file-document/FileVersionMapper.xml
A  test(test): auto/ext/eventhandler/files/FilePublicSyncHandlerTest.java
A  test(test): auto/ext/http/files/InternalFilePublicApiTest.java
A  test(test): auto/ext/http/files/InternalFileReplacementApiTest.java
A  test(test): auto/ext/http/filestorage/FileRecordManagementTest.java
A  test(test): auto/ext/http/filestorage/FileScanPolicyManagementTest.java
A  test(test): auto/ext/http/filestorage/FileStorageBucketAccessTest.java
A  test(test): test/base/InternalFileApiTestBase.java
A  test(test): auto/ext/http/files/ScanPolicyRevisionFreezeTest.java
```

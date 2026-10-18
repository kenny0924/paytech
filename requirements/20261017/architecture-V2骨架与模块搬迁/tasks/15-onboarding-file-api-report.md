# 第 15 步报告 · 入驻文件两条 API 按写接口规范重构

- 分支：`refactor/step-15-onboarding-file-api`（起点 `refactor-main@3b31406a`，含任务卡提交 `5e8d5fe3`）；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step15`。
- 范围：`OnboardingFileApi`（start / complete / read / download）与 `OnboardingFileVerificationApi`（verify）的 API 模型、Controller、Processor、测试；消费方 client-console-bff、client-master 只改访问方式。
- 结论：五个入口各由一个 Processor 处理，Controller 只做转换；HTTP 路径、Header、JSON 字段、错误码、HTTP 状态和事务顺序保持不变。新增 5 个 HTTP 入口测试类共 78 例，连同既有入驻文件测试 100 例全部通过；三模块 `verify` 全绿（Platform 876、client-console-bff 80、client-master 58）。
- 本步未执行 SQL，未操作容器，未调用 Superpowers skill，未推送。

## 1. 已读与开工说明

已读：AGENTS.md（Start here 第 1 条、Change discipline）、`DEVELOPMENT_CONVENTIONS.md` 第 2、4、5（5.1–5.4）、7 节，`JAVA_PROJECT_STRUCTURE.md` 第 4、5 节，`WRITE_API_EXAMPLE.md` 全文，`UNIT_TEST_GUIDE.md`、`UNIT_TEST_EXAMPLE.md`，`decisions/2026-09-16-payment-based-coding-conventions.md`；Foundation `AbstractProcessor`、`AbstractLockProcessor`、`ResourceLockKeyResolver`；参照实现 `OnboardingFileCompletionProcessor`、`OnboardingFileLocks`、risk-decision `ChangeSubjectTagStatusProcessor` / `DecisionResourceLocks`。

| 入口 | Processor（core `service/processor`） | 本地原子写入 | 事务外步骤 |
|---|---|---|---|
| `POST /uploads` | `StartOnboardingUploadProcessor`（AbstractLockProcessor） | 一个写事务：锁账号与唯一入驻 Tenant → 读存储配置 → 按当前策略登记 Upload | 会话查询（只读事务）、CCT 授权、存储签发 PUT |
| `POST /uploads/{id}/completion` | `OnboardingFileCompletionProcessor`（未改，参照实现） | 第一段锁定校验、第二段登记文件 + 扫描补偿任务 | CCT 读取 / 重新授权、存储字节核验 |
| `GET /files/{id}` | `ReadOnboardingFileProcessor`（AbstractProcessor） | 无写入 | 两次只读快照之间的 CCT 读取 |
| `POST /files/{id}/download` | `DownloadOnboardingFileProcessor`（AbstractProcessor） | 无写入 | CCT 读取（签发前后各一次）、存储签发 GET |
| `GET /internal/.../verification` | `VerifyOnboardingFileProcessor`（AbstractProcessor） | 无写入，单个只读快照 | 无（不回调 CCT） |

## 2. 改动摘要

**API 模型（platform-support-api）**
- 删除 record 容器 `OnboardingFileModels`，拆成 `request/StartUploadRequestBody` 与 `response/` 下 6 个普通 class（Lombok Getter/Setter/无参/全参构造，字段中文 JavaDoc）。脱敏 `toString` 全部保留（含 `UploadAuthorization`）。
- 顶层类型按命名规则调整：`FileCompletionReceipt` → `UploadCompletionResponseBody`；`VerificationResponseBody` → `FileVerificationResponseBody`（同一 JAR 已有 `authentication.response.VerificationResponseBody`）。JSON 字段名不变。
- 两个 API 接口方法补 `@param/@return`。
- `ConfigurationManagementRequestConfiguration` 的严格 JSON 解析前缀从 `OnboardingFileModels$` 改为 `onboardingfiles.request.`，未知字段、重复字段仍拒绝（`StartOnboardingUploadTest.test_unknownField` 覆盖）。

**start**
- `OnboardingUploadProcessor` 改名 `StartOnboardingUploadProcessor`，继承 `AbstractLockProcessor`，内部 `StartOnboardingUploadRequest` / `StartOnboardingUploadResponse`。
- Controller 原有的文件名长度、归一化后展示名、mimeType 格式检查移入 Request 注解（`@Pattern`、`@Size`、`@NotBlank`）与 `Processor.validate`；归一化规则是 Request 的纯方法 `displayName()`。
- 主流程四个阶段注释；存储配置合法性、授权合法性拆成 `validStorageSettings`、`validGrant`；存储适配器异常转 503 时保留 Cause。

**read / download**
- 原 `OnboardingFileReadProcessor` 的三个方法拆为三个 Processor。read 与 download 共用的两次快照授权放在包内抽象类 `AbstractOnboardingFileAccessProcessor`（`authorized`、`sameOrigin`、`downloadable`、`requireDownloadable`），共用请求 `OnboardingFileAccessRequest`。
- download 的签发结果检查拆成 `storageFor`、`requestDownload`、`requireValidGrant`、`safeUrl`，顺序与原实现一致。

**verify**
- 独立 `VerifyOnboardingFileProcessor` + `VerifyOnboardingFileRequest/Response`。`X-Caller-Service` 校验留在 `OnboardingFileVerificationController`；purpose 合法性与 ID 解析在 Converter，顺序与原实现一致（caller → purpose → 各 ID）。purpose 只校验、不影响核验范围，因此不进入内部 Request。

**Controller / Converter（platform-support-app）**
- 新增 `OnboardingFileConverter`：会话 Header 非规范正整数仍返回 `CLIENT_SESSION_INVALID`，两类 ID 错误提示保持原文案。
- 两个 Controller 只有“转换 → Processor.process → 转换”，不再注入 `Validator`。

**消费方**
- client-master：`ClientMaterialAuthorityClient` 与测试 Mock 改用 `FileVerificationResponseBody` 的 getter。
- client-console-bff：三处源码改用新类型与 getter；两个 pom 中 `com.paytech:platform-support-api`（本地仓库里的旧工程 JAR）改为 Reactor 内的 `paytech-platform-support-api`，否则 BFF 无法看到新类型。见“待确认”第 1 项。

## 3. WRITE_API_EXAMPLE 第 11 节逐项核对

| 核对项 | 结果 |
|---|---|
| API 与 Body 定义最小合同，class、字段注释、Hibernate Validator；非 HTTP 入口有真实校验触发点 | 是。Body 为 class 并保留约束注解，BFF 入口 `@Valid` 触发；Platform 入口不加 `@Valid`（原因见待确认第 2 项），等价约束在内部 Request 上由 `AbstractProcessor.validateBasic` 触发，Path/Header ID 由 Converter 解析。 |
| Controller/Converter 仅适配；写操作 Processor 位于所属模块，主方法显示原请求提前返回与新请求完整阶段 | 是。Processor 位于 core `service/processor`；start 每次新建 Upload，没有业务操作身份，所以没有原请求分支；complete 的“已完成返回原回执”分支保留在第一段事务。 |
| Processor 查询本模块 Repository，完成归属、原输入和新请求资格检查；不调用 Mapper 或逐表写入 | 是。归属与资格经 `FileLifecycleService`、`ClientOnboardingAccessService`、`CustomerAuthenticationService` 完成，写入只经 `FileUploadService.create`。这些 Service 属 file-document / access-control 模块，是既有公开能力，本步未改。 |
| 同一个具名 Context 持有本模块 DataObject，构造完整；无同义 ID、执行标志、Input/View/Snapshot | 不适用。五个入口都没有需要贯穿多个阶段的 Context，没有新增 Context；跨模块只用 Owner 发布的 `Upload`、`Owned`、`Grant` 等领域模型。 |
| Service 不重复查询已确认关系，不调用其他 Service、Processor 或外部依赖；多个 Service 由 Processor 组合 | 是（未改 Service）。start 的写事务由 Processor 组合 `lockUploadTenant` 与 `files.create`。 |
| 本地原子写入及事务位置明确，事务内没有直接或间接外部调用、Processor 调用；保存失败不推进 | 是。见第 1 节表格；start 登记事务失败时不签发授权（`test_businessRuleViolation` 断言无 Upload）。 |
| Repository 按表维护，Context 不进入持久化层；条件 SQL 与影响行数保护并发，成功后同步原对象 | 不适用。本步未改 Repository/Mapper；complete 的 `completeUpload` 行数检查保持。 |
| 锁针对实际资源，模式与同资源其他入口一致；没有叠加锁或用 Redis 替代数据库保护 | 是。见第 4 节；账号行锁、Upload 当前读与状态 CAS 保持。 |
| 原请求比较通过后直接响应，终态/过期重放不受首次资格拦截；不同命令身份不被误当重试 | 是。complete 先查已完成再检查 canUpload / 有效期（`test_repeatedCompletion`）；start 无业务身份，每次新建。 |
| 已区分 Gateway 请求重放与业务防重；业务模型不携带 Idempotency-Key | 是。没有 Idempotency-Key 参数、字段或旧组件依赖。 |
| 多表组合查询使用 View，写接口一般不增加 View；边界没有泄露 DataObject | 是。没有新增 View；HTTP 边界只用 ResponseBody。 |
| 复用公共包；配置按模块集中，没有生产 Mock、伪造成功、无消费者字段或多余抽象 | 部分。复用 Foundation 模板与公共异常。start 仍用 `Environment` 读取 `files.onboarding.*`（未改配置读取方式，见待确认第 4 项）。新增的抽象类只承载 read/download 真实共用的授权步骤。 |
| 按测试规范完成实际验证，说明未覆盖项 | 是。见第 5、6 节。 |

## 4. 锁互斥域对照

| 入口 | 旧机制 | 旧 Key | 新机制 | 新 Key | 等待 / 租约 |
|---|---|---|---|---|---|
| start | `@ResourceLock(prefix="file:upload:start", key="#lockKey")`，lockKey = `accountId:applicationId:subjectPartyId` | `file:upload:start:<accountId>:<applicationId>:<subjectPartyId>` | `LockCommand.tryLocks(OnboardingFileLocks.startKeys(...))`，普通 RLock | 同左 | 500ms / 30s，与 `redis.coordination.resource-lock` 一致 |
| complete | 第 3b31406a 步已迁移 | `file:upload:complete:<uploadId>` | 未改 | 同左 | 500ms / 30s |
| read / download / verify | 无锁 | — | 无锁（无互斥需求，不造假 Key） | — | — |

两个测试以测试侧 `RedissonClient.getLock(旧 Key)` 持锁，证明新入口在等待上限后返回 `409 CONCURRENT_REQUEST`、不访问 CCT、不落库；start 另证明同账号其他主体不受影响。

`grep -rn "@ResourceLock" paytech/platform-support` 只剩 `GatewayConfigurationManagementProcessor`（6 处）。

## 5. 测试

**新增测试设施**
- `CctFileTargetMocker` + `ClientFileTargetMockController`：在独立 Mock HTTP 服务中实现 `ClientFileTargetApi`，按“账号 + 申请 + 主体”返回授权 DTO 或 404 Problem；未登记的请求记为意外调用。响应 `X-Request-Id` 与 Problem 体一致。
- `OnboardingApplicantMocker`：CUSTOMER 账号、账号上下文会话、PENDING CLIENT 租户（来源投影版本 1）、OWNER 成员与 `TENANT_ONBOARDING_OWNER` 系统角色；`secondOnboardingTenant` 用于目标冲突。
- `StartUploadRequestBuilder`；`OnboardingFileBuilder.owner(...)`；`OnboardingFileMocker.openUpload` / `awaitScanTasks`；`StorageObjectMocker.track`；`FileTestMapper` 的 Upload / 文件 / Owner / 补偿任务查询；`OperationsTestMapper.projectTenant`。
- `OnboardingFileTestBase` 注入测试策略代码，每例结束先有界等待 `FILE_SCAN` 补偿任务结束（超时标记环境不可复用），再检查 CCT 意外调用；`OnboardingFileMocker.clear` 删除本例扫描任务。
- `formatPolicy` 的 `allowed_mime_rules` 改为生产要求的 `{"mimeTypes":[...]}` 对象格式。原数组格式只因没有测试经过上传开始才未暴露。

**新增测试矩阵（全部经真实 HTTP、本地 MySQL/Redis/MinIO，CCT 经真实出站 Client 到 Mock HTTP）**

| 测试类 | 例数 | 场景 |
|---|---|---|
| `StartOnboardingUploadTest` | 22 | 正常（DB 字段、201、PUT 授权可用于浏览器直传）、文件名归一化、13 组非法参数、未知 Body 字段、会话无效、CCT 不可上传、CCT 申请不存在、策略拒绝 2 组、同目标锁竞争与其他目标并行 |
| `CompleteOnboardingUploadTest` | 15 | 首次完成（文件 / Owner / 扫描任务，提交后扫描推进为 AVAILABLE）、重复完成返回同回执、存储核验失败、CCT 不可上传、目标冲突、上传过期、锁竞争、其他账号、会话无效、5 组非法 ID、非空 Body |
| `ReadOnboardingFileTest` | 16 | 正常（含不泄露摘要与存储坐标）、6 组不可下载状态、无数据 / 他人文件、CCT 申请不存在、Client 不符、会话无效、5 组非法 ID |
| `DownloadOnboardingFileTest` | 13 | 正常（60 秒 HTTPS、取回原字节、签发前后两次 CCT、无审计事件）、两次下载各自独立且不改写文件、4 组不可下载状态、他人文件、会话无效、非空 Body、4 组非法 ID |
| `VerifyOnboardingFileTest` | 12 | 两个用途代码的正常核验（不调用 CCT）、调用方错误、4 组期望值不符、未知用途、3 组非法 ID、未知 Query 参数 |

合计 78 例。既有 `ReviewFileDownloadTest` 17 例、`FileScanHandlerTest` 5 例同时通过。

**未覆盖**
- 测试与重构同步编写，没有先在重构前的代码上运行；行为一致性靠逐项对照原实现及上述用例。编写中曾发现入口 `@Valid` 会把字段错误从 400 改为 422，已据此保持原合同。
- 存储适配器故障、签发结果越界、CCT 断连等 503 路径没有新增用例。

## 6. 验证输出

```
cd paytech && ./mvnw -B -o verify \
  -pl platform-support/paytech-platform-support-test,client-console-bff/paytech-client-console-bff-test,client-master/paytech-client-master-test \
  -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
BUILD SUCCESS（45 个 Reactor 模块）
```

| 测试模块 | Tests run | Failures / Errors / Skipped |
|---|---|---|
| paytech-platform-support-test | 876 | 0 / 0 / 0 |
| paytech-client-console-bff-test | 80 | 0 / 0 / 0 |
| paytech-client-master-test | 58 | 0 / 0 / 0 |

- 三个服务的 `ArchitectureAutoTest` 均通过，`known-deviations.yaml` 未新增条目。
- `grep -rn "@ResourceLock" paytech/platform-support` 只剩 `GatewayConfigurationManagementProcessor`。
- 构建日志中 messaging 测试的 RabbitMQ `PRECONDITION_FAILED` 与 Platform 外部 API 用例的 `INTEGRATION_INITIALIZATION_FAILED` 是既有负例的预期日志，对应用例均通过。
- 开工前曾执行一次 `./mvnw install -DskipTests`（platform-support-test 及上游），把与 refactor-main 等价的 SNAPSHOT 写入 `~/.m3`；之后全部用 Reactor 构建，未再 install。

## 7. 待确认

1. **client-console-bff 的 Platform API 坐标。** BFF 原依赖本地仓库中的旧工程 JAR `com.paytech:platform-support-api`，不是 Reactor 内的 `paytech-platform-support-api`。为了让任务卡要求的 BFF 同步生效，本步把 client-console-bff 两个 pom 改为 Reactor 坐标，编译与测试通过。api-gateway、operations-console-bff 仍依赖旧坐标，本步未动。是否保留此切换、是否同步迁移另外两个消费方，请确认。
2. **start 的 Body 校验触发方式。** 规范要求 HTTP 入口 `@Valid`，但公共处理器把 `@Valid` 失败映射为 `422 VALIDATION_ERROR`，原实现返回 `400 INVALID_REQUEST`。本步按“不改错误码与 HTTP 状态”保持 400：Platform 入口不加 `@Valid`，等价约束放在内部 Request 上由 `validateBasic` 执行，Body 注解继续由 BFF 入口 `@Valid` 使用。若接受改为 422 并补字段错误，入口加 `@Valid` 即可。
3. **错误优先级与文案的细微变化。** 错误码与状态不变，但以下情况的 Problem `detail` 或先后顺序变了：mimeType 格式错误的 detail 变为公共 `请求字段不符合约束：mimeType`；Body 字段错误与会话坐标同时非法时，原先先报 400，现在先报 401；ID 溢出与文件名错误同时出现时先报 ID 错误。
4. **start 读取配置的方式。** 仍用 `Environment.getProperty` 在登记事务内读取 `files.onboarding.storage-provider` 等四项，与原实现一致。规范要求模块 `*Properties`，迁移会改变缺省与刷新行为，本步未做。
5. **任务卡称 download“写审计”。** 实际入口没有 `@Audit`，原实现与 Controller 注释都写明不审计临时授权。本步保持不审计，并以 `auditChecker.assertNoEvent` 固定该行为。若需要客户下载审计，属于新增行为。
6. **历史需求文档。** `requirements/20260914/client-console-注册与客户入驻/` 下的合同与验证记录仍写旧 Java 类型名（`OnboardingFileModels` 等）。它们是历史记录，JSON 合同未变，本步未改。

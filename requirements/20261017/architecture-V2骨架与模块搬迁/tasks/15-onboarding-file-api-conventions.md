# 任务卡 15 · 入驻文件两条 API 按写接口规范重构：`OnboardingFileApi`、`OnboardingFileVerificationApi`

- 分支：`refactor/step-15-onboarding-file-api`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step15`；起点：`refactor-main@3b31406a`。
- 用户 2026-10-18 要求：Platform 入驻文件相关代码"写得太紧凑、不按规范"，要按仓库代码规范重构。`OnboardingFileCompletionProcessor`（`POST /uploads/{uploadId}/completion`）已由主会话按规范改好并作为**本步参照**（提交 3b31406a）：`AbstractLockProcessor` + 内部 Request / Response + `OnboardingFileLocks` + Controller 只做转换；逐条看它的写法。
- 范围：`paytech/platform-support` 中与这两条 API 有关的 API 模型、Controller、Processor、测试；消费方（client-console-bff、client-master）只在 API 合同形状改变时同步，不改其业务。

## 1. 先完整读取（规范）

- `AGENTS.md` 的 Start here 第 1 条（接口开发必读与自查）与 `Change discipline`；`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 2、4、5（含 5.1、5.2、5.3）、7 节；`agent-docs/JAVA_PROJECT_STRUCTURE.md` 的 Processor / Controller / API 部分；`agent-docs/examples/WRITE_API_EXAMPLE.md` 全文（第 11 节核对清单必须逐项回答）；`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`；`agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md`。
- Foundation：`paytech/foundation/paytech-common-core/.../processor/AbstractProcessor.java`、`paytech/foundation/paytech-redis-starter/.../processor/AbstractLockProcessor.java`、`.../redis/core/lock/ResourceLockKeyResolver.java`（注解锁 Key = `prefix + ":" + 参数值`）。
- 参照实现：`paytech/platform-support/paytech-platform-support-core/.../service/processor/OnboardingFileCompletionProcessor.java`、`OnboardingFileLocks.java`、`request/CompleteOnboardingFileRequest.java`、`response/CompleteOnboardingFileResponse.java`、`paytech-platform-support-app/.../ext/rest/files/OnboardingFileController.java#complete`；risk-decision 的 `ChangeSubjectTagStatusProcessor` + `DecisionResourceLocks`（注解锁 → 模板锁的迁移范例）。

## 2. 现状与改动清单

现状文件：`paytech-platform-support-api/.../onboardingfiles/{OnboardingFileApi, OnboardingFileVerificationApi, OnboardingFileModels}.java`；`paytech-platform-support-app/.../ext/rest/files/{OnboardingFileController, OnboardingFileVerificationController}.java`；`paytech-platform-support-core/.../service/processor/{OnboardingUploadProcessor, OnboardingFileReadProcessor}.java`（`ReviewFileDownloadProcessor` 属 `ReviewFileApi`，不在本步）。测试只有 `ReviewFileDownloadTest`、`FileScanHandlerTest` 间接覆盖，`OnboardingFileTestBase` / `OnboardingFileMocker` / `OnboardingFileBuilder` 已有。

1. **API 模型**：`OnboardingFileModels` 现为 record 集合，规范要求 RequestBody / ResponseBody 用普通 class、字段 JavaDoc、Hibernate Validator 约束（`StartUploadRequestBody` 现由 Controller 手工 `validator.validate`）。改为 class 后同步 `client-console-bff`（`ClientOnboardingFileController`、`ClientOnboardingProcessor`、`ClientOnboardingModels`、`ClientOnboardingConfiguration`）与 `client-master`（`ClientMaterialAuthorityClient`、`ClientDocumentConfiguration` 及其测试 Mock）的用法；只改访问方式，不改业务。`UploadAuthorization` 的脱敏 `toString` 必须保留。
2. **`start`（`OnboardingUploadProcessor`）**：改为 `AbstractLockProcessor<StartOnboardingUploadRequest, StartOnboardingUploadResponse>`；锁 Key 保持原互斥域 `file:upload:start:<accountId>:<applicationId>:<subjectPartyId>`（`tryLocks` 完整 Key 工厂，WAIT 500ms / LEASE 30s，放进 `OnboardingFileLocks`）；Controller 里的文件名归一化、mimeType 校验等属"只依赖输入的组合判断"，按规范放到 Request 的 Validator 注解或 Processor.validate；阶段注释、卫语句、花括号、中文 JavaDoc。
3. **`read` / `download`（`OnboardingFileReadProcessor`）**：按规范判断——`read` 是纯查询可保留简单 Processor 或 `AbstractProcessor`；`download` 每次签发临时授权且写审计，是写入口，用 `AbstractProcessor`（无锁需求不造假 Key）。内部 Request / Response 放 `service/processor/request|response`。
4. **`verify`（`OnboardingFileVerificationApi` → `OnboardingFileVerificationController` → `OnboardingFileReadProcessor`）**：拆出独立 `VerifyOnboardingFileProcessor`（一个入口一个 Processor），`AbstractProcessor`；`X-Caller-Service` 校验保留在入口；Query 参数的解析在 Controller / Converter。
5. **Controller**：只做协议解析与转换；身份解析失败仍返回 `CLIENT_SESSION_INVALID`；不再直接调 Validator。
6. **测试（UNIT_TEST_GUIDE，测试先行）**：为五个入口补业务级 `auto` 测试（HTTP 入口，真实链路，Mock 只在外部 HTTP：CCT 目标查询 / 授权、对象存储用现有 MinIO 本地环境或测试适配器）：`StartUploadTest`（正常、参数非法、会话无效、目标不可上传、同目标并发只允许一个进行中）、`CompleteUploadTest`（首次完成、重复完成返回同回执、存储校验失败、目标变更冲突、锁竞争返回并发冲突）、`ReadFileTest`、`DownloadFileTest`（授权每次独立、审计落库）、`VerifyFileTest`（caller 校验、期望值不符）。沿用现有 TestBase / Mocker / Builder / Checker 模式；禁止 `when().thenReturn` / `@MockBean`；每例清理。
7. **保持不变**：事务公理（存储 I/O、CCT 调用在事务外）、`OperationsTransactionExecutor` 的审计语义、两段事务顺序、错误码、HTTP 状态、API 路径与 Header 名；`files.onboarding.enabled` 条件装配。

## 3. 验证

1. `cd paytech && ./mvnw -B verify -pl platform-support/paytech-platform-support-test,client-console-bff/paytech-client-console-bff-test,client-master/paytech-client-master-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment` 全绿（测试需要 `PAYTECH_REDIS_PASSWORD`、`MOR_RABBITMQ_*`，从主仓库 `local-environment/.env.local` 导出，不打印值）。
2. `ArchitectureAutoTest` 零新增 known-deviations；`grep -rn "@ResourceLock" paytech/platform-support` 只剩 `GatewayConfigurationManagementProcessor`（不在本步）。
3. 按 WRITE_API_EXAMPLE 第 11 节逐项核对，写进报告。

## 4. 禁止事项

- 不改业务规则、错误码、API 路径；不执行 SQL；不操作容器；不碰 `GatewayConfigurationManagementProcessor`、`ReviewFileApi`；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。
- 本机当前有一套全链路服务在跑（宿主机 18xxx 端口，连 `*_v2e2e` 库），与 auto 测试用的 `mor_*` 库互不影响；不要杀这些进程。

## 5. 交付物

1. 短分支提交，前缀 `refactor(step-15): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；可按 API 模型 / start / read·download / verify / 测试分多次提交。
2. 报告 `tasks/15-onboarding-file-api-report.md`：改动摘要、第 11 节逐项核对、锁互斥域对照（旧注解 Key → 新 Key）、新增测试矩阵与数量、消费方同步点、验证输出。
3. 最终回复两行：commit 哈希 + 三模块测试计数。

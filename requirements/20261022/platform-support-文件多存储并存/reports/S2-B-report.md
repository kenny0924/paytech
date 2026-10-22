# S2-B CCT（client-master）执行报告

worktree：`Code-MoR-fs2-cct`（detached `a5479950`），未提交、未 install、未碰 mor-test。

## 1. 合同

- `ClientDocumentModels`：C12 请求 `DocumentReferenceRequestBody.fileAddress`；C12 回执 `DocumentCommandReceiptResponseBody.fileAddress`；申请详情材料 `DocumentResponseBody.fileAddress`（`fileId` 全部移除）。
- 审核登记（compliance I01）合同不变：仍发 `fileId + contentHash`，`fileId = FileAddress.parse(address).fileId()`。
- `FileAddress`、`InternalFileApi`、`StoredFileResponseBody`、`FileUploadCompletionResponseBody`、`enums/FileErrorCode` 已从 `Code-MoR-fs2-platform` 原样复制（`cmp` 一致），`OnboardingFileErrorCode` 已删除。

## 2. 实现

- `ClientMaterialAuthorityClient` 改调 `InternalFileApi.getFile`：地址先 `FileAddress.parse`（不合法 422 `INVALID_DRAFT`，不调 Platform）；响应的 `fileId`、`fileAddress` 必须与请求一致，摘要必须是 64 位小写十六进制，扫描与对象状态码必须可识别，否则按依赖不可用；Platform `FILE_NOT_FOUND` 或 `uploaderAccountId` ≠ 当前账号 → 404 `DOCUMENT_NOT_FOUND`（两者同码）；扫描非 `AVAILABLE` 或对象非 `ACTIVE` → 409 `MATERIAL_FILE_UNAVAILABLE`；Platform 其他 4xx/5xx、断连 → 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`。`ClientDocumentConfiguration` 为 Platform 文件客户端注册 `FileErrorCode`。删除 `checkedAt ±30 秒` 规则与 Clock 依赖。
- C14（`ClientSubmissionPreparer`）对每份已存材料按保存的地址调用同一核对；上传人不符、扫描未完成、摘要变化都使该材料逐项不可用。
- 调用身份：`ClientOnboardingRecoveryConfiguration.platformTransport` 增加默认头 `X-Caller-Service: client-master-service`，CCT 所有 Platform 客户端共用；internal-auth 模式未改。
- 持久化：`ClientApplicationDocument.fileAddress`（String），Mapper XML、Repository、Service（同文件比较改按地址）、Converter 同步；冻结摘要不变。
- 删除：`ClientFileTargetApi/Models/Controller`、`ClientFileTargetService(Impl)`、`ClientFileTarget`、配置 Bean；仅被它使用的 `ClientDocumentRepository.retainedSubject`（含 Mapper 与 XML）；`OnboardingFileVerificationApi` 引用与 `MockPlatformFileVerificationApi`。规则 Service 没有其他使用者。

## 3. 数据库

- 变更 SQL：`paytech/client-master/docs/database/sql/changes/2026-10-22-application-document-file-address.sql`（第 1 步可执行、第 2 步注释保留，文件头含两步回退）。
- Schema DDL、测试 `init/ddl.sql`、PDManer 模型、`CLIENT_CONTRACT_DATABASE.md` Catalog、`CLIENT_MASTER_PHYSICAL_DECISIONS.md`、现实场景文档均按最终形态（`file_address VARCHAR(255) NOT NULL`，三个唯一键原名改列）。
- 本机 `mor_client_master` 已执行第 1 步：先备份到 `~/Code-MoR-backups/mor_client_master-t_application_document-20261022-before-file-address.sql`；执行前后均 0 行；核对 `file_address` 可空 VARCHAR(255)、`file_id` 可空，三个 `*_addr` 唯一键与旧唯一键并存，注释与文件一致。

## 4. 测试

- `./mvnw -o -Dmaven.repo.local=~/.m3/repository -pl client-master/paytech-client-master-test -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='com.paytech.client.master.auto.**.*Test' package`：78 个用例全部通过（BUILD SUCCESS）。
  - 用 `package` 而不是 `test`：`test` 阶段下 reactor 内的 paytech-messaging test-jar 解析为整个 `target/test-classes`，其中的 `application-mock.yaml` 会执行 DROP 脚本，导致上下文启动失败（与本次改动无关）。
- 新增 `AssociateClientDocumentTest`（C12）：正常（回执与库中地址、摘要，调用头 `client-master-service`）、地址格式错误、扫描未完成、对象非 ACTIVE、摘要不合法、上传人非本人、Platform 404 FILE_NOT_FOUND、Platform 409 FILE_STATE_CONFLICT（→503）、Platform 503。C12 请求使用 JDK 传输的测试客户端，因为默认客户端会对 503 自动重发 POST。
- `SubmitClientApplicationTest`（C14）新增上传人非本人、Platform 503；正常提交断言审核登记仍发文件 ID + 摘要。
- `ClientApplicationAdminTest` 断言申请详情返回 `fileAddress`。
- PDManer node 测试：`client-contract-model` 与 `client-contract-scenario-doc` 各剩 2 个失败，与 a5479950 基线完全相同；其余相关 node 测试全部通过。为此同步了生成器 Catalog/元数据、测试期望，以及 Structural Projection 与 Model SHA-256 摘要。

## 5. 偏差与待办

- C12 回执也改为 `fileAddress`（卡上只写了请求与详情）。
- C02 客户侧详情需要 Vault Reveal，本模块没有 Vault 测试端点，所以没有用它做 HTTP 断言；详情地址由 O02 管理侧详情覆盖，两者共用 `ClientDraftConverter`。
- 按主会话确认，Platform `FILE_NOT_FOUND` 映射为 `DOCUMENT_NOT_FOUND`，与“他人文件”同码；卡上原写“4xx/5xx 都归为暂不可核对”，其余错误仍按此处理。
- 没有校验 `reasonCode` / `sourceSystem`，卡上没有要求。
- 本 worktree 的 platform-support core/app 仍引用已删除的 `ClientFileTargetApi`，由 S2-A 删除，合并后才能一致。

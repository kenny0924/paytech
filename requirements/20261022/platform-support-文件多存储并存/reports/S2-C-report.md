# S2-C 报告：client-console-bff、client-console-web、operations-console-bff、需求站

worktree：`Code-MoR-fs2-bff`（detached `a5479950`）。未提交、未 install、未执行 SQL、未碰 mor-test。

## 1. 合同同步（逐字节，`diff -r` 核对一致）

- **Platform** `paytech-platform-support-api`，与 fs2-platform 当前状态一致：
  - 新增 `files/FileAddress`、`enums/FileErrorCode`；
  - 修改 `files/InternalFileApi`、`files/response/StoredFileResponseBody`、`files/response/FileUploadCompletionResponseBody`；
  - 删除 `onboardingfiles/**`（`OnboardingFileApi`、`OnboardingFileVerificationApi` 及 DTO）、`reviewfiles/**`、`enums/OnboardingFileAction`、`OnboardingFilePurpose`、`OnboardingFileErrorCode`。
- **CCT** `paytech-client-master-api`，与 fs2-cct 当前状态一致：
  - 修改 `onboarding/ClientDocumentModels`；
  - 删除 `ClientFileTargetApi`、`ClientFileTargetModels`。

### 追加同步：下载方式与原文件名

- **新增：** `enums/FileDisposition`。
- **修改：**
  - `InternalFileApi.authorizeDownload(fileId, operatorAccountId, disposition)`，`disposition` 为可选 query；
  - `FileDownloadResponseBody` 新增 `disposition` 字段。
- **核对：** 同步后 `diff -r` 与 fs2-platform 完全一致。
- **调用方：**
  - 两个 BFF 都传 `disposition=null`，行为不变，仍按附件下载；
  - 测试端点返回补 `"disposition":"ATTACHMENT"`；
  - 两处下载用例另断言：请求不带 query。
- **对浏览器的影响：** client-console-bff 的 F04 原样返回 `FileDownloadResponseBody`，浏览器下载授权因此多出一个 `disposition` 字段（加字段，Web 不读取）。O10 响应由 ops-bff 自组，字段不变。

## 2. 实现

### client-console-bff

- **浏览器路径不变：** `/onboarding-files/v1/**` 改调 `InternalFileApi`。
  - **F01 开始上传：** `ClientOnboardingUploadProcessor` 只收 `OnboardingUploadRequestBody`（文件名、类型、大小，未知字段 400），固定 `reasonCode=CLIENT_ONBOARDING_MATERIAL`、`uploaderAccountId`=当前账号。
  - **F02 完成上传：** `ClientOnboardingUploadCompletionProcessor` 完成后 `getFile`，来源为 client-console-bff 且上传人为本人才返回，否则 `FILE_NOT_FOUND`。
  - **F03 / F04 查文件、下载：** `ClientOnboardingFileReadProcessor`、`ClientOnboardingFileDownloadProcessor` 共用 `ClientOnboardingFileAccess`：
    1. 回读 Platform 会话；
    2. `getFile`；
    3. 判断可读：本 BFF 上传且上传人为本人，或地址在本人申请材料中（CCT 导航 + 草稿，命中即停）；
    4. 否则 `FILE_NOT_FOUND`。下载授权不带操作人。
  - `PlatformFileClient(Impl)` 校验回执：文件 ID、地址一致，状态码已发布。
- **响应：** 浏览器字段不变，另加 `fileAddress`。
  - 完成回执：`commandId`、`uploadId`、`fileId`、`fileAddress`、`completedAt`；
  - 文件元数据新增 `fileAddress`，`allowedActions` 由 BFF 按"扫描 AVAILABLE 且对象 ACTIVE"给出；
  - 申请详情材料新增 `fileAddress`，`fileId` 由地址解析。
- **申请详情：** `ClientApplicationDetailsProcessor` 按 CCT 地址查 Platform；`CctOnboardingClientImpl` 校验草稿材料地址格式。
- **C12 转发：** 直接使用 CCT 合同，`fileAddress` 原样转发。
- **配置：**
  - `PlatformClientConfiguration` 全部 Platform 客户端默认带 `X-Caller-Service: client-console-bff`；
  - 新增 `InternalFileApi` 代理并登记 `FileErrorCode`；
  - `ClientErrorStatusResolver` 改用 `FileErrorCode`（404 / 422 / 409 不变）。
- **清理：** 删除 `PlatformOnboardingFileClient(Impl)`，以及 `ClientOnboardingConfiguration` 中的 `onboardingFileApi` Bean。拦截器与日志前缀的浏览器路径不变，所以保留。

### operations-console-bff（O10）

- **流程：** `ClientReviewFileDownloadProcessor` 依次：
  1. Risk 证据核对（不在证据中时不调用 Platform）；
  2. `getFile` 取文件名、类型、大小；
  3. `authorizeDownload`，带 `X-Operator-Account-Id`=操作人。
- **响应：** 拼成本地 `ClientReviewFileDownloadResponseBody`，字段与原响应相同，ops-web 不改。
- **客户端：** `ClientReviewFileClient(Impl)` 改用 `InternalFileApi`。
- **配置：** `PlatformClientConfiguration` 删除 `ReviewFileApi` Bean，新增 `InternalFileApi`（登记 `FileErrorCode`）；Platform RestClient 默认带 `X-Caller-Service: operations-console-bff`。
- **错误映射：** `OperationsErrorStatusResolver` 改用 `FileErrorCode`：404 / 409 / 422 穷举；`FILE_TARGET_CONFLICT` 已删除。

### client-console-web

- `onboarding.ts`：
  - 开始上传不再传 `applicationId` / `subjectPartyId`；
  - 完成回执校验 `fileAddress === /files/v1/{fileId}` 并存入进度；
  - 断点恢复在 SCAN / ASSOCIATE 阶段要求地址与文件 ID 一致，旧格式断点丢弃，需要重新上传；
  - C12 提交 `fileAddress`。
- `onboarding.test.ts` 同步。

### 需求站与文档

- **`requirements.ts`：**
  - PLT-I04 改写：BFF 直调文件服务、业务存地址、Platform 不反调 CCT、O10 审计；Partner 原合同保留；
  - E2E-01 摘要、CCT-I15 说明同步。
- **`global-use-case-sequences.mjs` 的 E2E-01：** 改写上传，新增 C12 只提交地址、CCT 读取元数据核对，以及 O10 下载。
- **`tests/global-use-case-sequences.test.mjs`：** 上传与提交两条正则随文字更新。
- **README：** 两个 BFF 的 README 已更新。

## 3. 测试（全部离线，`-pl … -am`，不 install；追加同步后前台重跑，两个 BFF 结果不变）

| 范围 | 命令 | 结果 |
|---|---|---|
| client-console-bff | `./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -pl client-console-bff/paytech-client-console-bff-test,operations-console-bff/paytech-operations-console-bff-test -am -Dtest='com.paytech.client.console.bff.**.*Test,com.paytech.operations.console.bff.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` | 87 / 87 通过 |
| operations-console-bff | 同上 | 2128 / 2128 通过 |
| client-console-web | `npm test`；`npm run build` | 63 / 63 通过（追加同步后重跑）；构建成功（追加同步前，Web 无改动） |
| 需求站 | `npm --prefix requirements-site test` | 127 通过 / 10 失败，失败项与 `a5479950` 基线逐项相同 |

### 新增或改写的用例

- **client-console-bff**（`ClientOnboardingHttpAutoTest`）：
  - 开始上传固定用途与上传人，并带调用身份；
  - 旧 Body 带申请 / 主体时返回 400，且不调用任何 Owner；
  - 完成上传：本人通过，他人返回 404，且不返回地址；
  - 本人上传的文件可查、可下载，不查 CCT，下载不带操作人；
  - 他人上传但已关联到本人申请的文件可读、可下载；
  - 他人未关联的文件：查与下载都返回 404，且不签发地址；
  - Platform 404 / 409 透传，503 返回 503，会话失效返回 401 且不查文件；
  - 申请详情返回 `fileAddress`，不含摘要。
- **operations-console-bff**（`ClientOnboardingReviewBrowserAutoTest`，O10）：
  - 两次调用的顺序、方法、操作人头与调用身份；
  - 不在证据中：不调用 Platform；
  - getFile 404 时不签发；
  - 签发 409 透传，503 返回 503；
  - 元数据文件不符返回 502，且不签发。
- **DataQueryBrowserAutoTest：** 调用身份头改为对所有 Platform 调用都断言。

## 4. 偏差与说明

1. **会话回读：** BFF 在四个文件入口都先回读 Platform 会话。内部文件接口不核验客户会话，原 F01—F04 由 Platform 核验。
2. **本人上传判定：** 除上传人 = 本人外，还要求来源系统 = client-console-bff。上传人 ID 只有在本 BFF 写入时才代表客户账号。
3. **关联判定范围：** 只覆盖 CCT 导航返回的申请（最多 20 条）。
4. **旧浏览器断点：** SCAN / ASSOCIATE 阶段没有地址的旧断点会被丢弃，用户需要重新上传。

## 5. ops-bff 带 X-Caller-Service 后的影响核对

Platform 中按调用身份判断的入口经 `InternalCallerResolver` / internal-auth 取调用方：

- Webhook 事件提交；
- Notification 提交；
- 内部 Token 签发；
- 数据查询执行（`data-query.http.callers` 白名单）；
- 内部文件接口。

ops-bff 调用的 Platform 合同包括：apicredentialmanagement、apiscopemanagement、authentication、authorization、configurationmanagement、consolemanagement、dataquery、files、filestorage、gatewayconfiguration、notificationmanagement、operations、permissionmanagement、referencedata、tenantaccessmanagement、webhookmanagement。

- **用到调用身份的：** 只有数据查询执行（原本就显式发送同值头）和内部文件接口（本次所需）。
- **不读调用身份的：** 其余管理与认证接口，不按"无调用身份"分支，也不要求特定调用方。

**结论：** 没有受影响的接口。

唯一的约束：日后 ops-bff 启用内部 Token 时，Token `sub` 必须是 operations-console-bff。否则 `InternalCallerResolver` 会以"头与 Token 不一致"拒绝。按现有设计，`sub` 取 `spring.application.name`，二者一致。

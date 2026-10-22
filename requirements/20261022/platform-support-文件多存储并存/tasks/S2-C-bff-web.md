# 任务卡 S2-C：client-console-bff、client-console-web、operations-console-bff、需求站

先读 [S2-common.md](./S2-common.md)。worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-fs2-bff`。

## 合同来源

- **Platform：**
  - `FileAddress` 与 `InternalFileApi` 的新字段、请求头，来自 `Code-MoR-fs2-platform`（S2-A）。
  - Platform 删除的 API 类：`OnboardingFileApi`、`OnboardingFileVerificationApi`、`ReviewFileApi` 及其 DTO / 枚举。
- **CCT：** C12 请求与申请详情的 `fileAddress`，来自 `Code-MoR-fs2-cct`（S2-B）。
- 主会话会通知合同就绪。届时把这些 api 文件原样复制到你的 worktree 同一路径（新增、修改、删除都照做），不得修改。在此之前按 S2-common 第 1 节编码。

## 范围

### client-console-bff

- **入驻文件路由：** Gateway 路径 `/api/client/onboarding-files/v1/**` 与浏览器可见路径**不变**，改为调用 Platform `InternalFileApi`，带 `X-Caller-Service: client-console-bff`。
  - **开始上传：** 只收文件名、类型、大小；`reasonCode=CLIENT_ONBOARDING_MATERIAL`、`uploaderAccountId` = 当前账号。
  - **完成上传：** 调用完成接口；返回文件后核对 `uploaderAccountId` = 当前账号。
  - **查文件：** `getFile`。允许条件二选一：上传人 = 当前账号；或该地址出现在本人可读申请的材料中（调 CCT 申请详情）。否则按“文件不存在”拒绝。
  - **下载：** 按查文件的同一授权，再调用下载授权接口。
- **响应：**
  - 响应体给浏览器的字段除下面这点外保持不变；字段映射在 BFF。
  - 涉及文件的响应增加 `fileAddress`，供 Web 关联时提交。
- **申请详情：** `ClientApplicationDetailsProcessor` 改用 CCT 返回的 `fileAddress`，再查 Platform。
- **C12 转发：** 改为提交 `fileAddress`。
- **清理：** 删掉对已删 Platform API 的引用与配置，包括 `ClientOnboardingConfiguration`、`PlatformOnboardingFileClient(Impl)`、拦截器与错误映射中的旧路径。

### client-console-web

- `src/onboarding.ts`：
  - 开始上传不再传 `applicationId`、`subjectPartyId`；
  - 关联材料（C12）提交 `fileAddress`；
  - 直传 → 完成 → 轮询 → 关联的流程不变。
- 同步 `onboarding.test.ts`；`npm test` 与 `npm run build` 通过。

### operations-console-bff（O10）

- `ClientReviewFileDownloadProcessor` 保留 Risk 证据核对，改为调用 `InternalFileApi`：
  - `getFile` 取文件名、类型；
  - 下载授权带 `X-Operator-Account-Id` = 当前操作人，以及 `X-Caller-Service: operations-console-bff`。
- 拼成现有响应，ops-web 不改；删除对 `ReviewFileApi` 的引用。

### 需求站与文档

- **需求站：**
  - `requirements-site/content/requirements.ts` 中 PLT-I04 的描述：上传与下载经 BFF 直调文件服务、业务存地址、不反调 CCT；
  - `global-use-case-sequences.mjs` 中相关时序。
- **文档：** 两个 BFF 的 README。

## 测试

- **两个 BFF：** 从 HTTP 入口覆盖授权矩阵，Platform 与 CCT 都用测试 HTTP 端点返回配置的 DTO：
  - 本人上传可查、可下载；
  - 已关联的材料可读；
  - 他人文件拒绝；
  - Platform 503 透传；
  - O10 两次调用的组合与失败分支。
- **运行：** 只跑这两个 BFF 模块与 client-console-web。
- **需求站：** `npm --prefix requirements-site test` 失败项与 `a5479950` 基线相同。

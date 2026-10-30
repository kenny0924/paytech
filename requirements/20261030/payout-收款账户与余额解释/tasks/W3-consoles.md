# W3 任务卡：Operations BFF / Web（核验工作台、Owner 账户与余额）+ Client BFF（无 Web）

- 工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-po-ops`（detached，基于 main `30361be7`）。只在这里改；不提交、不推送、不碰 mor-test。
- 依赖：payout 合同模块 `paytech-payout-api` 由 W1 在 `/Users/kenny/Desktop/Workspace/Code-MoR-po-svc/paytech/payout/paytech-payout-api` 完成；开工前把该目录与 `paytech/payout/pom.xml`、`paytech/pom.xml` 的 payout 模块行同步到本工作树（只复制 api 与父 POM，不复制 core / app），并在报告中写明同步时 W1 的文件快照时间。合同以 technique 第 8 节为准，W1 实现与之不一致时以 W1 代码为准并在报告列出差异。
- 权威材料（只读，主工作树）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/payout-收款账户与余额解释/` 的 `requirement.md`（AC-V3 / V4、R8 / R9）、`technique.md` 第 8.4—8.6、10 节；原型 PNG `prototypes/ops/O1—O5`（在 `operations-console-web/docs/operations-console.pen` 中名为"出款 · 01—05"，可用 Pencil MCP 只读查看）。规范同 W1 卡。
- 参照：Ops 四眼 `operations-console-web/src/clientReview/ReviewDecision.tsx`、Reveal 组件 `src/vault/SensitiveValue.tsx`、页面能力 `src/navigation/pageCapabilities.ts`、财务人工调整 BFF `FinanceManualAdjustmentController`；Client BFF `ClientOnboardingController`、`ClientActorResolver`、`ClientPermissionGuard`、Platform 工作区 `clientId`。

## 范围

1. **Operations BFF：** `/payout-admin/v1` 11 个接口（technique 8.5），payout 客户端（`@HttpExchange` 用 payout-api）、client-master Admin 法定名聚合（S-02）、actorId 取 Operations 会话；权限按 8.6；auto 测试（payout / client-master 用测试 HTTP 端点返回 DTO）。
2. **Operations Web：** 新页"收款账户核验"（O1、O2、O4、O5 状态）与"Owner 账户与余额"（O3）；`pageCapabilities` 动作资源；i18n operations-console 命名空间 key（en / zh-CN）；单测、tsc、i18n 覆盖率、生产构建。按原型实现布局与交互，不自行增删信息。
3. **Client BFF：** `/client-payout/v1` 7 个接口（technique 8.4），clientId 取当前工作区，权限 `CLIENT_PAYOUT_ACCOUNT_READ / MANAGE`、`CLIENT_BALANCE_READ`；auto 测试。**不写 Client Web 页面**（原型未画，用户暂缓）。

## 本机约束

同 W1 卡：JDK 25、`./mvnw -o …`、只跑两个 BFF 模块（`package`）与 operations-console-web 的 npm 检查；不跑全 reactor；不启动共享服务。

## 交付

工作树内 `REPORT-W3.md`；回复一句摘要与报告路径。

# W4 任务卡：集成工作树合并后的收尾（真实合同、原型缺口、全量相关验证、文档）

- 工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-po-int`（基于 main `30361be7`，已由主会话合入 W1 / W2 / W3 三个工作树的全部改动；注册块 SQL 在 `release-draft/`）。只在这里改；不提交、不推送、不碰 mor-test；只连 `mor-local-mysql-1`。
- 先读三份报告了解现状：`/Users/kenny/Desktop/Workspace/Code-MoR-po-svc/REPORT-W1.md`、`/Users/kenny/Desktop/Workspace/Code-MoR-po-plat/REPORT-W2.md`、`/Users/kenny/Desktop/Workspace/Code-MoR-po-ops/REPORT-W3.md`；权威合同与原型同 W1—W3 卡（`requirements/20261030/payout-收款账户与余额解释/` 在主工作树 `/Users/kenny/Desktop/Workspace/Code-MoR`）。规范同 W1 卡。

## 范围

1. **payout 改用真实合同：** 依赖 `paytech-account-api`（`BalanceQueryApi` positions）与 `paytech-platform-support-api`（通知 `tenantRecipients`、`deliveryCount`），删除 payout-core `integration/http` 下的自建替身 DTO 与 HttpService；行为不变，测试改用提供方 DTO。
2. **原型缺口（运营端 O1 / O2 / O5 已批准，按原型补齐）：**
   a. **共用账号的对方 Owner：** payout 运营视图（P3 详情 / P11 队列）把布尔改为 `sharedOwners[{ownerType, ownerId, accountCode, status}]`（只列其他 Owner 的未终结账户，最多 5 条）；Ops BFF 用 client-master Admin 接口补名称；O1 提示文案"该账号也登记在 N 个其他 Owner 名下：名称（类型 ID）· 编号 · 状态"。
   b. **核验意见备注与给 Client 的说明：** P8 增可选 `note`（≤500，存新列 `review_maker_note`，O2 在 Maker 意见下显示）；P9 驳回与 P8 拒绝增可选 `clientNote`（≤500，存新列 `reason_note`，作为通知模板 `reasonText` 的补充随邮件发送，O5 弹窗显示该输入）。两列均 `VARCHAR(500) NULL`，同步 PDManer、`payout-schema.sql`、`db/payout/ddl.sql`（主工作树需求目录，改成含两列的首建版本）并另写增量 `db/payout/ddl-20261030-notes.sql`（`ALTER TABLE t_beneficiary_account ADD COLUMN …`），**不执行**——主会话执行到本机。日志与事件不记 note 原文以外的敏感值（note 是运营文字，可记）。
   c. **Owner 按名称搜索：** 先查 client-master 是否已有 Client / Local Partner 按名称搜索的运营 Admin 接口；有则 Ops BFF 用它做 O3 搜索（名称或编号），无则保持类型 + 编号并在报告写明。
3. **全量相关验证（不跑全 reactor）：** 编译 `-pl payout/…,account/…,platform-support/…,client-console-bff/…,operations-console-bff/…,tools/arch-rules,tools/arch-rules-reactor-test -am`；测试 payout、account、platform-support（含 MinIO 证书依赖：可临时软链主工作树 `local-environment/minio/certs`，跑完删除）、两个 BFF、arch-rules；operations-console-web：tsc、单测、i18n 覆盖率、生产构建。用 `package`；环境变量从主工作树 `local-environment/.env.local` 读取不打印；测试加 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。
4. **文档：** `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` 的 payout 行从"预留"改为实际；`agent-docs/CURRENT_IMPLEMENTATION.md` 新增条目"payout 收款账户与余额解释（S1，阶段 4，未部署、未提交）"，写已实现、已验证（附本次命令与结果）、Pending（Client Web 未做、mor-test 未接入、注册块 147 未执行、语言包命名空间待建等）。主工作树需求目录 `technique.md` 的接口计数（Ops BFF 12、Gateway 路由 19）与第 8 节 P3 / P8 / P9 / P11 增量由主会话改，你在报告里列出需改的点即可。

## 交付

工作树内 `REPORT-W4.md`（Bash heredoc）：改动、测试命令与逐模块结果（失败要写）、与原型 / 合同的剩余差异；回复一句摘要与路径。前台执行，不要空等后台事件。

# 任务卡 E · 阶段 5 本机综合验证（billing → account → ledger → Operations 控制台）

先读 `00-common.md`（git / Superpowers / 容器禁令同样适用；本卡例外见下）、`T0-report.md`、`A-report.md`、`B-report.md`、`C-report.md`、`D-report.md`，`requirement.md` 验收标准 AC-01—20，`technique.md` 2.2 / 2.5。前序先例（结构、登录、运行记录、清理与基线核对方式照抄）：`requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/{local_acceptance.py,local_browser.py,test_regression.py,acceptance-cleanup-local.sql}`、同目录 `delivery.md`「阶段 5 本机验证」，以及 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/{run-service.sh,e2e_http.py,sql/apply-e2e.sh}`。

## 用户已授权的本机动作（2026-10-19，"继续"；只限本机 local-environment / `*_v2e2e` 库，绝不碰 mor-test、`mor_*` 测试库之外的库，不提交）

1. **备份**：对 `mor_account_v2e2e`、`mor_ledger_v2e2e`、`mor_platform_v2e2e` 各做一次 `mysqldump`（root 口令只从 `local-environment/.env.local` 读，不打印）到 `local-environment/e2e/backup/<ts>/`（该目录被 .gitignore 忽略；若不存在则创建）。
2. **SQL**（root，按序，每步记录核对输出）：`mor_account_v2e2e` ← `REQ/db/account/ddl.sql`（执行前核对期望 0 行；若该库尚不存在新表之外的旧表也照常执行）；`mor_ledger_v2e2e` ← `REQ/db/ledger/ddl.sql` 再 `REQ/db/ledger/dml.sql`；`mor_platform_v2e2e` ← `REQ/db/platform-support/registration-dml.sql`（块 99）再 `REQ/db/platform-support/activation-local.sql`。执行后核对：account 9 表、ledger 31 表且 `t_instruction_line.account_entry_id` 存在、规则行 36、平台新增 3 条路由与 1 个 Console Resource 已 ENABLED。
3. **构建与重启**（`run-service.sh` 在上面的 local-e2e 目录；它只管自己记录 PID 的进程）：`cd paytech && ./mvnw -B -o package -DskipTests -pl billing/paytech-billing-app,account/paytech-account-app,ledger/paytech-ledger-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am`；`run-service.sh jars`；`run-service.sh stop ops-web gateway ops-bff ledger account billing platform`；`run-service.sh start platform billing ledger account ops-bff gateway ops-web`（**ledger 先于 account 启动**：account 发布端不可路由即失败）；`run-service.sh status all` 全部 UP。其他服务（nginx、vault 等）不动；若某服务不是由 run-service.sh 启动（status 显示无 PID），先在报告里说明再决定是否能处理，不要 kill 非本启动器的进程。
4. **验收写入与清理**：合成数据只写 `*_v2e2e` 库，验收后按清理 SQL 删除并核对行数回到基线。

## 验收脚本（新建 `REQ/verification/`）

1. `local_acceptance.py`（`python3 -I`，`E2E_TARGET=local` 默认，结构照前序）：经 Gateway `https://127.0.0.1:18444` 运营登录（照 `e2e_http`）走控制台路由，经内部端口直连 billing（18113）登记事实。场景：
   - **S1 基础资料**：用现有或新建的财务主体、日历、OPEN 期间、账簿 AE0xx / USD（`fresh_entity_no` 方式取未用 OE）；新建账户只允许五值 Owner（R11 用 CLIENT → 400 `ACCOUNT_IDENTITY_INVALID` 或 BFF 400），控制科目账户不手工建。
   - **S2 链路**：billing `POST /billing/v1/economic-facts` 登记 MANUAL_ADJUSTMENT（付方 OPERATING_ENTITY 该 OE、收方 CLIENT 某 Owner ID，CLIENT_PRINCIPAL 100.00）并 `approve`；轮询 account `GET /api/operations/finance/owner-accounts/v1?...`（R19）出现 `CLIENT_PAYABLE / CLIENT / PENDING` 贷 100.00；R20 流水一行、R21 批次详情（`sourceType = ECONOMIC_FACT`、`publishedTime` 非空）；account 直连 `GET /account-postings/v1/progress`（18114）`appliedFactCount = 1`、流序号 1；ledger 经 R10 账簿账户列表出现 `CLIENT_PAYABLE` 控制账户（Owner = OPERATING_ENTITY 该 OE，桶 NONE，净余额 100.00）；R12 流水 `originType = ACCOUNT_POSTING` 且 `accountPostingId` / 序号；R13 分录详情控制科目行 `accountEntryId` 等于 R20 的流水 ID；借贷平衡；R17 余额查询 pending 100.00、其他桶 0；未知 Owner → 400 `OWNER_ACCOUNT_NOT_FOUND`。
   - **S3 第二笔与替代**（若 billing 支持替代登记：`supersedesFactId`）：登记替代事实（CLIENT_PRINCIPAL 80.00）并批准 → account 批次 2 含冲回行（`reversesEntryId`）与新行，`CLIENT_PAYABLE / PENDING` 净 80.00；ledger 控制账户净 80.00，分录详情含对应 `accountEntryId`。billing 不支持则记 N/A 并说明。
   - **S4 ledger 追平**：直连 ledger（18105）`POST /posting-streams/v1/catch-up`（带 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`）→ `caughtUp = true`、`processedCount = 0`。
   - **S5 关账检查**（可选，若 ledger 内部 PeriodCloseApi 能在无 Maker-Checker 审批下启动关账流程并返回步骤结果，则启动一次并断言 `CHECK_ACCOUNT_POSTING_PROGRESS` SUCCEEDED；若流程进入审批态需人工，可只断言步骤结果后用清理 SQL 删除流程记录；做不到则记 N/A）。
   - **S6 权限**：无财务权限账号访问 R19 → 403；只读账号看不到新建按钮属浏览器脚本。
   - 运行记录写 `local-environment/e2e/runtime/subledger-acceptance-<ts>.json`；`--cleanup` 按记录执行 `acceptance-cleanup-local.sql` 删除本次写入的 billing / account / ledger / Platform（若有）行并核对三库行数回到基线。
2. `local_browser.py`（Playwright，照前序）：登录后按画板 04 / 05 / 07 / 08 / 09 / 11 / 13 / 14 / 15 走查：账簿账户列表五值、新建账户表单余额桶固定 NONE、账本查询下钻到 ACCOUNT_POSTING 分录抽屉并点击「流水 …」链接跳到明细账、余额查询结果与未找到、明细账三级下钻与返回保留筛选、空状态；每步截图到 `REQ/assets/stage5/browser/`；断言页面文字与数据与 S2 一致。
3. `test_regression.py`：沿用前序只读回归（全部页面可打开，33 → 34 页）。
4. `acceptance-cleanup-local.sql`。

## 报告

`REQ/verification/stage5-report.md`：环境事实（SQL 执行输出、备份路径、各服务版本 / 启动时间、ops-web 端口）、逐项结果表（AC 编号、通过 / 失败 / N/A、证据路径）、清理与基线核对、发现的问题（按本需求缺陷 / 既有问题分类）。最终回复三行：报告路径、通过计数、阻塞项（无则写无）。发现本需求缺陷时**不要自行改业务代码**，在报告中给出文件、现象与建议，由主会话决定分配。

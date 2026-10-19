# 任务卡 E · 阶段 5 本机综合验证（channel-connector 渠道成本 → billing 计费事实 → account → ledger）

先读 `00-common.md`（git / Superpowers 禁令同样适用；本卡例外见下）、`T0-report.md`、`A-report.md`、`B-report.md`、`C-report.md`、`D-report.md`，`requirement.md` AC-01—07，`technique.md` 2.1 / 2.2 / 2.5。前序先例（结构、登录、运行记录、清理与基线核对方式照抄）：`requirements/20261019/finance-account明细账与ledger控制科目重构/verification/{local_acceptance.py,acceptance-cleanup-local.sql,stage5-report.md}` 与其 `tasks/E-local-verification.md`，以及 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/{run-service.sh,e2e_http.py,sql/apply-e2e.sh,sql/50-channel-connector-payermax-mock.sql}`。

## 用户已授权的本机动作（2026-10-19，阶段 5–8 一并授权；只限本机 local-environment 的 `*_v2e2e` 库与 run-service.sh 管理的进程，绝不碰 mor-test，不提交）

1. **并行会话协同（重要）**：另一会话（commercial 需求）今晚也在用本机。你只能 `run-service.sh stop/start` **billing、channel-connector、ledger、account** 四个服务；platform、ops-bff、gateway、ops-web、nginx、commercial 等一律不停不启不改。只对 `mor_billing_v2e2e`、`mor_channel_connector_v2e2e` 两库执行 SQL。
2. **备份**：对两库各做一次 `mysqldump`（root 口令只从 `local-environment/.env.local` 读，不打印）到 `local-environment/e2e/backup/<ts>/`。
3. **SQL**（root，按序，记录核对输出）：`mor_channel_connector_v2e2e` 当前 **0 张表** → 执行 `paytech/channel-connector/docs/database/sql/channel-connector-schema.sql`（17 表）；渠道 / 技术账号 / MID 种子若 `local-e2e/sql/50-channel-connector-payermax-mock.sql` 可用则执行它，否则自己插入最少一条 ACTIVE Channel、一条 Channel Account、一条 MID（OE 用 S1 的 entityNo），ID ≥ 1,000,000 便于清理。`mor_billing_v2e2e`（9 张旧表，先核对事实三表与成本四表 0 行）→ `REQ/db/billing/ddl.sql` 再 `REQ/db/billing/dml.sql`；核对 11 表、日历 DEFAULT 1 行。
4. **构建与重启**：`cd paytech && ./mvnw -B -o package -DskipTests -pl billing/paytech-billing-app,channel-connector/paytech-channel-connector-app,account/paytech-account-app,ledger/paytech-ledger-app -am`；`run-service.sh jars`；`run-service.sh stop account ledger billing channel-connector`；`run-service.sh start channel-connector billing ledger account`（**ledger 先于 account**）；`run-service.sh status all` 四个 UP、其余状态与之前相同。channel-connector 新增必填环境变量：`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID` / `_WORKER_ID`、`CHANNEL_CONNECTOR_REDIS_HOST/PORT/PASSWORD`（local-environment Redis 16379，口令 `MOR_REDIS_PASSWORD`）、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`，以及 XXL 执行器（照 billing 在 run-service.sh 中的做法，本机不连调度中心则禁用）：**允许只修改 `run-service.sh` 的 `channel-connector)` 一段**来注入，变量名以 B-report.md 第 6 节第 9 项与 `paytech-channel-connector-app/src/main/resources/application.yml` 为准。
5. **验收写入与清理**：合成数据只写两库 `*_v2e2e` 与 account / ledger 的 `*_v2e2e`（后两者沿用前序脚本的写法），验收后按清理 SQL 删除并核对行数回到基线。

## 验收脚本（新建 `REQ/verification/`）

1. `local_acceptance.py`（`python3 -I`，`E2E_TARGET=local` 默认，结构照前序，**同时预留 `E2E_TARGET=test`**：库 `mor_billing` / `mor_channel_connector` / `mor_account` / `mor_ledger`、23306、入口 `http://localhost:28080`、私网接口经 `e2e_http` 探针容器，阶段 7 复用）：
   - **S0 基础资料**（照前序 S1）：经 Gateway 运营登录，用 `fresh_entity_no` 取未用 OE，建财务主体 / 日历 / OPEN 期间 / 账簿（OE / USD）；若前序运行记录里已有可复用的 OE 与账簿则复用并说明。
   - **S1 渠道成本（channel-connector 18110 直连）**：`POST /channel-connector/v1/channel-cost-plans`（channelId = 种子 Channel）→ `POST .../{planCode}/versions`（1 个范围：midId = 种子 MID、entityNo = MID 的 OE、US / USD / CARD / PAY，组件 PROCESSING_FEE 费率 0.029 + 固定费 0.30）→ submit → approve（approvedContentHash = 创建返回的 contentHash，checker ≠ maker，effectiveFrom 为过去时间 → 状态 ACTIVE）→ `GET .../{planCode}` 当前版本 1 → `POST /channel-connector/v1/channel-costs/resolutions`（同六维 + asOfTime=now）返回该范围与 1 个组件（rateValue "0.029"、fixedAmount "0.3"）；负例：midId 不存在 → 400 `BUSINESS_CHECK_FAILED`；不同 countryCode 命中 → 400 `CHANNEL_COST_NOT_RESOLVED`。
   - **S2 计费事实链路（billing 18113 直连）**：`POST /billing/v1/facts`（MANUAL_ADJUSTMENT，sourceSystem OPERATIONS、sourceReference 随机，clientId 随机，entityNo = S0 的 OE，USD，refs [{TICKET, T-xxx}]，lines [CLIENT_PRINCIPAL：OPERATING_ENTITY(entityNo) → CLIENT(clientId) 100.00 CHARGE targetBucket AVAILABLE]，makerId）→ 201；同请求再提交 → 200 同 factId；同 sourceReference 改金额 → 400；`POST .../{factId}/approve`（checkerId ≠ makerId）→ APPROVED、factVersion 1；`GET /billing/v1/facts/{factId}` 含 refs、lines（targetBucket）、links 空、pricing 空；`GET /billing/v1/facts/by-ref?refType=TICKET&refId=` 命中 1；`GET /billing/v1/facts/progress?entityType=OPERATING_ENTITY&entityId=&currency=USD` 轮询至 pendingCount 0、publishedFactCount 1。
   - **S3 account / ledger**（照前序 S2 的断言，路径不变）：account 18114 `GET /owner-accounts/v1?...` 出现 `CLIENT_PAYABLE / CLIENT / PENDING` 贷 100.00（本期 account 仍按 PENDING，targetBucket 未启用）；`GET /account-postings/v1/progress` appliedFactCount 1；ledger（经控制台路由或 18105 直连，照前序）控制账户 `CLIENT_PAYABLE` 净 100.00，分录 originType ACCOUNT_POSTING；`POST /posting-streams/v1/catch-up` caughtUp true。
   - **S4 替代**：登记替代事实（supersedesFactId = S2 事实，CLIENT_PRINCIPAL 80.00）并批准 → account 批次 2 含冲回行，净 80.00；ledger 控制账户净 80.00。
   - 运行记录写 `local-environment/e2e/runtime/billing-fact-acceptance-<ts>.json`；`--cleanup` 按记录执行 `acceptance-cleanup-local.sql`（删除本次 billing 事实 / 引用 / 行 / 补偿事件、channel-connector 方案 / 版本 / 范围 / 组件与种子（若本卡插入）、account / ledger 的本次行、Platform / ledger 基础资料若新建）并核对四库行数回到基线。
2. `acceptance-cleanup-local.sql`。
3. 不做浏览器脚本（本期无页面改动）；`test_regression.py` 只读回归若前序脚本可直接复用（34 页可打开）则跑一次作为旁证，否则记 N/A。

## 报告

`REQ/verification/stage5-report.md`：环境事实（SQL 执行输出、备份路径、四服务 JAR 构建与启动时间、run-service.sh 改动）、逐项结果表（AC 编号、通过 / 失败 / N/A、证据路径）、清理与基线核对、发现的问题（本需求缺陷 / 既有问题分类）。最终回复三行：报告路径、通过计数、阻塞项（无则写无）。发现本需求缺陷时**不要自行改业务代码**，在报告中给出文件、现象与建议，由主会话决定分配。

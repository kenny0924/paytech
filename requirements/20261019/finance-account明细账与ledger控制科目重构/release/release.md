# account 明细账与 ledger 控制科目重构：mor-test 发布清单

阶段 6（评审稿，2026-10-19）。确认后执行阶段 7，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本阶段只做只读核对、本地构建与发布包准备，未改 mor-test 任何容器、配置、数据或队列。

**本次：mor-test 的 billing、account、ledger、Platform、Operations BFF、Operations Web 六个容器换成「HEAD `2bae8597` + 本需求 441 个文件」构建的镜像；`mor_account` 整体换成 9 张明细账表；`mor_ledger` 加 `account_entry_id`、改注释、删 8 条 Owner 角色规则行；`mor_platform` 追加并启用块 99（1 个菜单、3 条路由）；account-service 删 `ACCOUNT_LEDGER_SERVICE_URL`；删除 ledger 对 `billing.events` 的旧队列与交换机（E-1）；经运营入口验收后清理合成数据。Gateway、Nginx、Vault、Redis、RabbitMQ 容器、XXL 容器与其余 21 个服务不动。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop（context `desktop-linux`）的 `mor-test`：30 个容器均 healthy；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306`；RabbitMQ vhost `/mor-test`。不是生产，不是 `mor-local` |
| 源码基线 | HEAD `2bae8597`（2026-10-19）干净 worktree + 本需求 441 个文件（[source-overlay.txt](./source-overlay.txt)：account 214、ledger 164、BFF 20、Web 15、billing 13、`scripts/pdmaner` 7、arch-rules 3、Platform 2、messaging 2、Compose 1；新增 148 / 修改 199 / 删除 94；清单 SHA-256 前缀 `afa017a82560bd79`）。见第 3 节 D1 |
| 镜像（6 个） | billing（载荷 `balanceAvailableTime`、进度 `publishedFactCount`、详情 `paymentSucceededTime`）；account（9 表明细账、入账批次与 `AccountPosted` 发布、桶移动、`accountBalanceMaturity`、按流重建、查询；不再调用 ledger / billing）；ledger（`AccountPosted` 严格有序消费、控制科目过账、`catch-up`、关账 `CHECK_ACCOUNT_POSTING_PROGRESS`；删 `account-totals` 与 `PROJECTION_REBUILD`；不再订阅 billing）；Platform（`console-resource-capabilities.json` +1 `OPERATIONS_FINANCE_SUBLEDGER`）；BFF（Owner 五值、3 条明细账路由）；Web（「明细账查询」页，页面 34）。Gateway 不变 |
| SQL（4 份） | `mor_account` ← [db/account/ddl.sql](../db/account/ddl.sql)（删 5 建 6，结果 9 表）；`mor_ledger` ← [db/ledger/ddl.sql](../db/ledger/ddl.sql)（D-1 修复版）+ [db/ledger/dml.sql](../db/ledger/dml.sql)（规则行 44 → 36）；`mor_platform` ← 块 99 [阅读副本](../db/platform-support/registration-dml.sql)（39 行休眠，与初始化 SQL 末尾逐字一致）+ [test-activate.sql](./test-activate.sql)（3 / 1 / 1）。ddl 需 root（`account_app` / `ledger_app` 只有 DML 权限）；Platform 用 `platform_app` |
| Compose | `compose.applications.yaml` 已在工作区删 account-service 的 `ACCOUNT_LEDGER_SERVICE_URL`（D 线，随阶段 8 提交）；[compose-candidate.yaml](./compose-candidate.yaml) 只固定 6 个镜像 ID，`config --quiet` 通过，渲染结果 account 无该变量、Gateway 仍为原镜像 |
| RabbitMQ（E-1） | 删除 `ledger.billing.p0`—`p3`、`ledger.billing.dlq` 队列与 `ledger.billing.hash`、`ledger.billing.dlx` 交换机（删哈希交换机即删除它与 `billing.events` 的绑定）。新 ledger 声明 `account.events` → `ledger.account.hash` → `ledger.account.p0`—`p3` / `ledger.account.dlq` |
| XXL-Job | account 执行器新 Handler `accountBalanceMaturity` 需在 Admin 登记任务，见 D4；`compensationEventScan`（account 任务 13）已登记并运行，承担 `ACCOUNT_POSTED` 发布兜底 |
| 顺带发布 | Platform 镜像含 HEAD 上尚未进 mor-test 的 `70f9f630` / `a05888f8` 的 Platform 部分（网关配置一入口一 Processor、审核下载继承模板，platform-support app 3 + core 24 个文件）；billing 运行镜像为 2026-10-18 15:55Z 的 `:development`，无 build-info，无法判断是否含 messaging `339db7b8` / `8d123532`，新镜像必含。ledger / account / BFF / Web 自上次 test 部署（`7acbcf4f` 内容）以来无其他源码变化 |
| 不动 | Gateway（路由由快照加载，5 秒刷新）、Nginx、Vault、Redis、RabbitMQ / XXL / MySQL 容器、Client 链路与其余业务服务；不推送 Git 与镜像 |

## 2. 只读核对结果（2026-10-19 约 10:20Z）

### 2.1 运行容器与镜像（回退位点）

| 服务 | 运行镜像 ID | 容器启动（UTC） | 状态 |
|---|---|---|---|
| billing | `sha256:7336c0de49885d36638ba84a718bff354bb34203be327bdf07023f539561c596`（`:development`） | 2026-10-18 16:15:06 | healthy |
| account | `sha256:d341485d92600a44423ee2052e597f0470e217e99df674176e8354ef43cda6d3`（`finance-ops-20261019`） | 2026-10-19 04:11:02 | healthy |
| ledger | `sha256:0c0b6d4e6b0dbce3d11f1f653187ca58f49384e0b9c84853a6da5019d85d3e95` | 2026-10-19 04:10:28 | healthy |
| Platform | `sha256:7c8af66064607050c2c64bf4890d597fe1988be3e627c49c78eb2a8bf094d1e9` | 2026-10-19 04:11:37 | healthy |
| BFF | `sha256:ea20c4a31f08deab58bfcb0886116502c25bfa82e7256fe90a0737bd23ac14f2` | 2026-10-19 04:12:14 | healthy |
| Web | `sha256:a16abbcd15497eea8675dc837cb6e17a354d063d44cca9504bac707f8b39b63e` | 2026-10-19 04:12:35 | healthy |
| Gateway（不动） | `sha256:4cf14912680a…` | 2026-10-18 13:39:31 | healthy |

`:development` 标签（Platform `144ec2bf`、ledger `97022a0b`、account `dcc0acf3`、BFF `d6d934d1`）与运行容器不同；本次与前序一样只用固定镜像 ID 部署与回退。`xxl-job-admin` 于 09:36Z 重启过（46 分钟前），healthy。

### 2.2 数据库

| 库 | 结果 |
|---|---|
| `mor_ledger` | 31 张表；科目表 1、科目 29、币种 1、规则包 2 / 版本 2、规则行 **44**（5101：12，5102：32），Owner 角色行 8 条正是 dml.sql 要删的 `5202, 5208, 5315—5320`；关账科目规则 7；财务主体 / 账簿 / 日历 / 期间 / 指令 / 分录 / 人工调整 / 试算 / 消费幂等 / 补偿事件全部 0；`t_ledger_account` **0 行**（无 Client / Partner 账户，D3 不需要删任何行）；`t_instruction_line.account_entry_id` 列与 `idx_instr_line_entry` 都不存在（ddl 未执行过） |
| `mor_account` | 8 张旧表（`t_account_balance`、`t_balance_applied_entry`、`t_balance_applied_fact`、`t_balance_discrepancy`、`t_balance_movement`、`t_compensation_event`、`t_consumed_aggregate_version`、`t_consumed_event`）**全部 0 行**，满足 ddl.sql 执行前核对 |
| `mor_billing` | 9 张表；`t_currency_spec` 1，其余（经济事实、事实行、渠道成本、补偿事件）全部 0。**没有未发布或已发布事实**，account 新消费者启动后没有历史事件可消费，关账两段检查的 billing 已发布数为 0 |
| `mor_platform` | 块 97 / 98：18 条路由、5 个资源 ACTIVE，`FINANCE_ACCOUNTING_READ` / `MANAGE` 权限与 2 条授权 ACTIVE，父菜单 `OPERATIONS_FINANCE` ACTIVE；块 99 ID 段（资源 993xx、路由 994xx、Header 995xx、Query 996xx、限流 997xx）**全部 0 行**，资源码 `OPERATIONS_FINANCE_SUBLEDGER` 与 3 个路由码 0；PLATFORM_ADMIN 版本 **30**；Target `900000000000000001` ACTIVE；路由 295、资源 91（全 ACTIVE）；财务子菜单 SETUP 10 / LEDGER 20 / BALANCES 30，块 99 的 sort 40 排在最后 |
| 账号权限 | `account_app` / `ledger_app`：本库 SELECT / INSERT / UPDATE / DELETE；`platform_app`：本库 ALL。ddl 用 root（`MOR_MYSQL_ROOT_PASSWORD`） |

### 2.3 RabbitMQ `/mor-test`

| 对象 | 消息 / 消费者 | 说明 |
|---|---|---|
| `account.billing.p0`—`p3` | 0 / 各 1 | 参数 `x-single-active-consumer`、`x-dead-letter-exchange=account.billing.dlx`、`x-delivery-limit=5`、quorum；与本机新 account 镜像使用中的同名队列参数一致，新镜像可直接接管 |
| `account.billing.dlq` | 0 / 0 | |
| `ledger.billing.p0`—`p3` | 0 / 各 1（旧 ledger） | 待删除。新 ledger 不再订阅 billing，替换后这四个队列失去消费者并开始堆积 billing 事件 |
| `ledger.billing.dlq` | 0 / 0 | 待删除 |
| 绑定 | `billing.events` → `account.billing.hash`、`ledger.billing.hash`；哈希交换机 → 各分区队列；dlx → dlq | |
| `account.events` / `ledger.account.*` | 不存在 | 新 ledger 启动时声明（订阅方会声明发布方交换机），因此 ledger 先于 account 启动即可满足 account `unroutable-policy: FAIL` |

与本机 E-1 不同，mor-test 的旧队列与 DLQ 都没有滞留消息，D5 只在执行时计数非 0 时才需要。

### 2.4 XXL-Job

| 执行器 | 地址 | 任务 |
|---|---|---|
| `account-service`（group 12） | `http://172.30.73.40:9999/`，心跳正常 | 任务 13 `compensationEventScan`，CRON `*/30 * * * * ?`，运行中；**无** `accountBalanceMaturity`；旧注释里的 `accountBalanceReconciliation` 也未登记（新镜像已删除该 Handler） |
| `billing-service`（group 11） | 心跳正常 | 任务 12 `compensationEventScan` 运行中 |
| ledger | 无执行器（Compose 未配置 XXL） | — |

### 2.5 Compose 与配置

- 运行中 account 容器环境含 `ACCOUNT_LEDGER_SERVICE_URL=http://ledger-service:18105`、`ACCOUNT_BILLING_SERVICE_URL`；ledger 含 `LEDGER_ACCOUNT_SERVICE_URL`、`LEDGER_BILLING_SERVICE_URL`（新 ledger 两个都需要，保留）；BFF 已有 `OPERATIONS_LEDGER_BASE_URL` / `OPERATIONS_ACCOUNT_BASE_URL`（新 BFF 属性名未变）。
- 新 account `application.yml` 已删除全部 HTTP 客户端：`ACCOUNT_BILLING_SERVICE_URL` 也已无用（保留无害，卡片只要求删 ledger 一行）；新增 `paytech.instance-id: ${PAYTECH_INSTANCE_ID:main}`，Compose 未给 account 设该变量，事件信封 `instanceId` 将为 `main`（billing 为 `billing-test`）。两项都不阻塞，列为后续可选清理。
- 新 billing / ledger 的环境变量占位均已由现有 Compose 提供，没有新增必填变量。

### 2.6 发布 SQL 一致性

- `db/ledger/ddl.sql` + `dml.sql` 与模块变更文件 `paytech/ledger/docs/database/sql/changes/2026-10-19-ledger-control-accounts.sql` 正文一致（模块文件为两者合并），含 D-1 修复（不再按 `account_id` 删除试算 / 期间余额）。
- `db/account/ddl.sql` 与 `paytech/account/docs/database/sql/changes/2026-10-19-account-subledger.sql` 正文一致。
- 块 99 阅读副本 49 行非注释正文与 `platform-support-initial-data.sql` 末尾逐字一致。

### 2.7 构建（本地，未部署）

- 干净 worktree `2bae8597` + 441 个文件（叠加后 `git status` 441 项，与清单一致；构建后复核主工作树这 441 个文件哈希未变），JDK 25 离线：`./mvnw -B -o -DskipTests -pl billing,account,ledger,platform-support,operations-console-bff 的 app 模块 -am clean package` BUILD SUCCESS。
- 发布源码上的测试：五个 `*-test` 模块 `test-compile` 通过（未运行：auto 测试会写本机 `mor_*` 测试库，本阶段禁止）；`tools/arch-rules` `verify` **60/60**（纯静态规则）；Web `npm test` **1087/1087**，`npm run build` 通过（`node_modules` 复用主仓库，`package.json` / lockfile 相同）。阶段 4 / 5 在同一工作区内容上已跑过 billing 90、account 58、ledger 234、BFF 1497、Platform 44（指定 3 类），阶段 7 不重跑。
- JAR 内容核对：account 含 `BalanceMaturityJobHandler`，account-api 含 `AccountPostedPayload` / `BalanceMovementApi` / `OwnerAccountQueryApi` / `AccountPostingApi`、不含 `BalanceBookApi` / `BalanceMovedPayload`，不再打包 ledger-api，`application.yml` 无 `ACCOUNT_LEDGER_SERVICE_URL`；ledger-core 含 `AccountPostedPostingHandler`、`PostingStreamCatchUpProcessor`，不含 `ProjectionRebuild` / `AccountTotals`；billing-api 含 `balanceAvailableTime`、`publishedFactCount`；BFF 含 `FinanceSubledgerController` / `FinanceSubledgerClientImpl`；Platform access-control 能力 JSON 含 `OPERATIONS_FINANCE_SUBLEDGER`。

### 2.8 候选镜像（tag `subledger-20261019`）

| 服务 | 镜像 ID | JAR SHA-256 / Web 入口 |
|---|---|---|
| billing | `sha256:403fc7decdc759784d438d2551ce839cd6d8942640ce760d14de47f2db77c850` | `3d754be040cc21b240dd0387063901b3bc8fd9707fdc9254951a43a5a9d2a4ea` |
| account | `sha256:b356a4f38060367d7f6ccf4cd65d34f46d041f398d80b3535da7d154cb9481d6` | `5a698ead20ec29ccec04aca00a0b976317e1f1fa88d031f48c60f992e66cde03` |
| ledger | `sha256:fb7c661dcede9656e998bdb51f3ff4d24e15552b8a12132de1a23a907675fde7` | `c59ab8b42c31773a0c6a82aafdcbc85fe36c910ffebd553264cfc55865a5e15f` |
| Platform | `sha256:33c196ec9f9a1d18af5876f7c24b32563580bc9b5bc895ea5bc8bb4f494a5b82` | `76a776b4e61468bbacf53b5a1dbacd047fc61db499565109d3fe8fae5c5ec110` |
| BFF | `sha256:446ad6977e6e77725d3c2a517d5419b9e22ead9ad92b72827e09789a4ca1a314` | `a41dfb0990f44f6c549ba876aa0209b3a0a43635a2a083fe988d2f22f297a1c6` |
| Web | `sha256:12e2e021014e13d0d61d81d711b56c3af051ff546cf8d8cd89c4bd0cb35e55b2` | `assets/index-BBzYeyTZ.js` |

Java 镜像用 `test-environment/java/Dockerfile`（JAR + 由 `test-environment/java/*.java` 编译的健康探针），Web 用 `operations-console-web/Dockerfile`（宿主机 `dist`）。构建 worktree 与构建上下文已删除，镜像保留在本机 Docker。

### 2.9 验收脚本 test 适配

`local_acceptance.py` / `local_browser.py` / `test_regression.py` 的入口、库、私网调用全部经 `e2e_http`，`E2E_TARGET=test` 时为 `http://localhost:28080`、`mor_*`（23306，`.env.test` 应用账号）、接入 `mor-test-backend` 的一次性探针容器（`mor-test/platform-support-service:development` 镜像，本机存在）。三个脚本都不读服务日志，因此无需前序 AC-08 那样的 `docker logs` 分支；只在 `local_acceptance.py` 文档串与 `acceptance-cleanup-local.sql` 头注释补了 test 说明。本机只做了语法检查与 `E2E_TARGET=test` 下的导入干跑（打印目标、库名、私网地址），未连接 mor-test 写入。billing / account / ledger 被调用的私网接口都没有 Profile / 开关限制。

## 3. 需要你决定

**D1 源码基线。**
- 选项 A（建议）：HEAD `2bae8597` 干净 worktree + 本需求 441 个文件（已按此构建第 2.8 节镜像）。
- 选项 B：当前工作区原样构建。六个镜像涉及的模块中，清单外只有 `paytech/compliance` 一个其他会话改动且不进这六个镜像，因此产物与 A 相同；A 的价值是可复现。
- 阶段 7 开始前若 HEAD 或清单中任一文件变化（`source-overlay.txt` 已写复核方法），重新构建并更新本文件与候选 Compose。

**D2 合成数据。**
- 选项 A（建议）：同本机阶段 5，接口脚本新建 OE、财务主体 / 日历 / 期间 / 账簿、2 条 billing 事实（100.00 → 替代 80.00）、关账发起并驳回；完成后 `--cleanup` 把 `mor_billing` / `mor_account` / `mor_ledger` 删回第 2 步备份的行数。
- 选项 B：保留合成数据供演示，清理另行授权。

**D3 `mor_ledger` Client / Partner 账户与分录。** 核对结果为 0 行，ddl.sql 第 3 段会删除 0 行，无需决定；执行时若计数不为 0 则停止并回报。

**D4 XXL-Job 登记 `accountBalanceMaturity`。**
- 选项 A（建议）：在 Admin 的 `account-service` 执行器下新建任务，Handler `accountBalanceMaturity`、CRON `0 */10 * * * ?`、路由 FIRST、阻塞策略单机串行、启用；登记后手动触发一次，确认执行日志为「matured 0, movements 0, failed 0」。billing 目前没有写 `balance_available_time` 的生产路径，任务实际无事可做，登记只验证接线。
- 选项 B：登记但保持停止。
- 选项 C：本次不登记。

**D5 `ledger.billing.*` / DLQ 历史消息。** 当前全部为 0。建议：删除前再计数，仍为 0 直接删除；若非 0（billing 当前无事实，出现即异常），停止并回报，不直接丢弃。

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询与容器清单，六个运行镜像与 2.1 一致；`mor_*` 行数与 2.2 一致；与其他会话确认没有正在进行的 test 部署或验收；HEAD 与 `source-overlay.txt` 复核（D1）。
2. **备份**：存 `~/Code-MoR-backups/<UTC>-subledger-test/`（目录 0700、文件 0600，`SHA256SUMS`，不进版本库）：`mor_account`、`mor_ledger`、`mor_billing` 整库 `mysqldump --single-transaction`；`mor_platform` 的 `t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`、`t_permission`、`t_role_permission`；`git show HEAD:test-environment/compose.applications.yaml`（含 `ACCOUNT_LEDGER_SERVICE_URL`，回退用）；6 个容器 `docker inspect`；RabbitMQ `/mor-test` 定义导出（只读）；`xxl_job_info` 中 account 执行器行。核对每个 dump 非空、可读、含预期 `CREATE TABLE` 数（account 8、ledger 31、billing 9）。
3. **停 account、ledger**：`compose.sh stop account-service ledger-service`，避免 SQL 期间消费；billing 无事实，期间产生的事件留在 `account.billing.*` 队列。
4. **SQL**（root，客户端 `--default-character-set=utf8mb4`，保存输出）：
   1. `mor_account` ← `db/account/ddl.sql`：执行前 SELECT 全 0；执行后 9 张表。
   2. `mor_ledger` ← `db/ledger/ddl.sql`：Owner 账户与指令计数 0；执行后 31 张表、`account_entry_id` 列 1。
   3. `mor_ledger` ← `db/ledger/dml.sql`：`owner_rule_lines` 0、`rule_lines` 36。
   4. `mor_platform`（`platform_app`）← 块 99 阅读副本：先在事务内试跑并 ROLLBACK 核对 1 / 3 / 6 / 11 / 18，再提交；全部 DISABLED（旧镜像运行中不生效）。
5. **替换**：`compose.sh -f <REQ>/release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait <服务>`，依次 Platform → billing → **ledger → account** → BFF → Web；各自 healthy、启动日志无 ERROR。ledger 后核对 `account.events`、`ledger.account.hash`、`ledger.account.p0`—`p3`（各 1 消费者）、`ledger.account.dlq` 已声明；account 后核对 `account.billing.p0`—`p3` 各 1 消费者、account 执行器在 XXL 心跳。Web 入口 `index.html` 引用 `index-BBzYeyTZ.js`。
6. **删除旧队列**（ledger 与 billing 已换新镜像之后；旧 ledger 的 messaging 会在队列被删时自动重声明，新 billing 的事件目录不再含 ledger 订阅方，不会补回绑定）：计数为 0（D5）→ 删除队列 `ledger.billing.p0`—`p3`、`ledger.billing.dlq`，删除交换机 `ledger.billing.hash`、`ledger.billing.dlx`（`rabbitmqctl delete_queue` 与管理 API `DELETE /api/exchanges/%2Fmor-test/<名>`）；核对 `billing.events` 只剩到 `account.billing.hash` 的绑定。
7. **XXL**：按 D4 登记 `accountBalanceMaturity`。
8. **启用**：[test-activate.sql](./test-activate.sql)（`platform_app`）试跑 ROLLBACK 核对 3 / 1 / 1 后提交，PLATFORM_ADMIN 30 → 31；等 Gateway 快照加载（5 秒），经入口 `GET /api/operations/finance/owner-accounts/v1` 未登录返回 401。
9. **验收**（`E2E_TARGET=test`）：接口 [local_acceptance.py](../verification/local_acceptance.py) 期望 **58/58**；浏览器 [local_browser.py](../verification/local_browser.py) 期望 **31/31**，截图存 `assets/stage7/browser/`；全页面只读回归 [test_regression.py](../verification/test_regression.py) 期望 **34/34**（mor-test 全部服务在运行，本机 7 页 503 不适用；每页间隔避开登录限流）；六个服务与 Gateway 自替换起的日志无 ERROR、无口令 / Token 明文。
10. **清理**（D2=A）：`--cleanup`，`mor_billing` / `mor_account` / `mor_ledger` 行数与第 2 步备份一致（`t_consumed_event` 等基础设施表随运行记录一并清理）；删除本机 `subledger-20261019` 以外的临时产物。
11. **记录**：写入第 6 节，更新交付记录、决定记录状态与 `CURRENT_IMPLEMENTATION.md`。

预计中断：account / ledger 自第 3 步到第 5 步不可用（SQL 约 1 分钟 + 替换各约 30 秒）；Platform / BFF / Web 各约 30 秒。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 第 4 步 SQL 中途失败 | 停止。account：用第 2 步 dump 整库恢复（`DROP` 后旧表结构只能从 dump 回来）；ledger：ALTER 可逆（`DROP KEY idx_instr_line_entry, DROP COLUMN account_entry_id`），但为一致性同样整库恢复；Platform 块 99 未提交则无影响，已提交则保持休眠或按 ID 段 993xx—997xx 删除（另行授权）。然后 `compose.sh up -d --no-deps` 用旧镜像启动 account、ledger |
| 某个容器替换后不健康 | 回退顺序与替换相反。**account 不能单独回退**：旧镜像（`d341485d…`）不认识新表结构，必须先用 dump 整库恢复 `mor_account`，并用含 `ACCOUNT_LEDGER_SERVICE_URL` 的覆盖文件启动（旧 account 余额查询会调用 ledger）。**ledger 回退**（`0c0b6d4e…`）需整库恢复 `mor_ledger`（规则行被删，旧 ledger 依赖 Owner 规则行），旧 ledger 会重新声明 `ledger.billing.*` 与绑定。**billing 回退**（`7336c0de…`）只能与 ledger / account 一起：新 ledger 关账与事实读取依赖新字段。Platform（`7c8af660…`）、BFF（`ea20c4a3…`）、Web（`a16abbcd…`）可各自独立回退 |
| 回退用覆盖文件 | 写入备份目录，内容为 6 个旧镜像 ID，另给 account-service 补 `ACCOUNT_LEDGER_SERVICE_URL: http://ledger-service:18105`；或把备份的旧 `compose.applications.yaml` 放回工作区后用旧镜像 ID 覆盖 |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（3 / 1 / 1）先关入口，再按上一行回退 |
| 注册数据 | 块 99 共 39 行休眠时对任何版本无影响，保留；删除另行授权 |
| RabbitMQ | 旧队列删除不可逆，但旧 ledger 镜像启动会重新声明（空队列）；新声明的 `account.events`、`ledger.account.*` 在回退后无发布方，保留无害 |
| XXL | 回退 account 时停用 `accountBalanceMaturity` 任务（旧镜像无该 Handler，调度会失败） |
| 验收合成数据 | `--cleanup` 按运行记录删除；脚本失败时用同一组 ID 手工执行 [acceptance-cleanup-local.sql](../verification/acceptance-cleanup-local.sql) 三段（各应用账号） |
| 不可逆项 | `mor_account` 旧表删除（只有 0 行旧表，由 dump 恢复结构）、ledger 8 条规则行删除（dump 可恢复）、旧队列删除；Platform 审计表的验收操作记录不回滚 |

触发条件：任一步核对数与本清单不符、容器 `--wait` 超时、启动日志出现 ERROR、验收出现非环境性失败。先停止后续步骤，再按上表从最后完成的一步向前回退。

## 6. 执行记录（2026-10-19 阶段 7，用户"继续部署"：D1=A、D2=A、D4=A、D5=计数为 0 直接删）

执行人：任务卡 G agent（Opus 5.5）。时间均为 UTC。凭据只从 `test-environment/.env.test` 读入环境变量（`MYSQL_PWD` / curl 配置文件），未打印；临时 curl 配置文件已删除。

| 步骤 | 结果 |
|---|---|
| 1 复核（10:40—10:42Z） | HEAD `2bae8597`；`source-overlay.txt` 441 行逐个 `shasum -a 256` 全部一致（删除项均不存在），清单正文 SHA-256 `afa017a8…` 一致；镜像相关路径的 `git status -uall` 恰为这 441 个文件。30 个 mor-test 容器 healthy；六个运行镜像与 2.1 一致，Gateway `4cf14912…`；6 个候选镜像（tag `subledger-20261019`）存在。`mor_account` 8 表全 0；`mor_ledger` 31 表、规则行 44（5101：12、5102：32），Owner 角色行恰为 5202 / 5208 / 5315—5320，`t_ledger_account` 0，`account_entry_id` 列与索引 0；`mor_billing` 9 表、只有 `t_currency_spec` 1。`mor_platform`：块 99 各 ID 段与资源码 / 路由码 0，路由 295、资源 91（全 ACTIVE），块 97 / 98 路由 18 与资源 5 ACTIVE，两个财务权限 ACTIVE，Target ACTIVE，PLATFORM_ADMIN 30。RabbitMQ 与 2.3 一致（`ledger.billing.*` 0 消息、分区各 1 消费者，`account.events` / `ledger.account.*` 不存在）。XXL 与 2.4 一致（account 执行器心跳 10:42:00，只有任务 13）。没有其他部署 / 验收进程在运行。候选 Compose `config --quiet` 通过，渲染后 account 无 `ACCOUNT_LEDGER_SERVICE_URL` |
| 2 备份（10:42Z） | `~/Code-MoR-backups/20261019T104222Z-subledger-test/`（目录 0700、文件 0600）：`mor_account` / `mor_ledger` / `mor_billing` 整库 `mysqldump --single-transaction --routines --triggers`（`CREATE TABLE` 8 / 31 / 9，均以 `Dump completed` 结尾）；`mor_platform` 8 张注册相关表；`compose.applications.HEAD.yaml`（含 `ACCOUNT_LEDGER_SERVICE_URL`）；6 个容器 `docker inspect`；容器清单；RabbitMQ `/mor-test` 定义（管理 API 只读导出：交换机 13、队列 25、绑定 30）；`xxl_job_info` / `xxl_job_group` 的 account 与 billing 行；三库逐表行数基线 `rowcounts-before.tsv`（48 表）；回退覆盖文件 `compose-rollback.yaml`（6 个旧镜像 ID + account 的 `ACCOUNT_LEDGER_SERVICE_URL`）。后续 SQL 输出与清理后行数也存入该目录，`SHA256SUMS` 22 项 `shasum -c` 全部 OK |
| 3 停 account / ledger | 10:43:13Z `compose.sh stop account-service ledger-service`，10:43:21Z 两个容器 Exited（143） |
| 4 SQL（10:43—10:44Z） | 4.1（10:43:38Z，root）先单独跑执行前核对 6 项全 0，再执行 `db/account/ddl.sql`：9 表（`t_owner_account`、`t_account_posting_stream`、`t_account_posting`、`t_account_entry`、`t_balance_applied_fact`、`t_balance_movement` + 保留的 3 张基础设施表）。4.2（10:43:48Z，root）`db/ledger/ddl.sql`：Owner 账户 0、指令 0，删除段全部 0 行，执行后 31 表、`account_entry_id` 列 1。4.3（10:43:57Z，root）`db/ledger/dml.sql`：删除 2 + 6 行，`owner_rule_lines` 0、`rule_lines` 36。4.4（`platform_app`）阅读副本 49 行正文与 `platform-support-initial-data.sql` 末尾逐字一致；事务内试跑（去掉 COMMIT、末尾 ROLLBACK）得 1 / 3 / 6 / 11 / 18、DISABLED 4，回滚后各 ID 段为 0；10:44:14Z 提交，1 / 3 / 6 / 11 / 18 全部 DISABLED，路由 295 → 298。输出：备份目录 `sql-4.*.out` |
| 5 替换（10:44—10:46Z） | `compose.sh -f release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait`，逐个：Platform 10:44:22Z → healthy 10:44:51Z（`33c196ec…`）；billing 10:44:57Z → 10:45:10Z（`403fc7de…`）；ledger 10:45:16Z → 10:45:23Z（`fb7c661d…`），`account.events`、`ledger.account.hash`（绑定 `account.events`）、`ledger.account.p0`—`p3`、`ledger.account.dlx` / `dlq` 已声明，分区消费者在启动后约 8 秒挂上，10:45:31Z 各 1；account 10:45:37Z → 10:45:44Z（`b356a4f3…`），环境无 `ACCOUNT_LEDGER_SERVICE_URL`，`account.billing.p0`—`p3` 各 1 消费者，XXL 心跳 10:45:41；BFF 10:45:54Z → 10:46:12Z（`446ad697…`）；Web 10:46:16Z → 10:46:27Z（`12e2e021…`），入口 `index.html` 引用 `index-BBzYeyTZ.js`。六个容器启动日志 ERROR 0 行 |
| 6 删除旧队列（10:46—10:47Z） | 删除前计数：`ledger.billing.p0`—`p3`、`ledger.billing.dlq` 均 0 消息 / 0 未确认 / 0 消费者（D5：直接删）。10:46:39Z 管理 API `DELETE` 交换机 `ledger.billing.hash`、`ledger.billing.dlx` 各 204。首次删队列加了 `--if-empty` 防护，quorum 队列不支持该标志、5 个队列均未删除（无副作用）；10:47:04Z 逐个复核 0 消息后按清单原命令 `rabbitmqctl delete_queue` 删除 5 个队列（各「0 ready messages」）。核对：`ledger.billing` 队列 0、交换机 0，`billing.events` 只剩到 `account.billing.hash` 的绑定 |
| 7 XXL（10:47Z） | 经 Admin API（`/jobinfo/insert`、`/jobinfo/start`、`/jobinfo/trigger`）在 `account-service`（group 12）下登记任务 **14**：Handler `accountBalanceMaturity`、CRON `0 */10 * * * ?`、FIRST、SERIAL_EXECUTION、超时 300 秒、重试 0、misfire DO_NOTHING，已启用。10:47:45 手动触发：trigger 200 / handle 200，执行日志 `account balance maturity: matured 0, movements 0, failed 0`；10:50 定时调度一次，handle 200 |
| 8 启用（10:48Z） | `test-activate.sql`（`platform_app`）先把 COMMIT 换 ROLLBACK 试跑得 3 / 1 / 1，回滚后仍 DISABLED、版本 30；10:48:04Z 提交 3 / 1 / 1，PLATFORM_ADMIN 30 → 31，资源 ACTIVE 92。10:48:09Z 经入口未登录 `GET` 三条新路由均 401（未注册路径 404，说明快照已加载） |
| 9 验收（10:48—10:54Z） | 接口 `E2E_TARGET=test python3 -I verification/local_acceptance.py` **58/58**（10:48:23—10:48:51Z），运行记录 `local-environment/e2e/runtime/subledger-acceptance-20261019144824.json`（OE `ZE289`，事实 `237889630442217472` → 替代 `237889664449634304`）；浏览器 `local_browser.py assets/stage7/browser` **31/31**（运行 169DF2），截图 [assets/stage7/browser/](../assets/stage7/browser/) 10 张（04、05、07、08、09、10、11、13、14、15）；全页面只读回归 `test_regression.py assets/stage7/regression` **34/34**（10:49:25—10:54:14Z），财务四页截图 [assets/stage7/regression/](../assets/stage7/regression/)。日志（10:44:20Z 起，六个服务 + Gateway）：ERROR 0 行；`.env.test` 中 8 个口令 / Token 值与 JWT 形态字符串命中 0。验收结束时 `account.billing.*` 与 `ledger.account.*` 均 0 消息、DLQ 0 |
| 10 清理（10:54Z） | `E2E_TARGET=test python3 -I verification/local_acceptance.py --cleanup`：book `237889623727108096`、calendar `237889623089573888`、entityNo `ZE289`、2 条事实，脚本核对「billing / account / ledger 共 49 张表行数回到运行前基线」。独立对比第 2 步 `rowcounts-before.tsv`：差异只有本次授权 SQL 的结构变化（account 旧 5 表 → 新 6 表，均 0 行）与 ledger 规则行 44 → 36；其余各表行数相同，业务表全部 0。备份目录与 `subledger-20261019` 镜像保留；scratchpad 临时 curl 配置已删除 |
| 11 记录 | 本节；`agent-docs/CURRENT_IMPLEMENTATION.md` 本需求条目、决定记录 Implementation / Deployment status 行、`tasks/G-report.md`；`delivery.md` 由主会话写 |

**当前 mor-test：** billing `403fc7de…`、account `b356a4f3…`、ledger `fb7c661d…`、Platform `33c196ec…`、BFF `446ad697…`、Web `12e2e021…`（均 `subledger-20261019`），Gateway 未动；「明细账查询」菜单与 3 条路由 ACTIVE；XXL 任务 14 运行中；`ledger.billing.*` 已删除；30 个容器 healthy。回退按第 5 节，旧镜像 ID 与覆盖文件在备份目录 `compose-rollback.yaml`。

**后续可选清理（不阻塞，未做）：** Compose 中 account 的 `ACCOUNT_BILLING_SERVICE_URL` 已无用、未设 `PAYTECH_INSTANCE_ID`（信封 `instanceId` 为 `main`）、account 注释仍提到已删除的 `accountBalanceReconciliation`（同 2.5 / F 报告第 5 节）。

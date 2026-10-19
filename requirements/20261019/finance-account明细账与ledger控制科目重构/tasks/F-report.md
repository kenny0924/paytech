# F 线报告：阶段 6 mor-test 发布准备（2026-10-19）

结论：发布包已就绪，mor-test 未做任何改动。六个候选镜像（tag `subledger-20261019`）已从「HEAD `2bae8597` + 本需求 441 个文件」构建并固定到候选 Compose。mor-test 的财务三库都处于与本机阶段 5 相同的空数据起点：`mor_ledger` 无 Client / Partner 账户，`mor_account` 8 张旧表全 0，`mor_billing` 无事实，旧 `ledger.billing.*` 队列无滞留消息。没有阻塞项；5 项待用户决定，见 [release.md 第 3 节](../release/release.md#3-需要你决定)。

## 1. 已读

`tasks/00-common.md`、`tasks/F-release-prep.md`、`delivery.md`（阶段 4 / 5 与 E-1）、`verification/stage5-report.md`、`tasks/D-report.md`、`release/test-activate.sql` / `test-deactivate.sql`；前序 `requirements/20261018/operations-console-财务基础资料与账本余额查询/release/{release.md,compose-candidate.yaml,source-overlay.txt}` 与其 `delivery.md` 阶段 6 / 7；`test-environment/{README.md,compose.sh,compose.applications.yaml,scripts/build.sh,java/Dockerfile}`；`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py`；`paytech-messaging` 拓扑声明 `MessagingTopologyDeclarer`、`catalog/events.yaml`；三个 app 的 `application.yml` 与差异。`CURRENT_IMPLEMENTATION.md` 与 `initialize.py` 未逐节通读：mor-test 实际状态全部以本次只读核对为准，未依赖台账记载。

## 2. 只读核对（命令摘要与结果）

| 命令 | 结果 |
|---|---|
| `docker ps` / `docker inspect`（镜像 ID、启动时间、非密钥环境变量） | 30 个容器 healthy；六个目标服务运行镜像与启动时间见 release.md 2.1（即回退位点）；account 仍有 `ACCOUNT_LEDGER_SERVICE_URL` |
| `rabbitmqctl list_queues / list_bindings / list_exchanges -p /mor-test`（只列） | `account.billing.*`、`ledger.billing.*` 全部 0 消息，分区各 1 消费者；`billing.events` 绑定 account 与 ledger 两个哈希交换机；`account.events` / `ledger.account.*` 不存在；`account.billing.*` 参数与本机新镜像使用中的同名队列一致 |
| `SELECT`（`127.0.0.1:23306`，`.env.test` 应用账号，`MYSQL_PWD` 传参不打印） | `mor_ledger` 31 表、规则行 44（待删 8 行 ID 吻合）、`t_ledger_account` 0、新列未存在；`mor_account` 8 表全 0；`mor_billing` 9 表、事实 0；`mor_platform` 块 97 / 98 ACTIVE、块 99 ID 段与代码 0、PLATFORM_ADMIN 版本 30；`SHOW GRANTS`：account / ledger 应用账号只有 DML |
| `SELECT`（`xxl_job` 库） | account 执行器在线，只有 `compensationEventScan`；`accountBalanceMaturity` 未登记 |
| `compose.sh -f compose-candidate.yaml config --quiet` / `--format json` | 通过；六个镜像为候选 ID、Gateway 不变、account 无 `ACCOUNT_LEDGER_SERVICE_URL`、ledger 仍有 account / billing 地址 |

## 3. 构建产物

- worktree：`git worktree add --detach <scratchpad>/wt-subledger 2bae8597`，叠加 441 个文件后 `git status` 441 项；构建后复核主工作树 441 个文件哈希未变；worktree（`git worktree remove --force`）与镜像构建上下文已删除，其他 8 个 worktree 未受影响。
- Java（JDK 25，`-o`）：五个 app `-DskipTests clean package` BUILD SUCCESS；五个 `*-test` 模块 `test-compile` 通过；`tools/arch-rules` `verify` **60/60**。auto 测试未运行（会写本机 `mor_*` 测试库，本线禁止）。
- Web：`npm test` **1087/1087**，`npm run build` 通过，入口 `assets/index-BBzYeyTZ.js`。
- 镜像 6 个（ID 与 JAR SHA-256 见 release.md 2.8）：billing `403fc7de…`、account `b356a4f3…`、ledger `fb7c661d…`、Platform `33c196ec…`、BFF `446ad697…`、Web `12e2e021…`。JAR 内容抽查符合预期（新类在、删除类不在、account 不再打包 ledger-api）。

## 4. 发布包文件

| 类型 | 路径 |
|---|---|
| 新增 | `release/release.md`（范围、核对、D1—D5、11 步执行顺序、恢复、空执行记录表） |
| 新增 | `release/compose-candidate.yaml`（6 个固定镜像 ID） |
| 新增 | `release/source-overlay.txt`（441 行，`<A/M/D> <SHA-256> <路径>`，清单 SHA-256 `afa017a8…`） |
| 修改 | `verification/local_acceptance.py`（只改文档串：test 目标前置说明） |
| 修改 | `verification/acceptance-cleanup-local.sql`（只加一行头注释：库名由 `E2E_TARGET` 决定） |
| 未改 | `release/test-activate.sql` / `test-deactivate.sql`（D 线版本可直接用）；`local_browser.py` / `test_regression.py` 已经支持 `E2E_TARGET=test` |

验收脚本检查：三份 Python 语法通过；`E2E_TARGET=test` 下导入 `e2e_http` 干跑得到入口 `http://localhost:28080`、库 `mor_billing` / `mor_account` / `mor_ledger`、私网地址为 Compose 服务名、探针镜像本机存在。没有连接 mor-test 写入。

## 5. 发现与提醒（不阻塞）

1. 旧队列删除必须在 ledger 与 billing 都换新镜像之后：messaging `8d123532` 让旧 ledger 在队列被删后自动重声明；旧 billing 会按旧事件目录补回 `billing.events → ledger.billing.hash` 绑定。已写入执行顺序第 6 步，并要求删交换机 `ledger.billing.hash` / `ledger.billing.dlx`，不只删队列。
2. account 不能单独回退：旧镜像需要旧表结构与 `ACCOUNT_LEDGER_SERVICE_URL`；billing 只能与 ledger / account 一起回退。已写入恢复表。
3. 新 account 不再使用 `ACCOUNT_BILLING_SERVICE_URL`，且 Compose 未设 `PAYTECH_INSTANCE_ID`（信封 `instanceId` 为 `main`）；Compose 中 account 注释仍提到已删除的 `accountBalanceReconciliation`。都不影响运行，建议另行清理（不在本卡范围）。
4. Platform 镜像会顺带发布 HEAD 上的 Processor 模板迁移（`70f9f630` / `a05888f8` 的 Platform 部分），阶段 7 全页面回归会覆盖网关配置页面。
5. 阶段 7 前若 HEAD 前进或清单文件变化，按 `source-overlay.txt` 头部的方法复核并重建。

## 6. 待用户决定

D1 源码基线（建议 A：HEAD + 441 文件，已构建）；D2 合成数据验收后清理（建议 A）；D3 Client / Partner 账户删除（实际 0 行，无需决定）；D4 XXL 登记 `accountBalanceMaturity`（建议 A：登记启用并手动触发一次）；D5 旧队列滞留消息（当前 0；非 0 时停止回报）。

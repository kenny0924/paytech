# ledger 会计科目分级与维护：mor-test 发布记录

阶段 6、7 按用户 2026-10-19 连续执行授权合并执行（见 [delivery.md](../delivery.md)）。时间均为 UTC。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop 的 `mor-test`；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306` |
| 源码 | `main` `cf39a1ee`（含 billing `8b7b4e7a`、commercial E 线）干净 worktree `../Code-MoR-chart2` + 本需求 201 个文件（[source-overlay.txt](./source-overlay.txt)）。未提交 |
| 镜像（tag `chart-20261020`） | ledger `sha256:c1ab307d888e06eceeb731af5875b5905e796a3315167f405d30d62d9b313ba1`；account `sha256:8bc8f8588729bd40241ec5582c872a974d3163678ff07fc2c35e8d49159c9f9e`；operations-console-bff `sha256:971c9238091c61eba5002133d167ca6a55521171214b00b1be45cca8248dbc92`；operations-console-web `sha256:db35544fa2d60d33efbf1af2d850b95f1d9d3703b747cbcfce048ddaf213aa43`。见 [compose-candidate.yaml](./compose-candidate.yaml) |
| 回退位点 | [compose-rollback.yaml](./compose-rollback.yaml)：ledger `0e939066…`、account `ad0621b1…`（billing 会话 `billing-fact-20261019`）、BFF `2bd2afee…`、Web `edf140b9…`（commercial 会话 `commercial-20261020`） |
| SQL | `mor_ledger` ← worktree `paytech/ledger/docs/database/sql/changes/2026-10-19-ledger-chart-hierarchy.sql`；`mor_account` ← `paytech/account/docs/database/sql/changes/2026-10-19-owner-account-no.sql`；`mor_platform` ← 块 91 [registration-dml.sql](../db/platform-support/registration-dml.sql)（与 worktree `platform-support-initial-data.sql` 末尾块 91 逐字一致）+ [test-activate.sql](../db/platform-support/test-activate.sql) |
| 不动 | Platform、Gateway、Nginx、billing 及其余服务镜像；Compose 基础文件；XXL-Job；RabbitMQ 定义 |

## 2. 执行记录

| 步骤 | 结果 |
|---|---|
| 0 演示数据清理（2026-10-19 18:56Z） | 备份后删除 AE001 旧演示数据，见 delivery.md 变更记录 |
| 1 只读核对（22:5xZ） | 运行镜像与两会话通报一致；`mor_ledger` 31 表、STANDARD 29 平铺科目、账簿 0；`mor_account` 9 表、账户 0；块 91 ID 段 0 行；PLATFORM_ADMIN 版本 32；四个服务渲染环境变量与运行容器完全一致 |
| 2 备份（23:06Z） | `~/Code-MoR-backups/20261019T230633Z-chart-release/`：`mor_ledger`（31 表）、`mor_account`（9 表）、`mor_platform`（57 表）整库 dump，回退覆盖文件、四个容器 inspect、`SHA256SUMS` |
| 3 停 ledger、account（23:07Z） | 两容器停止 |
| 4 SQL | ledger：采用 STANDARD 的账簿 0；48 个科目全部编号，`bad_parent` 0，回填 0 行，结果 33 表；account：回填 0 行，结果 10 表，`uk_owner_account_no` 存在 |
| 5 替换 | ledger 23:07Z、account 23:09Z、BFF 23:11Z、Web 23:14Z；Web、BFF 正常 healthy；ledger、account 因 **mor-test RabbitMQ 于 22:53:57Z 被 OOM Kill（与本发布无关，见第 4 节）** 启动失败重启，23:17Z `docker start mor-test-rabbitmq-1` 后两服务 23:17Z 起 healthy，启动日志无其他错误；`account.billing.p0—p3`、`ledger.account.p0—p3` 各 1 消费者、0 消息 |
| 6 Platform | 块 91 注册 96 行；激活 8 条路由 ACTIVE、3 条既有路由 version + 1、PLATFORM_ADMIN 32 → 33 |
| 7 接口验收 | [verification/test_acceptance.py](../verification/test_acceptance.py)：首轮 23/24，唯一失败为明细账查询在连续调用中短暂返回空（数据正确，重跑 3/3 通过：8 个账户号格式唯一、精确搜索命中 1 条、不存在返回 0 条），合计 **26/26** 项检查通过。运行记录 `local-environment/e2e/runtime/chart-acceptance-20261020032732.json` |
| 8 浏览器验收 | 科目树（48 / 49 含停用、已采用提示、搜索、展开）、变更记录抽屉、试算平衡页签科目余额级次汇总（一级借贷 357.60 平衡）、明细账按账户号搜索、余额查询页、商务合同 / 价目页回归；控制台无 JS 错误。截图见 `assets/stage7/` |

## 3. 演示数据（保留）

- AE001 日历 `AE001-STD`（Asia/Dubai）、2026-10 OPEN、2026-11 FUTURE、AE001 / USD 账簿（采用 STANDARD）。
- 8 条已批准 `MANUAL_ADJUSTMENT`（Commerce 8 笔 PAID 订单，本金 42.90、手续费 1.80，来源 `SEED-<order_no>`）→ 8 个明细账账户 `AE001-USD-220101-S000001—S000008`（PENDING 桶）、总账控制账户 `AE001-USD-220101-0001`。
- 验收新增末级科目 `CLIENT_RESERVE_PAYABLE`：创建为 220104，未采用时调整为 221102，采用后改名「Client 风险准备金（演示）」并停用，留有 4 条变更记录。

## 4. 已知问题与遗留

- **mor-test 与 local RabbitMQ 均被 OOM Kill**（22:53:57Z / 22:56:20Z，`OOMKilled=true`）：容器内存上限 768 MiB，但 RabbitMQ 按宿主机内存计算高水位（12.5 GiB），不会在接近上限前限流。宿主机负载 21—25（`gitlab` 容器约 420% CPU），健康检查 `rabbitmq-diagnostics ping` 需 7 秒，超过 5 秒超时，mor-test RabbitMQ 重启后仍显示 unhealthy 但服务正常。本次只重启了 mor-test RabbitMQ；`mor-local-rabbitmq-1` 仍为退出状态，未处理。建议另行在 `rabbitmq.conf` 设置绝对 `vm_memory_high_watermark`，并评估健康检查超时。
- **mor-test XXL-Job Admin 内存紧张**：容器上限 512 MiB，23:36Z 时占用约 507 MiB，当晚已重启 19 次；account 执行器 20 分钟内注册超时约 20 次（ledger 无），定时任务（余额到期、补偿扫描）可能漏触发。commercial 会话也观察到同样现象。未处理，建议调高其内存上限或 JVM 堆配置。
- 「发起试算」按钮本期不做（E2）；试算记录目前为 0 条。
- 变更记录的操作人显示为账号 ID。
- 科目表页签在 1512 px 宽度下「操作」列被截断，需要横向滚动。
- 代码未提交；提交需用户另行授权（阶段 8）。

## 5. 回退

应用：`compose.sh -f requirements/20261019/ledger-会计科目分级维护/release/compose-rollback.yaml up -d --no-deps --no-build --pull never ledger-service account-service operations-console-bff operations-console-web`。注意旧 ledger / account 镜像不认识 NOT NULL 的 `account_no`，回退应用必须同时恢复数据库：用第 2 步 dump 整库恢复 `mor_ledger`、`mor_account`（会丢失演示数据），`mor_platform` 执行 `test-deactivate.sql` 使块 91 休眠。

## 7. 三级科目树重发（2026-10-20）

用户变更：「按常见财务系统设计成3级，清理数据后重建」，随后「可以启动」（mor-test 于 02:56Z 被整体停止，非本发布所致；按原容器 `docker start` 恢复 30 个容器，镜像与配置不变）。

| 项 | 内容 |
|---|---|
| 源码 | 同第 1 节 + 任务卡 E（三级树，[E-report](../tasks/E-report.md)） |
| 镜像（tag `chart3-20261020`） | ledger `sha256:a716585bed12eab8bb5a5878cbf92e7d57755fbeb00c2ca3c54a8d928f08b790`；account `sha256:dfa6254cb889bf5287af8652554b570b8e01082ca9ff2f0b0b1a52f40abd78d7`（`OwnerAccountType` 含 8 位编号，已在 JAR 内核对）；operations-console-web `sha256:a8feab2c14f405e8794482d3c154a5cef5578ca258bde64405e85e3b1e408a40`（只改两处输入框示例文字）；BFF 不变 `971c9238…`。见 [compose-candidate-3level.yaml](./compose-candidate-3level.yaml) |
| 回退位点 | 第 1 节两级版本镜像（`c1ab307d…` / `8bc8f858…` / `db35544f…`），配合本节备份恢复 `mor_ledger` |
| 备份（03:11Z） | `~/Code-MoR-backups/20261020T031145Z-chart3-release/`（`mor_ledger` 33 表、`mor_account` 10 表、`mor_billing` 11 表，`SHA256SUMS`）。另有一个 03:01Z 的空目录 `20261020T030115Z-chart3-release`（当时 MySQL 已停，dump 为 0 字节，删除被权限拦截，可人工删除） |
| SQL | [cleanup-demo-20261020.sql](../db/mor-test/cleanup-demo-20261020.sql)（三库各一事务，删除 10-20 验收数据与演示科目 CLIENT_RESERVE_PAYABLE）→ [convert-two-to-three-level.sql](../db/mor-test/convert-two-to-three-level.sql)：一级 19 / 二级 26 / 三级 29，`bad_parent` 0，末级全部在三级 |
| 替换 | ledger 03:11:55Z、account 03:12:02Z、ops-web 之后，均 healthy；30 个容器 healthy |
| 接口验收 | [test_acceptance.py](../verification/test_acceptance.py) **26/26**（三级结构、维护与冻结、停用、变更记录、billing → account → ledger、账户号 `AE001-USD-22010101-0001` / `-S00000n` 与搜索、三级汇总 2201 = 220101 = 22010101 = 328.80、一级借贷 357.60 平衡）。运行记录 `local-environment/e2e/runtime/chart-acceptance-20261020071257.json` |
| 浏览器 | 未完成：Chrome 扩展访问 `localhost:28080` 显示错误页（3 次），同一地址 `curl` 返回 200；两级版本的浏览器验收截图见 `assets/stage7/`，三级版本页面待人工复看 |

演示数据（保留）：AE001 日历、期间、账簿；8 条已批准事实；明细账账户 `AE001-USD-22010101-S000001—S000008`；总账控制账户 `AE001-USD-22010101-0001`；演示科目 `22110102 CLIENT_RESERVE_PAYABLE`（已停用，4 条变更记录）。

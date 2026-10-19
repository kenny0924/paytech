# G 线报告：阶段 7 mor-test 部署与环境验收（2026-10-19）

结论：mor-test 部署完成，release.md 第 4 节第 1—11 步全部按顺序执行并逐步核对通过，没有触发停止条件，没有回退。验收：接口 **58/58**、浏览器 **31/31**、全页面只读回归 **34/34**。合成数据已清理，三库行数回到备份基线，差异只有本次授权 SQL 的结构与规则行变化。完整时间线与核对输出见 [release.md](../release/release.md) 第 6 节。

## 1. 已读

`tasks/00-common.md`、`tasks/G-test-deploy.md`、`release/release.md` 全文、`tasks/F-report.md`、`verification/stage5-report.md`（运行命令与计数部分）、`release/compose-candidate.yaml`、`release/test-activate.sql`、`release/source-overlay.txt` 头部、`db/account/ddl.sql`、`db/ledger/ddl.sql`、`db/ledger/dml.sql`、`db/platform-support/registration-dml.sql`、`test-environment/compose.sh`、`compose.scheduler.yaml`（XXL 段）、前序 `requirements/20261018/operations-console-财务基础资料与账本余额查询/release/release.md` 第 6 节与其 `delivery.md` 阶段 7、`requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py`（XXL Admin API 用法）、三个验收脚本头部。

## 2. 执行摘要

| 步骤 | 结果 |
|---|---|
| 1 复核 | HEAD 与 441 个叠加文件哈希一致；六个运行镜像、三库行数、Platform、RabbitMQ、XXL 均与第 2 节一致 |
| 2 备份 | `~/Code-MoR-backups/20261019T104222Z-subledger-test/`，三库整库 dump（8 / 31 / 9 表）、Platform 8 表、Compose、inspect、RabbitMQ 定义、XXL 行、行数基线、回退覆盖文件；`SHA256SUMS` 22 项 OK |
| 3 停服 | account、ledger 10:43:21Z 停止 |
| 4 SQL | account 9 表；ledger 31 表 + `account_entry_id`；规则行删 2 + 6 → 36；块 99 试跑 1 / 3 / 6 / 11 / 18 后提交，全部休眠 |
| 5 替换 | Platform → billing → ledger → account → BFF → Web，全部 healthy，启动日志 ERROR 0；新队列与消费者、XXL 心跳、Web 入口资源均核对 |
| 6 旧队列 | 0 消息，5 个队列与 2 个交换机已删除；`billing.events` 只绑定 account |
| 7 XXL | 任务 14 `accountBalanceMaturity` 登记并启用，手动触发 `matured 0, movements 0, failed 0` |
| 8 启用 | 试跑 3 / 1 / 1 后提交，PLATFORM_ADMIN 30 → 31；新路由未登录 401 |
| 9 验收 | 58/58、31/31（截图 `assets/stage7/browser/` 10 张）、34/34（财务四页截图 `assets/stage7/regression/`）；七个服务日志 ERROR 0、口令 / Token 命中 0 |
| 10 清理 | `--cleanup` 通过；独立对比备份基线一致 |
| 11 记录 | release.md 第 6 节、`CURRENT_IMPLEMENTATION.md` 本需求条目、决定记录状态行、本报告、任务卡执行状态行 |

## 3. 与清单的偏差

- **第 6 步删队列多了一次尝试。** 我给 `rabbitmqctl delete_queue` 加了 `--if-empty` 作为额外防护，quorum 队列不支持该标志，5 个队列都没有删，也没有其他副作用。随后逐个复核 0 消息，再按清单原命令删除。交换机删除不受影响。
- **ledger 分区队列消费者有短暂延迟。** ledger healthy 后立即查询时 `ledger.account.p0`—`p3` 是 0 消费者，约 8 秒后各 1，属于监听容器启动时序，不是缺陷。

## 4. 现场与回退位点

- 当前 mor-test：六个服务为 `subledger-20261019` 镜像，Gateway 未动，30 个容器 healthy。
- 回退：按 release.md 第 5 节执行。旧镜像 ID 与 account 的 `ACCOUNT_LEDGER_SERVICE_URL` 在备份目录 `compose-rollback.yaml` 中；account / ledger 回退前必须先用备份 dump 整库恢复。
- 保留：备份目录、六个候选镜像、验收运行记录 `local-environment/e2e/runtime/subledger-acceptance-20261019144824.json`。Platform 审计表里的验收登录记录不回滚。

## 5. 待主会话处理

1. `delivery.md` 阶段 7 段落由主会话写。
2. `CURRENT_IMPLEMENTATION.md` 第 75 行「account / ledger 明细账与控制科目（2026-10-18 决定已确认，未实施）」已过时。卡片只授权更新本需求条目，所以没有改它，建议主会话合并或删除该行。
3. 后续可选清理，不阻塞：Compose 中 account 的 `ACCOUNT_BILLING_SERVICE_URL` 已无用；account 未设 `PAYTECH_INSTANCE_ID`；account 注释仍提到已删除的 `accountBalanceReconciliation`。
4. 代码与文档尚未提交，git 未做任何改变索引或工作树的操作。

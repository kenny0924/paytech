# G 线报告：阶段 7 mor-test 发布执行（2026-10-19）

结论：**mor-test 发布完成，接口验收 59/59，合成数据已清理并回到备份基线。** G 线完成了第 1 步复核和第 2 步备份。第 3 步 `compose.sh stop account-service` 被本会话的权限分类器拒绝，G 线没有绕过。随后主会话复用 G 线备份，执行了第 3—9 步；G 线负责第 10 步记录。有一项偏差：Snowflake Worker 32 超出允许范围，已改为 26。逐步时间与核对输出见 [release.md](../release/release.md) 第 6 节。

## 1. 已读

`tasks/00-common.md`；`release/release.md` 第 1—5 节；`release/compose-candidate.yaml`；`release/source-overlay.txt`；`release/xxl-job-channel-cost-activation.md`；`release/test-channel-seed.sql`；`tasks/F-report.md`；`verification/stage5-report.md` 第 1—2 节。前序 `requirements/20261019/finance-account明细账与ledger控制科目重构/release/release.md` 第 6 节与其 `tasks/G-report.md`。`test-environment/compose.sh`、`compose.scheduler.yaml` 的 XXL 段。

G 线没有执行 git 写命令，没有调用 Superpowers skill，没有改业务代码。第 3 步以后没有执行任何 mor-test 动作。

## 2. 执行摘要

| 步骤 | 执行方 | 结果 |
|---|---|---|
| 1 复核 | G 线 | 通过。四个运行镜像、四库行数、RabbitMQ、XXL 都与第 2 节一致。Gateway、ops-bff、Platform healthy，最近 5 分钟没有重启 |
| 2 备份 | G 线 | 备份在 `~/Code-MoR-backups/20261019T193439Z-billing-fact-test/`。四库 dump 分别为 9 / 13 / 9 / 31 表，`SHA256SUMS` 校验通过 |
| 3 停 account | 主会话 | 19:37:04Z 停止，Exited（143） |
| 4 SQL | 主会话 | 19:37:14Z 执行。channel-connector 变为 17 表；billing 变为 11 表，含 DEFAULT 日历 1 行 |
| 5 替换 | 主会话 | channel-connector 首次启动失败，原因是 Worker 32。改为 26 后，19:38:57Z healthy。billing、ledger、account 依次在 19:39:46Z、19:40:03Z、19:40:16Z healthy。四个服务 ERROR 0，队列消费者与 XXL 心跳正常 |
| 6 种子 | 主会话 | 1 渠道、1 账号、1 MID，能力行 0 |
| 7 XXL | 主会话 | 分组 13、任务 15 `channelCostVersionActivation` 已启用，手动和定时触发均 200 / 200，`activated 0` |
| 8 验收 | 主会话 | **59/59**，运行记录 `billing-fact-acceptance-20261019234112.json` |
| 9 清理 | 主会话 | `--cleanup --drop-seed` 通过。与 `rowcounts-before.tsv` 对比，差异只有授权 SQL 的结构变化 |
| 10 记录 | G 线 | 更新 release.md 第 6 节、本报告、`delivery.md`、`CURRENT_IMPLEMENTATION.md` |

| 服务 | 部署镜像 ID |
|---|---|
| channel-connector | `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3` |
| billing | `sha256:eeec27f923b7f55f7f3f6b7a8b32528b063fe9a64197615b1e645d3f8cf6540e` |
| ledger | `sha256:0e9390666b839739d7b418783d70682040d761d0e3e5634be86f89076d4de020` |
| account | `sha256:ad0621b12a80731ed550729ff7709383a7dee28017bc736c3ea9e2152a58ce00` |

## 3. 与清单的偏差

1. **Snowflake Worker 由 32 改为 26。** Snowflake 只允许 0—31，F 线选 32 时只核对了占用情况，没有核对上限。主会话改了工作区 `test-environment/compose.applications.yaml`。G 线同步修改了 release.md 第 2.5 节、第 4 节 5.1 和 `compose-candidate.yaml` 注释。这项改动随阶段 8 提交。
2. **HEAD 在复核时已经是 `691b695c`。** 这是 commercial T0 提交，不在四个服务的依赖闭包里，所以没有按 D1 重建，主会话也沿用了候选镜像。
3. **叠加清单有 1 行哈希变化。** 文件是 billing 模块的 changes SQL，它是 Owner 的同步改动，与 REQ SQL 逐字一致，不进 JAR。
4. **XXL 登记顺序。** XXL 登记在验收之后（19:44Z），清单原定在验收之前。第一次手动触发发生在执行器地址注册之前，返回 500 `registry address is empty`；地址出现后重新触发成功。验收不依赖这个任务。

## 4. 现场与回退位点

- 当前 mor-test：四个服务为 `billing-fact-20261019` 镜像。Gateway、Platform、BFF、Web 没有动。RabbitMQ 没有改。
- 回退：按 release.md 第 5 节，旧镜像 ID 在备份目录的 `compose-rollback.yaml`。channel-connector 回退用替身 `7eea94df1e63…`。billing、ledger、account 必须一起回退，并且要先用 dump 整库恢复 `mor_billing`。
- 保留：备份目录（含 `rowcounts-after.tsv` 与 `sql-4.*.out`）、候选镜像、回退替身镜像、验收运行记录。

## 5. 待主会话处理

1. 阶段 8 提交需要包含工作区 Compose 的 channel-connector 段，其中 Worker 为 26。
2. `config/nacos/channel-connector-service.yaml` 还没有补新配置键。mor-test 不加载它，所以不阻塞。
3. 代码与文档都没有提交，git 没有做过任何改变索引或工作树的操作。

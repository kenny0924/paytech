# mor-test 切换到 V2：保留现有数据的部署与迁移 — 交付记录

## 适用阶段

见 [requirement.md](./requirement.md)：阶段 1 方案与演练 → 阶段 2 执行 → 阶段 3 清理，每阶段结束等用户确认。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 0. 登记 | 2026-10-18 | [requirement.md](./requirement.md) | 全部数据迁移；不停 nexus 与 mor-local；容器限额双向优化 | 阶段 1 |
| 1. 方案与演练 | 2026-10-18 | [design/01](./design/01-migration-map.md)、[sql/](./sql/)、[scripts/](./scripts/)、[runbook](./runbook.md)、[阶段 1 报告](./tasks/01-report.md)（演练 150/150） | 进入阶段 2；待确认 1、2、3 按归档方案；4 旧调用方规则保留；5 用评审 SQL；6 FOREIGN 先报再定；7 只迁 acquiring 执行器；10 接受 JVM 取舍；11 旧队列先导出消息数再删；12 指纹不符即停（见[任务卡 02](./tasks/02-execute-on-mor-test.md)） | 阶段 2 |
| 2. 执行 | 2026-10-18 | [阶段 2 报告](./tasks/02-report.md) | 待用户确认 | 阶段 3 清理（须另行确认） |

## 动作授权

| 动作 | 状态 |
|---|---|
| mor-test 备份、停旧服务、重建基础设施、执行迁移、构建镜像、按序启动、登记、验收 | 2026-10-18 已授权（任务卡 02）；root 执行迁移 SQL 由主会话执行 |
| 本地提交（分支 `test-env/v2-migration-stage2`） | 已授权；不提交到 main |
| 推送、合并 | 未授权 |
| 阶段 3：删除旧容器 / 镜像 / 旧库 / 旧账号 / 旧规则 / 归档库 | 未授权 |

## 阶段 2 结果（2026-10-18）

| 项 | 结果 |
|---|---|
| 前置 `plan` | 五旧库表数与结构指纹与演练一致；无 FOREIGN，未使用 `--park-legacy` |
| 备份 | `test-environment/private/backup-20261018/`（不入库）：9 个库逻辑转储、4 个卷快照、`env.test` 与 `private/` 副本、`SHA256SUMS` 18 项 |
| 迁移核对 | 40 号核对 150 项全部一致；4,210 行守恒；14 个 V2 库表集合一致；Platform 57 表 / USD / Target `acquiring-service`；Vault 7 条规则；61 个种子主键；重复执行全部跳过 |
| 镜像 | 18 个 Java 服务 + 2 个 Web 的 `:development` 镜像与 Vault 镜像；镜像 ID 见报告备份目录 `v2-images.txt` |
| 启动 | 按第 13 步顺序，29 个容器 healthy；mor-test 常驻 6.3 GiB（启动后）/ 7.4 GiB（验收后）；Platform 达上限的 92% |
| 登记 | Nacos 14 个服务（Finance 五个服务不用 Nacos）；Gateway 3 个 Target / CLIENT_API 9 条路由；Vault 规则；XXL acquiring 补偿扫描已启动；Webhook 调用方仅 acquiring；旧队列消息数 0；内部 Token OFF |
| 验收 | 入驻 41/41、场景 85/85、工作区 53/53、接入自助 78/78、团队 123/123、Client API 90/90、`--outage` 92/92（第 1 次 90/92，时序）、Vault 烟测通过、迁移数据抽样 8/8；退款 RefundSucceeded 为已知 GAP |
| 未能运行 | `smoke.py`、`risk_rule_configuration_smoke.py`：目录门禁要求 `t_gateway_target` 只有初始 1 行，mor-test 自 2026-10-14 起有 3 行，迁移前已如此（报告第 7 节第 4 项） |
| 日志检查 | 近 60 分钟全部 mor-test 容器 `BEGIN PRIVATE KEY` / `whsec_` / `password=` 为 0 |
| 验收脚本 | V2 目录脚本加 `E2E_TARGET=test`；V1 test 分支与 `register_client_api.py` 停用（报告第 5 节） |

SQL 执行记录：本需求 [sql/](./sql/) 的 10、20、21、30、31、32、33、50 已在 mor-test 执行一次，并重放验证一次；日志在备份目录 `migrate-apply.log`、`migrate-apply-rerun.log` 与 `verify-detail.txt`。

## 回滚

按 [runbook](./runbook.md) 第 11 节：恢复卷快照 + V1 Compose（`b59ec6b8`）+ `container-image-ids.txt` 中的镜像。旧库 `mor_cct`、`mor_commerce`、`mor_risk` 原样保留。

## 遗留

- 阶段 3 清理范围待确认，见[报告](./tasks/02-report.md)第 9 节，另加 RabbitMQ 卷内旧节点目录。
- `smoke.py` 与 Risk rollout 工具的目录门禁需重定。
- 待确认：Platform 内存上限、compose 固定 RabbitMQ `hostname`、`--outage` 判定等待时间。
- 已知生产缺口：Payment 退款（PAY-I04）与 RefundSucceeded。
- `cardOrphanInstrumentPurge` 未登记。
- consumer Target 未新增：mor-test 没有 Customer 路由。

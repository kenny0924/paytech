# test-environment（mor-test）切换到 V2：保留现有数据的部署与迁移

- 日期：2026-10-18；系统：test-environment / 全部 V2 服务；来源：用户要求把 main（V2 单 Reactor，03e81a12 起）部署到 mor-test，同时保留 mor-test 上维护的数据（渠道整合配置等）。
- 用户决定（2026-10-18）：**全部数据迁移**（5 个旧库的全部表按 V2 归属迁入新库）；Docker 内存已调大，**不停 nexus 与 mor-local**；容器限额同时调小（双向优化）。

## 现状（2026-10-18 盘点，mor-test MySQL 8，只起了 mysql 容器）

| 旧库 | 表 | 约行数 | V2 去向 |
|---|---|---:|---|
| mor_platform | 56 | 21484 | mor_platform（原库，升级 Schema 到 V2 57 表） |
| mor_risk | 85 | 3566 | mor_risk_decision（54 表）+ mor_compliance（24 表） |
| mor_cct | 38 | 494 | mor_client_master（29 表）+ mor_commercial（14 表） |
| mor_commerce | 17 | 166 | mor_acquiring（16 表）+ mor_consumer（2 表） |
| mor_vault | 6 | 1664 | mor_vault（原库，升级 Schema） |
| nacos_config / xxl_job | — | — | 保留；XXL 需登记新执行器 |

mor-test 没有 Payment / Finance 数据；payment / channel-connector / card / billing / ledger / account / reconciliation / tax 为空库新建。`.env.test` 已由 `prepare.py` 追加 15 个 V2 键（只追加，不改旧值；备份在主会话 scratchpad）。

## 阶段划分（按 REQUIREMENT_DELIVERY_WORKFLOW，每阶段结束等用户确认）

1. **阶段 1 · 方案与演练（不触碰 mor-test）**：旧表→新表映射（含更名、拆库、新增列默认值）、Platform / Vault Schema 升级 SQL、迁移脚本、compose 内存调优、部署 Runbook、回滚方案；在本机用 mor-test 数据副本（scratchpad 转储）演练一遍，并用 V2 服务以 mock Profile 连演练库做启动与冒烟。交付：`design/`、`sql/`、`scripts/`、演练报告。
2. **阶段 2 · 执行**：备份 mor-test 卷 → 停旧服务 → 执行迁移 → 构建镜像 → 按第 13 步报告第 6 节顺序启动 → 登记 Nacos / XXL / Vault 规则 / Gateway Target → 跑既有验收脚本（Client 入驻、Client Console、Client API、运营链路）→ 写 `delivery.md`，更新 `CURRENT_IMPLEMENTATION.md`。
3. **阶段 3 · 清理**：删除旧服务容器与镜像、旧库（迁移核对通过后）。

## 约束

- 事务公理与 Code-MoR 规范照常；迁移 SQL 为评审后手工执行的纯 SQL；不引入 Flyway。
- 迁移前后行数与关键唯一键核对必须有脚本证据；回滚 = 恢复卷快照 + 旧 compose。
- 任何对 mor-test 的写操作只在阶段 2 并经用户确认后进行。

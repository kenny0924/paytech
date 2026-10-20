# 交付记录：ledger 会计科目分级与维护

| 阶段 | 状态 | 日期 | 说明 |
|---|---|---|---|
| 1 需求评审与范围确认 | 已确认 | 2026-10-19 | [requirement.md](./requirement.md)；Q1—Q4 已决定，R8 可读账户号纳入；用户回复「继续」确认 |
| 2 现状差异与影响分析 | 已确认 | 2026-10-19 | [technique.md 第 1 节](./technique.md)；用户「没问题，按照你的来」：C1—C3 按建议修订，D1—D5 按倾向，阶段 4 排在 billing 重构提交之后 |
| 3 方案、接口与数据设计 | 已完成（按用户连续执行授权） | 2026-10-19 | [technique.md 第 2 节](./technique.md) + [db/](./db/) + [决定记录](../../../agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md)；E1 允许，E2 本期不做「发起试算」按钮 |
| 4 研发实现 | 已完成 | 2026-10-20 | 任务卡 A、B、C、D 与合并核对 R，报告见 [tasks/](./tasks/)；worktree `../Code-MoR-chart2`（基于 `cf39a1ee`），201 个文件，未提交 |
| 5 综合验证与本地验收 | 已完成（范围调整） | 2026-10-20 | 各服务自动测试在专用本机库通过：ledger 294、account 63、BFF 1592、Web 1109、arch-rules 61、PDManer 21；全链路与浏览器验收改在 mor-test 执行（本机 MySQL / RabbitMQ 为多会话共用） |
| 6 test 发布准备 | 已完成 | 2026-10-20 | [release/release.md](./release/release.md) 第 1 节 |
| 7 test 部署与环境验收 | 已完成 | 2026-10-20 | 接口 26/26、浏览器验收通过，见 [release/release.md](./release/release.md)；含 mor-test AE001 演示数据清理与重建（用户 2026-10-19 已同意清理） |
| 8 代码提交与交付归档 | 已完成（本地提交，未推送） | 2026-10-20 | 用户「合并代码吧，页面我看过了」；需求目录、worktree 改动、决定记录与台账本节一次提交，只含本需求文件；用户已复看三级科目树页面 |

## 已确认事实

- 2026-10-19：用户确认 mor-test 现有财务演示数据可以清理；科目需要按正常财务系统的设计分级维护。
- 2026-10-19：用户同意把财务人员可读账户号（R8 / AC-11）纳入本需求；它只用于显示与搜索，不作为对外账户号。
- 演示数据 ID 见 `local-environment/e2e/runtime/finance-seed-20261019221825.json`。

## 连续执行授权（2026-10-19）

- 用户原话：「我准备睡觉了，等下你可以帮我把这个需求做完发到test环境吗？注意别的chat别冲突了」。
- 覆盖范围：阶段 3 收尾（自行确认 E1、E2）、阶段 4 研发、阶段 5 本机验证（含在本需求专用本机库执行 SQL）、阶段 6 发布准备、阶段 7 mor-test 部署（替换 ledger、account、operations-console-bff、Operations Web 镜像，执行 mor_ledger、mor_account、mor_platform SQL，清理 AE001 演示数据并重建）。
- 不覆盖：代码提交、推送、PR、生产或其他环境。
- E1：科目表被采用后允许新增一级科目（只追加）。E2：「发起试算」按钮本期不做，因为需要改 Platform 能力清单并重建 Platform 镜像，今晚多个会话共用 mor-test，避免覆盖；试算平衡页签只展示科目余额（实时）与已有试算记录。
- 冲突控制：代码在独立 worktree `../Code-MoR-chart`（detached，基于 `e174c79e`）实现，不改主工作区；本机测试使用专用库 `mor_ledger_chart`、`mor_account_chart` 与 RabbitMQ vhost `/mor-chart`；Maven 只做 reactor 构建，不 install；与 commercial 会话（code-mor-a3）约定其先发 ops-bff / Web / 注册块 95，本需求构建 ops-bff / Web 时叠加其提交；已通知 code-mor-32、code-mor-85。

## 变更记录

| 时间（UTC） | 环境 | 动作 | 依据 |
|---|---|---|---|
| 2026-10-20 03:06—03:14Z | mor-test | 按原容器恢复被整体停止的 30 个容器；备份三库；清理 10-20 验收数据；两级 → 三级转换；替换 ledger、account、ops-web（tag `chart3-20261020`）；验收 26/26 并保留演示数据 | 用户「按常见财务系统设计成3级，清理数据后重建」「可以启动」；见 release.md 第 7 节 |
| 2026-10-19 23:06—23:30Z | mor-test | 备份三库；停 ledger / account；执行 ledger、account 增量 SQL；替换 ledger、account、ops-bff、ops-web 镜像（tag `chart-20261020`）；重启被 OOM Kill 的 `mor-test-rabbitmq-1`；注册并启用 Platform 块 91；验收并保留 AE001 演示数据 | 用户连续执行授权；详见 [release/release.md](./release/release.md) |
| 2026-10-19 18:56Z | mor-test MySQL | 备份 `mor_ledger`、`mor_account`、`mor_billing` 整库到 `~/Code-MoR-backups/20261019T185625Z-chart-seed-cleanup/`（目录 0700、文件 0600）；按 `finance-seed-20261019221825.json` 用 V3 验收清理脚本删除 AE001 演示数据（日历、2 个期间、账簿、8 个 Owner 账户、8 条批次与分录、8 条计费事实及消费 / 补偿记录）；三库业务表回到 0 行 | 用户 2026-10-19「数据可以清理」；提前到 billing 会话迁移 `mor_billing` 之前执行 |

## 变更请求：STANDARD 改为三级（2026-10-20）

- 用户原话：「为什么还是只有2级科目呢？」→「按常见财务系统设计成3级，清理数据后重建」。
- 影响：technique 2.1 改为三级树（一级 19 不变、新增二级 26、29 个既有科目改为 8 位三级末级，共 74 个）；account 账户类型目录编号与账户号随之变为 8 位科目编号；系统代码与记账规则不变。代码改动见任务卡 [E](./tasks/E-three-level.md)。
- mor-test：先按 [db/mor-test/cleanup-demo-20261020.sql](./db/mor-test/cleanup-demo-20261020.sql) 清理 10-20 验收数据（含演示科目 CLIENT_RESERVE_PAYABLE），再执行一次性转换 [db/mor-test/convert-two-to-three-level.sql](./db/mor-test/convert-two-to-three-level.sql)（已在 mor-test 当前 `mor_ledger` 副本上演练：19 / 26 / 29，`bad_parent` 0），然后替换 ledger、account 镜像并重新验收、重建演示数据。BFF、Web 无代码变化，不重发。
- 仍不包含提交。

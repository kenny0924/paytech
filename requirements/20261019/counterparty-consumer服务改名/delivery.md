# counterparty：consumer 服务改名 交付记录

## 适用阶段（建议，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 完成（用户夜间授权连续推进，2026-10-19）：范围 S1–S7、不做项、AC-01—06 |
| 2. 现状差异与影响分析 | 与阶段 3 合并完成：[technique.md](./technique.md) |
| 3. 方案、接口与数据设计 | 完成：改名映射、迁库 SQL 与恢复方式（[technique.md](./technique.md)、[db/](./db/)） |
| 4. 研发实现 | 完成：分支 `feature/counterparty-rename`，合入 main `c661e9fc` |
| 5. 综合验证与本地验收 | 完成：见下"已完成证据" |
| 6. test 发布准备 | 完成：[release/release.md](./release/release.md) 第 1 节 |
| 7. test 部署与验收 | 完成（2026-10-20 03:19Z—03:24Z）：[release/release.md](./release/release.md) 第 2–3 节 |
| 8. 提交与归档 | 完成：代码 `c661e9fc`、文档提交见下；均为本地提交，未推送 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-20 | 8 后清理 | "先不推送吧，可以直接删除了" | 删除旧库、旧账号、旧容器与旧 XXL 登记（见下"旧资源清理"）；不推送 |
| 2026-10-19 | 1–7 | "我准备睡觉了，等下你可以帮我把这个需求做完发到test环境吗？注意别的chat别冲突了。"：授权本需求阶段 1–7 连续推进（本机迁库、mor-test 部署与迁库），不推送；按惯例完成后本地提交（私有 index，不推送） | 阶段 2–3 合并设计 → 阶段 4–5 agent 在 worktree `Code-MoR-counterparty`（分支 `feature/counterparty-rename`）实现与本机验证 → 门禁合入 → 阶段 6–7 mor-test |
| 2026-10-19 | 立项前讨论 | "可以，counterparty 吧"；Q1–Q3、Q5、Q6"可以"（按建议） | 登记决定记录与本需求，交付阶段 1 评审稿 |

## 当前阶段与下一确认点

阶段 1–8 完成（2026-10-20）。等待用户确认本次结束；推送与旧库 / 旧容器清理需另行授权。

## 已完成证据

- **执行过程：** 2026-10-19 23:00 起执行 agent 在等待后台构建时停住，整夜无进展；2026-10-20 06:37 发现后催促仍无进展，06:58 停止 agent 由主会话接手。agent 已完成的部分：改名改动 140 个文件、本机迁库 SQL 与执行、mor-test SQL 草稿；其余验证、提交、构建与部署由主会话完成。
- **代码：** 145 个文件（rebase 后 146 个，含现行验收脚本 `e2e_http.py` 的私网地址表），合入 main `c661e9fc`（快进，父提交 `e73764d1`）。
- **改名前基线（2026-10-19 23:00）：** acquiring 804、consumer 21、card-test 38、card-core 11，全绿。
- **改名后（2026-10-20）：** 全 Reactor `test-compile` 通过；受影响模块 acquiring **804/804**、counterparty **21/21**、card-test **38/38**、card-core **11/11**、arch-rules **61/61**、instance-manifest **16/16**（rebase 后 arch-rules 61、instance-manifest 16 复跑通过）；PDManer 相关 node 测试 26/26（rebase 后 22/22 子集复跑）。未跑全 Reactor 测试：会清空本机其他服务测试库，与并行会话冲突；foundation 的 Redis / 消息集成测试缺本机环境变量而失败，本需求未改动 foundation。
- **test-environment 脚本测试：** 322 通过 / 14 失败，改名前后失败清单逐项相同（`test_smoke_stage_contract.py` 等既有问题）。
- **AC-01：** `grep -rIl -E 'consumer-service|mor_consumer|consumer_app|paytech-consumer|com\.paytech\.consumer|/consumer/v1|MOR_CONSUMER_DB'` 只剩历史需求目录 20261017 / 20261018（现行的 `e2e_http.py` 已改）、决定记录、design-discuss、`CURRENT_IMPLEMENTATION.md` 历史段落，以及 arch-rules 生成脚本 / 表归属中记录改名映射的注释。
- **本机：** `mor_counterparty` 与 `counterparty_app` 已建（agent 执行），两表 0 = 0；`paytech-counterparty-app` 本机启动 UP，`/counterparty/v1/customers/resolve` 建或取幂等，旧前缀 404，冒烟行已删除。本机 RabbitMQ 容器曾被强杀（137），2026-10-20 06:45 用 `docker start` 原样拉起。
- **mor-test：** 见 [release/release.md](./release/release.md)。

## 旧资源清理（2026-10-20）

- **mor-test：** 删除已停止的 `mor-test-consumer-service-1` 容器与镜像 `mor-test/consumer-service:development`（`sha256:ea962e94…`）；`DROP DATABASE mor_consumer`、`DROP USER consumer_app`；XXL 删除 job 8 及其日志、group 8 与 `consumer-service` 注册行；`.env.test` 删除 `MOR_CONSUMER_DB_PASSWORD`，`compose.sh config --quiet` 通过。删除前 `mor_consumer` 两表均 0 行，备份在 `~/Code-MoR-backups/20261020T031956Z-counterparty-test/`。
- **本机：** 备份后 `DROP DATABASE mor_consumer`（0 行）与 `consumer_app`；本机全链路验收工具 `requirements/20261017/…/verification/local-e2e/run-service.sh` 与 `sql/00-e2e-databases.sql` 改为 counterparty（模块已改名，原样会启动失败），新建空库 `mor_counterparty_v2e2e` 并授权 `counterparty_app`，删除空的 `mor_consumer_v2e2e`。
- 回退能力随之取消：如需回到 consumer，只能从备份恢复库并回退代码与镜像。

## 遗留项

- 推送：用户 2026-10-20 决定先不推送。
- 切片 2（档案与保护、资料提交入口改到 counterparty）与 Gateway Route 另行登记需求。

# reconciliation 渠道资金对账切片 1 交付记录

## 适用阶段（2026-10-20 确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-20，按建议）：[requirement.md](./requirement.md) |
| 2. 现状差异与影响分析 | 已确认（2026-10-20）：[technique.md 第 1 节](./technique.md)；AC-14 按现行做法改写，Q-03 维持 |
| 3. 方案、接口与数据设计 | 已确认（2026-10-20）：[technique.md 第 2 节](./technique.md) + [db/](./db/)（含 D-01 分段导入修订 Q-03） |
| 4. 研发实现 | 已确认（2026-10-20）：T0、A、B、C、D、E1–E3 七线报告见 [tasks/](./tasks/)；实施决定 I-01–I-18 见 [technique.md §2.10](./technique.md)；门禁见下 |
| 5. 综合验证与本地验收 | 已确认（2026-10-21）：[verification/stage5-report.md](./verification/stage5-report.md)（含 §8 D-1 修复与复测）、[tasks/F-report.md](./tasks/F-report.md) |
| 6–7. mor-test 发布与验收 | 已确认（2026-10-21）：mor-test 验收 69/69、浏览器 27/27，执行记录见 [release/release.md 第 8 节](./release/release.md)：发布包 [release/release.md](./release/release.md)、[tasks/G-report.md](./tasks/G-report.md)；阶段 7 执行另行授权 |
| 8. 提交 | 已完成（2026-10-21）：本地提交 `2f5f37f7`（566 个文件，父 `4ab205e0`），未推送；见 [tasks/H-report.md](./tasks/H-report.md) |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-20 | 立项 | "开始登记切片 1 需求吧" | 登记需求目录，交付阶段 1 评审稿（不写代码、不改模型、不执行 SQL、不提交） |
| 2026-10-20 | 1 | "继续"：范围、不做项、AC-01–16 / AC-V1–V6、Q-01–Q-06 按建议 | 进入阶段 2（只写文档） |
| 2026-10-20 | 2 | "继续"：接受现状差异与影响；AC-14 改为 Gateway 路由按权限点拦截 + reconciliation 校验提交人 ≠ 复核人；Q-03 维持 BFF 收 CSV | 进入阶段 3（只写文档与 SQL 文件，不改代码、不执行 SQL） |
| 2026-10-20 | 3 | "继续"：接受阶段 3 设计，含 D-01 上传改分段导入（修订 Q-03） | 进入阶段 4 研发（不提交、不部署；本机 SQL 另行授权） |
| 2026-10-20 | 4 | AskUserQuestion 选择"授权（推荐）"：备份后删 `mor_reconciliation` 18 张旧表、按新 DDL 建 13 张，`mor_billing` 执行 ALTER；只动本机 | 已执行，见下「环境操作记录」 |
| 2026-10-20 | 4 | AskUserQuestion：接受 15 张表（加两张公共消息去重表）并授权本机补建；无订阅方策略选 `FAIL` | 已在本机补建两张表；文档同步（technique I-07、I-08） |
| 2026-10-20 | 4 → 5 | "可以"：接受阶段 4 结果（含 I-15 整单拒绝修订），进入阶段 5；授权本机：备份后重建 `mor_ledger`，`mor_reconciliation_v2e2e` 重建、`mor_billing_v2e2e` ALTER、`mor_platform_v2e2e` 块 123 注册与激活、`mor_vault_e2e` 与本机 Vault 策略生效；不碰 mor-test | 阶段 5 本地验收 |
| 2026-10-21 | 5 | "继续"：接受阶段 5 结果（含 D-1 修复） | 进入阶段 6 mor-test 发布准备（只出发布包与只读核对，不改 mor-test） |
| 2026-10-21 | 6 → 7 | "继续"：接受阶段 6 发布包，授权阶段 7 在 mor-test 按 release.md 第 5 节执行（替换 reconciliation、ledger、billing、platform-support、ops-bff、ops-web，执行四库 SQL、XXL 登记、验收与清理） | 阶段 7（不提交） |
| 2026-10-21 | 7 → 8 | "继续"：接受阶段 7 结果，授权阶段 8 本地提交（不推送） | 阶段 8 |

## 当前阶段与下一确认点

当前：阶段 1—8 完成（2026-10-21）。本地提交 `2f5f37f7`，未推送。**后续材料**（2026-10-21 用户授权，作为单独的本地提交，父 `13c02c87`，提交号 `faf72ff9`）：本文件、需求索引行与台账的提交号登记、`tasks/H-report.md`、`release/demo/**`（演示数据脚本，已在 mor-test 执行）；`test-environment/compose.applications.yaml` 补回 reconciliation-service 的 MQ / Redis / XXL 变量与依赖、ops-bff `OPERATIONS_RECONCILIATION_BASE_URL`（阶段 7 只写在发布覆盖文件里，漏进主 compose；币种会话 2026-10-21 替换 ops-bff 时因此对账页 503 约 10 分钟，已由对方补上；主 compose 已于 2026-10-21 修正，`compose.sh config --quiet` 通过）。

## 变更记录

| 日期 | 变了什么 | 影响 |
|---|---|---|
| 2026-10-20 | Q-03 由"BFF 收 CSV（≤ 5 MB）"改为分段 JSON 导入（D-01，Gateway 64 KiB 绝对上限） | AC-V2 上传交互（分段进度）；阶段 1 范围不变 |
| 2026-10-20 | AC-04 措辞："停在补偿 ERROR" 改为 "重投后进 DLQ 留存"，与已确认的 technique §2.4 一致 | 仅措辞；按新措辞验证 |
| 2026-10-20 | 表数 13 → 15（公共消息去重表） | AC-01、需求 §3.1、决定第 15 条与设计稿 6.1 加实施修订注 |
| 2026-10-20 | AC-14 由"BFF 与 reconciliation 两层都校验"改为"Gateway 路由按权限点拦截 + reconciliation 校验提交人 ≠ 复核人" | 阶段 1 验收标准措辞；无需重验 |

## 环境操作记录

| 时间 | 环境 | 动作 | 依据 |
|---|---|---|---|
| 2026-10-20 15:49Z | 本机 local-environment MySQL | 备份 `mor_reconciliation`（18 表，全部 0 行）、`mor_billing`（`t_billing_fact_ref` 0 行）到 `~/Code-MoR-backups/20261020T154908Z-recon-slice1-local/`；`DROP TABLE` 旧 18 张（不删库，保留 `reconciliation_app` 库级授权）后执行 [db/reconciliation/ddl.sql](./db/reconciliation/ddl.sql)（13 表）；`mor_billing` 执行 [db/billing/ddl.sql](./db/billing/ddl.sql)（引用表 5 → 7 列）。`mor_billing_v2e2e` 等 e2e 副本未动 | 用户 2026-10-20 授权 |
| 2026-10-20 22:49Z | 本机 local-environment MySQL | 备份 `mor_ledger`（`~/Code-MoR-backups/20261020T154908Z-recon-slice1-local/mor_ledger-before-rebuild.sql`）后 `DROP TABLE` 全部旧表，执行 `paytech/ledger/docs/database/sql/ledger-schema.sql` + `ledger-initial-data.sql`：33 表、初始化 123 行 | 用户 2026-10-20 阶段 5 授权 |
| 2026-10-20 | 本机 e2e 库 | 备份 `mor_reconciliation_v2e2e`、`mor_billing_v2e2e`、`mor_platform_v2e2e`、`mor_vault` 到同目录 `stage5/`；`mor_reconciliation_v2e2e`（原 0 表）执行 db/reconciliation/ddl.sql → 15 表；`mor_billing_v2e2e` 执行 db/billing/ddl.sql（引用表 7 列）。 | 同上 |
| 2026-10-20 | 本机 e2e 库 / 本机 Vault | `mor_platform_v2e2e` 执行 db/platform-support/registration-dml.sql（块 123 休眠行）与 activation-local.sql（影响 38 / 10 / 3 / 1）；本机 `mor_vault` 执行 db/vault/dml.sql（数据类型 `CASH_ACCOUNT_NUMBER_V1` + reconciliation PROTECT 规则 + 策略变更记录） | 首次被工具权限拦截，用户追问"你能执行吗"后执行 |
| 2026-10-20 | 本机 local-environment MySQL | `mor_reconciliation` 执行 [db/reconciliation/ddl.sql](./db/reconciliation/ddl.sql) 中 `t_consumed_event`、`t_consumed_aggregate_version` 两段 CREATE（13 → 15 表） | 用户 2026-10-20 授权 |
| 2026-10-21 03:11–03:3xZ | mor-test | 按 release.md 第 5 节：备份 `~/Code-MoR-backups/20261021T031139Z-recon-slice1-mor-test/`；`mor_billing` ALTER；替换 ledger `b84f8f97…`、billing `b773d2cd…`；`mor_reconciliation` 删旧 18 表（全空）建 15 表；`mor_vault` 3 行；替换 reconciliation `f181a04c…`；`mor_platform` 块 123 注册 + 激活（38 / 10 / 3 / 1）；替换 platform `79e33d43…`、ops-bff `9520a545…`、ops-web `edfb6628…`；XXL 分组 17、任务 20 / 21；验收 69/69、浏览器 27/27；清理回基线 | 用户 2026-10-21 阶段 7 授权 |
| 2026-10-21 03:46—03:52Z | mor-test 演示数据 | 用户要求"帮我造一些数据"：[release/demo/demo_seed.py](./release/demo/demo_seed.py) `--backdate-exceptions`，记录 `~/Code-MoR-backups/recon-demo/demo-20261021074643.json`（0600）。新建接入方与渠道 `DEMO_NI`（AE001 技术账号、2 个 MID）、清算账户 `NI-AE001-USD` / `NI-AE001-USD-B`、结算户（末四位 6021，ACTIVE）与出款户（7710，待复核）、平台流水 17、渠道账单 7（已到账 / 在途 / 匹配中 / 被拒 / 作废）、银行对账单 4（连续 2、被拒 1、断档 1）、各状态异常与处置；未结异常 `opened_time` 前移以填满账龄段。演示复核人 `DEMO_RECON_CHECKER_202610210746` 已停用（脚本最后一步按 ID 读账户的路由不存在而失败，主会话改为列表查找后单独停用并修正脚本；`--resume` 不跳过已结案步骤，勿续跑）。数据保留；删除用 `demo_cleanup.py <记录> [--dry-run]`。事件无订阅方，补偿任务留存 FAILED（I-08） | 用户 2026-10-21 要求 |

## 测试 / 验收证据

**阶段 4 门禁（2026-10-20 本机，JDK 25，`./mvnw -B -o`，local-environment）**

| 范围 | 结果 | 说明 |
|---|---|---|
| reconciliation-test | 90/90 | 主会话按 surefire 报告复核；含 Architecture、ModuleBoundary、持久化映射、出站客户端、消息接线与 B / C 全部业务用例（S1 端到端）；跑后库为空 |
| billing-test | 81/81 | E2 线 |
| ledger-test | 未跑 | 本机 `mor_ledger` 落后（缺 `t_chart_account_change`），需授权重建后补跑；改动只有两行透传 |
| operations-console-bff | 全模块 1939（D 线）；C 线完成后主会话复跑 `ReconciliationBrowserAutoTest` + `FinanceBillingFactBrowserAutoTest` 117/117 | |
| operations-console-web | 1194/1194，`tsc --noEmit` 通过 | 主会话复跑 |
| arch-rules | `TableOwnershipAutoTest` 等 10/10 | `generate-table-ownership --check`、`generate-oe-client-schemas --check` 通过 |
| scripts/pdmaner | reconciliation-model、billing-model、oe-client-model 24/24 | finance-model 2 项为 HEAD 既有漂移（tax、payout） |
| requirements-site | 137 例 127 通过 | 10 个失败与改动前相同 |
| 全仓 reactor | 未跑 | 共享本机库 |

**阶段 5（2026-10-20 / 21 本机）**

| 范围 | 结果 | 说明 |
|---|---|---|
| reconciliation-test | 93/93（D-1 修复后；首轮 90/90） | 主会话按 surefire 复核 |
| ledger-test | 319/319 | 本机 `mor_ledger` 重建后补跑 |
| billing-test | 81/81 | |
| account-test | 本机环境问题（缺 `t_owner_account_no_sequence`），与本需求无关 | 见遗留项 |
| 全链路 S1（Nginx → Gateway → BFF → reconciliation，两名合成人员） | 78 + 6 项通过 | AC-02、03、04、05、08–12、14、F5；AC-06、07 其余分支由模块测试覆盖；AC-13 本机无 XXL，留 mor-test |
| 浏览器 AC-V1–V6 | 通过，截图 18 张 | `verification/screenshots/` |
| 性能 2 万行 | 首轮匹配 100.8 s 超时（D-1）；修复后 4.86 s 一次完成，1,000 行 0.67 s | 改为批量读写，业务规则不变 |
| 清理 | `mor_reconciliation_v2e2e` 回 0；合成渠道 / MID / 人员已删；临时 vhost 已删；Vault 记录按先例保留计数 | |

## 部署 / 恢复方案及 SQL 执行记录入口

无。

## 提交记录

| 提交 | 内容 |
|---|---|
| `2f5f37f7` | feat(reconciliation)：渠道资金对账切片 1，部署 mor-test（父 `4ab205e0`；566 个文件；私有索引构建，11 个与商务会话共用的文件、AGENTS.md、两个 README 与台账只取本需求改动块；`configurationModel.test.ts` 页数取 BASE 48 + 7 = 55，`TableOwnershipAutoTest` 取 BASE − 13 + 10） |
| （后续） | docs：登记提交号、H 报告、演示数据脚本 |

未推送。

## 遗留项及后续需求

- 发布前端后，已打开的控制台标签页仍跑旧包，会静默略过未登记的新菜单；需刷新或重新登录（2026-10-21 用户在 mor-test 遇到，刷新后正常）。
- `table-ownership.yaml` 的 `--check` 在 BASE `4ab205e0` 即有一行差异（`t_channel_config_change` 手写、生成器在商务提交 6570ef73 才补），本提交沿袭，非本需求引入。

- 2026-10-21 03:25:35Z 商务会话在本需求之后替换 mor-test ops-web 为 `sha256:333e7f2b…`（`capability-20261021d` = 本需求 `edfb6628` 源 + 其 9 个 `src/commercial` 文件），回滚目标为本需求 `edfb6628`。本需求下次发 ops-web 以它为基线。

- 2026-10-21 商务会话预告：其用户确认验收反馈后，可能先于本需求再发一版 ops-web（只改 `operations-console-web/src/commercial/**`）；届时本需求的 ops-web 候选需在其新基线上重建，ops-bff、platform 候选不受影响。阶段 7 执行前先核对。

- 共享文件 `platform-support-initial-data.sql` 三方合并时丢了块 123 结尾 `COMMIT;`，主会话 2026-10-21 已补回（第 5272 行），已通知商务会话；mor-test 执行用的是带 COMMIT 的 `registration-dml.sql`。
- reconciliation 在 mor-test 用小档内存（堆 160m），2 万行性能实测是 512m；mor-test 验收只用小数据，大账单前需评估内存档位。

- D-2（补偿组件通用行为）：处理器超时后补偿框架只中断线程，处理线程仍可提交，且中断导致 Redis 锁释放失败、等 Lease 过期；交 Foundation Owner 另行评估（阶段 5 报告 §6）。
- 本机 `mor_account` 缺 `t_owner_account_no_sequence`（`e73764d1` 引入），account-test 本机 57 失败为环境问题，与本需求无关；重建需另行授权。

- requirements-site：FIN-12、FIN-24、E2E-08 及 `global-use-case-sequences.mjs` 的 E2E-08 时序仍是旧 Journal 模型（被测试正则固定），本期只加了取代说明；完整改写连同测试另立（E3 报告）。

- 阶段 6 构建 ops-web：商务会话（code-mor-98）已把含其 9 个文件的 ops-web 发布到 mor-test（`sha256:5853565b…`，tag `commercial-20261020c`，基于 main 5f12cf3e，排除了本需求文件），以它为上一版；从主工作区构建自然包含那 9 个文件，对方确认无需再协调。
- 2026-10-21 商务会话（code-mor-98）在 mor-test 发布「commercial 开通与定价模型调整」：commercial-service、operations-console-bff、operations-console-web、platform-support-service 改为 tag `capability-20261021`（基于 main `4ab205e0`，不含本需求），并执行 `mor_platform` 块 124。本需求发布 ops-bff / ops-web / platform 时以这批镜像为上一版；它在主工作区的改动合入前会再通知。阶段 7 前与其确认窗口，避免同时动这四个服务。
- 2026-10-21 21:02Z 商务会话已发布完成：commercial `4a0c4531…`、ops-bff `9708ccdb…`、ops-web `abab7475…`、platform `95348566…`（tag `capability-20261021`），块 124 已激活——本需求以它们为上一版。其改动已合入主工作区（未提交），与本需求重叠的 11 个共享文件已三方合并（ops-web `configurationModel.test.ts`、`ConsolePage.tsx`、`pageCapabilities.ts`；ops-bff `OperationsConsoleBffApplicationAutoTest.java`、`PlatformOperationsSimulator.java`、`README.md`；`platform-support-initial-data.sql`（块 123 在前、124 在后）、`console-resource-capabilities.json`；arch-rules `generate-table-ownership.mjs`、`table-ownership.yaml`、`TableOwnershipAutoTest.java`）。主会话核对：块 123 行仍在、对账 componentKey 仍在、表归属 `--check` 通过。阶段 8 提交须按 hunk 拆分，只取本需求部分。
- 2026-10-21 21:53:50Z 商务会话 commercial 最终镜像 `sha256:c6c9b8f663ae…`（tag `capability-20261021c`）；ops-bff、ops-web、platform 不变。本需求不替换 commercial。
- 本机 `mor_ledger` 落后（缺 `t_chart_account_change`、初始化数据 78/123），ledger 测试补跑前需单独授权重建（E2 报告）。

- billing 需求 B（支付事实）：PAYMENT / REFUND 事实生成、接收四类对账事件、ledger 规则包归类码映射（Q-01）。
- Payment：`PaymentSucceeded` 补 `channelTransactionRefId`、`referenceType`；`RefundSucceeded`。
- 切片 2、3 见 [design.md 第 10 节](../../../design-discuss/reconciliation/design.md)。

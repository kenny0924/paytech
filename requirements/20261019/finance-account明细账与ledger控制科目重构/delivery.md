# account 明细账与 ledger 控制科目重构交付记录

## 适用阶段（建议，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-19，"可以，都按建议"：Q-01—Q-09 全部按建议） |
| 2. 现状差异与影响分析 | 已确认（2026-10-19，与阶段 1 合并确认），见 [technique.md 第 1 节](./technique.md#1-现状差异能力缺口与逐系统影响阶段-2) |
| 3. 方案、接口与数据设计 | 评审稿已交付（2026-10-19）：[technique.md 第 2 节](./technique.md#2-方案接口与数据设计阶段-32026-10-19-评审稿)、[db/account/ddl.sql](./db/account/ddl.sql)、[db/ledger/ddl.sql](./db/ledger/ddl.sql)、[db/ledger/dml.sql](./db/ledger/dml.sql)、Pencil 画板 04 / 05 / 07 / 08 / 09 / 11 修改与 13 / 14 / 15 新增（`operations-console-web/docs/design.pen`，未提交；导出见 [assets/stage3/pencil](./assets/stage3/pencil/)） |
| 4. 研发实现 | 已确认（2026-10-19，"继续"：接受结果与 3 项偏离的建议）：T0 + A / B / C / D 五个 Opus 5.5 agent，主会话（Fable 5.1）任务卡、门禁、跨线核对与独立重跑；用户授权后 A / B 重建了本机 `mor_account` / `mor_ledger`（local-environment）；未提交，未动 mor-test。见下方「阶段 4 研发结果」 |
| 5. 综合验证与本地验收 | 已确认（2026-10-19，"继续下一阶段"）：本机 V2 全链路接口 58/58、浏览器 31/31（C-1 修复后重跑）、只读回归 27/34（7 页为未启动的 risk-decision / compliance 503）；清理后 49 张表回到基线。见 [verification/stage5-report.md](./verification/stage5-report.md) |
| 6. test 发布准备 | 已确认（2026-10-19，"继续"：D1=A、D2=A、D4=A、D5 按建议）：[release/release.md](./release/release.md)（只读核对、D1–D5、执行顺序、恢复）、[compose-candidate.yaml](./release/compose-candidate.yaml)（6 个镜像 tag `subledger-20261019` 固定 ID）、[source-overlay.txt](./release/source-overlay.txt)（HEAD `2bae8597` + 441 文件）、验收脚本已支持 `E2E_TARGET=test`；mor-test 未改 |
| 7. test 部署与验收 | 已确认（2026-10-19，"提交"）：release.md 第 4 节 11 步全部通过，接口 58/58、浏览器 31/31、全页面回归 34/34；备份 `~/Code-MoR-backups/20261019T104222Z-subledger-test/`；六个镜像 tag `subledger-20261019` 已替换并 healthy；`ledger.billing.*` 旧队列与交换机已删；XXL 任务 14 `accountBalanceMaturity` 已登记启用；块 99 已激活（PLATFORM_ADMIN v31）；合成数据已清理回基线。见 [release.md 第 6 节](./release/release.md#6-执行记录) 与 [tasks/G-report.md](./tasks/G-report.md) |
| 8. 提交 | 已完成（2026-10-19）：`080909fb`（528 个文件：account / ledger / billing / Foundation 事件目录 / arch-rules / scripts/pdmaner / Operations BFF / Web / Platform 注册 SQL / test-environment / requirements-site / system-design / 决定与台账 / 需求目录，含 `design.pen`）+ 归档提交（本记录与台账）；私有索引构建，不含其他会话的 compliance 改动与未纳入范围的 `system-design/architecture-design.pen`；未推送 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-19 | 8 | "提交"：确认阶段 7 结果并授权本地提交 | 私有索引提交 `080909fb` 与归档提交；不推送、不建分支、不发 PR |
| 2026-10-19 | 7 | "继续部署"：授权按 release.md 第 4 节执行阶段 7（首次启动被权限分类器拒绝，第二次通过） | G agent 执行 11 步并写执行记录、台账与决定记录状态 |
| 2026-10-19 | 6 | "继续"：确认发布包，D1=A（HEAD `2bae8597` + 441 文件）、D2=A（验收后清理）、D4=A（登记并启用 `accountBalanceMaturity`）、D5 计数为 0 则删 | 阶段 7：按 release.md 第 4 节在 mor-test 备份、停 account / ledger、执行四份 SQL、替换六个镜像、删旧队列、XXL 登记、启用、验收、清理、更新台账；不提交。**主会话的 agent 启动与部署命令被 Claude Code 权限分类器以「修改共享资源」拒绝，阶段 7 待用户另行安排执行方式** |
| 2026-10-19 | 5 | "继续下一阶段"：接受阶段 5 结果 | 阶段 6 mor-test 发布准备：只读核对与发布清单、镜像构建，不改 mor-test 环境、不执行 SQL、不提交 |
| 2026-10-19 | 4 | "继续"：接受阶段 4 结果；偏离 1（迟到事实过账日期取下一开放期间起始日）、2（保留 `PayableBalanceMatured`）、3（聚合 ID 用 `.`）按建议接受 | 阶段 5 本机综合验证：对 `mor_account_v2e2e` / `mor_ledger_v2e2e` 执行变更 SQL、对 `mor_platform_v2e2e` 执行块 99 注册与本机激活 SQL、用新 JAR 重启本机 billing / account / ledger / Platform / Operations BFF / Gateway 与 Web 开发服务器、接口与浏览器验收并清理合成数据；不涉及 mor-test、不提交 |
| 2026-10-19 | 3 | "可以"：确认阶段 3 方案、接口、SQL 与画板 | 阶段 4 研发（Opus 5.5 多 agent）；用户随后回答"授权（推荐）"：允许 agent 在阶段 4 内用 root 重建本机 local-environment 的 `mor_account`、`mor_ledger`（只有测试数据） |
| 2026-10-19 | 1 + 2 | "可以，都按建议，进阶段 3"：确认范围、AC-01—20 与 Q-01—Q-09 全部建议项 | 阶段 3 设计：只写文档、SQL 文件与画板，不改业务代码、不执行 SQL |
| 2026-10-19 | 立项 | "可以，开始一个重构任务吧，operations console也改一下。设计和分析用Fable5.1模型，做实际的重构任务的时候开多agents用opus5.5的模型" | 登记需求并交付阶段 1、2 评审稿 |

## 当前阶段与下一确认点

全部阶段完成（2026-10-19）。代码与文档已本地提交 `080909fb` 及归档提交，未推送；后续切片见「遗留项及后续需求」。主会话独立核对：六个 mor-test 容器 healthy 且镜像 ID 与候选一致；`/mor-test` 只剩 `account.billing.*` 与 `ledger.account.*` 队列（各 1 消费者），旧 `ledger.billing.*` 已不存在。阶段 5 记录：追加本机动作（主会话批准，范围同类）：`mor_billing_v2e2e` 原为空库，按 billing 完整 Schema 与初始化数据建表以启动本机 billing。已授权动作：对本机 V2 全链路库 `mor_account_v2e2e`、`mor_ledger_v2e2e` 执行本需求变更 SQL（`db/account/ddl.sql`、`db/ledger/ddl.sql` + `dml.sql`）、对 `mor_platform_v2e2e` 执行块 99 注册与 `db/platform-support/activation-local.sql`、用新 JAR 重启本机 billing / account / ledger / Platform / Operations BFF / Gateway 与 Web 开发服务器、接口与浏览器验收并清理合成数据。

## 阶段 4 研发结果（2026-10-19）

执行方式：T0 合同先行（account-api、ledger-api、billing 载荷与进度字段、事件目录、表归属），合入后 A（account）、B（ledger + PDManer 脱离生成器）、C（Operations BFF + Web）、D（Platform 注册、Compose、requirements-site、文档回写）并行；主会话写任务卡（`tasks/`）、裁决 agent 提出的 6 个设计问题、跨线一致性核对、独立重跑。各 agent 报告：`tasks/T0-report.md`、`A-report.md`、`B-report.md`、`C-report.md`、`D-report.md`。未提交；只按授权重建了本机 `mor_account`（9 表）与 `mor_ledger`（31 表、规则行 36）；mor-test 未动。

| 线 | 改动摘要 | 主会话独立重跑（JDK 25，`-o`，`-am`） |
|---|---|---|
| T0 | account-api：`AccountPostedPayload` + 3 个新 API（桶移动、入账批次 / 进度 / 重建、Owner 账户与流水查询）、`OwnerAccountType` 目录、`OwnerType` 收窄、删 `BalanceBookApi` / `BalanceMovedPayload`；ledger-api：`LedgerOwnerType` / `RuleOwnerRole`、分录响应加 `accountPostingId` / `accountPostingSequence` / `accountEntryId`、`PostingStreamCommandApi`、删 `account-totals` 与 `TrialExceptionApi`；billing：载荷 `balanceAvailableTime`、`publishedFactCount`、详情 `paymentSucceededTime`（B 线补）；`events.yaml` 删 `BalanceMoved` 加 `AccountPosted`（`EconomicFactRecorded` 只剩 account）；表归属生成器 + design/03 + `TableOwnershipAutoTest` | billing-test **90/90**；arch-rules **60/60**；表归属 `--check` current |
| A account | 9 张表（Schema、变更 SQL、测试 DDL、`account.pdma.json` 直接维护）；入账批次 + 流序号 + 流水 + 冲回行 + 补偿事件发布 `AccountPosted`；桶移动命令；成熟 Job `accountBalanceMaturity`；按流重建；进度 / 账户 / 流水 / 批次查询；删 ledger / billing 客户端、日终核对、差异表；README | account-test **58/58**；`account-model.test.mjs` 4/4；`oe-client-model.test.mjs` 10/10 |
| B ledger | `AccountPosted` 严格有序 HANDLER_MANAGED 消费 + 首条序号校验 + billing 事实读取；控制科目过账、替代净额、SKIPPED 冲回行、桶移动只推进；`catch-up` 追平；关账 `CHECK_ACCOUNT_POSTING_PROGRESS` 两段检查；身份矩阵 / 规则版本 / 人工调整改控制科目规则；删 `PROJECTION_REBUILD` 链路与 `account-totals`；Schema / 变更 SQL / 初始化 DML（规则行 86 → 78 保留行）；`ledger.pdma.json` 脱离生成器 + `ledger-model.test.mjs`；README | ledger-test **234/234**；`ledger-model.test.mjs` 通过 |
| C 控制台 | BFF：Owner 五值、分录新字段透传、`OWNER_ACCOUNT_NOT_FOUND`、3 条明细账路由；Web：账户表单 / 列表、分录抽屉（account 批次 + 流水链接）、余额错误、新页面「明细账查询」（账户列表 → 流水 → 批次抽屉）、页面计数 34 | bff-test **1497/1497**；Web **1086/1086**，`build` 通过 |
| D 注册与文档 | Platform 初始化 SQL 块 99（39 行，休眠）+ 能力目录 +1 + 阅读副本 + 本机 / mor-test 激活与停用 SQL；Compose 删 `ACCOUNT_LEDGER_SERVICE_URL`；requirements-site 冲突条目与钉住断言；设计文档（9 表、`.` 分隔聚合 ID、追平）、决定记录状态、台账 | Platform 3 个测试类 **44/44**；requirements-site 127/137（失败 10 例与 HEAD 相同）；`scripts/pdmaner` 全套 515/566，失败 50 例与 HEAD 逐条相同（cf4a429c 漂移等既有问题） |

跨线一致性核对（主会话）：BFF 3 条新路由 = Web 路径常量 = 块 99 Gateway 路由与 Query 白名单；`LedgerOwnerType` 五值在 ledger-api 正则、BFF 正则、Web `ownerTypes` 一致（BFF 以测试断言正则等于枚举）；account / ledger 的 `@DomainEventHandler` 与 `events.yaml` 一致；ledger 读取的载荷字段全部存在于 `AccountPostedPayload`；改动路径全部在各卡允许范围内（T0 额外改了 `YamlEventCatalogAutoTest`，B 额外改了 billing 响应体两处，均经主会话批准）。

**需要用户裁决的偏离（3 项）：**

1. **迟到事实的过账日期（B，决定第 12 条 / 设计 8.9 / technique 2.1）：** 设计写"记入下一个开放期间并保留原业务日期"，但现行试算与损益结转按 `posting_date` 区间取数，保留旧日期会让迟到分录落入下一期期初且永不结转。已实现为：顺延到其后最早的 OPEN 期间，`posting_date` 取该期间开始日并记 WARN，业务日期经 `economic_fact_id` 追溯。建议接受并回写决定记录与设计文档；若坚持保留业务日期，需把试算与结转改为按会计期间取数（另排期）。
2. **`PayableBalanceMatured` 保留（D，Q-07）：** Commerce 仍订阅该事件推进订单资金可用性，2026-10-18 决定未覆盖；requirements-site 中保留，只把发布方改为 account 到期桶移动。建议保留，后续 account 实现该事件时另立切片。
3. **聚合 ID 分隔符改 `.`（T0）：** Foundation 聚合 ID 不允许 `:`；已回写技术方案与设计文档，决定记录未提及该细节无需改。建议接受。

其他已按主会话裁决落地、仅告知：SKIPPED 批次可带冲回行且 ledger 照抄（A / B）；billing 详情接口补 `paymentSucceededTime`（B）；ledger 规则行不得引用控制科目（B）；成熟 Job 不封顶 PENDING、不跳过 FROZEN（A，按设计"净贷方金额"实现）；account 发布端 `unroutable-policy: FAIL`，部署时 ledger 须先于或同时于 account 启动（阶段 6 注意）；明细账流水页未做时间筛选、来源列无事件键（C，account 合同无字段，画板 14 相应从简）。

验收映射：AC-01 / 02 / 03 / 05 / 06 / 11 account-test（消费、批次、冲回、桶移动、成熟、重建）；AC-03 / 04 / 07 / 08 / 09 / 10 ledger-test（控制科目、平衡、两段检查、追平、顺延、人工调整与 CLOSED 告警）；AC-12 模块边界测试 + arch-rules + 事件目录测试；AC-13 本节各套件；AC-14 / 15 / 16 / 17 BFF + Web 测试与源码核对；AC-18 块 99 + 阅读副本；AC-19 / 20 留阶段 5 浏览器验收（C 未做浏览器走查）。

环境注意：Pencil 画板改动仍只在 Pen 应用内存中（`operations-console-web/docs/design.pen` 磁盘时间未变），请在 Pen 中保存后再提交。

## 阶段 5 本机验证（2026-10-19）

执行：Opus 5.5 agent E 按 `tasks/E-local-verification.md`（备份 → SQL → 构建与重启 → 接口 / 浏览器 / 回归 → 清理），主会话裁决发现项并复核截图。详见 [verification/stage5-report.md](./verification/stage5-report.md)。

| 项 | 结果 |
|---|---|
| 备份 | `local-environment/e2e/backup/20261019133614/`（account / ledger / platform / billing 四库） |
| SQL | account 9 表（旧 11b 合成数据随表删除，已备份）；ledger 31 表、`account_entry_id` + 索引、规则行 36；Platform 块 99 共 39 行并激活 3 路由 + 1 菜单；billing 空库按完整 Schema 建 9 表（追加授权） |
| 重启 | platform → ledger → account → ops-bff → gateway → ops-web（vite 18301），billing 建库后启动；全部 UP |
| 接口验收 | 58/58：基础资料与五值账户、MANUAL_ADJUSTMENT → account 批次 / 流水 / 进度 → ledger 控制科目与 `accountEntryId` → 余额查询；替代事实冲回与净额；catch-up；关账 7 步含 `CHECK_ACCOUNT_POSTING_PROGRESS` 成功；人工调整拒绝控制科目；按流重建；旧接口 404；注册行数 |
| 浏览器 | 31/31（画板 04 / 05 / 07 / 08 / 09 / 10 / 11 / 13 / 14 / 15 + C-1 修复后新增一条），截图 `assets/stage5/browser/` |
| 清理 | billing / account / ledger 49 张表回到基线 |
| N/A 或部分 | AC-05 桶移动成功路径、AC-06 到期、AC-08 卡住续跑、AC-09 顺延、AC-10 FROZEN / CLOSED 告警：本机无数据来源，由 auto 测试覆盖；AC-13 属阶段 4 门禁 |

发现与处置：D-1 ledger 变更 SQL 两条无效 DELETE（主会话已修两处文件，本机补跑）；C-1 批次抽屉进入流水页摘要缺方向与净余额（C 线已修，重验通过）；AC-14 状态码口径回写需求（Body 422）；E-1 `/mor-e2e` 与 mor-test 仍有 11b 的 `ledger.billing.*` 队列绑定在 `billing.events`，阶段 6 发布清单须删除旧队列与绑定；E-2 `run-service.sh jars` 遇缺失 data-service JAR 报错（既有）。

## 变更记录

（无）

## 测试 / 验收证据

阶段 4 独立重跑与阶段 5 / 7 验收计数见上文各阶段记录；脚本与截图在 `verification/`、`assets/stage5/`、`assets/stage7/`。

## 部署 / 恢复方案及 SQL 执行记录入口

[release/release.md](./release/release.md)（第 4 节顺序、第 5 节恢复、第 6 节执行记录）；本机 e2e 库的 SQL 执行见 [verification/stage5-report.md](./verification/stage5-report.md) 第 1 节。

## 提交记录

| 仓库 | 提交 | 内容 |
|---|---|---|
| Code-MoR（main，本地） | `080909fb` | 本需求全部代码、SQL、模型、画板、文档（528 个文件） |
| Code-MoR（main，本地） | 归档提交 | 本记录与台账的提交号登记 |

推送状态：未推送。

## 遗留项及后续需求

- 退款与争议入账（设计 8.6）、payout / tax 接入 account 桶移动、渠道结算到账 / 出款完成 / 缴税完成登记经济事实：各自切片。
- requirements-site Finance 目录按 billing / account / ledger 三系统整体重写：另立文档需求（Q-07）。
- `PayableBalanceMatured`（Commerce 订阅）后续由 account 到期桶移动后发布：另立切片（阶段 4 D 线待决项，用户已接受保留）。
- Compose 可选清理：account 的 `ACCOUNT_BILLING_SERVICE_URL` 已无用、`PAYTECH_INSTANCE_ID` 未设（事件 instanceId 为 main）。
- 决定第 12 条过账日期规则已按阶段 4 偏离 1 修订（取下一开放期间开始日）。

## 实现 / 验证 / 部署状态

以 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 中 Finance / account / ledger 条目为准。

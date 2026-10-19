# D 线报告（架构规则、PDManer 生成器、文档与站点）

日期：2026-10-19。执行者：D 线。没有执行任何 git 写命令，没有对数据库执行 SQL，没有调用 Superpowers skill。只改了 D 卡「允许改动的路径」，以及主会话追加授权的三个文件（两个 arch-rules Java fixture、channel-connector Schema），见第 7 节。

## 1. 已读规范与设计

- `tasks/00-common.md`、`tasks/D-rules-docs.md`
- `tasks/T0-report.md`（第 1–3 节）、`A-report.md`（第 5–7 节）、`B-report.md`（第 5–9 节，含四表字段清单）、`C-report.md`（第 5–6 节）
- `technique.md` 第 2 节全文（重点 2.3、2.6）、`db/channel-connector/ddl.sql`、`db/billing/ddl.sql`（定价与明细行两表）
- `AGENTS.md`（会话注入全文）、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 1–4 节、`agent-docs/DATABASE_DESIGN_GUIDE.md` 第 4、5、5.1、11–15 节
- 决定 `agent-docs/decisions/2026-10-19-billing-fact-engine.md` 全文

本线不实现接口，不涉及 Processor、Context 或事务，第 11 节自查见第 4 节。

## 2. 改动文件

| 动作 | 文件 | 内容 |
|---|---|---|
| 修改 | `paytech/tools/arch-rules/scripts/generate-table-ownership.mjs` | 新增 `stepRemovedTables`（billing 删经济事实三表与渠道成本四表），在 §4.2 计数核对之后、step-added 之前应用；step-added 加 billing 9 张与 channel-connector 4 张；头注释增加 step-removed 计数 |
| 重生成 | `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` | billing 11 张，channel-connector 17 张；265 + 56 − 7 + 20 |
| 修改（追加授权） | `paytech/tools/arch-rules/src/test/java/com/paytech/arch/fixture/r12domain/pass/ledgercore/EconomicFactPostingHandler.java`、`.../r12domain/violation/ledgerapp/AppFactHandler.java` | 注解 `eventType` 改为 `BillingFactRecorded`，类名不动 |
| 修改 | `paytech/tools/arch-rules/src/test/resources/fixtures/r12domain/foundation/paytech-messaging/catalog/events.yaml` | 事件名改为 `BillingFactRecorded` |
| 覆盖（追加授权） | `paytech/channel-connector/docs/database/sql/channel-connector-schema.sql` | 用 `renderSchema` 渲染结果覆盖。覆盖前 diff 确认唯一差异是 B 线第 220–221 行的空行与注释 `-- 渠道成本配置四表（…）`，覆盖后只删了这两行 |
| 修改 | `paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java` | 总数断言改为 `265 + 56 + 1 + 6 - 7 + 9 + 4`；经济事实旧表名的断言改为「不再出现」；新增 `test_billingFactAndChannelCostOwnership` |
| 修改 | `scripts/pdmaner/generate-payment-model.mjs` | 四表字段、索引、5 条关系、`channel_cost` 图组；不可变表加 scope、component；版本表加 plan；总数 31 表 / 391 字段 / 45 UK / 45 IDX / 68 关系 / 8 图组；开放代码 51；索引注释可按表覆盖 |
| 修改 | `scripts/pdmaner/payment-field-metadata.mjs` | 四表显示名与表注释；20 个新字段键（209 → 229）；5 个闭合枚举、7 个开放代码来源 |
| 修改 | `scripts/pdmaner/payment-channel-field-overrides.mjs` | 22 条按表字段注释覆盖（字段键与 Payment 其他表共用时，使注释与 REQ DDL 逐字一致）；`version` 默认值 0；`channel_cost_component.scope_id` 取消继承的 ascii 编码；新增 `CHANNEL_INDEX_COMMENTS`（8 条） |
| 修改 | `scripts/pdmaner/payment-service-partition.mjs` | `CHANNEL_CONNECTOR_TABLES` 加四表；三个分区的 name / describe 改为提交 `cf4a429c` 后的服务名与中文范围（channel-connector 加「渠道成本配置」） |
| 修改 | `scripts/pdmaner/payment-model.test.mjs` | 独立 Oracle 加四表的字段、索引、关系、显示名、表注释、图组与版本白名单；当前模型统计与结构摘要独立成常量；Physical Decisions 的冻结 Manifest 改用 `historicalModelTotals` / `historicalStructuralDigest`，因为该文档明确记录的是拆分前 27 表；表集合按集合比较 |
| 重生成 | `paytech/channel-connector/docs/database/channel-connector.pdma.json` | 13 → 17 张；describe 加「渠道成本配置」 |
| 修改 | `scripts/pdmaner/finance/service-split.mjs` | `FINANCE_SERVICES` 去 billing；经济事实三表与渠道成本四表标为不投影；`READ_ONLY_COPIES`、`SERVICE_TABLE_VARIANTS` 清空；计数去 billing；删除未用的 import |
| 修改 | `scripts/pdmaner/finance/generate-finance-model.mjs` | `SERVICE_TITLES` 去 billing，注释改为三份 |
| 修改 | `scripts/pdmaner/finance-model.test.mjs` | 投影测试：`t_currency_spec` 与 7 张表不再被投影，`owners.size` 改为 `84 - 1 - 27 - 7 - 1` |
| 修改 | `scripts/pdmaner/pdmaner-canonical-paths.test.mjs` | billing 的生成器改为 `null`，表示直接维护 JSON |
| 修改 | `requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md` | §2.1 七行备注加「2026-10-19 起」说明；§3 共享枚举行加迁移说明；§4.2 表后加取代说明。前三列未改，生成器 `--check` 通过 |
| 修改 | `.../design/04-finance-account-ledger-split.md` | §2 开头加取代段，指向决定第 3 节 |
| 修改 | `agent-docs/database/FINANCE_DATABASE.md` | §4.2 开头加取代说明 |
| 修改 | `agent-docs/CURRENT_IMPLEMENTATION.md` | 新增「billing 计费事实模型重构与渠道成本迁出」一节；V2 节的消息行、R4 计数、`unroutable-policy` 待办（关闭）、Finance 生成器漂移项同步；「Finance 渠道成本配置」节加迁出说明 |
| 修改 | `agent-docs/decisions/2026-10-19-billing-fact-engine.md` | Implementation status 改为 In progress，并在同一行注明阶段 3 细化 |
| 修改 | `design-discuss/billing/database.md` | 头部加阶段 3 细化说明；第 1 节、第 4 节、第 5 节行表（20 列、15 组件、`COMMERCIAL_PRICE`、`target_bucket`）、6a 枚举表（`FactComponentType` 15 个、`SourceRuleType`、新增 `FactTargetBucket`、`AmountSource` 与 `AutoInstructionType` 迁 ledger-api）、关系图列名 |
| 修改 | `design-discuss/billing/ddl-draft.sql` | 头注释注明已被 REQ `db/billing/ddl.sql` 取代，保留为讨论稿 |
| 修改 | `design-discuss/billing/cases.md` | C5 改为 `CHANNEL_COST` ADJUSTMENT 渠道 → OE；待定点加第 5 条（C1 自动 PAYMENT 行与 account 映射不兼容） |
| 修改 | `design-discuss/billing/design.md` | 6a 接口表的路径与「现状」列；第 8 节成本调整组件写法 |
| 修改 | `requirements-site/content/requirements.ts` | FIN-04 写明配置 Owner 为 channel-connector、billing 只命中并冻结证据；`EconomicsCalculated` → `BillingFactRecorded`（4 处） |
| 修改 | `requirements-site/content/global-use-case-sequences.mjs` | `EconomicsCalculated` → `BillingFactRecorded`（1 处） |

content 目录中没有 `EconomicFactRecorded`、`FeesCalculated` 或 `t_economic_fact*`，改后这三个名字与 `EconomicsCalculated` 均零命中。

## 3. 前后对照

| 项 | 前 | 后 |
|---|---|---|
| R4 billing | 9 张（含经济事实三表、渠道成本四表） | 11 张：计费事实五表、日历两表、消费幂等两表、`t_currency_spec`、`t_compensation_event` |
| R4 channel-connector | 13 张 | 17 张 |
| R4 物理表总数 | 328 | 334 |
| Payment 生成模型（逻辑全集） | 27 表 / 349 字段 / 38 UK / 44 IDX / 63 关系 / 7 图组 | 31 / 391 / 45 / 45 / 68 / 8 |
| `channel-connector.pdma.json` | 13 表 | 17 表，新增「渠道成本配置」图组 |
| Finance 生成器输出 | billing、reconciliation、tax、payout | reconciliation、tax、payout；逻辑模型仍为 84 表 |

四表的 5 条库内关系按卡片：`plan.channel_id → payment_channel`、`plan_ver.plan_id → plan`、`scope.cost_ver_id → plan_ver`、`scope.mid_id → channel_mid`、`component.scope_id → scope`。原 Finance 逻辑模型中还有一条 `plan.current_ver_id → plan_ver` 的 1:1 关系，卡片没有列出，本线没有加。

**Schema 一致性：** 用 `renderSchema` 渲染重生成的模型，四表与 B 线 `channel-connector-schema.sql`、REQ `db/channel-connector/ddl.sql` 逐字一致，其余 13 表与 HEAD 逐字一致。唯一差异是 B 线在第 220–221 行加的空行与注释，渲染器不会输出它。经主会话授权已用渲染结果覆盖该文件，现在 Schema 等于渲染输出。

## 4. 写接口示例第 11 节自查

本线没有新增或修改同步业务接口、Processor、Context、事务或补偿，第 11 节各项均不适用。与本线相关的是数据库一致性项：

- **模型与 DDL：** channel-connector 四表的字段、类型、默认值、索引、表注释、列注释、索引注释与 REQ DDL 逐字一致，已用渲染结果验证。
- **无 CHECK、无外键：** 生成器只输出主键、唯一索引与普通索引。
- **跨库关系：** 四表关系两端都在 `mor_channel_connector`，分区投影后 payment 模型不含它们。

## 5. 测试命令与计数

```text
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
set -a; source ../local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD=${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}
./mvnw -B -o verify -pl tools/arch-rules -am
./mvnw -B -o verify -pl billing/paytech-billing-test,channel-connector/paytech-channel-connector-test -am \
  -Dtest='ArchitectureAutoTest,ChannelConnectorArchitectureTest,BillingModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false
node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check
node scripts/pdmaner/generate-channel-connector-model.mjs --check
cd scripts/pdmaner && node --test *.test.mjs
npm --prefix requirements-site test
node scripts/verify-markdown-links.mjs
```

| 命令 | 结果 |
|---|---|
| arch-rules verify | 61/61，BUILD SUCCESS；`TableOwnershipAutoTest` 5/5、`DomainEventHandlerCatalogRuleAutoTest` 3/3（fixture 改名后重跑） |
| R4 定向重跑 | billing `ArchitectureAutoTest` 1/1、`BillingModuleBoundaryTest` 4/4；channel-connector `ArchitectureAutoTest` 1/1、`ChannelConnectorArchitectureTest` 3/3。B 线等待的 2 例已通过 |
| 表归属 `--check` | 通过 |
| channel-connector 模型 `--check` | 通过（17 表） |
| 本需求涉及的 PDManer 测试 | `billing-model` 7/7、`pdmaner-canonical-paths` 3/3、`payment-model` 13/14、`finance-model` 25/27、`payment-channel-query` 1/1（Schema 覆盖后）；未通过项均为既有失败，见下 |
| PDManer 全量 | 覆盖 Schema 前 573 例，通过 523，失败 50；覆盖后 `payment-channel-query` 转为通过，即失败 49。HEAD 基线为 566 例，通过 516，失败 50 |
| requirements-site | 137 例，通过 127，失败 10；失败集与 HEAD 快照逐条相同，没有新增失败 |
| Markdown 链接 | 7914 条，3 条断链都在本线未改的文件中 |

基线做法：用 `git archive HEAD` 把 HEAD 解到 scratchpad，在快照中跑同样的命令，再按测试名比较失败集。

**本需求涉及、仍未通过的 3 例（`payment-channel-query` 已在 Schema 覆盖后通过）：**

| 测试 | 原因 | HEAD 是否已失败 |
|---|---|---|
| `payment-model`「deterministic … eight diagram themes」 | `payment.pdma.json` 的字节摘要不等于冻结值：提交 `cf4a429c` 手改 name / describe，提交 `a457754f` 手改 `transaction_assessment_id` 注释；之后还有两个模型合并后的图组断言和生成器 `--check` | 是 |
| `finance-model`「artifact is deterministic」「service artifacts are exact projections」 | 提交 `cf4a429c` 手改了 reconciliation、tax、payout 的 name / describe，生成器未同步 | 是（HEAD 时先在 billing 上失败） |

**与 HEAD 相比失败集的变化：**

- 新增 1 例失败：`oe-client-model`「Four manual schemas …」。原因是 `t_agreement table comment exceeds MySQL limit`，来自其他会话未提交的 commercial 生成器改动，与本需求无关，A 线报告第 7.5 条已记录。
- 减少 2 例失败：`payment-model`「exact approved … table set」与 `payment-channel-query`「Channel target …」，现在通过。

**既有失败清单（按文件，失败数）：** risk-decision-engine-contract 10、risk-service-model 6、commerce-service-model-contract 6、platform-support-schema 4、platform-support-model 3、risk-settlement-eligibility-balanced-simplification 2、risk-service-scenario-doc 2、risk-limit-balanced-simplification 2、finance-model 2、client-contract-scenario-doc 2、client-contract-model 2、vault-model 1、risk-decision-initial-data 1、platform-support-scenario-doc 1、payment-scenario-doc 1、payment-model 1、finance-manual-payout-simplification 1、card-model 1、oe-client-model 1。

requirements-site 的 10 个既有失败：checkout 订单上下文渲染、Commerce 15 表模型、Commerce Client API 十条路由、Commerce 隐私与 Fulfillment 导航、Java 测试按入口组织、Payment 退款容量、Project context 控制面、共享写作约定、台账核心模型快照、台账 Platform 快照。卡片要求「全过」，但这 10 例在 HEAD 已失败，本线未修。

## 6. 偏离设计与说明

1. **Finance 生成器没有删除 manifests/02 的 7 张表 DSL（主会话已同意）。** 卡片要求删除，本线只停止投影，billing 不再由生成器写出。这 7 张表属于 84 表 Finance 逻辑模型，DSL 保留为历史模型，84 表不变量不动。删除会连锁影响 03、05 的三条关系、model-policy 中的 CHECK 与白名单、生成器 84 表不变量，以及卡外的 `finance-test-oracle/schema.oracle.mjs` 和 `finance-scenario-doc.test.mjs`。先例是 ledger（2026-10-19 控制科目重构）与 account（第 11b 步）脱离生成器时的做法：「逻辑模型仍为 84 张表，只是不再投影」，见 `scripts/pdmaner/finance/service-split.mjs` 头注释与 `finance-model.test.mjs` 投影测试注释。
2. **Payment 分区的 name / describe 同步到提交 `cf4a429c`。** 不同步的话，重生成 `channel-connector.pdma.json` 会把该提交手改的 name / describe 改回旧值。
3. **没有重生成 `payment.pdma.json`。** 卡片允许改它，但它已因提交 `a457754f` 的手改注释与生成器不一致，重生成会抹掉该注释，而 `payment-schema.sql` 用的是新注释。本线唯一引入的差异是 PDManer 命名规则里不可变表清单多了两张成本表，它在所有分区模型中都存在。payment 与 card 两份模型应等漂移收敛后一并重生成，见第 7 节第 4 项。
4. **Physical Decisions 测试固定为历史值。** 该文档的 Manifest 明确是拆分前 27 表的统计，本线没有改文档（不在路径内），而是把测试中的冻结常量拆为历史值与当前值。
5. **索引注释按表覆盖。** 生成器默认生成「唯一索引：…」，REQ DDL 是「保证业务唯一性：…」「支持访问路径：…」。本线新增 `CHANNEL_INDEX_COMMENTS` 只覆盖这 8 个索引，其他表不变。
6. **arch-rules fixture 多改了两个 Java 文件（主会话已授权）。** 只改 fixture `events.yaml` 会让 `DomainEventHandlerCatalogRuleAutoTest` 失败，因为两个 Java fixture 的注解仍写 `EconomicFactRecorded`。已按授权只改注解字符串，类名不动，测试仍断言 `EconomicFactPostingHandler`。
7. **决定记录只改了 Implementation status 一行。** technique 2.6 说决定记录要加一条「阶段 3 细化」注。卡片只允许改这一行，所以细化说明和链接写在了这一行里。
8. **REQ DDL 一处措辞：** `t_channel_cost_scope.mid_id` 注释写「本库 t_channel_mid.id」，但 `t_channel_mid` 的主键列名是 `mid_id`。模型按 REQ 逐字保留，供主会话决定是否在执行前改注释。改的话需同步 REQ DDL、B 线 Schema 与测试 DDL、本线覆盖项。

## 7. 待主会话确认或执行

已答复并处理：arch-rules 两个 Java fixture 改名、channel-connector Schema 覆盖、Finance DSL 保留为历史模型。

1. **既有模型漂移：** `payment.pdma.json` 与 `card.pdma.json` 和生成器不一致，来自提交 `cf4a429c`、`a457754f`，以及本线新增的不可变表规则。建议另开小任务处理：把 `a457754f` 的注释写进 `payment-execution-field-overrides.mjs`，再重生成 payment 与 card。同类的 Finance reconciliation、tax、payout name / describe 漂移已登记在台账。
2. **REQ DDL 措辞：** 第 6 节第 8 项，`mid_id` 注释里的「t_channel_mid.id」是否在执行前更正。
3. **台账状态：** `CURRENT_IMPLEMENTATION.md` 新节写的是「阶段 4 已实现，未提交、未部署」，并按 A、B 报告记录本机 `mor_billing`、`mor_channel_connector` 已由主会话重建。阶段 5–7 的结果由主会话回写。

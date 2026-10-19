# 任务卡 A · billing：计费事实模型重构、渠道成本迁出、PDManer 脱离生成器

先读 `00-common.md` 与 `T0-report.md`（T0 已改好 billing-api；本卡让 core / app / test / docs 跟上）。

## 允许改动的路径

- `paytech/billing/paytech-billing-core/**`、`paytech/billing/paytech-billing-app/**`、`paytech/billing/paytech-billing-test/**`
- `paytech/billing/docs/**`、`paytech/billing/README.md`
- 新建 `scripts/pdmaner/billing-model.test.mjs`（只新建；Finance 生成器相关文件由 D 线改）

## 1. 数据层（`core/dal`，按 `REQ/db/billing/ddl.sql` 逐列）

1. DataObject：`BillingFact`（25 列）、`BillingFactRef`、`BillingFactPricing`、`BillingFactLine`（20 列，`sourceEvidence` 为 `Map<String, Object>` 或等价 JSON 对象，经 MyBatis TypeHandler 读写 JSON 列；参考 `docs/superpowers/specs/2026-09-03-foundation-mybatis-type-handlers-design.md` 与 `paytech-mybatis-support` 现有 Handler）、`BillingFactLineLink`、`BusinessCalendar`、`BusinessCalendarDay`；枚举列用 T0 的 billing-api 枚举 + `core/enums` 的 `CalendarDayType`、`CalendarStatus`；`CodeEnumTypeHandler` 注册在 `BillingPersistenceConfiguration`。删除 `EconomicFact*`、`ChannelCost*` DO / Mapper / XML / Repository / `ChannelCostPlanStatus` 等成本枚举。
2. Mapper + XML：`BillingFactMapper`（insert、selectById、selectBySource、selectByBook 游标分页、progress 两段计数、按 `supersedes` / `parent`、状态与版本推进 CAS、`published_at` 写入）、`BillingFactRefMapper`（batchInsert、selectByFactId、selectFactIdsByRef）、`BillingFactPricingMapper`（insert、selectByFactId；本期只读路径有行为空）、`BillingFactLineMapper`、`BillingFactLineLinkMapper`（selectBySourceLineIds / byTargetLineIds）、`BusinessCalendarMapper`、`BusinessCalendarDayMapper`（本期只 select）。
3. Repository `interface + *Impl`，简单委托。

## 2. 领域与处理器

1. `domain`：`EconomicFactContext` / `EconomicFactPayloads` / `EconomicFactBookScope` / `view/*` 改为 `BillingFact*`；`BillingFactPayloads` 组装 `BillingFactPayload`（头 + `refs` + `lines`，`recordedTime = approvedTime`，`businessTime` 取列）。
2. `domainservice/validator/fact/EconomicFactInvariants` → `BillingFactInvariants`，按 technique 2.1「人工登记的目录约束」：`MANUAL_ADJUSTMENT`（`clientId` 必填、`parentFactId` 空、组件类型集合、`targetBucket` 只在收方 CLIENT / LOCAL_PARTNER 时允许、`refs` 只允许 `TICKET`）、`COST_ADJUSTMENT`（`parentFactId` 必填且为 APPROVED PAYMENT、`refs` 必含 `CLEARING_ACCOUNT`、只允许 `CHANNEL_COST` + `ADJUSTMENT`、付方 / 收方 CHANNEL ↔ OPERATING_ENTITY）；行级不变量同现状。
3. `service/processor`：`SubmitBillingFactProcessor`、`ApproveBillingFactProcessor`（逻辑同现状，表换新；提交写 `origin_type = MANUAL`、`business_time` 先写提交时间，批准覆盖为批准时间并写 `approved_time`；补偿事件 `BILLING_FACT_RECORDED`、`event_key = {factId}:{factVersion}` 与现状一致）；查询 Processor：按 ID（头 + refs + pricing + lines + links）、按流分页、进度、按引用（`BillingFactRefMapper.selectFactIdsByRef` → 批量读头与行，limit 缺省 100、上限 200，`nextFromFactId` 规则同列表）。`BillingLocks` 键名改 `billingFact*`。
4. `service/handler/EconomicFactRecordedHandler` → `BillingFactRecordedHandler`（`eventType() = BILLING_FACT_RECORDED`，发布 `BillingFactRecorded`，确认后写 `published_at`；退避策略不变）。
5. 渠道成本：删除 `ChannelCostPlanService*`、`ChannelCostVersionReviewValidator`、`ChannelCostContentHash`、`ChannelCostVersionContext`、`ChannelCostPlanView` / `ChannelCostVersionView`、五个 `*ChannelCost*Processor` 及其 request、`ChannelCostPlanController` / `ChannelCostPlanConverter`。`ChannelCostEstimationServiceImpl` 改为 `ChannelCostCalculator`（`domainservice/service` 下 `interface + Impl`，纯计算：输入组件列表（代码、类型、基数语义、费率、固定费、上下限、舍入）+ 基数 + 币种精度，输出逐组件 `basisAmount / rateValue / rawAmount / postedAmount`；8 位 HALF_UP、上下限、币种精度归整，逻辑从现有 `calculateComponents` 搬，不读库），本期无调用方，保留给需求 B。
6. `app`：`BillingFactController`（实现 `BillingFactCommandApi` + `BillingFactQueryApi`）、`BillingFactConverter`；`application.yml` 的 `messaging.publisher.unroutable-policy` 改 `FAIL` 并删那段注释（测试 yml 已是默认 FAIL）。

## 3. 文档与模型

1. `docs/database/sql/billing-schema.sql`：11 张表，`t_currency_spec`、`t_compensation_event` 原样，其余逐字取 `REQ/db/billing/ddl.sql` 的 CREATE（文件头保留"generated from the approved PDManer model"改为"maintained with billing.pdma.json"口径）；`billing-initial-data.sql` 加 `REQ/db/billing/dml.sql` 的日历行；`docs/database/sql/changes/2026-10-19-billing-fact-model.sql` = `REQ/db/billing/ddl.sql` + `dml.sql` 内容（头注释指向 REQ）。
2. `docs/database/billing.pdma.json`：脱离 Finance 生成器，直接维护 JSON（照 `paytech/account/docs/database/account.pdma.json` 与 `scripts/pdmaner/account-model.test.mjs` 的做法）：11 个实体、字段 / 类型 / 默认值 / 索引 / 注释与 Schema 逐字一致；库内关系：ref n:1 fact、pricing 1:1 fact、line n:1 fact、link n:1 line（source / target）、calendar_day n:1 calendar；图组三个：「计费事实」（5 表）、「参考数据副本」（币种 + 日历 2 表）、「可靠执行与消费幂等」（3 表）。新建 `scripts/pdmaner/billing-model.test.mjs`（`node --test`）：读 JSON 校验 11 表、关键列、索引、与 `billing-schema.sql` 渲染一致（照 `account-model.test.mjs` / `ledger-model.test.mjs` 的渲染器）。
3. 测试 DDL `paytech-billing-test/src/test/resources/init/ddl.sql`：按新 Schema 重写（11 张），保留行同初始化 DML。
4. `README.md` 按新模块职责、接口、事件、表数改写。

## 4. 测试（`paytech-billing-test`，重写为新模型）

- `auto/ext/http/fact`：`SubmitBillingFactTest`（正常、幂等重复 200、同键不同内容 400、各目录约束 400、`refs` TICKET、`targetBucket` 合法 / 非法、COST_ADJUSTMENT 带 `CLEARING_ACCOUNT` 与父事实）、`ApproveBillingFactTest`（版本定稿、替代链、同人拒绝、重复批准幂等）、`BillingFactQueryTest`（按 ID 含 refs / lines / links 空列表、按流分页、进度两段计数、`by-ref` 命中 / 空 / 非法 refType）。
- `auto/ext/eventhandler/BillingFactRecordedHandlerTest`：载荷含 `refs`、`lines`、`businessTime`、`targetBucket`；`published_at` 写入；信封聚合类型 `BILLING_FACT`。
- `auto/domain`：`BillingFactInvariantsTest`；新增 `ChannelCostCalculatorTest`（纯 Java：百分比、固定费、上下限、舍入）。
- 删除 `auto/ext/http/cost/*`、`auto/domainservice/cost/*`、`AbstractChannelCostApiTest`。
- 基础设施：`BillingTestMapper` 清理 11 张表（ID ≥ 1,000,000 规则不变）、`EconomicFactRequestBuilder` → `BillingFactRequestBuilder`、`EconomicFactEnvelopeChecker` → `BillingFactEnvelopeChecker`、`EconomicFactEventProbe` → `BillingFactEventProbe`、`BillingModuleBoundaryTest` 按新包。
- 目标：覆盖现 90 例同等场景，计数在报告中给出。

## 5. 验证与门禁

`./mvnw -B -o verify -pl billing/paytech-billing-test -am`；`node --test scripts/pdmaner/billing-model.test.mjs`。依赖新表的测试在主会话重建 `mor_billing` 测试库前会失败：报告中写明"需执行 `paytech/billing/paytech-billing-test/src/test/resources/init/ddl.sql` 重建"，并先交付编译通过 + 纯 Java 测试通过；主会话重建后再跑全量并补报告。

## 6. 报告

`REQ/tasks/A-report.md`（按 00-common）。

# A 线报告（billing：计费事实模型重构、渠道成本迁出、PDManer 脱离生成器）

日期：2026-10-19。执行者：A 线。未执行任何 git 写命令，未对数据库执行 DDL / DML，未调用 Superpowers skill。只改 A 卡「允许改动的路径」。

## 1. 已读规范与设计

- `tasks/00-common.md`、`tasks/T0-report.md`、`tasks/A-billing.md`
- `technique.md` 第 2 节（2.1–2.6）；`db/billing/ddl.sql`、`db/billing/dml.sql`
- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（全文）、
  `agent-docs/examples/WRITE_API_EXAMPLE.md`（全文，含第 11 节）、`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`（全文）、
  `agent-docs/DATABASE_DESIGN_GUIDE.md`（第 4、5、13–15 节）、`agent-docs/COMPENSATION_EVENT_GUIDE.md`（第 1–4 节）
- 形态参照：`paytech/account/docs/database/account.pdma.json`、`scripts/pdmaner/account-model.test.mjs`、`scripts/pdmaner/ledger-model.test.mjs`、
  `scripts/pdmaner/generate-oe-client-schemas.mjs`（Schema 渲染器）

本次入口与事务：写入口为 core `service/processor` 的 `SubmitBillingFactProcessor`、`ApproveBillingFactProcessor`（`AbstractLockProcessor`）。
Context 为 `BillingFactContext`（头、引用、明细行、被替代事实、币种精度）。本地原子写：提交在 `BillingFactServiceImpl.saveDraft` 一个事务内写头、引用与明细行；
批准在 `BillingFactServiceImpl.approve` 一个事务内批准、替代并登记补偿任务。事务外步骤：BillingFactRecorded 发布由补偿 Handler 在提交后执行。

## 2. 改动文件清单

路径前缀 `paytech/billing/`。

### 2.1 core（`paytech-billing-core/src/main`）

| 动作 | 文件 |
|---|---|
| 新增 | `core/dal/dataobject/`：`BillingFact`、`BillingFactRef`、`BillingFactPricing`、`BillingFactLine`、`BillingFactLineLink`、`BusinessCalendar`、`BusinessCalendarDay` |
| 新增 | `core/dal/mapper/` 与 `resources/mapper/billing/`：上述 7 张表各一个 Mapper + XML |
| 新增 | `core/dal/configuration/SourceEvidenceTypeHandler`（JSON 列 ↔ `Map<String, Object>`，只在 XML 按列引用） |
| 新增 | `core/enums/CalendarDayType`、`CalendarStatus`、`FactTargetBucket` |
| 新增 | `domain/`：`BillingFactBookScope`、`BillingFactContext`、`BillingFactPayloads`、`ChannelCostComponentRule`、`ChannelCostComponentAmount` |
| 新增 | `domain/view/`：`BillingFactView`、`BillingFactPageView`、`BillingFactProgressView`、`BillingFactPricingView`、`BillingFactLineEvidenceView`、`BillingFactLineLinkView` |
| 新增 | `domainservice/repository/` + `impl/`：7 张表各一个 `interface + *RepositoryImpl` |
| 新增 | `domainservice/service/` + `impl/`：`BillingFactService`、`BillingFactQueryService`、`BillingFactPublicationService`、`ChannelCostCalculator` |
| 新增 | `domainservice/validator/fact/BillingFactInvariants` |
| 新增 | `service/processor/`：`SubmitBillingFactProcessor`、`ApproveBillingFactProcessor` 及 request / response；`service/handler/BillingFactRecordedHandler` |
| 修改 | `core/constant/BillingLocks`（`billingFact` / `billingFactSource`，删渠道成本锁）、`package-info.java` |
| 删除 | 全部 `EconomicFact*`、`ChannelCost*` 的 DO / Mapper / XML / Repository / Service / Processor / request / View / Validator / Handler，以及 `ChannelCostPlanStatus`、`ChannelCostVersionStatus`、`ChannelCostComponentType`、`ChannelCostCalculationBase`、`ChannelCostContentHash`、`ChannelCostEstimateCommand`；共 71 个文件，均在 git HEAD 中，B 线可用 `git show HEAD:<path>` 读取 |

保留：`CurrencySpec*`、`BillingCodes`、`AmountRoundingMode`（计算器使用）、`BillingEventProperties`。

### 2.2 app（`paytech-billing-app/src/main`）

| 动作 | 文件 |
|---|---|
| 新增 | `app/ext/rest/BillingFactController`（实现 `BillingFactCommandApi` + `BillingFactQueryApi`）、`converter/BillingFactConverter` |
| 删除 | `EconomicFactController`、`ChannelCostPlanController`、`EconomicFactConverter`、`ChannelCostPlanConverter` |
| 修改 | `BillingPersistenceConfiguration`（注册 12 个枚举 TypeHandler，删渠道成本与 `AmountRoundingMode` 注册）、`application.yml`（`unroutable-policy: FAIL` 并删注释，注释改计费事实口径） |

### 2.3 test（`paytech-billing-test/src/test`）

| 动作 | 文件 |
|---|---|
| 新增 | `auto/ext/http/fact/SubmitBillingFactTest`、`ApproveBillingFactTest`、`BillingFactQueryTest`；`auto/ext/eventhandler/BillingFactRecordedHandlerTest`；`auto/domain/BillingFactInvariantsTest`、`ChannelCostCalculatorTest`；`auto/repository/BillingRepositoryTest` |
| 新增 | `test/base/AbstractBillingFactApiTest`、`test/builder/BillingFactRequestBuilder`、`test/checker/BillingFactEnvelopeChecker`、`test/probe/BillingFactEventProbe` |
| 修改 | `test/dal/BillingTestMapper`（11 张表计数与清理、前置事实 / 引用 / 规则行 / 行链接 / 日历例外日）、`test/clear/BillingDataCleaner`（保留行 2：USD + DEFAULT 日历）、`test/base/MockTestBase`、`auto/BillingModuleBoundaryTest`（注释）、`resources/init/ddl.sql`（11 张表 + 2 行保留数据） |
| 删除 | `auto/ext/http/cost/*`、`auto/domainservice/cost/*`、`AbstractChannelCostApiTest`、`AbstractChannelCostEstimationTest`、`ChannelCost*Builder`、全部 `EconomicFact*` 测试与基础设施 |

### 2.4 文档与模型

| 动作 | 文件 |
|---|---|
| 修改 | `docs/database/billing.pdma.json`：11 实体、6 条库内关系、3 个图组；按 REQ ddl.sql 逐列生成，字段显示名人工给定；`t_currency_spec`、`t_compensation_event` 原样；消费幂等两表取自 account 模型（新 ID） |
| 修改 | `docs/database/sql/billing-schema.sql`（由模型经仓库渲染器 `renderSchema` 生成，7 张新表与 REQ ddl.sql 逐字一致）、`billing-initial-data.sql`（加 DEFAULT 日历） |
| 新增 | `docs/database/sql/changes/2026-10-19-billing-fact-model.sql`（REQ ddl.sql + dml.sql 原文拼接，头注释指向 REQ） |
| 修改 | `README.md` |
| 新增 | `scripts/pdmaner/billing-model.test.mjs` |

## 3. 前后对照

### 3.1 接口（Controller 实现）

| 旧 | 新 |
|---|---|
| `POST /billing/v1/economic-facts` | `POST /billing/v1/facts`（201 新建 / 200 同内容重放） |
| `POST .../economic-facts/{id}/approve` | `POST /billing/v1/facts/{factId}/approve` |
| `GET .../economic-facts/{id}` | `GET /billing/v1/facts/{factId}`：加 `originType`、`sourceVersion`、`businessTime`、`recordedTime`、`balanceAvailableTime`、`refs`、`pricing`、`links`，行加计算证据 |
| `GET ...?bookEntityType&bookEntityId&...` | `GET /billing/v1/facts?entityType&entityId&currency&fromFactId&limit`（1–500） |
| `GET .../pending` | `GET /billing/v1/facts/progress` |
| — | `GET /billing/v1/facts/by-ref?refType&refId&fromFactId&limit`（1–200，缺省 100；refType 不在目录 400） |
| `/channel-cost-plans/v1/**` 7 个端点 | 删除（迁 channel-connector） |

### 3.2 表

`mor_billing` 9 → 11。删 `t_economic_fact*` 三表与 `t_channel_cost_*` 四表；新增 `t_billing_fact`（25 列）、`t_billing_fact_ref`、`t_billing_fact_pricing`、
`t_billing_fact_line`（20 列）、`t_billing_fact_line_link`、`t_business_calendar`、`t_business_calendar_day`、`t_consumed_event`、`t_consumed_aggregate_version`。
索引共 21 个（含保留两表的 4 个）。

### 3.3 事件与补偿

| 项 | 旧 | 新 |
|---|---|---|
| 补偿类型 | `ECONOMIC_FACT_RECORDED` | `BILLING_FACT_RECORDED`，`event_key = BILLING_FACT:{factId}:BillingFactRecorded:{factVersion}` |
| 事件 / 聚合 | `EconomicFactRecorded` / `ECONOMIC_FACT` | `BillingFactRecorded` / `BILLING_FACT`，occurredAt = approvedTime |
| Payload | 头 + lines（含 partyType / partyId 等） | 字段顺序同 T0 示例：头（含 originType、status、businessTime）+ `refs[{refType, refId}]` + `lines[…, targetBucket]` |
| Handler 错误码 | `ECONOMIC_FACT_*` | `BILLING_FACT_TRANSACTION_ACTIVE / EVENT_INVALID / NOT_PUBLISHABLE / UNPUBLISHED`，退避不变 |
| unroutable-policy | `ACK_AND_WARN` | `FAIL` |

## 4. 写接口示例第 11 节自查

| 项 | 实现与核对 |
|---|---|
| API 与 Body 最小合同 | T0 已交付；本线只实现。非 HTTP 入口 `SubmitBillingFactRequest` / `ApproveBillingFactRequest` 用 Jakarta 注解，由 `AbstractProcessor.validateBasic` 触发 |
| Controller / Converter 仅适配 | `BillingFactController` 只做转换与委托；`BillingFactConverter` 做 ID / 金额 / 游标 / limit / refType 解析。主流程见两个 Processor 的 `processWithinLock`：原提交提前返回，新请求构建 Context → 保存 → 响应 |
| Processor 查询本模块 Repository | 提交：`BillingFactRepository.findBySource / findById`、`CurrencySpecRepository`；批准：`findById` 读被替代事实。不调 Mapper、不逐表写 |
| Context | `BillingFactContext` 直接持有 DO；纯方法 `approvedVersion()`、`recorded()`、`toPayload()`，无外部访问 |
| Service 边界 | `BillingFactServiceImpl` 不重查关系、不调其他 Service；发布读取单列 `BillingFactPublicationService` 以避开补偿注册表装配环（同现状） |
| 本地原子写入与事务位置 | `saveDraft`：头 + 引用 + 行一个事务；`approve`：批准 + 替代 + 补偿登记一个事务；事务内无外部调用；发布在 Handler 事务外，`published_at` 单条条件更新 |
| Repository 与行数检查 | 插入核对行数；`approve`（`status = DRAFT AND fact_version = 0`）、`supersede`（状态 + 版本）、`markPublished` 条件更新，成功后同步原对象 |
| 锁 | 提交锁 `billing-fact-source:{sourceSystem}:{sourceReference}`，批准锁 `billing-fact:{factId}`，均 `LOCK`；数据库唯一键 `uk_fact_source` 与 CAS 仍是最终防线 |
| 原请求比较后直接响应 | 同来源引用按 `CODE-MOR:BILLING:BILLING_FACT_SUBMISSION:1` 摘要比较：相同 200 返回原事实，不同 400；已批准事实同一复核人重复批准返回原结果，不重复登记事件 |
| Gateway 幂等与业务防重 | 未引入 `Idempotency-Key` 或命令幂等组件；防重来自 `source_system + source_event_key` |
| View 与 DataObject 边界 | 多表详情用 `BillingFactView`（payload + 状态 + 定价 / 证据 / 链接 View）；app 模块不引用任何 DO |
| 公共包复用、无多余抽象 | 复用 `AbstractLockProcessor`、`CodeEnumTypeHandler`、`InstantTypeHandler`、`Rfc8785JsonCanonicalizer`、`DomainEventPublisher`；无生产 Mock |
| 测试验证 | 见第 5 节；DB 与 MQ 用例等待测试库重建 |

## 5. 测试

命令（`paytech/` 下，`JAVA_HOME` 指向 JDK 25，`.env.local` 已加载）：

```text
./mvnw -B -o verify -pl billing/paytech-billing-test -am
  -> BUILD SUCCESS：上游 Foundation 等模块全部通过（paytech-messaging 73、redis-starter 等）
  -> billing-test：Tests run 71，Failures 0，Errors 0，Skipped 0
node --test scripts/pdmaner/billing-model.test.mjs
  -> 7/7 通过
```

本次结果是在主会话重建 `mor_billing` 之后跑出的。重建方式是删库后执行 `init/ddl.sql`，得到 11 张表与 USD、DEFAULT 两行保留数据。D 线已把 billing 的 11 张表登记进 `table-ownership.yaml`。

| 类 | 用例 | 结果 |
|---|---|---|
| SubmitBillingFactTest | 14 | 通过 |
| ApproveBillingFactTest | 6 | 通过 |
| BillingFactQueryTest | 8 | 通过 |
| BillingFactRecordedHandlerTest | 5 | 通过（真实本地 RabbitMQ） |
| BillingRepositoryTest | 4 | 通过 |
| BillingApplicationTest | 12 | 通过 |
| BillingFactInvariantsTest | 7 | 通过 |
| ChannelCostCalculatorTest | 10 | 通过 |
| BillingModuleBoundaryTest | 4 | 通过 |
| ArchitectureAutoTest | 1 | 通过（R4 随 D 线的表归属更新通过） |

重建库后第一轮全量有 3 例失败，都不是 DDL 差异：

- **ArchitectureAutoTest（1 例）**：R4 报新表不在表归属清单，D 线更新 `table-ownership.yaml` 后通过。
- **SubmitBillingFactTest（2 例）**：违反 Body 正则或必填的请求，Foundation 统一返回 422 `VALIDATION_ERROR`，测试错写成期望 400。已在 `BillingResponseChecker` 新增 `assertValidationError()`，`test_invalidParameter` 与 `test_targetBucket` 的 5 处断言改用它。生产代码未改动。

与原 90 例对照：原计费事实相关 24 个方法扩为 44 个（提交 7 → 14、批准 6、查询 2 → 8、Handler 4 → 5、不变量 5 → 7、新增仓储 4）。
原渠道成本 49 个方法中，配置 / 审批 / 范围命中部分随表迁到 B 线。保留在 billing 的纯计算有 10 个，沿用原估算成功路径的金额与舍入场景。
应用骨架、模块边界、架构共 17 个不变。

## 6. 偏离设计与说明

1. **Schema 文件头未改口径。** 卡片要求把 `billing-schema.sql` 头改为 "maintained with billing.pdma.json"。但 `oe-client-model.test.mjs`（不在本线路径）要求该文件与 `renderSchema` 输出逐字相等，渲染器写死 "generated from the approved PDManer model"，所以保留渲染器原样。`billing-model.test.mjs` 同样断言逐字相等。
2. **消费幂等两表与 REQ ddl.sql 不逐字一致。** REQ 的两张表照 Foundation SQL：表排序规则 `utf8mb4_0900_bin`，索引无注释。渲染器固定输出 `utf8mb4_0900_ai_ci` 和 KEY COMMENT，所以模型与 Schema 采用 account 已有的同构写法：列、类型、主键、索引相同，注释与表排序规则不同。其余 7 张新表与 REQ 逐字一致，脚本已逐条比对。建议主会话执行前，把 REQ `db/billing/ddl.sql` 的这两段 CREATE 换成 `billing-schema.sql` 中的版本，让库与模型一致。
3. **新增 `core/enums/FactTargetBucket`。** `target_bucket` 是闭合列，按规范用枚举加 TypeHandler。卡片未列出这个枚举。
4. **COST_ADJUSTMENT 允许可选 TICKET。** technique 只写 CLEARING_ACCOUNT 必填，本线允许 {CLEARING_ACCOUNT, TICKET}，其他引用类型 400 INVALID_REQUEST。若要严格只允许 CLEARING_ACCOUNT，改 `BillingFactInvariants.checkCostAdjustment` 一处即可。
5. **成本调整的 Client。** 请求可不传 `clientId`。传了就必须等于来源 PAYMENT 的 Client，否则按「来源事实不合格」拒绝。落库值取自来源事实。提交摘要按请求原样计算。
6. **查询不建 Processor。** 卡片写"查询 Processor"。按开发规范第 5.2 节，纯查询委托本地 `BillingFactQueryService`，不建一行转发的 Processor。by-ref 的 refType 与 limit 解析在 Converter。
7. **列表项与详情同构。** 按账簿和按引用的列表项也返回定价与行链接，与按 ID 一致。为此批量读取各表，新增 `selectByIds`（事实）、`selectByFactIds`（引用、定价、行），不逐条 N+1。
8. **无生产调用方的 Mapper 方法照卡片保留，由 `BillingRepositoryTest` 覆盖。** 包括按 supersedes / parent 读取、定价 insert、按原行读链接、日历两表 select。
9. **证据字段格式。** `basisAmount` / `rawAmount` 统一输出 8 位小数，如 `10.00000000`；`rateValue` 去掉末尾零。提交响应与 GET 因此逐字段一致。
10. **不变量检查顺序。** 先查事实类型目录，再查合作方二元组与平台服务费，最后查行级金额、付方 / 收方与头部一致性。这样缺二元组时给出更具体的提示。原 PAYMENT 用的 `checkPartnerConsistency` 公共方法没有调用方，已删除。
11. **计算器输入。** `componentType`、`calculationBase` 以 channel-connector 返回的 String 代码传入，计算器不解释。舍入方式用保留的 `AmountRoundingMode`。
12. **范围内残留命中。** `grep EconomicFact|economic_fact|EconomicsType|AmountSource` 还有以下命中，都不在本线可改范围内，或是历史记录：
    - `paytech/billing/pom.xml` 第 48 行注释（不在允许路径）；
    - `compensation-event-catalog.yaml`（T0 的改名说明）；
    - `docs/database/sql/changes/2026-10-18-economic-fact-maker-checker.sql`（已执行的历史 SQL，保留）；
    - 新变更脚本中的 `DROP TABLE t_economic_fact*`（来自 REQ ddl.sql）；
    - `billing-model.test.mjs` 与 Handler 测试中断言旧名不出现的负例。

## 7. 待主会话执行或确认

1. 已完成：主会话重建 `mor_billing`；REQ `db/billing/ddl.sql` 两张消费幂等表已换成 `billing-schema.sql` 版本（第 6.2 条）；D 线已更新 `table-ownership.yaml`。
2. 遗留：`node --test scripts/pdmaner/oe-client-model.test.mjs` 失败在 commercial 的 `t_agreement` 表注释超长处，来自其他会话未提交的 commercial 生成器改动，与 billing 无关。billing 的逐字渲染已由 `billing-model.test.mjs` 覆盖。

# B 线报告（channel-connector：渠道成本配置、命中接口、到期切换 Job）

日期：2026-10-19。执行者：B 线。未执行任何 git 写命令、未对任何数据库执行 DDL / DML（自动测试只读到测试库缺表）、未调用 Superpowers skill。只改 B 卡「允许改动的路径」；`paytech/channel-connector/pom.xml` 未改（新依赖版本均已由 `paytech-dependencies` 管理）。

## 1. 已读规范与设计

- `tasks/00-common.md`、`tasks/T0-report.md`、`tasks/B-channel-connector.md`
- `technique.md` 第 2 节（2.1 链路与事务、枚举归属；2.2 channel-connector-api；2.3 channel-connector 库；2.5 B 线；2.6 细化）、`db/channel-connector/ddl.sql`
- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（全文）、`agent-docs/examples/WRITE_API_EXAMPLE.md`（全文，含第 11 节）、`agent-docs/UNIT_TEST_GUIDE.md`（全文）、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`（全文）、`agent-docs/COMPENSATION_EVENT_GUIDE.md`（全文）、`agent-docs/DATABASE_DESIGN_GUIDE.md`（目录与第 11 节）
- 搬迁来源（全部经 `git show HEAD:<路径>` 读取）：billing-core 的 `ChannelCost*` DataObject / Mapper / XML / Repository / 枚举 / `ChannelCostContentHash` / `ChannelCostVersionContext` / View / `ChannelCostPlanService(Impl)` / `ChannelCostVersionReviewValidator` / 5 个 Processor 与请求、`AmountRoundingMode`、`BillingCodes`、`BillingLocks`；billing-app 的 `ChannelCostPlanController`、`ChannelCostPlanConverter`、`BillingPersistenceConfiguration`、`BillingRuntimeConfiguration`、`BillingIdentifierProperties`、`application.yml`；billing-test 的 `AbstractChannelCostApiTest`、`ChannelCostVersionRequestBuilder`、3 个成本测试类、`BillingResponseChecker`、`BillingRedisCleaner`、`MockTestBase`、`application-mock.yml`
- 形态参照：account `MatureBalancesProcessor` / `BalanceMaturityJobHandler` / `BalanceMaturityJobTest`、account 测试 `application-mock.yml`（xxl 段）、Foundation `AbstractLockProcessor`、`MvcProblemExceptionMapper`（`FailException` → 400 + 枚举 Code）、`paytech-test-support/README.md`

**所属 Processor / Context、本地原子写入与事务外步骤：** 五个 Maker-Checker 写入口与到期切换单方案步骤是 `service/processor` 下的 `AbstractLockProcessor`（锁 `channel-cost-plan:{planCode}`，LOCK）；Context 为 `ChannelCostVersionContext`（方案、版本、范围、组成、被替代版本，均为本模块 DataObject）。本地原子写入全部在 `ChannelCostPlanServiceImpl` 的一个 `TransactionTemplate` 内（先锁方案行再锁版本行，状态条件更新，指针 CAS）。没有事务外步骤：本能力不调用任何外部服务。命中 `ResolveChannelCostProcessor` 只读、无事务。

## 2. 改动文件清单（均在 `paytech/channel-connector/` 下）

### 2.1 core（新增）

| 包 | 文件 |
|---|---|
| `core/constant` | `ChannelConnectorLocks` |
| `core/enums` | `ChannelCostPlanStatus`、`ChannelCostVersionStatus`、`ChannelCostComponentType`、`ChannelCostCalculationBase`、`AmountRoundingMode`（名称照来源，Code 值不变）、`ChannelCostCodes`（照 `BillingCodes`：未知 Code → `InvalidParameterException`） |
| `core/dal/dataobject` | `ChannelCostPlan`、`ChannelCostPlanVer`、`ChannelCostScope`、`ChannelCostComponent` |
| `core/dal/mapper` + `resources/mapper/cost/*.xml` | `ChannelCostPlanMapper`（新增 `selectById`）、`ChannelCostPlanVerMapper`（新增 `selectDueApproved`）、`ChannelCostScopeMapper`、`ChannelCostComponentMapper` |
| `domainservice/repository(+impl)` | 4 个 `interface + *RepositoryImpl`；Plan 新增 `findById`，Ver 新增 `findDueApproved`、`activate`（APPROVED → ACTIVE 条件更新） |
| `domainservice/service(+impl)` | `ChannelCostPlanService` / `ChannelCostPlanServiceImpl`（搬入；去掉币种精度加载；新增 `findCurrentVersion`、`activateVersion`） |
| `domainservice/validator/cost` | `ChannelCostVersionReviewValidator` |
| `domain` | `ChannelCostContentHash`（SCHEMA = `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1`）、`ChannelCostVersionContext`（新增纯方法 `checkLaterThan`、`replacing`）、`ChannelCostPlanView`、`ChannelCostVersionView`（组成带 `componentId`）、`ChannelCostResolutionView`、`ChannelCostActivationResult` |
| `service/processor(+request)` | `CreateChannelCostPlanProcessor`、`CreateChannelCostVersionProcessor`（+ MID 三项校验）、`SubmitChannelCostVersionProcessor`、`ApproveChannelCostVersionProcessor`、`RejectChannelCostVersionProcessor`、`ResolveChannelCostProcessor`、`ActivateDueChannelCostVersionsProcessor`、`ActivateChannelCostVersionProcessor`；请求 7 个 |

修改：`paytech-channel-connector-core/pom.xml`（加 `paytech-redis-starter`、`paytech-snowflake-id`、`paytech-json-canonicalization`、`spring-tx`、`slf4j-api`）。

### 2.2 app

| 动作 | 文件 |
|---|---|
| 新增 | `ext/rest/ChannelCostPlanController`（实现 `ChannelCostPlanApi`）、`ext/rest/ChannelCostResolutionController`（实现 `ChannelCostResolutionApi`）、`ext/rest/converter/ChannelCostPlanConverter`、`ext/job/ChannelCostVersionActivationJobHandler`、`configuration/ChannelConnectorIdentifierProperties`、`configuration/ChannelConnectorRuntimeConfiguration`（UTC `Clock`、Snowflake `IdGenerator`） |
| 修改 | `configuration/ChannelConnectorConfiguration`（新增 MyBatis `ConfigurationCustomizer`：全局 `InstantTypeHandler`、下划线转驼峰、应用分配主键；枚举不全局登记，见第 5 节）、`application.yml`（`spring.data.redis`、`redis.coordination.enabled`、`compensation.*`、`channel-connector.identifier.*`）、`pom.xml`（加 `paytech-snowflake-id`、`paytech-mybatis-support`、`paytech-redis-starter`、`paytech-compensation-event`） |

### 2.3 test

| 动作 | 文件 |
|---|---|
| 新增 | `auto/cost/ChannelCostPlanApiTest`、`ChannelCostVersionLifecycleTest`、`CreateChannelCostVersionValidationTest`（三类搬入）、`ChannelCostResolutionTest`（新）、`auto/ext/job/ChannelCostVersionActivationJobTest`（新）；`test/base/AbstractChannelCostApiTest`、`test/builder/ChannelCostVersionRequestBuilder`、`test/checker/ChannelCostResponseChecker`、`test/mocker/RedisNamespaceCleaner` |
| 修改 | `ChannelConnectorMockTestBase`（加 `@AutoConfigureRestTestClient` 以注入公共 `HttpTestInvoker`，全部测试共用一个上下文）、`ChannelConnectorTestConfiguration`（删除测试自建 `IdGenerator`，与生产 Bean 冲突；Fixture 改用生产生成器，Region/Worker 仍为 31/29）、`ChannelTestMapper`（四表计数、清理、方案行读取、版本数、`rewriteVersionWindow`）、`ChannelDataMocker`（空基线与全局清理覆盖四表）、`ChannelConnectorArchitectureTest`（Payment 源码禁止出现四表名）、`application-mock.yml`（Redis、`redis.key-prefix: channel-connector-mock`、`compensation.*`、`xxl.job.executor.enabled: false`、`channel-connector.identifier`）、`init/ddl.sql`（追加四表） |

### 2.4 docs / README

| 动作 | 文件 |
|---|---|
| 修改 | `docs/database/sql/channel-connector-schema.sql`：末尾逐字追加 REQ 四段 CREATE，表数 17 |
| 新增 | `docs/database/sql/changes/2026-10-19-channel-cost-tables.sql` = `REQ/db/channel-connector/ddl.sql` 原文 |
| 修改 | `README.md`：职责、模块表、测试库说明、新章节「渠道成本配置」（接口、并发、Job、依赖与配置键） |
| 未改 | `docs/database/channel-connector.pdma.json`（D 线经 Payment 生成器重生成，清单见第 7 节） |

## 3. 接口 / 表 / 事件前后对照

| 项 | billing（搬迁前） | channel-connector（现在） |
|---|---|---|
| Maker-Checker 7 端点 | `/channel-cost-plans/v1/**` | `/channel-connector/v1/channel-cost-plans/**`，签名不变 |
| 命中 | 无（billing 内部估算服务直接查表） | `POST /channel-connector/v1/channel-costs/resolutions` |
| `scopes[].midId` | 只作匹配条件，不核对 | 必须存在于 `t_channel_mid`、`channel_id` = 方案 Channel、`entity_no` = 范围 `entityNo`，否则 400 `BUSINESS_CHECK_FAILED`（消息含方案、scopeNo、midId） |
| 币种 | `t_currency_spec` 必须 ACTIVE，金额小数位 ≤ 币种精度 | 只校验格式；金额小数位 ≤ 8 由入口 `@Pattern` 保证 |
| 金额输出 / 摘要输入 | 按币种精度补齐（`0.30`） | 去掉末尾零（`0.3`），见第 6 节偏离 1 |
| 内容摘要 Schema | `CODE-MOR:FINANCE:CHANNEL_COST:1` | `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1`（无存量数据） |
| 组成响应 | 无主键 | 详情与命中均带 `componentId` |
| APPROVED 到期 | 无人切换 | XXL `channelCostVersionActivation` 切换 |
| 表 | `mor_billing` 四表 | `mor_channel_connector` 四表（13 → 17），结构同 REQ DDL |
| 事件 | 无 | 无（补偿目录为空） |

## 4. 写接口示例第 11 节自查

| 项 | 结果 |
|---|---|
| API 与 Body 最小合同 | T0 已交付；本线只实现。Body 校验由入口 `@Valid`，内部 Request 注解由 `AbstractProcessor.validateBasic` 触发。 |
| Controller / Converter 仅适配；Processor 位置 | `ChannelCostPlanController` / `ChannelCostResolutionController` 只转换与委托；五个写 Processor 与命中、切换 Processor 均在 core `service/processor`。查询 `getChannelCostPlan/Version` 直接委托 `ChannelCostPlanService`（单一本地查询）。 |
| Processor 查询本模块 Repository | 是：`CreateChannelCostVersionProcessor` 读 `ChannelMidRepository`、`ChannelCostPlanVerRepository`；`ResolveChannelCostProcessor` 读四个渠道成本 Repository。没有 Processor 访问 Mapper 或逐表写入。 |
| Context | `ChannelCostVersionContext` 一次构造，持有本模块 DataObject；纯方法 `effectiveAt`、`checkLaterThan`、`replacing`、`computeContentHash`、`toView` 不访问外部。 |
| Service 边界 | `ChannelCostPlanServiceImpl` 只用本模块 Repository 与 `TransactionTemplate`，不调用其他 Service、Processor 或外部依赖。 |
| 本地原子写入与事务位置 | 创建版本（版本 + 范围 + 组成）、提交、批准（含停用旧版本与指针切换）、驳回、到期切换各自一个事务，均在 Service 内；事务内无外部调用。保存失败抛出，不推进。 |
| Repository 与行数检查 | 一表一 Repository；状态迁移带原状态条件、指针切换带 `version` 条件，行数 ≠ 1 抛 409，成功后直接同步原对象；批量插入核对行数。 |
| 锁 | 同一方案六个写入口（含到期切换）统一 `ChannelConnectorLocks.channelCostPlan(planCode)`、LOCK 模式；数据库行锁、条件更新与唯一键保留为最终防线。 |
| 原请求比较后直接响应 | 不适用：这些接口没有业务操作唯一身份（与 billing 原合同一致）；重复创建由 Channel / 方案代码唯一与"已有待处理版本"拒绝。到期切换锁内复核已处理即跳过。 |
| Gateway 幂等与业务防重 | 无 `Idempotency-Key`，未引入命令幂等组件。 |
| View / DataObject 不泄露 | 多表结果用 `ChannelCostPlanView` / `ChannelCostVersionView` / `ChannelCostResolutionView`（复制字段，不持有 DataObject）；app 模块只接触 View 与内部 Request。 |
| 公共包复用、无多余抽象 | 复用 `AbstractLockProcessor` / `AbstractProcessor`、`CodeEnumTypeHandler` / `InstantTypeHandler`、`Rfc8785JsonCanonicalizer`、`HttpTestInvoker` / `AbstractHttpResponseChecker`。新增的 `ActivateChannelCostVersionProcessor` 是为了让每个方案获得独立资源锁，见偏离 4。 |
| 测试验证 | 见第 5 节：建库后 125 例中 123 例通过，2 例 R4 等待 D 线。 |

## 5. 测试命令与计数

```text
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
set -a; source ../local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD=${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}
./mvnw -B -o verify -pl channel-connector/paytech-channel-connector-test -am
```

**建库后结果（2026-10-19，主会话已在 `mor_channel_connector` 建好四表，共 17 张）：** 上游模块全部通过；`paytech-channel-connector-test` 运行 125 例，通过 123，失败 2。

| 测试类 | 运行 | 通过 |
|---|---|---|
| `ChannelCostPlanApiTest` | 5 | 5 |
| `ChannelCostVersionLifecycleTest` | 15 | 15 |
| `CreateChannelCostVersionValidationTest` | 17 | 17 |
| `ChannelCostResolutionTest` | 7 | 7 |
| `ChannelCostVersionActivationJobTest` | 6 | 6 |
| 既有 `GetChannelExecutionConfigTest` / `QueryMidCandidatesTest` / `ChannelCandidateQueryHttpTest` / `ExecuteChannelOperationTest` / `MidCapabilityResolutionTest` | 30 / 14 / 3 / 15 / 9 | 全部通过 |
| `ChannelConnectorArchitectureTest` | 3 | 2 |
| `ArchitectureAutoTest` | 1 | 0 |

**等待 D 线，单列：** 失败的 2 例是 `ArchitectureAutoTest.test_allRulesPass` 与 `ChannelConnectorArchitectureTest.keepsChannelTablesInsideOwnerModule`。违规全部是 R4，内容都是"channel-connector 引用了不属于本库的 t_channel_cost_*"，原因是 `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` 仍把四表列在 billing 下。没有其他规则的违规。

**建库后发现并在本线修复的实现缺陷：** 首轮重跑时，既有用例出现 `未知枚举 Code: AmountRoundingMode, code=ACTIVE`，例如读取 `t_channel_account.account_status`。原因是我在 `ChannelConnectorConfiguration` 里全局登记了五个 `CodeEnumTypeHandler` 实例。MyBatis 按 Handler 类缓存实例，既有 XML 中内联的 `typeHandler="CodeEnumTypeHandler"` 于是取到了最后登记的 `AmountRoundingMode` 实例。修复方式：
- 删除枚举的全局登记，只保留 UTC `InstantTypeHandler` 与下划线转驼峰。
- 渠道成本四个 XML 改为 `resultMap autoMapping="true"`，闭合枚举列用内联 `CodeEnumTypeHandler` 加 `javaType`。
- 参数写入改为 `#{xxx.code}`，与既有渠道 XML 和 TestMapper 的写法一致。

修复后全量结果即上表。测试 DDL 与 Schema 没有不一致。

## 6. 偏离设计与待主会话确认事项

1. **金额与费率输出去掉末尾零，不按币种精度补齐。** 本库没有币种精度表，`DECIMAL(24,8)` 读回为 8 位小数，无法还原币种精度。View、响应和内容摘要统一用 `stripTrailingZeros().toPlainString()`（`0.30` 输出 `0.3`），测试按数值比较。T0 的 `ChannelCostComponentResponseBody` 中 fixedAmount / minAmount / maxAmount 的 JavaDoc 仍写"按范围币种精度补齐，例如 0.30"，需要 api 文件的 Owner 改成"去掉末尾零的普通十进制字符串"。
2. **金额小数位超过 8 位返回 422，不是 400。** `ChannelCostComponentRequestBody` 的 `@Pattern` 已限制最多 8 位小数，入口先返回 422 `VALIDATION_ERROR`，Processor 不再重复检查。`ChannelCostPlanApi.createChannelCostVersion` 的 JavaDoc 把"金额小数位超过 8 位"列在 400 `BUSINESS_CHECK_FAILED` 中，需要 api 文件的 Owner 修正；测试 `test_amountOverEightDecimalsInvalid` 按 422 断言。
3. **`paytech-compensation-event` 只加在 app。** core 没有使用它的代码；它在 app 中提供 XXL 执行器与 `xxl-job-core`（Job Handler 需要）。卡片写的是 core / app 都加。
4. **多了一个 `ActivateChannelCostVersionProcessor`。** 卡片只命名了 `ActivateDueChannelCostVersionsProcessor`。为了"每个方案一个锁 + 短事务"且不复制 Redisson 逻辑，批量 Processor（`AbstractProcessor`，无锁、无事务）逐个调用单方案 Processor（`AbstractLockProcessor`，锁 planCode），两者均在事务外。
5. **到期版本的生效时间不晚于当前版本时无法自动处理。** 例如未来生效的 v2 已 APPROVED，之后又批准了生效时间更晚、立即生效的 v3。Job 会把 v2 计为失败、保持 APPROVED，并每轮 `handleFail`。目前没有作废 APPROVED 版本的入口，需要主会话决定是否接受"人工处理"或在后续切片补作废能力。
6. **多重命中只可能来自版本。** 范围由唯一键 `(cost_ver_id, 六维)` 保证每个版本至多一个，所以 `CHANNEL_COST_AMBIGUOUS` 只在覆盖 asOfTime 的 ACTIVE / INACTIVE 版本多于一个时出现；测试用 TestMapper 改写版本区间制造该损坏场景。
7. **创建方案不校验 `channelId` 存在于 `t_payment_channel`。** 逻辑照搬来源；不存在的 Channel 无法创建任何版本，因为范围 MID 必须属于方案 Channel。
8. **既有测试也等待建库。** 全局空基线必须覆盖本库全部表，所以四表缺失时既有 channel-connector 用例同样失败；这是测试规范要求的失败关闭行为。
9. **部署配置。** `application.yml` 新增必填 `CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID` / `_WORKER_ID`（无默认，缺失启动失败），以及 `CHANNEL_CONNECTOR_REDIS_HOST/PORT/PASSWORD`、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`。XXL 执行器沿用 billing / account 做法由部署注入（`xxl.job.executor.enabled=true`、`app-name` 等），`application.yml` 未写。`config/nacos/channel-connector-service.yaml` 不在本卡允许路径内，未改；阶段 6 发布需同步这些键。
10. **一次 `install`。** 实现初期为编译检查执行过一次 `./mvnw -o -q install -DskipTests -pl channel-connector/paytech-channel-connector-app -am`，把上游 SNAPSHOT 写入了本地仓库 `~/.m3`；之后全部改用 `verify`。

## 7. 供 D 线核对的四表字段 / 索引清单

`t_channel_cost_plan`（表注释：保存每个 Payment Channel 的预计渠道成本规则稳定身份和当前版本指针；billing 经命中接口取组件并冻结证据，本库不算金额。）

| 字段 | 类型 | 空 | 默认 |
|---|---|---|---|
| id | BIGINT | NOT NULL | — |
| channel_id | BIGINT | NOT NULL | — |
| plan_code | VARCHAR(64) | NOT NULL | — |
| current_ver_id | BIGINT | NULL | — |
| status | VARCHAR(64) | NOT NULL | —（DRAFT、ACTIVE、INACTIVE） |
| version | BIGINT | NOT NULL | 0 |
| create_time | DATETIME(6) | NOT NULL | — |
| update_time | DATETIME(6) | NOT NULL | — |

索引：PK(id)；`uk_channel_cost_plan`(channel_id)；`uk_channel_cost_code`(plan_code)；`uk_channel_cost_current`(current_ver_id)。

`t_channel_cost_plan_ver`（表注释：保存成本规则发布批次的生效期、内容摘要及双人审批状态，当前版本仅由方案根指针确定；APPROVED 版本到达生效时间后由到期切换 Job 原子切换为当前版本。）

| 字段 | 类型 | 空 |
|---|---|---|
| id | BIGINT | NOT NULL |
| plan_id | BIGINT | NOT NULL |
| cost_ver | INT | NOT NULL |
| effective_from | DATETIME(6) | NOT NULL |
| effective_to | DATETIME(6) | NULL |
| content_hash | CHAR(64) | NOT NULL |
| status | VARCHAR(64) | NOT NULL（DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE） |
| maker_id | BIGINT | NOT NULL |
| checker_id | BIGINT | NULL |
| create_time | DATETIME(6) | NOT NULL |
| update_time | DATETIME(6) | NOT NULL |

索引：PK(id)；`uk_channel_cost_ver`(plan_id, cost_ver)。

`t_channel_cost_scope`（表注释：冻结成本版本对 MID、经营主体、国家、币种、支付方式和动作的确定性匹配范围。）

| 字段 | 类型 | 空 |
|---|---|---|
| id | BIGINT | NOT NULL |
| cost_ver_id | BIGINT | NOT NULL |
| scope_no | INT | NOT NULL |
| mid_id | BIGINT | NOT NULL（本库 t_channel_mid） |
| entity_no | CHAR(5) ascii / ascii_bin | NOT NULL |
| channel_agreement_ref | VARCHAR(128) | NOT NULL |
| country_code | CHAR(2) | NOT NULL |
| currency | CHAR(3) | NOT NULL |
| payment_method_code | VARCHAR(64) | NOT NULL |
| action_type | VARCHAR(64) | NOT NULL |
| create_time | DATETIME(6) | NOT NULL |

索引：PK(id)；`uk_channel_cost_scope_no`(cost_ver_id, scope_no)；`uk_channel_cost_scope`(cost_ver_id, mid_id, entity_no, country_code, currency, payment_method_code, action_type)；`idx_channel_cost_match`(mid_id, entity_no, country_code, currency)。

`t_channel_cost_component`（表注释：冻结渠道成本适用范围下的百分比、固定费、跨境费、退款费或争议费参数；金额由 billing 按证据计算。）

| 字段 | 类型 | 空 |
|---|---|---|
| id | BIGINT | NOT NULL |
| scope_id | BIGINT | NOT NULL |
| component_code | VARCHAR(64) | NOT NULL |
| component_type | VARCHAR(64) | NOT NULL（PROCESSING_FEE、CROSS_BORDER_FEE、REFUND_FEE、DISPUTE_FEE） |
| calculation_base | VARCHAR(64) | NOT NULL（TRANSACTION_AMOUNT） |
| rate_value | DECIMAL(18,10) | NULL |
| fixed_amount | DECIMAL(24,8) | NULL |
| currency | CHAR(3) | NULL |
| min_amount | DECIMAL(24,8) | NULL |
| max_amount | DECIMAL(24,8) | NULL |
| rounding_mode | VARCHAR(64) | NOT NULL（HALF_UP） |
| create_time | DATETIME(6) | NOT NULL |

索引：PK(id)；`uk_channel_cost_component`(scope_id, component_code)。

四表共 8 个索引（含主键 4 个）；列注释以 `REQ/db/channel-connector/ddl.sql` 为准。

## 8. 到期切换 Job 的 XXL 登记参数

| 参数 | 值 |
|---|---|
| 执行器（AppName） | 建议 `channel-connector-service`（`xxl.job.executor.app-name`，由部署注入；须满足 `[a-z][a-z0-9-]{1,63}`） |
| JobHandler | `channelCostVersionActivation` |
| 建议 Cron | `0 */5 * * * ?` |
| 路由 / 阻塞策略 | 建议"第一个" + "单机串行"（Redis 方案锁与条件更新已保证多实例安全，串行只为减少竞争） |
| 任务参数 | 无 |
| 失败语义 | 有版本切换失败时 `XxlJobHelper.handleFail`，失败版本保持 APPROVED 由下一轮重试 |

## 9. 收尾（主会话裁定后）

- 主会话已接受第 6 节第 1–10 项。第 5 项本期按人工处理，作废 APPROVED 版本的入口登记为后续项。第 9 项的 Nacos 配置键留到阶段 6 发布处理。
- 经主会话授权，修改了 channel-connector-api 两处 JavaDoc，只改文字：
  - `cost/response/ChannelCostComponentResponseBody` 的 fixedAmount / minAmount / maxAmount 改为"去掉末尾零的普通十进制字符串，例如 "0.3""。
  - `cost/ChannelCostPlanApi.createChannelCostVersion` 把金额小数位超过 8 位从 400 BUSINESS_CHECK_FAILED 的列举中移出，改为 422 VALIDATION_ERROR。
- 验证：`./mvnw -B -o verify -pl channel-connector/paytech-channel-connector-api` 结果为 BUILD SUCCESS。该模块没有自身测试。
- 测试库重建由主会话申请授权后执行。执行后重跑第 5 节的全量命令。

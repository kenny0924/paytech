# 任务卡 B · channel-connector：接收渠道成本配置、命中接口、到期切换 Job

先读 `00-common.md` 与 `T0-report.md`（T0 已把 `ChannelCostPlanApi` 与 `ChannelCostResolutionApi` 放进 channel-connector-api）。billing 现有实现是搬迁来源，位于 `paytech/billing/paytech-billing-core/src/main/java/com/paytech/billing/{core/dal,domain,domainservice,service/processor}` 的 `ChannelCost*` 类、`paytech-billing-app` 的 `ChannelCostPlanController` / `ChannelCostPlanConverter`、`paytech-billing-test` 的 `auto/ext/http/cost/*`、`auto/domainservice/cost/*`、`test/base/AbstractChannelCostApiTest`（A 线会删除这些来源，只读不改）。

## 允许改动的路径

- `paytech/channel-connector/paytech-channel-connector-core/**`、`paytech/channel-connector/paytech-channel-connector-app/**`、`paytech/channel-connector/paytech-channel-connector-test/**`
- `paytech/channel-connector/docs/**`、`paytech/channel-connector/README.md`
- `paytech/channel-connector/pom.xml`（仅依赖版本从父 BOM 取）

## 1. 依赖与配置

1. core / app 加 `paytech-redis-starter`（`AbstractLockProcessor`）、`paytech-snowflake-id`（本服务现无 ID 生成；配置键照 billing `billing.identifier.*` 的做法命名 `channel-connector.identifier.*`）、`paytech-compensation-event`（带 XXL 执行器；`compensation.enabled: true`、`instance-id`，照 billing）。`application.yml` 与测试 `application-mock.yml` 加 Redis（local-environment 16379，密码键照 billing 测试 yml）、`redis.key-prefix: channel-connector-mock`、`xxl`（测试不连调度中心，照 `paytech/account/paytech-account-test/src/test/resources/application-mock.yml` 第 74 行起）。
2. `ChannelConnectorArchitectureTest` / `ArchitectureAutoTest` 如有包依赖断言，按新包补。

## 2. 数据层（按 `REQ/db/channel-connector/ddl.sql`）

`core/dal/dataobject` 的 `ChannelCostPlan`、`ChannelCostPlanVer`、`ChannelCostScope`、`ChannelCostComponent` + Mapper + XML（`mapper/cost/*.xml`）+ Repository `interface + *Impl`，从 billing 搬入并改包；`core/enums` 搬入 `ChannelCostPlanStatus`、版本状态、组件类型、计算基数、舍入方式枚举（名称照来源）；TypeHandler 注册在本服务持久化配置。

## 3. 处理器与接口

1. 五个 Maker-Checker Processor（创建方案、创建版本、提交、批准、驳回）+ 两个查询，从 billing 搬入（`AbstractLockProcessor` 锁 `planCode`，锁键常量 `ChannelConnectorLocks`），`ChannelCostContentHash.SCHEMA = "CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1"`。创建版本新增三项校验（technique 2.1）：每个 `scopes[].midId` 在 `t_channel_mid` 存在（`ChannelMidRepository`）、其 `channel_id` 等于方案 Channel、其 OE 等于 `scopes[].entityNo`；不满足 400 `BUSINESS_CHECK_FAILED`（消息含 scopeNo、midId）。币种只校验格式与小数位 ≤ 8（本库无币种表；原来的 `t_currency_spec` 校验删除，注释说明精度归整在 billing）。
2. `ResolveChannelCostProcessor`（只读，无事务，`AbstractProcessTemplate`）：方案 ACTIVE（按 `channelId`）→ 覆盖 `asOfTime` 的唯一 ACTIVE / INACTIVE 版本（`effective_from <= asOf < effective_to`，`effective_to` 空视为无穷）→ 六维精确命中唯一范围 → 组件按 `componentCode` 升序；缺任一步 400 `CHANNEL_COST_NOT_RESOLVED`，多于一个 400 `CHANNEL_COST_AMBIGUOUS`，消息含全部维度。
3. 到期切换：`ActivateDueChannelCostVersionsProcessor`（遍历 `status = APPROVED` 且 `effective_from <= now` 的版本，按方案逐个：锁 `planCode` → 短事务内复核仍 APPROVED → 原当前版本 `INACTIVE`、`effective_to = 新版 effective_from` → 新版 `ACTIVE` → 方案 `current_ver_id` CAS；幂等）+ `app/ext/job/ChannelCostVersionActivationJobHandler`（`@XxlJob("channelCostVersionActivation")`，照 account `BalanceMaturityJobHandler`：失败计数 > 0 则 `handleFail`）。批准 Processor 现有"已到生效时间直接 ACTIVE"逻辑保留。
4. `app/ext/rest`：`ChannelCostPlanController`（实现 `ChannelCostPlanApi`）、`ChannelCostResolutionController`、Converter。

## 4. 文档与模型

1. `docs/database/sql/channel-connector-schema.sql`：追加四表（逐字取 `REQ/db/channel-connector/ddl.sql`），表数 17；新建 `docs/database/sql/changes/2026-10-19-channel-cost-tables.sql` = REQ 文件内容。
2. `docs/database/channel-connector.pdma.json`：由 D 线通过 Payment 生成器重生成；本卡不手改该 JSON，但要在报告里给出四表的字段 / 索引清单供 D 线核对。
3. 测试 DDL `init/ddl.sql` 追加四表；`README.md` 加渠道成本章节（接口、Job、依赖、配置键）。

## 5. 测试（`paytech-channel-connector-test`）

- 搬入 billing 的 `ChannelCostPlanApiTest`、`ChannelCostVersionLifecycleTest`、`CreateChannelCostVersionValidationTest` 与 `AbstractChannelCostApiTest`，路径改 `/channel-connector/v1/channel-cost-plans`，用 `ChannelDataMocker` 先造 Channel / MID；新增三项 MID 校验用例；删除币种校验用例。
- 新增 `ChannelCostResolutionTest`（唯一命中、无方案、无版本覆盖、无范围、多命中、版本边界 `effective_to`）。
- 新增 `ChannelCostVersionActivationJobTest`（到期切换、未到期不动、重复执行幂等、原当前版本 `INACTIVE` 与 `effective_to`）。
- `ChannelTestMapper` 清理四表；`ChannelConnectorMockTestBase` 全局 AfterEach 覆盖。

## 6. 验证与门禁

`./mvnw -B -o verify -pl channel-connector/paytech-channel-connector-test -am`。新表建库前相关测试失败是预期的：报告写明需执行 `paytech/channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql` 中新增的四段 CREATE；先交付编译通过。

## 7. 报告

`REQ/tasks/B-report.md`（按 00-common），附四表字段 / 索引清单与 Job 的 XXL 登记参数（执行器名、Handler 名、建议 Cron `0 */5 * * * ?`）。

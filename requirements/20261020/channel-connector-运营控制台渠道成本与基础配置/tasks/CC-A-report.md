# CC-A 交付报告：变更记录表、成本方案列表 / 作废 / 渠道校验、MID 只读列表

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/agent-af39a2b74ffb12ccc`（分支 `worktree-agent-af39a2b74ffb12ccc`，基于 `4204a8d9`；未提交，未执行任何 git 写命令）
- 本需求目录在该 worktree 中原本不存在（只在主检出未提交），本卡在 worktree 内新建了 `db/channel-connector/ddl.sql` 与本报告两个文件，合并时与主检出的需求目录合并即可。
- 结果：channel-connector-test **150 例，0 失败**（新增 25 例）；arch-rules 61 + reactor 3 例全部通过；PDManer 相关 node 测试 25 例中 23 例通过，2 例失败在本卡改动前就已存在（见第 8 节）。

## 1. 已读规范清单

- `AGENTS.md`「接口开发必读与自查」与 Automated test boundaries。
- `agent-docs/DEVELOPMENT_CONVENTIONS.md` 全文。
- `agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 4–7 节。
- `agent-docs/examples/WRITE_API_EXAMPLE.md` 全文（含第 11 节）。
- `agent-docs/UNIT_TEST_GUIDE.md` 全文。
- `agent-docs/DATABASE_DESIGN_GUIDE.md` 第 5.1、11 节。
- 本需求 `tasks/CC-A.md`，以及 `technique.md` 全文（重点第 3.1、4、6、10 节）。
- channel-connector 相关：
  - `README.md`、测试 `init/ddl.sql`、`docs/database/sql/channel-connector-schema.sql`。
  - 生成器 `scripts/pdmaner/generate-channel-connector-model.mjs`，及其依赖：`generate-payment-model.mjs`、`payment-field-metadata.mjs`、`payment-channel-field-overrides.mjs`、`payment-service-partition.mjs`、`generate-oe-client-schemas.mjs#renderSchema`。
  - 生成器测试：`payment-model.test.mjs`、`payment-channel-query.test.mjs`。
- 既有成本链路：
  - Processor：`CreateChannelCostPlanProcessor`、`ApproveChannelCostVersionProcessor`、`ActivateChannelCostVersionProcessor`。
  - Service：`ChannelCostPlanService(+Impl)`。
  - Repository：`ChannelCostPlanRepository(+Impl)`、`ChannelCostPlanVerRepository(+Impl)`。
  - Mapper 及其 XML、`ChannelConnectorLocks`、`ChannelCostVersionContext`。
  - 接口层：`ChannelCostPlanController`、`ChannelCostPlanConverter`、`ChannelCostPlanApi` 与各 DTO。
- 既有测试：
  - 用例：`ChannelCostPlanApiTest`、`ChannelCostVersionLifecycleTest`、`ChannelCostVersionActivationJobTest`、`ChannelCostResolutionTest`、`ChannelConnectorArchitectureTest`。
  - 支撑：`AbstractChannelCostApiTest`、`ChannelConnectorMockTestBase`、`ChannelCostVersionRequestBuilder`、`ChannelCostResponseChecker`、`ChannelDataMocker`、`ChannelTestMapper`。
- 分页：`PagingRequestBody` / `PagingResponseBody`；参照 `IntegrationManagementApi.integrations` 的写法。
- 错误映射：`MvcProblemExceptionMapper`（Query 参数约束失败映射为 400 INVALID_REQUEST）。

## 2. 所属 Processor / Context、本地原子写入与事务外步骤

- **作废（唯一新增写入口）：** `CancelChannelCostVersionProcessor extends AbstractLockProcessor`。
  - 锁：`ChannelConnectorLocks.channelCostPlan(planCode)`，LOCK 模式，与同一方案的另外 5 个写入口和到期切换 Job 共用。
  - Context：复用 `ChannelCostVersionContext`，它持有方案、版本、范围和组成的 DataObject。新增纯判断 `checkCancellableAt(now)`：不是 APPROVED，或已到生效时间，都会业务拒绝，提示含方案代码、版本号和当前状态。
  - 本地原子写入：`ChannelCostPlanService.cancelVersion(context, change)`，在一个 `TransactionTemplate` 内依次执行：
    1. 按方案、版本的顺序 `FOR UPDATE` 锁行。
    2. `ChannelCostPlanVerRepository.cancel`：条件更新 `WHERE id=? AND status='APPROVED' AND effective_from > now`，行数不为 1 时抛 `ConcurrentRequestException`（409）。
    3. `ChannelConfigChangeRepository.insert` 写一条 `CHANNEL_COST_VERSION / CANCEL / APPROVED → INACTIVE` 记录，带操作者和原因，写入行数不为 1 时抛 `ErrorException`。
  - 事务外步骤：无，没有任何外部调用。
- **方案创建：** 既有的 `CreateChannelCostPlanProcessor` 在锁内、事务前新增 `checkChannelExists`，查询 `PaymentChannelRepository.findById`，不存在时返回 400「渠道不存在: {channelId}」。
- **三个读接口：** Controller 直接委托本地只读 Service，没有 Processor、锁或事务。
  - 成本方案列表：委托 `ChannelCostPlanService.findPage`。
  - 变更记录：委托 `ChannelConfigChangeQueryService.listChanges`。
  - MID 列表：委托 `ChannelMidQueryService.listMids`。
  - 分页统一由数据库执行 `COUNT` 和 `LIMIT/OFFSET`，排序稳定。

## 3. 改动文件清单（70 个，路径相对 worktree 根）

| 类别 | 文件 | 新增 / 修改 |
|---|---|---|
| 数据库 | `paytech/channel-connector/docs/database/channel-connector.pdma.json`（由生成器重新生成，18 张表） | 修改 |
| 数据库 | `paytech/channel-connector/docs/database/sql/channel-connector-schema.sql`（由 `renderSchema` 渲染，末尾追加新表） | 修改 |
| 数据库 | `paytech/channel-connector/docs/database/sql/changes/2026-10-20-channel-config-change.sql` | 新增 |
| 数据库 | `paytech/channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql`（头注 17 → 18，末尾追加新表） | 修改 |
| 数据库 | `requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/db/channel-connector/ddl.sql`（执行文件） | 新增 |
| PDManer 生成输入 | `scripts/pdmaner/generate-payment-model.mjs`：字段与索引清单、图组「渠道配置变更留痕」、不可变表白名单、汇总不变量 | 修改 |
| PDManer 生成输入 | `scripts/pdmaner/payment-field-metadata.mjs`：表名、表注释、3 个新字段键（232 个）、闭合与开放代码 | 修改 |
| PDManer 生成输入 | `scripts/pdmaner/payment-channel-field-overrides.mjs`：表级字段名称与注释、索引注释 | 修改 |
| PDManer 生成输入 | `scripts/pdmaner/payment-service-partition.mjs`：新表归入 channel-connector 分区 | 修改 |
| PDManer 测试 | `scripts/pdmaner/payment-model.test.mjs`：独立 Oracle（字段、索引、表名、注释、图组、汇总） | 修改 |
| 表归属（已获授权） | `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` | 修改 |
| 表归属（已获授权） | `paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java`：总数加 1，17 改 18 | 修改 |
| api | `api/change/ChannelConfigChangeApi.java`、`api/change/response/ChannelConfigChangeResponseBody.java` | 新增 |
| api | `api/channel/ChannelMidApi.java`、`api/channel/response/ChannelMidSummaryResponseBody.java` | 新增 |
| api | `api/cost/request/CancelChannelCostVersionRequestBody.java`、`api/cost/response/ChannelCostPlanSummaryResponseBody.java` | 新增 |
| api | `api/cost/ChannelCostPlanApi.java`（新增 cancel、list）、`api/cost/request/CreateChannelCostPlanRequestBody.java`（字段注释） | 修改 |
| core 枚举 / DataObject | `core/enums/ChangeTargetType.java`、`core/enums/ChangeType.java`、`core/dal/dataobject/ChannelConfigChange.java` | 新增 |
| core Mapper | `core/dal/mapper/ChannelConfigChangeMapper.java`、`resources/mapper/channel/ChannelConfigChangeMapper.xml` | 新增 |
| core Mapper | `ChannelCostPlanMapper.java/.xml`（selectSummaries / countSummaries） | 修改 |
| core Mapper | `ChannelCostPlanVerMapper.java/.xml`（cancelApproved） | 修改 |
| core Mapper | `ChannelMidMapper.java/.xml`（selectSummaries / countSummaries） | 修改 |
| core domain | `domain/ChannelCostPlanSummaryView.java`、`domain/ChannelMidSummaryView.java`、`domain/ChannelMidListCriteria.java` | 新增 |
| core domain | `domain/ChannelCostVersionContext.java`（checkCancellableAt） | 修改 |
| core Repository | `ChannelConfigChangeRepository(+Impl)` | 新增 |
| core Repository | `ChannelCostPlanRepository(+Impl)`（findPage）、`ChannelCostPlanVerRepository(+Impl)`（cancel）、`ChannelMidRepository(+Impl)`（findPage） | 修改 |
| core Service | `ChannelConfigChangeQueryService(+Impl)`、`ChannelMidQueryService(+Impl)` | 新增 |
| core Service | `ChannelCostPlanService(+Impl)`（cancelVersion、findPage） | 修改 |
| core Processor | `service/processor/CancelChannelCostVersionProcessor.java`、`request/CancelChannelCostVersionRequest.java` | 新增 |
| core Processor | `service/processor/CreateChannelCostPlanProcessor.java` | 修改 |
| app | `ext/rest/ChannelConfigChangeController.java`、`ext/rest/ChannelMidController.java` | 新增 |
| app | `ext/rest/converter/ChannelConfigChangeConverter.java`、`ext/rest/converter/ChannelMidConverter.java` | 新增 |
| app | `ext/rest/ChannelCostPlanController.java`、`ext/rest/converter/ChannelCostPlanConverter.java` | 修改 |
| 测试 | `auto/cost/ChannelCostPlanListTest.java`（7 例）、`auto/cost/CancelChannelCostVersionTest.java`（8 例） | 新增 |
| 测试 | `auto/channel/ChannelConfigChangeQueryTest.java`（4 例）、`auto/channel/ChannelMidListTest.java`（5 例） | 新增 |
| 测试 | `auto/cost/ChannelCostPlanApiTest.java`（新增 test_channelNotFound）、`auto/architecture/ChannelConnectorArchitectureTest.java`（Payment 禁止表名加 `t_channel_config_change`） | 修改 |
| 测试支撑 | `test/dal/ChannelTestMapper.java`（insertConfigChange、countConfigChangeRows、selectConfigChanges、clearConfigChanges 等） | 修改 |
| 测试支撑 | `test/mocker/ChannelDataMocker.java`（空基线检查与清理覆盖新表） | 修改 |
| 测试支撑 | `test/base/AbstractChannelCostApiTest.java`（cancel 辅助方法）、`test/checker/ChannelCostResponseChecker.java`（assertPage / item） | 修改 |
| 文档 | `paytech/channel-connector/README.md`（合同表、DDL 说明、并发说明） | 修改 |
| 报告 | 本文件 | 新增 |

上表中 api / core / app / 测试各行的路径分别位于 `paytech/channel-connector/paytech-channel-connector-{api,core,app,test}/src/{main,test}/java/com/paytech/channel/connector/` 下。

## 4. 接口 / 表 / 事件前后对照

| 项 | 之前 | 之后 |
|---|---|---|
| `ChannelCostPlanApi` | create / createVersion / submit / approve / reject / get / getVersion | 新增 `listChannelCostPlans`（`GET /channel-connector/v1/channel-cost-plans`）与 `cancelChannelCostVersion`（`POST .../{planCode}/versions/{costVer}/cancel`）；既有签名不变 |
| `createChannelCostPlan` | Channel 只作匹配条件，不核对 | Channel 必须存在于本库，否则 400「渠道不存在: {channelId}」 |
| `ChannelConfigChangeApi` | 无 | `GET /channel-connector/v1/config-changes?targetType&targetId&page&size`（targetType 与 targetId 必填） |
| `ChannelMidApi` | 无 | `GET /channel-connector/v1/mids?channelId&channelAccountId&entityNo&status&page&size`，写接口由 CC-B 追加到同一接口 |
| `mor_channel_connector` | 17 张表 | 18 张表：新增 `t_channel_config_change`，其余表结构不变 |
| `t_channel_cost_plan_ver` 状态 | APPROVED 只能到期切换 | 新增 APPROVED → INACTIVE（作废），`effective_to = effective_from` |
| MQ / 补偿事件 | 无 | 无变化 |

## 5. WRITE_API_EXAMPLE 第 11 节核对

- **合同：** API 与 Body 是最小合同，使用普通 class、中文字段注释和 Hibernate Validator。
  - `CancelChannelCostVersionRequestBody`：operatorId 用 `@Pattern`，reason 用 `@NotBlank` 加 `@Size(1,512)`。
  - Query 参数用 `@Pattern`，失败返回 400。
  - 内部 `CancelChannelCostVersionRequest` 的约束由 `validateBasic` 触发。
- **入口：** Controller 与 Converter 只做协议适配，作废 Processor 位于 core 的 `service/processor`，主方法依次是业务验证和本地保存两个阶段。
- **Processor：** 作废时通过 `ChannelCostPlanService.loadVersion` 读本模块数据，方案或版本不存在时 400，不访问 Mapper，也不逐表写入。创建方案时通过 `PaymentChannelRepository` 校验 Channel 存在。
- **Context：** 沿用 `ChannelCostVersionContext`，一次构造。`checkCancellableAt(now)` 是纯判断，时间由调用方传入。
- **Service：** `cancelVersion` 只做锁行、条件更新和插入，不重复校验，不调用其他 Service 或外部依赖。
- **事务：** 原子写入在 `ChannelCostPlanServiceImpl.cancelVersion` 的 `TransactionTemplate` 内，事务内没有外部调用。测试基类的 `VaultTransactionObserver.assertOutsideTransactions` 每例都会检查。
- **Repository：** 一张表对应一个 Repository，Context 不进入持久化层。`cancel` 先判断条件 SQL 的行数，成功后才同步原 DataObject 的 status、effectiveTo、updateTime。
- **锁：** 锁 `channel-cost-plan:{planCode}`，与同一方案的另外 5 个写入口和 Job 一致，数据库行锁加条件 SQL 作为最终防线，没有叠加其他锁机制。
- **原请求比较：** 不适用。作废没有业务操作身份，按当前状态处理，重复作废返回 400（`test_repeatedCancelRejected`）。
- **请求幂等：** 没有 Idempotency-Key，也没有新增命令幂等组件。
- **View：** 两个多表列表分别使用 `ChannelCostPlanSummaryView` 和 `ChannelMidSummaryView`。变更记录是单表查询，直接复用 `ChannelConfigChange` DataObject。HTTP 一律经 Converter 输出 ResponseBody，不泄露 DataObject。
- **公共包与配置：** 复用 `PagingRequestBody` / `PagingResponseBody` 和 `ChannelCostCodes.parse`，没有新增配置、生产 Mock 或无消费者字段。`ChannelMidListCriteria` 用于把超过 3 个的 Service 参数封装为具名对象。
- **验证：** 见第 6 节。并发竞争没有专门写用例，由同一把锁、行锁和条件 SQL 共同保护。

## 6. 实际执行的测试命令与结果

在 worktree 的 `paytech/` 目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
set -a; source /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a   # 不打印
export PAYTECH_REDIS_PASSWORD="${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}"
./mvnw -o -pl channel-connector/paytech-channel-connector-test -am test \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
./mvnw -o -pl tools/arch-rules,tools/arch-rules-reactor-test test
```

- 命令要显式指定 `paytech.local-environment.dir`：worktree 内的 `local-environment/` 没有 `.env.local`，而 Foundation Redis 集成测试需要 `PAYTECH_REDIS_PASSWORD`。
- channel-connector-test 结果为 `Tests run: 150, Failures: 0, Errors: 0`，整个 `-am` reactor BUILD SUCCESS。新增 25 例：
  - ChannelCostPlanListTest 7 例
  - CancelChannelCostVersionTest 8 例
  - ChannelConfigChangeQueryTest 4 例
  - ChannelMidListTest 5 例
  - ChannelCostPlanApiTest 新增 1 例
- 在补登 arch-rules 表归属之前，上面同一命令有 2 例失败，原因是 R4 认为该表不属于 channel-connector；获授权补登后全部通过。
- arch-rules 结果：61 例加 arch-rules-reactor-test 3 例，0 失败，BUILD SUCCESS。
- PDManer：
  - `node scripts/pdmaner/generate-channel-connector-model.mjs --check` 输出 up to date（18 tables）。
  - `node --test` 跑 payment-model、payment-channel-query、pdmaner-canonical-paths、payment-scenario-doc 四个文件，共 25 例，23 例通过，2 例失败在改动前就已存在（见第 8 节）。

## 7. DDL 执行记录

| 时间 | 库 | 动作 | 表数 |
|---|---|---|---|
| 2026-10-20 03:54:48（数据库时钟） | 本机 `mor_channel_connector` | 他人先行创建了 `t_channel_config_change`，中文注释乱码 | 18 |
| 2026-10-20 03:59:21 | 同上 | 本卡执行 `SHOW TABLES` 核对，表已存在，未执行 DDL，已报告主会话 | 18 |
| 2026-10-20 03:59:58Z | 同上 | 主会话 DROP 后，按本卡 `db/channel-connector/ddl.sql` 当时的版本，用 `--default-character-set=utf8mb4` 重建，0 行 | 17 → 18 |

本卡随后按生成器口径修正了 3 个 DDL 文件中 `from_status` / `to_status` 的注释，在末尾加上「取值来源：渠道、账号、MID、能力组或成本版本的状态代码；非固定枚举。」。本机表在这一句上与文件不同，结构完全一致，按主会话的安排不再重建。

## 8. 遗留与偏差

1. **作废写 `effective_to = effective_from`：** technique 只写了 → INACTIVE。原有命中 SQL `selectEffectiveAt` 会选中 `effective_to IS NULL` 的 INACTIVE 版本，作废版本若不设失效时间，到期后会被命中甚至造成 AMBIGUOUS。设成空区间后，命中和到期切换都不会再选中它，由 `test_cancelledVersionNotActivatedByJob` 验证。
2. **`cancelVersion` 签名改为 `(context, ChannelConfigChange change)`：** 任务卡写的是 `(context, operatorId, reason, now)`，共 4 个业务参数，违反「超过三个参数封装具名对象」。现在由 Processor 准备好变更记录（含 Snowflake 主键、操作者、原因、创建时间），记录的创建时间同时作为作废时刻。
3. **DDL 注释与 technique 第 4 节原文的差异：** 两处均来自生成器约定。
   - `from_status` / `to_status` 被生成器和 Oracle 识别为状态字段，必须标为开放代码，因此注释多了「取值来源…；非固定枚举。」，开放代码计数 51 → 53。
   - 渲染器总会输出索引注释：「支持访问路径：target_type、target_id、id」。
4. **两例 PDManer 测试失败在改动前就已存在，不是本卡引入：**
   - `Payment model is deterministic...`：`paytech/payment/docs/database/payment.pdma.json` 的 sha256 与测试冻结值不符，自 `cf4a429c` 改模型 name 后就已过期。`generate-payment-model.mjs --check` 改动前也报 stale。
   - `Payment scenario ... field=value rows`：场景文档引用了已删除的 `t_payment_channel.adapter_code`。
   - 本卡不在授权范围内重生成 Payment 模型。要注意：重生成时 `payment.pdma.json` 会多出新图组 `channel_config_change`（在 payment 侧为空），上面第一例里图组断言才会被真正执行到。
5. **与 CC-B 的合并冲突点：** 已与 cc-b 约定名称，`ChannelMidApi`、`ChannelMidController` 与只读 Service 的命名都按约定。
   - `ChannelMidMapper.java/.xml`、`ChannelMidRepository(+Impl)` 两边都会改，合并时有冲突。
   - `ChannelMidApi` 与 `ChannelMidController`，CC-B 会在同一文件内追加写接口。
   - `ChannelTestMapper` 与 `ChannelDataMocker` 的新表方法名与 CC-B 一致，按约定以本卡版本为准。
6. **未覆盖：**
   - 作废的多实例并发竞争：由共用的 LOCK、行锁和条件 SQL 保护，没有写竞争用例。
   - 本需求目录下的 `requirements-site` 测试：本卡没有改站点，未执行。
7. **新 HTTP 合同尚未发布：** 包括 3 个读接口和作废接口。Gateway / Platform 注册（REG）以及 BFF / Web（BW-A）仍在后续任务卡中。

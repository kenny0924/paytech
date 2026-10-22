# 任务卡 C 交付报告：Platform 后端（2026-10-22）

## 0. 开工前说明（AGENTS“接口开发必读与自查”）

- **已读：** `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`、`database/PLATFORM_SUPPORT_DATABASE.md`、`paytech/platform-support/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`，需求目录 `technique.md`、`requirement.md`、`tasks/A-report.md`（裁决 1—4）。参照链路：币种维护（Processor / Support / `OperationsTransactionExecutor` / Controller / Converter / API）、`InternalCallerResolver` + `NotificationCallerProperties`、Webhook 管理（`WebhookManagementSupport`、`CreateWebhookEndpointProcessor`、`WebhookSecretVault`）、`PersistenceConfiguration`、ledger `CurrencySnapshotRefreshScheduler`。
- **归属：** Processor 在 `paytech-platform-support-core` 的 `com.paytech.platform.support.dataquery.service.processor`（17 个接口 Processor + 执行 Processor）；数据、Service、引擎与连接池在 `paytech-platform-support-data-query`。因 DataObject 以 Owner JAR 为边界，core Processor 不碰 data-query 的 DataObject / Repository，只用其发布的 Domain Service 与领域对象（`DataSourceDetail`、`QueryTemplateDetail`、各 `*Command`、`*Change`）。
- **本地原子写入：** 数据源写入（新建 / 修改 / 启停 / 删除 / 回写检查）与模板写入（新建 = 模板头 + 版本 1；修改与恢复 = 插入版本行 + 切换 `current_version_no` 与冗余 `data_source_id` + `version+1`）都在 `OperationsTransactionExecutor.write` 的一个短事务内完成，事务内先复核会话权限、再写入、再登记审计事实。模板写入在事务内对数据源 `FOR SHARE`，删除数据源在取得行锁后再读 ACTIVE 引用，二者不交错。
- **事务外步骤：** 权限预检、Vault Protect / Reveal、连接检查（单连接 `SELECT 1` + `SHOW GRANTS`）、被查库执行、提交后的本地连接池刷新；全部不在 `mor_platform` 事务内。

## 1. 改动文件（工作区 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`）

**接口合同（第 1 步）**
- `paytech/platform-support/paytech-platform-support-api/.../api/dataquery/`：`DataSourceManagementApi`、`QueryTemplateManagementApi`、`DataQueryExecutionApi`；`request/` 13 个、`response/` 13 个 class。
- `.../api/enums/DataQueryErrorCode.java`（`DATA_QUERY_TIMEOUT`、`DATA_QUERY_RESULT_TOO_LARGE`、`DATA_SOURCE_UNAVAILABLE`）。
- 合同摘要：`requirements/20261022/platform-support-数据查询服务/tasks/C-api-contract.md`（主工作区）。

**数据库工件（第 2 步，未执行任何 SQL）**
- `paytech/platform-support/docs/database/platform-support.pdma.json`：脚本插入 3 个实体与图组 `data_query`（仅新增 1625 行，0 删除；插入前校验原文件可逐字节往返）。
- `docs/database/sql/platform-support-schema.sql`：追加 3 张表，头注释 47 → 50 张。
- `docs/database/sql/changes/2026-10-22-data-query.sql`：预检、执行顺序、核对与回滚 DROP。
- 需求目录 `db/ddl.sql`（主工作区，需求目录只在主工作区存在）：一行索引指向增量文件。
- 同步：`paytech-platform-support-test/src/test/resources/init/ddl.sql`（追加 3 表）、`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`（生成器重生成，`--check` 通过）、`scripts/pdmaner/platform-support-schema.test.mjs`（期望表清单加 3 张）。

**data-query 模块（第 3、4 步）**
- `pom.xml`：加 api、vault-client、http-problem、mybatis、spring-context/tx/jdbc、spring-boot、HikariCP、mysql-connector-j（runtime）、micrometer、validation-api。
- `core/enums`：新增 `DataSourceStatus`、`QueryTemplateStatus`、`DataSourceCheckResult`、`ColumnMaskType`。
- `core/dal`：`DataSource`、`QueryTemplate`、`QueryTemplateVersion` 与 3 个 Mapper；XML 在 `resources/mapper/dataquery/`。
- `domainservice/repository`（一表一个，interface + Impl）、`domainservice/service`：`DataSourceService`、`QueryTemplateService`（+Impl）、`domainservice/convert/DataQueryDetailConverter`、`domainservice/validator/QueryTemplateDraftValidator`（B2 保存校验）。
- `domain`：连接参数、检查结果、命令、前后事实、草稿 / 约束 / 列、`QueryTemplateJson`（JSON 列固定结构）、`QueryCursorCodec`、`DataQueryRun`、`DataQueryResult`。
- `engine`：SPI `QueryEngine`（加 `renderedQueryCheck`、`execute`、`check`）、`JdbcQueryEngine`、`SqlDialect` 改为接口 + `MysqlDialect` / `DorisDialect`（裁决 3：原同义枚举删除，系统库名单进方言）、`JdbcConnectionSettings`、`QueryEngines`、`QueryBudget`、`QueryResult`、`DataSourceHandle`、`QueryResultShaper`、`DataQueryRunner`（第 3—8 步）。
- `integration`：`DataSourcePoolRegistry`、`DataSourcePasswordVault`。
- `core/configuration`：`DataQueryLimitsProperties`（`data-query.limits.*`）、`DataSourceRefreshProperties`（`data-query.datasource.*`）、`DataQueryManagementProperties`（锁参数）、`DataQueryCallerProperties`（`data-query.http.callers`）。

**core / app（第 5 步）**
- core `pom.xml` 加 data-query 依赖；`dataquery/service/processor/`：18 个 Processor、`DataQueryManagementSupport`、`DataQueryAuditFacts`、`request/` 17 个、`response/` 2 个。
- app：`ext/rest/dataquery/` 三个 Controller + `DataQueryConverter`；`ext/job/DataSourcePoolRefreshScheduler`；`configuration/DataQueryConfiguration`（EnableConfigurationProperties + 条件 EnableScheduling）；`PersistenceConfiguration` 加 Mapper 包与 4 个枚举 TypeHandler；`application.yml` 加 `data-query.*` 与 4 个 metadata-only 前缀（`/data-query-admin/v1/data-sources`、`/data-source-connection-tests`、`/template-trials`、`/data-query/v1`）。
- `local-environment/vault/policy.local.json`：`DATA_SOURCE_PASSWORD_V1`（REFERENCE）与 platform-support-service 的 PROTECT（DATA_SOURCE_CONFIGURATION）/ REVEAL（DATA_SOURCE_CONNECTION）。未重启 Vault。

**测试（第 6 步）**
- data-query 模块（纯 Java）：`auto/engine/ConnectionPrivilegeCheckTest`（MySQL / Doris 授权判定含角色授权、驱动参数强制）、`auto/engine/QueryResultShaperTest`（屏蔽、游标）。
- test 模块：`auto/ext/http/dataquery/DataSourceManagementApiTest`、`QueryTemplateManagementApiTest`、`DataQueryExecutionApiTest`；基础设施 `test/base/DataQueryApiTestBase`、`test/dataquery/`（`DataQueryFixture` 被查库探针与准备说明、`DataQueryMocker`、`DataQueryRequestBuilder`、`DataSourcePasswordVaultMock`、`DataQueryPoolCleaner`）、`test/dal/DataQueryTestMapper`；修改 `MockTestBase`（Vault Mock 空基线与意外调用检查）、`VaultMockController`（数据源密码用途）、`OperationsCatalog`（4 项权限）、`PlatformDataCleaner`（3 表整表回收）、`LocalMockEnvironmentInitializer`（读取 fixture 两个密码键）、`application-mock.yml`、`ArchitectureAutoTest`（模块清单加 data-query）。

## 2. WRITE_API_EXAMPLE 第 11 节自查

| 项 | 结果 |
|---|---|
| API 与 Body 最小合同、class、字段注释、Hibernate Validator | 三个 Api 均 `@HttpExchange`，Body 为 `@Getter/@Setter` 普通 class、中文 JavaDoc、注解约束；内部 Request 由 `AbstractProcessor.validateBasic` 触发 |
| Controller/Converter 仅适配；Processor 位于所属模块，主方法可读 | Controller 只解析 ID / Code、委托 Processor；Processor 主方法按“事务外准备 → 本地保存与审计 → 提交后刷新”注释分段 |
| Processor 查询、校验，不调用 Mapper、不逐表写 | core Processor 只调 data-query 的 Service / 验证器 / 引擎 / 连接池；表访问全在 data-query 的 Service → Repository → Mapper |
| Context 与 DataObject | 跨模块边界不传 DataObject；以 data-query 发布的领域对象（`DataSourceDetail`、`QueryTemplateDetail`、`*Command`、`*Change`）承载，未另建同义 View。偏差说明见第 5 节第 6 条 |
| Service 不调其他 Service / Processor / 外部依赖 | `DataSourceServiceImpl`、`QueryTemplateServiceImpl` 只用本模块 Repository；Vault 与被查库访问在 Processor / Runner / 注册表 |
| 本地原子写入与事务位置 | 见第 0 节；`QueryTemplateServiceImpl` 自带 REQUIRED 事务，加入 Processor 事务；保存失败不刷新连接池 |
| Repository 按表、条件 SQL 与行数、成功后同步原对象 | 三个 RepositoryImpl 以 `version` / 状态 CAS，行数≠1 抛 409，成功后回写原对象；`uk_` 冲突转业务拒绝或 409 |
| 锁针对实际资源 | `lock:data-query:source:{id}`、`lock:data-query:template:{id}`；新建按 `source-code:` / `template-code:` 编码锁；连接测试、读、校验、试运行、执行无锁 |
| 原请求比较 / 业务防重 | 不适用：没有业务操作唯一身份；以编码唯一索引与版本 CAS 处理重复与并发 |
| Gateway 请求幂等与业务防重区分 | 未接入任何 Idempotency-Key 或命令幂等组件 |
| 多表组合查询 View | 列表 / 详情由 Service 组合模板 + 当前版本 + 数据源，返回领域对象，HTTP 层转换为 ResponseBody，不泄露 DataObject |
| 复用公共包、无生产 Mock | 复用 `PagingRequestBody/ResponseBody`、`RequestBodyValidationException`、`AbstractLockProcessor`、`OperationsTransactionExecutor`、`CodeEnumTypeHandler`；生产代码无测试分支 |
| 按测试规范验证 | 纯 Java 与架构测试已运行；HTTP 入口测试仅编译（见第 4 节） |

## 3. 测试命令与实际输出

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech
./mvnw -q -o -pl "<platform-support 9 个子模块，同任务卡>" -am install -DskipTests   → exit 0
./mvnw -o -pl platform-support/paytech-platform-support-data-query test
  QueryTemplateEngineTest 27、ReadOnlySqlGuardTest 15、QueryResultShaperTest 2、ConnectionPrivilegeCheckTest 4
  Tests run: 48, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
./mvnw -q -o -pl platform-support/paytech-platform-support-test test-compile   → exit 0
./mvnw -o -pl platform-support/paytech-platform-support-test test -Dtest=ArchitectureAutoTest -Dsurefire.failIfNoSpecifiedTests=false
  Tests run: 1, Failures: 0（R1–R14 含新模块，无未登记违规）
node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check   → table-ownership.yaml is current
```

`node --test scripts/pdmaner/platform-support-schema.test.mjs`：改动前就失败（期望表清单与 2026-09/10 的若干增量已漂移，与本卡无关）；把同一套断言只套在 3 张新表上，“字段 / 默认值 / 完整注释与 PDManer 一致”和“主键与索引一致”两项通过。

## 4. 未运行的测试及原因

- `DataSourceManagementApiTest`（8 例）、`QueryTemplateManagementApiTest`（6 例 + 8 组参数化拒绝）、`DataQueryExecutionApiTest`（9 例）：依赖尚未执行的 `2026-10-22-data-query.sql`（`mor_platform` 3 表）与本地被查库 `mor_data_query_fixture`（B 卡的 manual SQL）。只编译通过，**待主会话授权执行两份 SQL 后运行**：`./mvnw -o -pl platform-support/paytech-platform-support-test test -Dtest='*Api*Test' ...`（需按单 JVM 串行，且本地 `mor_platform` 测试独占表为空）。
- 首次运行可能需要微调的点：B2 拒绝样例只断言“第 N 行第 M 列：”前缀而非具体行号；连接被查库依赖驱动默认 `sslMode=PREFERRED`（本地 MySQL 8 的 caching_sha2_password 在非 SSL 时会要求 allowPublicKeyRetrieval，后者不在白名单）。
- 角色授权拒绝（technique 9 的一项）：本地被查库没有带角色的账号，由纯 Java `ConnectionPrivilegeCheckTest` 覆盖判定逻辑；HTTP 层只覆盖写权限账号。
- “停用时进行中的查询正常结束”：用注册表 `lease` 模拟进行中的查询（借出后停用、仍能执行、归还后关闭），不靠并发时序。
- Doris 方言 manual 测试（`DorisDialectManualTest`）未写，本卡范围外。

## 5. 需要主会话裁决的问题

1. **权限风险等级：** technique 7 写 `DATA_QUERY_READ` / `DATA_QUERY_EXECUTE` 为 NORMAL，但 `t_permission.risk_level` 允许值只有 LOW、MEDIUM、HIGH、CRITICAL。测试目录按 LOW 登记；注册 DML 卡需同样用 LOW（或确认改为 MEDIUM）。
2. **约束字段名：** constraints_json 的超时用 `timeoutSeconds`（整数秒），technique 4.4 写的是 `timeout`（无单位）。已写入接口合同；technique 需同步。
3. **SPI 最终形态（裁决 1 落地）：** 除 `execute`、`check` 外另加 `renderedQueryCheck(QuerySource)`（保存校验对展开组合做只读校验，对应 R7“渲染后校验”）；`execute` 带第三个参数 `QueryBudget`（超时与响应字节上限）。technique 4.1 待按此更新。
4. **专有错误码的 HTTP 状态：** `DATA_QUERY_TIMEOUT`、`DATA_QUERY_RESULT_TOO_LARGE` 走 `FailException` 默认 400（未在 `PlatformApiErrorConfiguration` 注册特殊状态）；`DATA_SOURCE_UNAVAILABLE` 为 503。如需 408 / 422 请指示。
5. **未登记调用方：** 返回 400 `BUSINESS_CHECK_FAILED`（与 Notification 白名单一致），未用 403。
6. **跨模块边界：** DEVELOPMENT_CONVENTIONS 第 3 节禁止 core 使用 data-query 的 DataObject / Repository，因此 data-query 发布了一组领域对象与命令（约 20 个类），版本核对、状态迁移与删除引用检查放在 data-query 的 Domain Service 内，core Processor 只编排权限、Vault、连接检查、事务与审计。比币种维护（同模块）多一层，但符合模块边界。
7. **共享本地仓库被覆盖：** 期间 `~/.m3` 中的 `paytech-platform-support-api` SNAPSHOT 被其他会话在 03:00 重新安装（不含本卡新增类），导致单模块 `test-compile` 一度找不到 `api.dataquery`。我改用 reactor `-am test-compile` 验证后，再按任务卡完整执行一次 install。并行会话共用 `~/.m3` 的 SNAPSHOT 会互相覆盖，建议合并前在主工作区统一重建。
8. **Vault 本地策略：** 只改了 `policy.local.json`，`policyVersion` 未改，未重启 Vault；生效需主会话重启本地 Vault，mor-test 策略文件另需同步。
9. **PDManer 写法：** PHYSICAL_DECISIONS 第 1 节写“只能由 Generator 生成、禁止直接改 JSON”，本卡按任务卡用脚本精确插入（生成器与模型已有漂移，见该文档 2026-10-20 注）。图组 defKey 用 `data_query`（与既有 `reference_data` 等下划线命名一致），technique 写的是 `data-query`。
10. **同一工作区的其他卡：** BFF / 前端卡也在 `Code-MoR-dataquery` 写文件（`operations-console-bff`、`operations-console-web`、`platform-support-initial-data.sql`），本卡未改动它们；BFF 侧复制了一份请求类，其中有两处未用的 `Positive` import（本卡的同名类已清理）。
11. **需求目录文件位置：** `tasks/C-api-contract.md`、`tasks/C-report.md` 与 `db/ddl.sql` 写在主工作区的需求目录（该目录只存在于主工作区）。

## 6. 中文摘要

完成卡 C 全部 6 步：18 个接口合同（含专有错误码）先行发布并写合同文档；PDManer、完整 Schema、增量 SQL、测试 DDL 与表归属同步（未执行 SQL）；data-query 模块补齐 3 张表的持久化、两个 Domain Service、保存校验、JDBC 引擎（超时、行数、字节上限、值转换、屏蔽、游标）、MySQL / Doris 方言（合并为接口 + 实现）、Hikari 连接池注册表与 Vault 适配；core 18 个 Processor 按“事务外权限 / Vault / 连接检查 → 短事务写入与审计 → 提交后刷新连接池”组织；app 三个 Controller、刷新任务与配置。纯 Java 48 例与架构测试通过，HTTP 入口测试 23 例已编译，待授权执行 SQL 后运行。

## 7. 主会话裁决（卡 D 之后）的处理结果

1. **硬上限下调（已改）：**
   - `DataQueryLimitsProperties`：`max-timeout` 默认 `8s`，`max-body-length` 默认 `48KB`。
   - `application.yml`、`application-mock.yml` 同步修改。
   - API 中模板正文 `@Size(max = 65536)` 改为 `49152`，四个请求类同步；`QueryTemplateConstraints` 与 `TemplateLimits` 的说明同步。
   - `t_query_template_version.body` 的字段注释“默认 64 KB”改为“默认 48 KB”，PDManer、Schema、增量 SQL、测试 DDL 四处一致。
   - 合同文档 `C-api-contract.md` 已同步：`timeoutSeconds` 为 1..8，body ≤48 KB，并加了裁决说明。
   - 测试 Builder 的默认超时改为 5 秒；“结果过大”用例的模板超时改为 8 秒。
2. **注册资源登记（已改）：**
   - 在 `paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` 中，`OPERATIONS_CURRENCY_SPECS` 之后新增两个菜单：
     - `OPERATIONS_DATA_SOURCES`（数据源，`/data-query/data-sources`），按钮 `OPERATIONS_DATA_SOURCE_MANAGE`；
     - `OPERATIONS_QUERY_TEMPLATES`（查询模板，`/data-query/templates`），按钮 `OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`。
   - 测试基线 `OperationsCatalog` 已登记 4 项权限，ID `900000000000131101..104`，与卡 D 块 131 一致，风险等级 LOW / HIGH / HIGH / LOW。
3. **日志前缀：** Platform 的 `web.logging.metadata-only-path-prefixes` 与卡 D 的 BFF 前缀是同一组：
   - `/data-query-admin/v1/data-sources`
   - `/data-query-admin/v1/data-source-connection-tests`
   - `/data-query-admin/v1/template-trials`
   - `/data-query/v1`

   正式配置与 mock 配置都已包含，无需再改。
4. **`~/.m3` 被覆盖后重建，实际执行的命令（都在 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech` 下，`JAVA_HOME` 为 JDK 25）：**
   ```
   M="platform-support,platform-support/paytech-platform-support-api,platform-support/paytech-platform-support-access-control,platform-support/paytech-platform-support-audit-security,platform-support/paytech-platform-support-file-document,platform-support/paytech-platform-support-delivery,platform-support/paytech-platform-support-data-query,platform-support/paytech-platform-support-core,platform-support/paytech-platform-support-app,platform-support/paytech-platform-support-test"
   ./mvnw -q -o -pl "$M" -am install -DskipTests
     → exit 0
   ./mvnw -o -pl "$M" -am test -Dsurefire.failIfNoSpecifiedTests=false \
     -Dtest='QueryTemplateEngineTest,ReadOnlySqlGuardTest,ConnectionPrivilegeCheckTest,QueryResultShaperTest,ArchitectureAutoTest'
     → data-query 48 例全部通过；ArchitectureAutoTest 1 例通过；BUILD SUCCESS
   ```
   第二条命令在 Reactor 内完成 test 模块的 test-compile，不依赖 `~/.m3` 中的 platform-support 构件。HTTP 入口测试仍只编译，等授权执行 SQL 后再运行。

### 补做（主会话第二次核对后）

- **第 1、2 项在上一轮已经落地，本次只在磁盘上逐项复核，未重复修改：**
  - `application.yml` 第 230、235 行：`max-timeout: 8s`、`max-body-length: 48KB`。
  - `DataQueryLimitsProperties` 默认值：`Duration.ofSeconds(8)`、`DataSize.ofKilobytes(48)`。校验只要求各项为正数，不引用旧值。
  - `C-api-contract.md` 已写 `timeoutSeconds` 1..8、正文不超过 48 KB，并附裁决说明。
  - `console-resource-capabilities.json` 第 194—213 行：已登记两个菜单 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`，各带一个按钮（`OPERATIONS_DATA_SOURCE_MANAGE`、`OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`）。
- **第 2 项的相关测试：**
  - 读取该文件的只有 `ConsoleResourceCapabilities`。它在 Spring 启动时加载，并拒绝重复的组件键和动作编码。
  - 唯一用到它的测试是 `GatewayRouteIdempotencyModeTest`。这是 HTTP 入口测试，需要本地 `mor_platform` 的测试独占表，按卡上的约束未运行。
  - 替代做法：用同样的唯一性规则直接检查 JSON 文件。结果为 51 个组件键、67 个动作编码，都没有重复。
- **重跑结果（`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech`）：**
  - `./mvnw -o -pl "$M" -am test-compile -q`：exit 0。`$M` 为卡上那组显式模块。
  - `./mvnw -o -pl "platform-support,platform-support/paytech-platform-support-api,platform-support/paytech-platform-support-data-query" -am test -Dtest='QueryTemplateEngineTest,ReadOnlySqlGuardTest,ConnectionPrivilegeCheckTest,QueryResultShaperTest' -Dsurefire.failIfNoSpecifiedTests=false`：Tests run: 48，Failures: 0，BUILD SUCCESS。
- **主会话对第 5 节问题的裁决：** 全部接受，即风险等级用 LOW、字段名用 `timeoutSeconds`、SPI 增加 `renderedQueryCheck` 与 `QueryBudget`、超时与结果过大返回 400 加专有码、未登记调用方返回 400、发布领域对象与命令、PDManer 图组 defKey 用 `data_query`。本卡无需再改。Vault 重启由主会话在验证阶段处理。

# 任务卡 C：Platform 后端——接口合同、持久化、JDBC 引擎、连接池、Processor 与 HTTP 入口测试

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`（与任务卡 A 同一目录，A 已完成 `paytech-platform-support-data-query` 模块的 `template`、`engine` 包与 JSqlParser 依赖）。只在该目录改文件。
- 授权：普通文件编辑、编译、运行 platform-support 相关模块测试。**不提交、不推送、不执行任何 SQL（含本地库）、不启动或修改 local-environment 容器。** 需要本地库表才能跑的测试，写好并编译通过即可，在报告中说明“待主会话授权执行 SQL 后运行”。
- 权威：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-数据查询服务/technique.md`（全文，重点第 2、4、5、6、7、9 节）与同目录 `requirement.md`。A 卡的实现报告与主会话裁决见同目录 `tasks/A-report.md`，**裁决 1—4 必须照做**（SPI 加 execute/check、参数错误用公共 422、方言枚举合并）。

## 开工前必读（实际读取）

`AGENTS.md` 的“接口开发必读与自查”要求：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（完成前按第 11 节逐项自查）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、`paytech/platform-support/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`。先在报告开头简述已读路径、所属 Processor/Context、本地原子写入与事务外步骤。

参照实现（读完整调用链再写）：
- 币种维护：`paytech-platform-support-core/.../referencedata/service/processor/CreateCurrencySpecProcessor.java` 及 Update/Status Processor、`OperationsTransactionExecutor`、`CurrencySpecManagementSupport`、app `ext/rest/referencedata/CurrencySpecManagementController.java`、api `referencedata/CurrencySpecManagementApi.java`。
- 调用方识别：app `ext/rest/internalauth/InternalCallerResolver.java` 与 `NotificationCallerProperties` 的用法。
- Vault：delivery 模块的 `WebhookSecretVault`、channel-connector 的 `ConfigureChannelAccountProcessor`（Protect 在锁内、事务外）与 `ChannelAccountConfigReader`（Reveal）。
- Mapper 扫描：app `configuration/PersistenceConfiguration.java`。
- 定时任务写法：ledger `CurrencySnapshotRefreshScheduler`（只参考结构）。

## 范围（按顺序做）

1. **接口合同（先做，完成后立即在本卡同目录写 `tasks/C-api-contract.md`**：列出三个 Api 接口的方法、路径、Header、Request/Response 字段与类型、错误码，供 BFF 与前端卡并行开工）：`paytech-platform-support-api` 下 `com.paytech.platform.support.api.dataquery`，`DataSourceManagementApi`、`QueryTemplateManagementApi`、`DataQueryExecutionApi`，按 technique.md 第 7 节 18 个接口；`request`/`response` 包、`*RequestBody`/`*ResponseBody` 普通 class、中文 JavaDoc、Bean Validation；ID 为字符串；分页复用 `PagingRequestBody`/`PagingResponseBody`。专有错误码 `DATA_QUERY_TIMEOUT`、`DATA_QUERY_RESULT_TOO_LARGE`、`DATA_SOURCE_UNAVAILABLE` 按 DEVELOPMENT_CONVENTIONS 第 4 节发布在 api 的 enums。
2. **数据库设计工件**：
   - PDManer `paytech/platform-support/docs/database/platform-support.pdma.json` 新增图组 `data-query`，3 张表按 technique.md 第 6 节（`t_data_source.database_name` 可空）；表与字段注释完整（含枚举允许值）。用脚本精确插入，保持 JSON 其余部分字节不变，参照现有表的实体结构。
   - `paytech/platform-support/docs/database/sql/platform-support-schema.sql` 追加 3 张表；新增增量 `paytech/platform-support/docs/database/sql/changes/2026-10-22-data-query.sql`（文件头：执行顺序、预检、回滚 DROP）。
   - 本需求目录下 `db/ddl.sql` 只写一行索引指向增量文件。
   - **不要执行任何 SQL。**
3. **持久化与 Service**（data-query 模块）：3 个 DataObject（普通 class、中文字段 JavaDoc、闭合枚举用 `CodeEnumTypeHandler` 显式注册、`Instant` 用 UTC TypeHandler）、Mapper + XML（`resources/mapper/dataquery/`）、Repository（interface + Impl，一表一个）、Domain Service `DataSourceService`、`QueryTemplateService`（interface + Impl）。CAS 用 `version`；模板保存在一个事务内插版本行、更新 `current_version_no` 和冗余 `data_source_id`、`version+1`。
4. **JDBC 引擎与连接池**（data-query 模块）：`JdbcQueryEngine` 实现 A 卡的 SPI（执行、超时 `setQueryTimeout`、`setMaxRows`、`setFetchSize`、响应字节上限、值转换表、列屏蔽、`hasMore`、`nextCursor` Base64URL JSON 带版本号）；`MysqlDialect`/`DorisDialect` 的账号权限检查（technique.md 5.2，`*.*` 范围允许、角色授权一律不通过）与只读会话；驱动参数强制 `allowMultiQueries=false` 等；按字段拼 JDBC URL，`connection_params` 白名单 5 项；`DataSourcePoolRegistry`（Hikari，`minimumIdle=0`、`readOnly=true`、退役池等 inFlight 归零或超时后关闭）；`DataSourcePasswordVault`（Protect/Reveal，数据类型 `DATA_SOURCE_PASSWORD_V1`，用途 `DATA_SOURCE_CONFIGURATION`/`DATA_SOURCE_CONNECTION`）；`DataQueryLimitsProperties`（`data-query.limits.*`）、刷新与调用方配置的 Properties。
5. **Processor 与入口**：
   - core `dataquery/service/processor`：数据源新建/修改/状态/删除/连接测试，模板新建/修改/状态/恢复/校验/试运行，执行。写 Processor 继承 `AbstractLockProcessor`（`lock:data-query:source:{id}` / `lock:data-query:template:{id}`），授权走 `OperationsManagementPolicyService`，审计走 `OperationsTransactionExecutor`；Vault 与被查库访问都在事务外；执行 Processor 继承 `AbstractProcessor`，额外校验 `data-query.http.callers`。
   - app `ext/rest/dataquery` 三个 Controller 实现 Api；`ext/job/DataSourcePoolRefreshScheduler`（`data-query.datasource.refresh-interval`，默认 PT5S，可关闭开关用于测试）；`PersistenceConfiguration` 增加 dataquery Mapper 包；`application.yml` 增加配置键（含 `web.logging.metadata-only-path-prefixes` 加入执行与试运行路径，以及管理路径中带密码的新建/修改/测试连接路径）。
   - 本地 Vault 策略：`local-environment/vault/policy.local.json` 增加 `DATA_SOURCE_PASSWORD_V1` 数据类型与 platform-support-service 的 PROTECT/REVEAL 规则（只改文件，不重启 Vault）。
6. **测试**（`paytech-platform-support-test`，`auto`，按 UNIT_TEST_GUIDE 与现有 Platform 测试基类）：technique.md 第 9 节的 `DataSourceManagementApiTest`、`QueryTemplateManagementApiTest`、`DataQueryExecutionApiTest`。被查库是本地 MySQL 的 `mor_data_query_fixture`（账号 `data_query_fixture_reader` / `data_query_fixture_writer`，任务卡 B 正在写初始化 SQL，表名以 B 的 `local-environment/mysql/manual/2026-10-22-data-query-fixture.sql` 为准——若在 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery-r1` 中已出现就读取它对齐表名）；缺少库或账号时测试立即失败并给出准备说明。Vault 用测试 HTTP 端点返回 DTO，不用 Mockito Stub。TestMapper 负责清理 3 张表。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech
# 注意：-pl platform-support -amd 会拉入 acquiring-core（依赖本地缺失的 counterparty-api SNAPSHOT），改为显式列出 platform-support 及其子模块：
./mvnw -q -pl "platform-support,platform-support/paytech-platform-support-api,platform-support/paytech-platform-support-access-control,platform-support/paytech-platform-support-audit-security,platform-support/paytech-platform-support-file-document,platform-support/paytech-platform-support-delivery,platform-support/paytech-platform-support-data-query,platform-support/paytech-platform-support-core,platform-support/paytech-platform-support-app,platform-support/paytech-platform-support-test" -am install -DskipTests
./mvnw -pl platform-support/paytech-platform-support-data-query test
./mvnw -q -pl platform-support/paytech-platform-support-test test-compile
```

HTTP 入口测试依赖尚未执行的 SQL，本卡只要求编译通过；不要连接或清理共享本地库。不要运行整个 reactor 测试。

## 交付报告（中文，写入 `tasks/C-report.md` 并在最终回复中给出摘要）

改动文件；接口合同位置；WRITE_API_EXAMPLE 第 11 节逐项自查结果；测试命令与实际输出；未运行的测试及原因；需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**

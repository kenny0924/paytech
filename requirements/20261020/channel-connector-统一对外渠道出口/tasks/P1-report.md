# P1 报告：第一段等价迁移（Platform 外部 API → channel-connector）

> 工作区：`/Users/kenny/Desktop/Workspace/Code-MoR-ccu`（基于 `4ab205e0`，detached）。未执行任何 SQL，未连接 MySQL / Redis，未 `mvn install`，未提交。
> 依据：任务卡 `tasks/P1.md`、`technique.md` 第 2–4 节。

## 1. 结论

- 卡片要求的 test-compile 命令通过（9 个目标模块，`-am`）。
- 不依赖数据库的测试全部实际运行并通过：channel-connector 架构测试（5）、协议纯 Java 领域测试（48）、Platform `ArchitectureAutoTest`（1）、arch-rules（62）、Reactor 架构测试（3）、operations-console-bff 全模块（1844，其中 `ExternalApiBrowserAutoTest` 102 个，含 `external-api-boundary.json` 44 例）。
- 依赖 MySQL / Redis 的入口测试（channel-connector、Platform、card）尚未运行，初始化与运行命令见第 6 节。

## 2. 改动文件清单（按系统）

### channel-connector
- **新模块 `paytech-channel-connector-protocol`**（`pom.xml` + 262 个文件）：`paytech-platform-support-integration` 主代码按包原样迁入 `com.paytech.channel.connector.protocol`。Mapper XML 放在 `resources/mapper/protocol/`。新增或改造的类：
  - `core/enums/ChangeTargetType`、`ChangeType`：自 core 迁入，新增 7 个目标类型和 `DELETE`。
  - `core/enums/ConfigurationField`：沿用 Platform 审计的字段名。
  - `core/enums/StandardApiNotSentCode`，`domain/execution/StandardApiNotSentException`：NOT_SENT 类型化异常。
  - `core/dal/dataobject/ChannelConfigChange`（新增 `changeDetail`）、`core/dal/mapper/ChannelConfigChangeMapper`、`domainservice/repository/ChannelConfigChangeRepository(+Impl)`：自 core 迁入。
  - `domain/ChannelConfigChangeRecord`、`domain/ChannelConfigChanges`（自 core 迁入）、`domain/ConfigurationFieldChange`、`domain/ExternalApiLocks`（原 `ExternalApiPermissions`，只保留锁键）。
  - `domainservice/service/ChannelConfigChangeService(+Impl)`：发布给 core 使用的变更记录服务。
  - `service/processor/EditorCapabilitiesCatalog`：替代 `GetEditorCapabilitiesProcessor`。
  - `ExternalApiTransactions`：去掉审计观察。
  - `ExternalApiProcessorSupport.changed`：改为构造变更记录。
  - 全部 Processor：去掉策略复核、审计和 `@ConditionalOnProperty`，改写变更记录。
  - request 类：去掉 `actor`、`session`，写请求改为 `long actorId`。
  - `ExecutionSnapshotLoader`、`InitializerRunner`、`ExecuteStandardApiProcessor`、`ExecuteVerifyRunProcessor`：抛出或捕获类型化 NOT_SENT。
  - `PaymentCardFunction.CALLER`：改为 `channel-connector-service`。
  - 配置前缀改为 `channel-connector.protocol.*`。
  - `ApiFieldMapper.xml`、`IntegrationScriptMapper.xml`：枚举列改为内联 TypeHandler。
- **api**：
  - 新增 `externalapi/*`：7 个接口、23 个请求、35 个响应，原样迁入。写接口改为 `@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER)`，查询与校验接口去掉请求头，同步更新 JavaDoc。
  - 新增 `api/enums` 下 7 个枚举。
  - `pom.xml` 增加 jackson-annotations 和 jackson-databind。
- **core**：
  - `pom.xml`：去掉 `paytech-platform-support-api`，改依赖 protocol。
  - 删除：`PlatformStandardApiClient`、`PlatformIntegrationClient(Impl)`、`ChannelConfigChangeQueryService(Impl)`，以及迁往 protocol 的 DataObject、Mapper、Repository、XML、enums、`ChannelConfigChanges`。
  - `PlatformPayOutputs` 改名为 `PayOutputs`。
  - 重写 `ExecuteChannelOperationProcessor`，改为进程内执行。
  - `ChangeChannelStatusProcessor` 改用 `IntegrationLookupService`。
  - 新增 `ChannelConfigTransactions`，由 Processor 在同一本地事务中组合业务写入和变更记录。
  - 13 个配置写 Processor 与 `CancelChannelCostVersionProcessor` 改为经该组件写入。
  - 5 个 ServiceImpl 去掉变更记录插入和内层事务。`ChannelCostPlanService.cancelVersion(context, Instant)` 改签名。
  - 4 个 Context 改持 `ChannelConfigChangeRecord`。
  - 若干 JavaDoc 去掉 Platform 表述。
- **app**：
  - 新增 `ext/rest/externalapi/`：7 个 Controller、3 个 Converter，以及 `ExternalApiInputs`（替代 Platform 的 `ConfigurationInputs` / `ClientApiManagementInputs`）。
  - 新增 `configuration/PaymentCardMaterialConfiguration`，自 Platform 迁入。
  - 删除 `ChannelConnectorOutboundConfiguration`、`ChannelConnectorOutboundProperties`。
  - `ChannelConnectorConfiguration` 的 MapperScan 增加 protocol。
  - `ChannelConfigurationWire.actor` 改为 public，供外部 API 管理复用现有校验。
  - 变更记录查询改走 protocol 服务。
  - `application.yml` 与 Nacos 模板：删除 `channel-connector.outbound.*`，增加 `channel-connector.protocol.*`，默认值不变。
  - `pom.xml` 增加 protocol 与 card-api。
- **其他**：
  - 父 POM：增加 protocol 模块，管理 card-api、aviator 5.3.3。
  - `compensation-event-catalog.yaml` 增加 `INTEGRATION_VERIFY_RUN`。
  - README 更新模块表、合同、外部 API 管理节、28 表测试库与隔离说明、依赖与配置键。
- **DDL / 模型**（子 agent）：
  - `channel-connector-schema.sql` 与测试 `init/ddl.sql`：10 张表原样复制，`t_channel_config_change` 增加 `change_detail JSON NULL` 并更新列注释与表注释。
  - 新增增量 `docs/database/sql/changes/2026-10-20-protocol-tables.sql`。
  - `scripts/pdmaner/generate-channel-connector-model.mjs`，新增 `scripts/pdmaner/channel-connector/protocol-{model,tables}.mjs` 与 `channel-connector-protocol-model.test.mjs`，重新生成 `channel-connector.pdma.json`。
  - 同步 `generate-payment-model.mjs`、`payment-*-overrides/metadata/test.mjs`：变更记录表的字段清单与注释在 Payment 分区生成器中维护。
- **测试**（子 agent）：
  - 迁入 Platform 测试 21 类：externalapi 10 个、standardapi 执行 7 个（改放 `auto/standardapi`）、`IntegrationVerifyRunHandlerTest`（`auto/ext/eventhandler`）、2 个 SmokeTest（`auto/repository`）、`InternalAuthDualOutboundTest` 读卡部分（`auto/internalauth`）；另有 8 个 domain 测试。
  - 迁入测试设施：`ExternalApiTestBase`、`ExternalApiResponseChecker`、`ExternalApiFixtures/Requests/Samples`、`ExternalApiTestMapper`、`VerifyTestMapper`、`ExternalApiMocker`、`VerifyRunFixture`、`ChannelMocker` + `ChannelMockController`、`PaymentCardMocker` + `PaymentCardMockController`。Vault 账号 Mock 并入现有 `VaultMocker` / `MockVaultController`。
  - 迁入资源 `externalapi/**`，新增 `operation-card-pay.json` 两份。
  - 删除 `MockPlatformController`、`PlatformMocker`。
  - `ChannelDataMocker` / `ChannelTestMapper` 覆盖 28 表，并接受隔离库名 `mor_channel_connector_{后缀}`。
  - `application-mock.yml`：增加 card-service 发现、protocol 配置，Mock 环境允许 http。
  - `ExecuteChannelOperationTest` 由 10 例扩为 16 例；`PaymentChannelApiTest` 改为插入本库接入方。
  - 架构测试：增加 protocol 模块与 10 表；新增 core 不访问 protocol 的 `*Impl`、Repository、Mapper、DataObject 的断言。

### platform-support（子 agent）
- **删除**：
  - `paytech-platform-support-integration` 模块，及父 POM、app、core、test POM 中的引用。
  - app 的 `ext/rest/externalapi/`、`ext/rest/standardapi/`、`PaymentCardMaterialConfiguration`。
  - api 的 `externalapi/`、`standardapi/`，以及 8 个枚举（7 个迁出，外加 `StandardApiExecutionErrorCode`）。
  - 测试：77 个文件，含 externalapi 资源。
- **修改**：
  - `PersistenceConfiguration`：去掉扫描与 4 个 TypeHandler。`PlatformApiErrorConfiguration`：去掉执行错误码分支。
  - `application.yml`：去掉 `ps.integration.*` 与 `/external-api-admin` 日志前缀。补偿目录去掉 `INTEGRATION_VERIFY_RUN`。
  - `PlatformDataCleaner` 去掉 10 表；`ArchitectureAutoTest` 去掉 integration；README 与测试 README 同步。
  - schema 与测试 DDL 去掉 10 表。PDManer：删除 `scripts/pdmaner/platform-support/manifests/09-integration.mjs` 并同步 manifest、oracle、测试，重新生成 `platform-support.pdma.json`。
  - `agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md` 第 9 节改为“已迁出”。
- **新增**：`requirements/20261020/channel-connector-统一对外渠道出口/db/platform-support-service/drop-integration.sql`，只有 10 个 DROP，标注“第一段 mor-test 验收后执行”。
- **保留**：`initial-data` 中的 `EXTERNAL_API_*` 权限、菜单、Route 行，以及 `console-resource-capabilities.json`。
- **我追加**：`PlatformDataCleaner`、`ApiCredentialMocker` 的库名检查也接受 `mor_platform_{后缀}`，见第 6 节。

### card（子 agent）
- `CardApiConstants.CHANNEL_CONNECTOR_SERVICE`。
- `RevealChannelCardMaterialProcessor` 同时允许 `channel-connector-service` 与 `platform-support-service`，注释写明第二段收回。
- `ChannelCardMaterialRevealTest` 增加三个场景：两种调用方都能读卡，其他调用方返回 404。
- 我追加：`CardDataMocker` 接受 `mor_card_{后缀}`。

### operations-console-bff（子 agent）
- `PlatformClientConfiguration` 删除 7 个 externalapi Bean。
- `ChannelConnectorClientConfiguration` 新增 7 个 Bean，改用独立的 externalApi 工厂，理由见第 3 节 D7。
- 7 个 `Platform*ManagementClient(Impl)` 改名为不带前缀的 `*Client(Impl)`：不再传 Session ID，读请求和校验请求不带操作人。
- `ExternalApiContractConverter` 同步调整；README 同步。
- `ExternalApiBrowserAutoTest` 改为 channel-connector 模拟端，44 个边界用例保持通过。

### tools/arch-rules
- 子 agent：
  - `generate-table-ownership.mjs`：channel-connector 的 `stepAddedTables` 加入 10 表。
  - 重新生成 `table-ownership.yaml`。
  - `TableOwnershipAutoTest`：Platform 57 → 47，channel-connector 18 → 28。
- 我：`known-deviations.yaml` 为 `paytech-channel-connector-api` 登记两条 R2，即 jackson-annotations 和 jackson-databind，理由见 D3。

## 3. 与 technique / 任务卡的偏差及原因

| # | 偏差 | 原因 |
|---|---|---|
| D1 | 变更记录写入不在 core Service 内调用 protocol 服务，而由 Processor 组合：经新组件 `ChannelConfigTransactions.write(record, () -> service.x(...))` 在同一本地事务中执行 | 开发规范 5.2 禁止 Service 调用其他 Service，跨模块写入应由 Processor 组合。`ConfigureChannelAccountProcessor` 同时注入 `VaultClient`，若直接使用 `TransactionTemplate` 会触发 R7，所以把组合放进专门组件，事务内只访问本库。原 5 个 Service 因此去掉了内层事务，改为加入调用方事务 |
| D2 | 4 个枚举 TypeHandler 没有全局登记，改为在 protocol Mapper XML 中内联 `CodeEnumTypeHandler`（同时指定 javaType） | channel-connector 的 MyBatis 配置注释写明：全局登记会让 XML 中内联的 `CodeEnumTypeHandler` 取到其他枚举的实例（MyBatis 按 Handler 类缓存实例）。core 的 XML 全部使用内联写法，若按卡片全局登记，会破坏现有映射。`VerifyRunStatus` / `VerifyStepStatus` 与 Platform 原状一致，仍用 MyBatis 默认枚举映射（Code 等于常量名） |
| D3 | `paytech-channel-connector-api` 新增 Jackson 依赖，并在 `known-deviations.yaml` 登记两条 R2 偏差 | 迁入的 DTO 使用 `@JsonCreator` / `@JsonProperty` 和 `JsonNode`，为保持 Wire 原样不改写。Platform api 有同样的登记。**按规则需主会话确认** |
| D4 | 4 个静态校验 POST（validate*）不带操作人 | 卡片规定“写接口必带、读接口不带”。校验接口不写库，所以按读接口处理。`SaveVerifyCaseRequest.actorId` 改为可空 `Long`，校验时为 null |
| D5 | `GetEditorCapabilitiesProcessor` 改为 `EditorCapabilitiesCatalog`，不经 AbstractProcessor 模板 | 去掉 actor / session 后，这个 Processor 已没有输入；R14 又要求名为 `*Processor` 的类必须继承模板。它是纯静态目录，不读库 |
| D6 | NOT_SENT 类型化异常额外携带原 HTTP 状态（`problemStatus`）和字段位置 | 沙箱验证运行的步骤结果要记录 `{kind:PROBLEM,status,code,fields}`，带上这两项才能保持原记录口径。输入校验失败（原 422）也归入该异常（`VALIDATION_ERROR`），与原 NOT_SENT 集合一致 |
| D7 | BFF 为外部 API 管理单独建立代理工厂，不复用渠道配置工厂 | 现有渠道工厂开启了 Owner Problem detail，会把 channel-connector 的 400/404/409 原因直接透给浏览器，而 Platform 工厂关闭了该项。新工厂沿用 Platform 设置：关闭 Owner detail，保留 422 字段错误投影，严格解码 RFC 9457，不额外登记错误码，连接 3s / 读取 5s，使 BFF 对前端的行为不变 |
| D8 | 增量 SQL 放在 `docs/database/sql/changes/2026-10-20-protocol-tables.sql`；technique 第 4 节写的是 `db/channel-connector/ddl-phase1.sql` | 以任务卡为准 |
| D9 | `ChannelConfigChangeApi` 的 `targetType` 白名单没有扩展到 7 个新类型 | 卡片没有要求，扩展属于合同变更。新记录已经写入，但暂时不能通过该接口查询，建议随第二段渠道详情页开放 |
| D10 | `change_detail` 保存 Platform 审计原有字段的前后值 | 卡片要求“不含值”，但这些值本身就是名称、编码或 SHA-256 摘要，与 Platform `ConfigurationFieldChange` 的口径一致，不含定义原文、参数或凭据 |
| D11 | 激活拒绝文案由“Platform 不存在同编码接入方”改为“不存在同编码接入方: {code}”。“Platform 失败 500”场景已删除 | 现在查询的是本库，不再有远端失败 |
| D12 | 三个测试库守卫放宽，接受隔离库名 `<库名>_{1–16 位小写字母数字}`：channel-connector、Platform（两处）、card | 主会话要求在隔离库运行测试，原守卫只接受精确库名 |

## 4. 未完成项与遗留

1. **DB 入口测试未运行**：约束要求不连接数据库。命令见第 6 节。
2. **执行行为变化**：
   - 进程内执行没有了原来 Platform 的 30s 读超时，现在只受流程预算约束（technique 已说明）。
   - `ExecuteChannelOperationProcessor` 遇到未预期异常时，只记录异常类型的 WARN 日志并返回 ERROR，与原 HTTP 失败一样视为结果未知。
3. **部署注意**：
   - 若任何环境开启 internal-auth，Platform 签发白名单需允许 `channel-connector-service` 访问 `card-service`、`vault-service`。仓库内只有测试配置，本次没有修改。
   - mor-test 目前没有开启。
4. **Vault**：technique 结论是 channel-connector 已有 `REVEAL CHANNEL_ACCOUNT_CONFIG_V1 [CHANNEL_EXECUTION]` 规则，本次没有改动，但未在环境中实际验证。
5. **既有失败，与本次无关**：以下 node 测试在 HEAD 的完整导出上同样失败，失败用例集合一致：
   - `payment-model.test.mjs`：1 个失败，payment.pdma.json 摘要不符。
   - `platform-support-model.test.mjs`：3 个失败。
   - `platform-support-schema.test.mjs`：4 个失败。
   - 本次新增或修改的 `channel-connector-protocol-model.test.mjs`（6/6）和 `payment-channel-query.test.mjs`（1/1）通过。
6. **共享状态风险**：
   - card 测试使用本机 RabbitMQ vhost `/mor-local`，读卡验证码 Redis Key 不带测试前缀。建议只运行 `ChannelCardMaterialRevealTest`。
   - Platform 全量测试同样涉及 RabbitMQ 和 MinIO Mock，详见第 6 节。
7. **主工作区合并冲突**：任务卡说明主工作区有其他会话在改 ops-bff 与 arch-rules，合入时需按段合并。
8. **数据迁移、删表和镜像替换**：technique 3.4、3.5，未执行。
9. **internal-auth 开启时无法启动（迁移前已存在）**：channel-connector 没有 Platform Token 来源（没有 `InternalTokenIssueApi` Bean，也没有 jwks-uri），`internal-auth.mode` 设为非 OFF 时启动失败。迁移后读卡路径也受影响。`InternalAuthDualOutboundTest` 只证明了静态密钥机制，Token 为 sub=channel-connector-service、aud=card-service、purpose GENERAL。
10. **迁入表的列注释保留旧表述**：10 张表按原样复制，`create_time` / `update_time` 列注释仍写“Platform Support 数据库”，建议第二段改名时一并修正。
11. **文档未同步**：以下文档仍按 Platform Owner 描述，留待合入后由主会话统一更新：
    - `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
    - `agent-docs/CURRENT_IMPLEMENTATION.md`
    - `PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
    - 历史需求文档
    - `scripts/moved-links-map.json`
12. **表归属脚本修正**：`generate-table-ownership.mjs` 在 HEAD 时 `--check` 已经失败，因为 `t_channel_config_change` 是手工加进 YAML 的。子 agent 已把它补进 `stepAddedTables`，现在 `--check` 通过。
13. **PDManer 模型手工维护**：`platform-support.pdma.json` 在 HEAD 已与生成器漂移（3 张表与名称、说明），所以没有重新生成，而是用脚本删除 10 个实体和 integration 图，只剩原有漂移。
14. **BFF 接口签名调整**：子 agent 从 BFF 读接口和校验接口的 Port、Impl、Controller 签名中删去了未使用的 `OperatorContext`。前端路径与行为不变，授权仍由 `OperationsRequestInterceptor` 负责。
15. **未用真实环境验证**：
    - BFF 只对模拟端测试过，没有接真实 channel-connector。
    - 新增的 ArchUnit 规则没有用故意构造的违规验证过能否失败。
16. **已删除的场景**：
    - `InternalAuthDualOutboundTest`：Platform 签发端的 WORKLOAD 审计计数与 Token 缓存复用断言。这两项属于 Platform 签发端，迁到 channel-connector 后不再适用。
    - `ExecuteChannelOperationTest`：PARTIAL executeStatus 用例。进程内执行不会出现这种状态，改为“渠道 500 → ERROR”。

## 5. 实际运行的验证（均在 worktree，JDK 25，`-o`）

| 命令 | 结果 |
|---|---|
| 任务卡完成标准中的 test-compile（9 个模块，`-am`） | 通过 |
| `-pl channel-connector/paytech-channel-connector-test -am test -Dtest='ArchitectureAutoTest,ChannelConnectorArchitectureTest'` | 5/5 通过（R1–R14 零未登记违规，含 core→protocol 访问断言） |
| `... -Dtest='com.paytech.channel.connector.auto.domain.*Test'` | 48/48 通过 |
| `-pl platform-support/paytech-platform-support-test -am test -Dtest=ArchitectureAutoTest` | 1/1 通过 |
| `-pl tools/arch-rules -am test` | 62/62 通过（含 TableOwnershipAutoTest 47 / 28） |
| `-pl tools/arch-rules-reactor-test -am test -Dtest=ReactorArchitectureAutoTest` | 3/3 通过 |
| `-pl operations-console-bff/paytech-operations-console-bff-test -am test`（全模块） | 1844/1844 通过，其中 ExternalApiBrowserAutoTest 102 |
| `node --test scripts/pdmaner/channel-connector-protocol-model.test.mjs` / `payment-channel-query.test.mjs` | 6/6、1/1 通过 |

**测试场景对照**：由 Platform 迁入的 21 个类方法数一致。下列用例因鉴权模型变化而替换：

| 类 | 原用例 | 处理 |
|---|---|---|
| `ExternalApiEditorTest` | `test_requiresPermission` | 删除；读接口没有操作人，也不在 Owner 侧复核 |
| `IntegrationManagementTest` | `test_readerCannotWrite` | 替换为 `test_actorHeaderRequired` |
| `StandardApiManagementTest` | `test_permissions` | 替换为 `test_actorHeaderRequired` |
| `VerifyAccountManagementTest` | `test_deleteProtectionAndPermission` | 替换为 `test_deleteProtectionAndActorHeader` |
| `VerifyRunTest` | `test_permissionAndNotFound` | 替换为 `test_actorHeaderAndNotFound` |
| `StandardApiExecutionTest` | `test_invalidBody` | 替换为 `test_invalidRequest`；入口改为 `ExecuteStandardApiProcessor` |

另外：
- 原审计断言全部改为对 `t_channel_config_change` 的断言（目标、类型、操作者、`change_detail`）。
- 不迁移 `IntegrationLookupApiTest` 与 `IntegrationLookupInternalAuthTest`，对应接口已删除。
- `ExecuteChannelOperationTest` 走 Payment 入口 → 进程内引擎 → 渠道 Mock：
  - 读卡（card Mock）与 Vault Mock 场景；
  - 6 类 NOT_SENT：标准 API 不存在、实现不存在、配置无效、初始化失败、预算耗尽、输入无效；
  - ERROR：发出后断连或超时；
  - 原有本地 NOT_SENT 场景。

## 6. 主会话执行：隔离测试库初始化与测试运行

> 先确认本机 MySQL 13306、Redis 16379（以及 card 用到的 RabbitMQ 5672）已启动。隔离库名带 `_ccu`，不碰共享的 `mor_*` 库。Platform 测试 DDL 第 4 行是 `DROP DATABASE mor_platform;`（HEAD 原有），下面的命令已删除这一行。

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR-ccu/paytech
set -a; . /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a
MYSQL='docker exec -i -e MYSQL_PWD='"$MOR_MYSQL_ROOT_PASSWORD"' mor-local-mysql-1 mysql -uroot'

# 1) 初始化三个隔离库（建库、本库授权、全量建表；账号已存在时不改密码）
sed 's/mor_channel_connector/mor_channel_connector_ccu/g' \
  channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql | eval $MYSQL
sed -e '/^DROP DATABASE/d' -e 's/mor_platform/mor_platform_ccu/g' \
  platform-support/paytech-platform-support-test/src/test/resources/init/ddl.sql | eval $MYSQL
sed 's/mor_card/mor_card_ccu/g' card/paytech-card-test/src/test/resources/init/ddl.sql | eval $MYSQL
# 核对表数：28 / 47 / 8
for db in mor_channel_connector_ccu mor_platform_ccu mor_card_ccu; do
  echo "$db: $(echo "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db';" | eval $MYSQL -N)"; done

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
LOCALENV=-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
JDBC='?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'

# 2) channel-connector 全部测试（含迁入的 Platform 用例与 ExecuteChannelOperationTest）
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_channel_connector_ccu$JDBC" REDIS_KEY_PREFIX=channel-connector-ccu \
  ./mvnw -o -pl channel-connector/paytech-channel-connector-test -am test $LOCALENV \
  -Dtest='com.paytech.channel.connector.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 3) platform-support 全部 auto 测试（确认删除 integration 后其余能力不受影响）
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_platform_ccu$JDBC" REDIS_KEY_PREFIX=platform-support:ccu \
  ./mvnw -o -pl platform-support/paytech-platform-support-test -am test $LOCALENV \
  -Dtest='com.paytech.platform.support.auto.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 4) card：只跑读卡入口（避免 RabbitMQ / 验证码 Redis Key 与其他会话交叉）
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_card_ccu$JDBC" REDIS_KEY_PREFIX=card-ccu \
  ./mvnw -o -pl card/paytech-card-test -am test $LOCALENV \
  -Dtest=ChannelCardMaterialRevealTest -Dsurefire.failIfNoSpecifiedTests=false

# 5) 用完删除隔离库（可选）
echo 'DROP DATABASE mor_channel_connector_ccu; DROP DATABASE mor_platform_ccu; DROP DATABASE mor_card_ccu;' | eval $MYSQL
```

说明：
- 环境变量会被 Surefire fork 继承，`SPRING_DATASOURCE_URL` 覆盖 `application-mock.yml` 中的库地址。`REDIS_KEY_PREFIX` 覆盖 `redis.key-prefix`；清理组件也读同一个值，所以只清理该前缀下的 Key。
- 三处测试基线只接受精确库名，或 `<库名>_{后缀}`。
- Platform 的 `ApiCredentialMocker` 还要求 JDBC URL 以 `jdbc:mysql://127.0.0.1:13306/<当前库>?` 开头，上面的 URL 满足。
- operations-console-bff 不需要数据库，已在第 5 节运行。

## 7. 写接口示例第 11 节自查

本次迁入的 22 个外部 API 写接口，以及 core 中 14 个改造过的写入口：

| 检查项 | 结果 |
|---|---|
| API 与 Body | DTO 原样迁入，均为 class 并带字段注释和 Hibernate Validator，Wire 不变。写接口操作人由 `ChannelConfigurationWire.actor` 校验，内部 Request 由 `validateBasic` 校验 |
| Controller / Converter 仅适配 | `ext/rest/externalapi/*` 只做转换和委托。Processor 位于 protocol 的 `service/processor`，阶段顺序保持 Platform 原样 |
| Processor 查本模块 Repository，不调 Mapper | 保持原样；架构测试新增断言：core 不访问 protocol 的 Repository、Mapper、DataObject、`*Impl` |
| Context 与本模块 DataObject | 原 Context 保留，core 的 Context 改持 protocol 的领域对象 `ChannelConfigChangeRecord`，不持有 DataObject |
| Service 不互调 | 已满足：core 的 Service 去掉对变更记录的写入，改由 Processor 经 `ChannelConfigTransactions` 组合（D1）。protocol 写 Processor 在 `transactions.write` 内依次调用本模块 Service 与 `ChannelConfigChangeService` |
| 事务位置与外部调用 | 写入使用 READ_COMMITTED、15s 的本地事务，只访问本库。Vault Reveal、读卡、渠道 HTTP 都在事务外的引擎中执行。`ExecuteChannelOperationProcessor` 先在只读事务中校验，再在事务外执行。R7 通过 |
| Repository 条件 SQL、行数与对象同步 | 原样迁入（version CAS、行数检查） |
| 锁 | `external-api-config` TRY_LOCK，参数不变（500ms / 30s），使用 channel-connector 的 Redis 前缀。渠道侧锁未改动 |
| 原请求比较与防重 | 外部 API 写接口原本就没有业务防重，保持不变（版本号 CAS 与唯一索引） |
| Idempotency-Key | 不涉及，没有新增幂等组件 |
| View 与边界 | 原 View 保留；HTTP 与跨模块边界不泄露 DataObject（R9 通过） |
| 配置、Mock、多余抽象 | 配置集中在 `ExternalApiConfigurationProperties` / `ExternalApiExecutionProperties`，只改了前缀。生产代码中没有 Mock 或伪造成功。新增抽象只有 D1 的组合组件和 D5 的能力目录，理由见第 3 节 |
| 测试 | 见第 5 节：架构、领域、BFF 测试已运行；DB 入口测试待第 6 节执行 |

## 8. `git -C /Users/kenny/Desktop/Workspace/Code-MoR-ccu status --short` 摘要

共 1006 条（`--untracked-files=all` 逐文件计数）：

| 区域 | M | D | ?? |
|---|---:|---:|---:|
| channel-connector（api / protocol / core / app / test / docs / 根文件） | 61 | 18 | 426 |
| platform-support | 19 | 416 | 0 |
| operations-console-bff | 12 | 14 | 14 |
| card | 4 | 0 | 0 |
| tools/arch-rules | 4 | 0 | 0 |
| scripts/pdmaner | 11 | 1 | 3 |
| agent-docs（场景文档） | 1 | 0 | 0 |
| requirements 本需求目录 | 0 | 0 | 2（`db/platform-support-service/drop-integration.sql` 与本报告） |

- channel-connector 的 426 个新文件中，protocol 模块 262 个，api 的 `externalapi` 与枚举 72 个，test 77 个，app 12 个，core 2 个，docs 增量 SQL 1 个。
- platform-support 的 416 个删除中，integration 模块 248 个，api 78 个，test 77 个，app 13 个。
- 没有暂存或提交任何内容。

## 需库测试修复（2026-10-21）

> 只在隔离库 `mor_channel_connector_ccu`、Redis 前缀 `channel-connector-ccu` 上运行；未执行其他 SQL，未 install，未提交。修复前的全量结果是 330 个用例、40 个失败，日志为 `ccu-cc.log`。

### 根因与改动

| # | 失败 | 根因 | 改动 |
|---|---|---|---|
| 1 | 外部 API 管理用例 30 个（含 `ExternalApiConfigurationConcurrencyTest`）：期望 404，实际 400 | protocol 用 `FailException(CommonErrorCode.NOT_FOUND)` 表示对象不存在。Platform 在 4ab205e0 的 `PlatformApiErrorConfiguration` 把它映射为 404。channel-connector 原来没有 `ApiErrorStatusResolver`，Foundation 对 `FailException` 默认返回 400，所以迁移后丢了这条映射。缺少 `X-Gateway-Actor-Subject` 时返回 400（缺少请求头）、非法值返回 400 `INVALID_REQUEST`，这两项本来就通过；失败的都是用例后面的 `GET … → 404` 断言。测试期望来自 Platform 原测试，因此修生产映射，没有改测试 | 新增 `paytech-channel-connector-app/.../configuration/ChannelConnectorApiErrorConfiguration.java`，只把公共 `NOT_FOUND` 映射为 404。core 的渠道配置接口用 `BizCheckFailException` 表示不存在，不受影响，仍返回 400 |
| 2 | `VerifyCaseManagementTest.test_validationMatrix` 返回 500 | 迁移时 `ExecutionSnapshotLoader` 改为抛出类型化的 `StandardApiNotSentException`（Platform 原来抛 `FailException` / `ErrorException`）。`VerifySupport.load` 仍只捕获原来的两种异常，所以步骤里出现不存在的标准 API（`NOPE`）时，异常一直冒泡成 500。临时打开 Foundation 异常堆栈确认后，已还原 Foundation。同一个方法也被 `stale()` 使用，验证运行列表计算“配置已变更”时有同样的隐患 | `protocol/.../service/processor/VerifySupport.java`：catch 增加 `StandardApiNotSentException`，保留原来的两种异常（编解码数据损坏仍可能抛 `ErrorException`），语义回到“加载失败即为 null” |
| 3 | `ExecuteChannelOperationTest` 7 个用例：“Card 读卡失败”导致 NOT_SENT / `INTEGRATION_INITIALIZATION_FAILED`，渠道 Mock 一次都没被调用 | 读卡客户端配置没有问题：base-url、调用方 `channel-connector-service` 和请求头都已对上，同样走这条路径的 `StandardApiCardPaymentTest` 一直通过。问题出在测试数据：`ChannelRequestBuilder.operationBody` 的卡票据是 `synthetic-instrument-token`，不满足 card 读卡合同 `RevealChannelCardMaterialRequestBody.ticket` 的 `@Pattern("[A-Za-z0-9_-]{43}")`。card Mock 实现了正式合同并带 `@Valid`，所以直接返回 400，Mocker 也没有记下读卡调用。迁移前这里调用的是 Platform Mock，不校验票据格式，问题没有暴露。日志里的 `PROPERTY_MISSING@flowDefinition.*` 来自 `test_configInvalidNotSent`，它故意保存 `{"timeoutMs":0}` 来制造配置无效，属于预期日志，该用例本来就通过；Fixture 的 `operation-card-pay` 流程是完整的 | `ChannelRequestBuilder` 新增 43 位合成票据常量 `CARD_TICKET`，`operationBody` 改用它；`ExecuteChannelOperationTest.INSTRUMENT` 引用同一个常量。断言没有改 |
| 4 | `StandardApiSignedChannelTest` 和 `StandardApiUrlSignedChannelTest` 各 1 个失败：`requestTime` 是当前时间，不是 `2026-09-17T12:00:00.123Z`，验签失败 | Platform 测试环境里有 `@Primary` 固定时钟 `TestClock`（`CredentialFixture.NOW`），迁移时没有带过来，channel-connector 的 `ExpressionEngine` 拿到的是生产 `Clock.systemUTC()` | 新增 `test/mocker/TestClock.java`：`@Primary @TestComponent`，实现 `Clear`。默认跟随系统 UTC 时间，其他用例行为不变；需要时由用例 `fix(…)` 固定时点，每个用例结束后恢复。两个签名测试在类级 BeforeEach 中固定为 `TestClock.PLATFORM_FIXED_NOW`（等于原 `CredentialFixture.NOW`），断言原样不变 |

### 全量结果

- `-pl channel-connector/paytech-channel-connector-test -am clean test -Dtest='com.paytech.channel.connector.**.*Test'`：**330/330 通过**，BUILD SUCCESS。
- 第一次全量重跑时，`GetChannelExecutionConfigTest` 有 1 个参数化用例报错：Snowflake 检测到宿主机时钟回拨 20ms，超过 5ms 容忍度。这个生成器使用独立的 `Clock.systemUTC()`，与上面的 TestClock 无关，属于环境偶发。紧接着第二次全量重跑全部通过。

### 偏差

- 第 3、4 项改的是测试设施和测试数据（合成票据、测试时钟），不是生产代码。理由：失败分别来自不满足正式 Wire 的合成数据，以及漏迁的 Platform 测试时钟，断言本身没有放宽。
- 新增的 `TestClock` 对整个测试上下文生效。它默认返回系统时间，只有显式调用 `fix` 的用例才会固定，并由 Clear 统一恢复。
- 第 1 项新增的 404 映射只覆盖 `CommonErrorCode.NOT_FOUND`。core 渠道配置接口的“不存在”仍返回 400 `BUSINESS_CHECK_FAILED`，这是 channel-connector 原有合同，本次没有改。

# 任务卡 CC-B · channel-connector：渠道 / 账号 / MID / 能力管理合同、Vault Protect、Platform 接入方检查

- 需求：[requirement.md](../requirement.md)；方案：[technique.md](../technique.md)（阶段 2–3 已确认）。本卡做 technique.md 第 10 节 CC-B 行；与 CC-A 并行。CC-A worktree：`/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/agent-af39a2b74ffb12ccc`（只读，不要在里面改任何文件）。
- 执行者：Opus 5.5 agent，独立 worktree。**不执行任何 git 写命令**；不碰共享环境；不调用 Superpowers skill；不用 Mockito / MockBean / spy。
- 本机数据库：只允许对 `mor_channel_connector`（127.0.0.1:13306）执行 `t_channel_config_change` DDL（若 CC-A 已执行则跳过，用 `SHOW TABLES` 核对 18 张表）；root 密码变量 `MOR_MYSQL_ROOT_PASSWORD` 在 `local-environment/.env.local`，用 `docker exec -e MYSQL_PWD=... mor-local-mysql-1 mysql -uroot` 形式执行，不打印密码。

## 0. 与 CC-A 的分工（并行规则）

- CC-A 拥有：`t_channel_config_change` 的 DDL / PDManer 生成输入 / `ChannelConfigChange` DataObject / `ChangeTargetType`、`ChangeType` 枚举 / `ChannelConfigChangeMapper(+XML)` / `ChannelConfigChangeRepository(+Impl)` / `ChannelConfigChangeApi` 读接口；成本方案的列表、作废、渠道存在校验；`ChannelMidApi.listChannelMids` 与 `ChannelMidSummaryResponseBody`。
- CC-B 开工时先从 CC-A worktree **逐字复制**上述 2.1 文件（DataObject、枚举、Mapper 接口与 XML、Repository 接口与 Impl、测试 `init/ddl.sql` 与 `channel-connector-schema.sql` 中的新表段）到自己的 worktree，不修改它们；若 CC-A 尚未写完，先按 technique.md 第 4、6 节的名称自行创建相同形态，并在报告中标明"待与 CC-A 对齐"。
- CC-A 已冻结的 2.1 签名（CC-B 开工时若 CC-A worktree 里这些文件已存在就逐字复制；不存在则按以下签名自行创建，形态须完全一致）：
  - `core/enums/ChangeTargetType`（CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION）与 `ChangeType`（CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL），实现 `CodeEnum`，`getCode()`。
  - `core/dal/dataobject/ChannelConfigChange`：`Long id`、`ChangeTargetType targetType`、`Long targetId`、`ChangeType changeType`、`String fromStatus`、`String toStatus`、`Long operatorId`、`String reason`、`Instant createTime`，getter/setter。
  - `core/dal/mapper/ChannelConfigChangeMapper`：`int insert(ChannelConfigChange record)`、`List<ChannelConfigChange> selectByTarget(targetType, targetId, offset, limit)`、`long countByTarget(targetType, targetId)`；XML `resources/mapper/channel/ChannelConfigChangeMapper.xml`。
  - `domainservice/repository/ChannelConfigChangeRepository` + `impl/ChannelConfigChangeRepositoryImpl`：`void insert(ChannelConfigChange change)`（行数必须为 1）、`PagingResponseBody<ChannelConfigChange> findPage(ChangeTargetType targetType, long targetId, PagingRequestBody paging)`。
  - `id` 与 `createTime` 由调用方在 insert 前填好，Repository 不生成。
- 共享文件只做**追加**，不改动既有行：`ChannelConnectorLocks`、`ChannelMidApi`（CC-B 追加写方法；若 CC-A 尚未创建该接口则 CC-B 创建，列表方法留给 CC-A）、`ChannelMidRepository(+Impl)`、`ChannelAccountRepository(+Impl)`、`PaymentChannelRepository(+Impl)`、`MidCapabilityRepository(+Impl)`、`MidCapabilityDetailRepository(+Impl)`、各 Mapper 与 XML、`ChannelDataMocker`、`ChannelTestMapper`、`ChannelConnectorMockTestBase`、`ChannelConnectorArchitectureTest`、`ChannelConnectorOutboundConfiguration`、`application.yml`、`application-mock.yml`、`README.md`、两个 `pom.xml`。
- 不碰 `ChannelCostPlan*`、`ChannelCostVersion*`、`ChannelCostResolution*` 任何文件。
- 合并由主会话完成：先合 CC-A，再把 CC-B 的差异叠加；因此追加段落请集中、自成一段，便于对齐。

## 1. 先完整读取

- 与 CC-A 相同的规范清单（`AGENTS.md`「接口开发必读与自查」、`DEVELOPMENT_CONVENTIONS.md` 全文、`JAVA_PROJECT_STRUCTURE.md` 第 4–7 节、`WRITE_API_EXAMPLE.md` 全文、`UNIT_TEST_GUIDE.md`、`COMPENSATION_EVENT_GUIDE.md` 不需要）。
- 09-21 需求第 2、3 节（五表业务关系与字段）：`requirements/20260921/payment-渠道账号与MID能力查询/requirement.md`；`channel-account-config.schema.json` 同目录。
- 10-13 材料交接：`requirements/20260914/platform-support-外部API配置与执行/material-handoff.md`。
- channel-connector 既有源码：`ChannelAccountConfigReader`（校验规则与 JSON 形态，Protect 时构造与之完全对称的对象）、`PlatformStandardApiClient` 与 `ChannelConnectorOutboundConfiguration`（照此为 `IntegrationManagementApi` 建第二个客户端 Bean，登记 `ExternalApiManagementErrorCode` 或该 api 模块实际的错误码枚举）、`QueryMidCandidatesProcessor` 与 `MidCapabilityContext`（能力解析规则，写入时的类型校验要与之一致）、`ChannelProperties` / `CapabilityDefinition`、五表 DataObject / Mapper / Repository、`CreateChannelCostPlanProcessor`（写入口形态）、`ChannelCostPlanServiceImpl`（TransactionTemplate 形态）、测试基础设施全部（`ChannelConnectorMockTestBase`、`ChannelDataMocker`、`ChannelTestMapper`、`VaultMocker`、`MockVaultController`、`PlatformMocker`、`MockPlatformController`、`ChannelRequestBuilder`、`ChannelCostResponseChecker`）。
- Vault 客户端：`paytech/vault/paytech-vault-client` 的 `VaultClient.protect(ProtectCommand)`、`ProtectCommand`、`ProtectResult`、`TokenMode`；JSON 规范化：`paytech-json-canonicalization`（core 已依赖，成本内容摘要 `ChannelCostContentHash` 是用法参照）。
- Platform api：`paytech-platform-support-api` 的 `IntegrationManagementApi.integration(integrationCode)` 与其 404 Problem 形态（找该 api 的错误码枚举，确认"接入方不存在"的 code）。

## 2. 范围（technique.md 第 3.2、5、6 节逐条实现）

### 2.1 合同（`paytech-channel-connector-api`）

- `ChannelConnectorHeaders.ACTOR_HEADER = "X-Gateway-Actor-Subject"`；所有命令方法第一个参数 `@RequestHeader(ACTOR_HEADER) String actorId`（参照 commercial 的 `AgreementApi` 写法）；查询不带。
- `PaymentChannelApi`（`/channel-connector/v1/channels`）：`createChannel`、`listChannels`、`getChannel`、`updateChannel`、`changeChannelStatus`。
- `ChannelAccountApi`（`/channel-connector/v1/channel-accounts`）：`createChannelAccount`、`listChannelAccounts`、`getChannelAccount`、`updateChannelAccount`、`configureChannelAccount`、`changeChannelAccountStatus`。
- `ChannelMidApi`（`/channel-connector/v1/mids`）：`createChannelMid`、`getChannelMid`（含能力组与明细）、`rebindChannelMidAccount`、`changeChannelMidStatus`（`listChannelMids` 归 CC-A）。
- `MidCapabilityApi`（`/channel-connector/v1/mid-capabilities`）：`listCapabilityDefinitions`、`createMidCapability`、`replaceMidCapabilityDetails`、`changeMidCapabilityStatus`。
- DTO 字段、校验注解与返回码按 technique.md 第 3.2 节表格；`ChangeChannelStatusRequestBody{targetStatus, reason?}` 三类共用；`ChangeMidCapabilityStatusRequestBody{targetStatus}`。分页复用 `PagingRequestBody` / `PagingResponseBody`。账号详情只返回 `configured` 与 `vaultTokenSuffix`（末 4 位），绝不返回 Token 全文、参数或凭据。

### 2.2 状态机与规则（Context 纯方法 + Processor 校验 + 条件 SQL）

- 三类共用 `ChannelStatus`：`DRAFT→ACTIVE`、`ACTIVE⇄SUSPENDED`、`DRAFT|ACTIVE|SUSPENDED→CLOSED`；CLOSED 终态；非法迁移 400（detail 含对象、当前状态、目标状态）。
- 渠道激活：事务外 `PlatformIntegrationClient.exists(channelCode)`；不存在 → 400「Platform 不存在同编码接入方: {code}」；Platform 不可用 → ErrorException（不写库）。渠道关闭：其下存在非 CLOSED 账号或 MID → 400。
- 账号创建：渠道存在且非 CLOSED；`entityNo` 格式 `^[A-Z]{2}[0-9]{3}$`（偏差 1，不查 client-master）。账号激活：渠道 ACTIVE 且 Token 非空。账号关闭：无非 CLOSED 的 MID 绑定本账号。OE 不可改。
- MID 创建：账号属于该渠道、非 CLOSED、OE 一致；`(channel_id, external_mid_code)` 唯一（唯一索引冲突 → 409）。改绑：目标账号同渠道同 OE 非 CLOSED；MID 非 CLOSED。激活：渠道与当前账号均 ACTIVE；首次激活写 `activated_at`；关闭写 `closed_at`。
- 能力组：MID 非 CLOSED；六维组合唯一（唯一索引冲突 → 409）；`schemeCode/countryCode/currency` 允许 `*`，`paymentMethodCode` 与 `mccCode` 不允许；明细每条恰有一个值、类型与 `ChannelProperties.capabilityDefinitions` 一致、CODE 值在 `allowedCodes` 内、未注册代码 400；整组替换明细 = 同一事务内删旧插新。
- 每个写操作与业务变更同事务插入一行 `t_channel_config_change`（target/change/from/to/operator/reason）。

### 2.3 账号配置写入（Vault Protect，technique.md 第 5 节）

- `ConfigureChannelAccountProcessor extends AbstractLockProcessor`，锁 `channel-account:{id}`（LOCK）；流程：读账号与渠道 → 非 CLOSED → 用 `ChannelAccountConfigPayload` 构造 `{schemaVersion:1, channelCode, channelAccountId(字符串), entityNo, parameters, credentials}` 并经 `paytech-json-canonicalization` 得规范字节 → 幂等键 `channel-account-config:{channelAccountId}:{SHA-256 hex}` → 事务外 `vaultClient.protect(new ProtectCommand("CHANNEL_ACCOUNT_CONFIG_V1", TokenMode.REFERENCE, json, "CHANNEL_CONFIGURATION", key))` → `ChannelAccountService.updateVaultToken(context)` 短事务：`UPDATE ... SET account_vault_token=? WHERE channel_account_id=? AND account_status<>'CLOSED'` 行数 1 + 变更记录（CONFIGURATION）。
- 参数 / 凭据校验与 `ChannelAccountConfigReader.validateEntries` 同规则（键 `^[A-Za-z][A-Za-z0-9_.-]{0,63}$`、值 1–8192、各 ≤64 项），在 RequestBody 注解 + Processor 中实现；凭据值不得进入日志、异常信息、变更记录。
- Vault 拒绝（FailException）→ 原样抛出；Vault 不可用 → ErrorException；Protect 成功但写库失败 → 记录日志（只含账号 ID）并抛出，不重试。

### 2.4 锁（`ChannelConnectorLocks` 追加）

| 入口 | 锁 |
|---|---|
| 渠道更新 / 状态 | `channel:{channelId}` |
| 账号创建、MID 创建 | `channel:{channelId}` |
| 账号更新 / 配置 / 状态 | `channel-account:{id}`；账号关闭另加 `channel:{channelId}`（`LockCommand.tryLocks`） |
| MID 改绑 | `channel-mid:{midId}` + `channel-account:{目标账号}`（tryLocks） |
| MID 状态 | `channel-mid:{midId}` |
| 能力创建 / 替换 / 启停 | `channel-mid:{midId}` |

### 2.5 分层落点

`core/dal/mapper` + XML 五表 `insert` / 条件 `update*` / 分页 `select`；`domainservice/repository` 补写方法；`domainservice/service` 新增 `PaymentChannelService`、`ChannelAccountService`、`ChannelMidService`、`MidCapabilityService`（interface + Impl，各自一个 `TransactionTemplate` 内保存 + 写变更记录）；`domain` 新增 `ChannelContext`、`ChannelAccountContext`、`ChannelMidContext`、`MidCapabilityContext` 的写侧上下文（已有同名只读 Context 时改名避免冲突，例如 `MidCapabilityWriteContext`）与 `ChannelAccountConfigPayload`；`integration/PlatformIntegrationClient`；`service/processor` 13 个写 Processor + Request；`ext/rest` 4 个 Controller + Converter；读接口简单委托 Service。

## 3. 不做

CC-A 范围；BFF / Web / Platform 注册；修改 `QueryMidCandidatesProcessor`、`ExecuteChannelOperationProcessor`、`GetChannelExecutionConfigProcessor` 的行为（如需复用其校验，提取纯方法时保证既有测试不变）；物理删除；Token 回收。

## 4. 测试（auto，经真实 HTTP 入口）

- 测试基础设施：`VaultMocker` 增加 `expectProtect(dataType, purpose, token)`，`MockVaultController.protect` 改为按配置返回 `ProtectResponse` 并记录请求（断言 Body 的 dataType / tokenMode / purpose / idempotencyKey，不断言明文值内容，但可核对 canonical JSON 的 SHA）；`PlatformMocker` 增加 `integrationExists(code)` / `integrationMissing(code)`，`MockPlatformController` 实现 `IntegrationManagementApi.integration` 返回 DTO 或 404 Problem（code 取 Platform 真实枚举）。未配置的 Protect / 接入方查询仍记为意外调用。
- 用例：`auto/channel/PaymentChannelApiTest`（创建 / 重复编码 409 / 列表筛选分页 / 改名 / 激活成功 / 激活因接入方不存在被拒 / 激活因 Platform 不可用失败且不写库 / 暂停恢复 / 关闭被子项阻止 / CLOSED 后拒绝一切写）、`ChannelAccountApiTest`（创建 / OE 格式 422 / 配置写入成功并核对 Token 落库与幂等键、变更记录 / 同内容重复配置复用幂等键 / Vault 拒绝不写库 / 未配置不能激活 / 关闭被绑定 MID 阻止 / 详情不含凭据）、`ChannelMidApiTest`（创建 / 唯一冲突 409 / 跨 OE、跨渠道改绑拒绝 / 激活前置 / activated_at、closed_at）、`MidCapabilityApiTest`（目录接口 / 创建 / 六维重复 409 / 类型不一致 400 / 未注册代码 400 / 整组替换 / 停用后 `queryMidCandidates` 不再返回该组）、变更记录经 CC-A 的查询接口核对（若该接口尚未合入，用 `ChannelTestMapper` 直查表）。
- 每个测试类仍走全局 BeforeEach 空基线与 AfterEach 意外调用检查 + 清理；`ChannelTestMapper` 覆盖新表；并发用例至少一例（同账号配置写入两线程，Redis 锁串行，最终一行、两条变更记录或按幂等键只一条 Protect）。
- 命令同 CC-A（JDK 25，`./mvnw -q -pl channel-connector/paytech-channel-connector-test -am test`，0 失败）。

## 5. 交付

报告 `tasks/CC-B-report.md`：已读清单；每个写入口的 Processor / Context / 本地原子写入 / 事务外步骤；改动文件表；接口 / 表 / 事件对照；第 11 节核对；测试命令与结果；DDL 执行记录；与 CC-A 共享文件的追加段落清单（文件 + 段落位置），便于合并；遗留与偏差。不要提交。

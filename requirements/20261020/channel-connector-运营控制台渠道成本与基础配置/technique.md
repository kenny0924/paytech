# 技术方案：channel-connector 运营控制台（渠道成本方案 + 渠道基础配置）

> 状态：阶段 2–3 评审稿（2026-10-20）。对应 [requirement.md](./requirement.md)（阶段 1 已确认，Q1–Q6 按建议）。第 1 节为现状差异（阶段 2），第 2 节起为方案（阶段 3）。与阶段 1 的偏差集中在第 9 节，需用户确认。

已读规范：`AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 2、3、4、5 节、`agent-docs/JAVA_PROJECT_STRUCTURE.md` 目录、`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节、`agent-docs/UNIT_TEST_GUIDE.md`（测试边界按 `AGENTS.md`「Automated test boundaries」）、09-21 渠道需求第 2–3 节、10-13 账号材料交接、B 线报告。

## 1. 现状差异与影响（阶段 2）

| 系统 | 现状 | 本需求要补的 | 影响 |
|---|---|---|---|
| channel-connector-api | 4 个合同：候选查询、执行、成本方案 Maker-Checker（无列表、无作废）、成本命中 | 成本列表与作废；渠道 / 账号 / MID / 能力 / 变更记录 5 组管理合同；能力目录读接口 | 新增合同，不改既有合同签名 |
| channel-connector-core | 五表只有 `findById*` 只读 Repository，mapper 无 insert/update；成本写入口用 `AbstractLockProcessor` + `TransactionTemplate`；Vault 只 Reveal；Platform 只调标准 API 执行 | 五表写 Mapper/Repository；管理 Processor；Vault Protect；Platform 接入方存在性查询；变更记录表 | `QueryMidCandidatesProcessor`、`ExecuteChannelOperationProcessor` 不改 |
| mor_channel_connector | 17 张表；`t_channel_cost_plan_ver` 无备注列；无操作留痕表 | 新增 `t_channel_config_change` 1 张表（第 4 节） | PDManer、schema SQL、增量 SQL、测试 ddl 同步 |
| Vault 策略 | `channel-connector-service` 只有 REVEAL `CHANNEL_ACCOUNT_CONFIG_V1 / CHANNEL_EXECUTION`（本机 policy.local.json、mor-test 21-vault-upgrade） | 新增 PROTECT 规则，用途 `CHANNEL_CONFIGURATION`（第 5 节） | 本机 JSON 与 mor-test DML 各一条 |
| Platform | 接入方管理 API 只有运营控制台合同（需人工会话与 `EXTERNAL_API_READ`），没有内部免会话查询；注册块 95 为 main 最新；91（科目分级，已进 main）与 93（商务 I2，`commercial/second-slice`）已用，92、94 空闲（2026-10-20 与 ledger 会话对齐：本需求用 92，财务后续预定 100–102） | 新增内部合同 `IntegrationLookupApi`（`GET /standard-apis/v1/integrations/{code}/existence`，鉴权同 `StandardApiCommandApi`）+ Controller + 只读 Service + 测试；注册块 92 | Platform 代码改动 1 个接口（用户 2026-10-20 决定，见第 9 节第 6 条）；mor-test 需发布 Platform 镜像 |
| Operations BFF | 不依赖 channel-connector-api；商务线形态：`*ClientConfiguration` + 本地端口 `integration/*Client` + 转发 Controller + 聚合 Processor；F9 detail 透传在 I1 worktree（未进 main） | `ChannelConnectorClientConfiguration`、5 个 Client、`/channel-admin/v1` Controller、1 个聚合 Processor（版本对比）、请求 JSON 严格配置、错误码登记、边界清单测试 | 与 I1 / chart2 在共享文件上冲突（第 10 节） |
| Operations Web | 页面注册在 `pageCapabilities.ts` + `ConsolePage.tsx`，服务端镜像 `console-resource-capabilities.json`；写命令用 `useXxxCommand` 模式；无 Client/OE 选择器组件 | `src/channel/**` 模型与组件、3 个页面、注册 3 项 | `configurationModel.test.ts` 页面计数同步 |
| client-master | api 模块没有 OE 查询；commercial 创建合同时也只做格式与覆盖校验，不向 client-master 核对 | 不改（第 9 节偏差 1） | B5 退为格式校验 |
| 本机 e2e / mor-test | 渠道种子靠 `50-channel-connector-payermax-mock.sql` 与 `channel_setup.py` 直写库；mor-test 成本验收用 `test-channel-seed.sql` | 阶段 5 改为经控制台接口配置；种子脚本退役 | 验收脚本重写 |

## 2. 方案总览

```text
Operations Web ──/api/operations/channel-admin/v1/**──▶ Gateway ──▶ Operations BFF
                                                                      │ 转发 / 聚合（版本对比）
                                                                      ▼
                                                     channel-connector-service /channel-connector/v1/**
                                                       │ 事务外：Platform 接入方查询（渠道激活）、Vault Protect（账号配置）
                                                       │ 事务内：五表 + 成本表 + t_channel_config_change
```

- 全部写入口为 `service/processor` 下的 `AbstractLockProcessor`，锁实际资源（第 6 节）；本地保存在 Service 的一个 `TransactionTemplate` 内；外部调用（Platform、Vault）在事务前完成，结果作为 Context 输入。
- 操作者：命令由 BFF 以会话 Account ID 填 `X-Gateway-Actor-Subject`（与 commercial 相同）；成本接口沿用既有请求体 `makerId` / `checkerId`，由 BFF 从会话填入，不改合同。
- 变更留痕：每次创建、字段更新、状态变更、配置写入、改绑、作废，与业务变更同事务写一行 `t_channel_config_change`。
- 不做：删除、路由策略、健康度、回调查询；Payment / billing / Platform 代码不改。

## 3. channel-connector-api 合同

公共约定：服务名 `channel-connector-service`，无 context path；ID 与金额为十进制字符串；分页复用 `PagingRequestBody` / `PagingResponseBody<T>`；DTO 为普通 class，命名 `*RequestBody` / `*ResponseBody`；命令请求头常量 `ChannelConnectorHeaders.ACTOR_HEADER = "X-Gateway-Actor-Subject"`；业务拒绝 400 `BUSINESS_CHECK_FAILED`（detail 为中文原因），字段校验 422，并发 409。

### 3.1 A 线：渠道成本

| 合同 | 方法与路径 | 说明 |
|---|---|---|
| `ChannelCostPlanApi.listChannelCostPlans` | `GET /channel-connector/v1/channel-cost-plans?channelId&status&page&size` | 返回 `PagingResponseBody<ChannelCostPlanSummaryResponseBody{planId, planCode, channelId, channelCode, channelName, status, currentCostVer, currentEffectiveFrom, pendingApprovalCount, updateTime}>`；按 `update_time DESC, id DESC` 稳定排序；渠道名经 View 从 `t_payment_channel` 同库联查 |
| `ChannelCostPlanApi.cancelChannelCostVersion` | `POST /channel-connector/v1/channel-cost-plans/{planCode}/versions/{costVer}/cancel`，Body `CancelChannelCostVersionRequestBody{operatorId, reason(1–512)}` | 仅 APPROVED 且 `effective_from` 晚于当前时间可作废 → `INACTIVE`；ACTIVE / 其他状态 400；作废后到期 Job 的 `selectDueApproved` 自然不再命中 |
| `createChannelCostPlan` | 既有 | 新增校验：`channelId` 必须存在于 `t_payment_channel`，否则 400「渠道不存在」 |
| `ChannelCostPlanApi.getChannelCostVersion` | 既有 | 响应不变；对比在 BFF 计算 |

### 3.2 B 线：渠道基础配置（新增 4 个合同 + 变更记录）

状态流转（渠道、账号、MID 共用 `ChannelStatus`）：`DRAFT → ACTIVE`、`ACTIVE ⇄ SUSPENDED`、`ACTIVE | SUSPENDED | DRAFT → CLOSED`；CLOSED 终态。统一请求体 `ChangeChannelStatusRequestBody{targetStatus, reason?}`。

**`PaymentChannelApi`，前缀 `/channel-connector/v1/channels`**

| 方法 | 路径 | 请求 / 响应 | 规则 |
|---|---|---|---|
| `createChannel` | `POST` | `CreateChannelRequestBody{channelCode ^[A-Z][A-Z0-9_]{1,63}$, channelName 1–255}` → 201 `ChannelResponseBody` | `channel_code` 唯一（唯一索引为最终防线）；初始 DRAFT |
| `listChannels` | `GET ?status&keyword&page&size` | `PagingResponseBody<ChannelSummaryResponseBody{channelId, channelCode, channelName, status, accountCount, midCount, updateTime}>` | keyword 匹配 code / name 前缀 |
| `getChannel` | `GET /{channelId}` | `ChannelResponseBody{…, platformIntegrationExists?}` | 详情不调 Platform；接入方存在性只在激活时检查 |
| `updateChannel` | `PUT /{channelId}` | `UpdateChannelRequestBody{channelName}` | CLOSED 拒绝 |
| `changeChannelStatus` | `POST /{channelId}/status` | `ChangeChannelStatusRequestBody` | 激活：事务外查 Platform `integration(channelCode)`，404 → 400「Platform 不存在同编码接入方」；关闭：渠道下不得有非 CLOSED 的账号或 MID |

**`ChannelAccountApi`，前缀 `/channel-connector/v1/channel-accounts`**

| 方法 | 路径 | 请求 / 响应 | 规则 |
|---|---|---|---|
| `createChannelAccount` | `POST` | `CreateChannelAccountRequestBody{channelId, entityNo ^[A-Z]{2}[0-9]{3}$, accountName 1–128, remark ≤512}` → 201 | 渠道存在且非 CLOSED；初始 DRAFT，无 Token |
| `listChannelAccounts` | `GET ?channelId&entityNo&status&page&size` | `PagingResponseBody<ChannelAccountSummaryResponseBody>` | |
| `getChannelAccount` | `GET /{channelAccountId}` | `ChannelAccountResponseBody{…, configured: boolean, vaultTokenSuffix: 末 4 位或 null, boundMidCount}` | 不返回 Token 全文、参数或凭据 |
| `updateChannelAccount` | `PUT /{channelAccountId}` | `UpdateChannelAccountRequestBody{accountName, remark}` | CLOSED 拒绝；OE 不可改 |
| `configureChannelAccount` | `PUT /{channelAccountId}/configuration` | `ConfigureChannelAccountRequestBody{parameters: Map<String,String>, credentials: Map<String,String>}`（键 `^[A-Za-z][A-Za-z0-9_.-]{0,63}$`，值 1–8192，各 ≤64 项，与 `ChannelAccountConfigReader.validateEntries` 同规则）→ 200 `ChannelAccountResponseBody` | CLOSED 拒绝；事务外 Vault Protect（第 5 节）后短事务条件更新 Token；旧 Token 保留在 Vault 不回收（无 Retire 合同，登记遗留） |
| `changeChannelAccountStatus` | `POST /{channelAccountId}/status` | `ChangeChannelStatusRequestBody` | 激活：渠道 ACTIVE 且 Token 非空；关闭：无非 CLOSED 的 MID 绑定本账号 |

**`ChannelMidApi`，前缀 `/channel-connector/v1/mids`**

| 方法 | 路径 | 请求 / 响应 | 规则 |
|---|---|---|---|
| `createChannelMid` | `POST` | `CreateChannelMidRequestBody{channelId, channelAccountId, externalMidCode 1–128, entityNo}` → 201 | 账号属于该渠道、非 CLOSED、OE 与请求一致；`(channel_id, external_mid_code)` 唯一；初始 DRAFT |
| `listChannelMids` | `GET ?channelId&channelAccountId&entityNo&status&page&size` | `PagingResponseBody<ChannelMidSummaryResponseBody>` | 成本版本编制的 MID 下拉用 `channelId + status=ACTIVE` |
| `getChannelMid` | `GET /{midId}` | `ChannelMidResponseBody{…, capabilities: [MidCapabilityResponseBody{…, details[]}]}` | 能力组随 MID 详情返回，不另设聚合 |
| `rebindChannelMidAccount` | `PUT /{midId}/account` | `RebindChannelMidAccountRequestBody{channelAccountId}` | 目标账号同渠道同 OE、非 CLOSED；CLOSED MID 拒绝；已冻结的 Attempt 不受影响（Payment 保存三元组） |
| `changeChannelMidStatus` | `POST /{midId}/status` | `ChangeChannelStatusRequestBody` | 激活：渠道与当前账号均 ACTIVE；首次激活写 `activated_at`；关闭写 `closed_at` |

**`MidCapabilityApi`，前缀 `/channel-connector/v1/mid-capabilities`**

| 方法 | 路径 | 请求 / 响应 | 规则 |
|---|---|---|---|
| `listCapabilityDefinitions` | `GET /definitions` | `CapabilityDefinitionsResponseBody{definitions: [{capabilityCode, valueType, allowedCodes[]}]}` | 来自 `ChannelProperties.capabilityDefinitions` |
| `createMidCapability` | `POST` | `CreateMidCapabilityRequestBody{midId, paymentMethodCode, schemeCode(目录值或 *), countryCode(^[A-Z]{2}$ 或 *), currency(^[A-Z]{3}$ 或 *), mccCode ^[0-9]{4}$, details: [MidCapabilityDetailRequestBody{capabilityCode, booleanValue?, decimalValue?, codeValue?}]}` → 201 | MID 非 CLOSED；六维组合在 MID 内唯一；每条明细恰有一个值且与目录类型一致，CODE 值在 allowedCodes 内；未注册代码 400；初始 ENABLED |
| `replaceMidCapabilityDetails` | `PUT /{midCapabilityId}/details` | `ReplaceMidCapabilityDetailsRequestBody{details[]}` | 整组替换：删旧插新，同一事务 |
| `changeMidCapabilityStatus` | `POST /{midCapabilityId}/status` | `ChangeMidCapabilityStatusRequestBody{targetStatus ENABLED/DISABLED}` | |

**`ChannelConfigChangeApi`，`GET /channel-connector/v1/config-changes?targetType&targetId&page&size`** → `PagingResponseBody<ChannelConfigChangeResponseBody{changeId, targetType, targetId, changeType, fromStatus, toStatus, operatorId, reason, createTime}>`，按 `id DESC`。

## 4. 数据库变更（mor_channel_connector）

新增 1 张表，需用户授权后才落 PDManer / SQL：

```sql
CREATE TABLE `t_channel_config_change` (
  `id`           BIGINT       NOT NULL COMMENT '服务侧生成的变更记录 Snowflake 主键。',
  `target_type`  VARCHAR(64)  NOT NULL COMMENT '被变更对象类型。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION。',
  `target_id`    BIGINT       NOT NULL COMMENT '被变更对象在本库的主键。',
  `change_type`  VARCHAR(64)  NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL。',
  `from_status`  VARCHAR(64)  NULL     COMMENT '状态变更前的状态；非状态变更为空。',
  `to_status`    VARCHAR(64)  NULL     COMMENT '状态变更后的状态；非状态变更为空。',
  `operator_id`  BIGINT       NOT NULL COMMENT '执行变更的 Platform Actor 稳定业务 ID。',
  `reason`       VARCHAR(512) NULL     COMMENT '操作者填写的非敏感原因；禁止填写凭据。',
  `create_time`  DATETIME(6)  NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  KEY `idx_channel_config_change_target` (`target_type`, `target_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道配置与成本版本的人工变更留痕；与业务变更同事务写入，只增不改。';
```

- 五表与成本表结构不变；`t_channel_cost_plan_ver` 的作废原因写在本表（`target_type=CHANNEL_COST_VERSION, change_type=CANCEL`）。
- 文件：`docs/database/channel-connector.pdma.json`（18 表）、`docs/database/sql/channel-connector-schema.sql` 追加、`docs/database/sql/changes/2026-10-20-channel-config-change.sql`、测试 `init/ddl.sql`、本需求 `db/channel-connector/ddl.sql`（执行文件）。不建 FK / CHECK。

## 5. Vault：账号配置 Protect

| 项 | 值 |
|---|---|
| Caller / Action / Data Type / Purpose | `channel-connector-service` / `PROTECT` / `CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_CONFIGURATION`（新用途码，与运营手动操作理由码同名但属 Vault 访问规则） |
| 原值 | `{"schemaVersion":1,"channelCode","channelAccountId","entityNo","parameters":{},"credentials":{}}`，由 Processor 用账号 DataObject 构造，经 `paytech-json-canonicalization` 生成规范字节 |
| 幂等键 | `channel-account-config.{channelAccountId}.{SHA-256(规范 JSON) 的 hex}`（107 字符；Vault Client 只接受 `[A-Za-z0-9._~-]`，研发期把冒号改为点）：同内容重放复用，不同内容产生新 Token |
| 调用位置 | `ConfigureChannelAccountProcessor.processWithinLock`：锁 `channel-account:{id}` → 读账号并校验非 CLOSED → 事务外 `vaultClient.protect(...)` → `ChannelAccountService.updateVaultToken(context)` 短事务条件更新（`WHERE channel_account_id=? AND account_status<>'CLOSED'`，行数 1）并写变更记录 |
| 失败 | Vault 拒绝 / 不可用：不写库，向上抛 Fail / Error；Protect 成功但写库失败：Token 成为孤儿，登记为已知遗留（与现有无 Retire 合同一致） |
| 策略文件 | 本机 `local-environment/vault/policy.local.json` 加一条 rule；mor-test `db/vault/dml.sql` 插入 `t_vault_access_rule` 一行并递增策略版本（格式照 `21-vault-upgrade.sql`） |

Platform 以 `CHANNEL_EXECUTION` 的 Reveal 规则不在本需求范围；台账第 404 节标记其 mor-test 未部署，阶段 6 一并核对。

## 6. channel-connector 实现要点

包与类（`com.paytech.channel.connector`）：

| 层 | 新增 |
|---|---|
| `core/dal/mapper` + XML | 五表增加 `insert`、条件 `update*`；`MidCapabilityDetailMapper.deleteByCapabilityId`；`ChannelConfigChangeMapper`；成本 `selectPage` / `countPage`、`cancelApproved` |
| `core/dal/dataobject` | `ChannelConfigChange` |
| `core/enums` | `ChangeTargetType`、`ChangeType` |
| `domainservice/repository(+impl)` | 五表补写方法；`ChannelConfigChangeRepository`；`ChannelCostPlanRepository.findPage` |
| `domainservice/service(+impl)` | `PaymentChannelService`、`ChannelAccountService`、`ChannelMidService`、`MidCapabilityService`（各自一个 `TransactionTemplate` 内保存 + 写变更记录）；`ChannelCostPlanService` 增 `cancelVersion`、`findPage` |
| `domain` | `ChannelContext`、`ChannelAccountContext`、`ChannelMidContext`、`MidCapabilityContext`（持本模块 DataObject，含 `canActivate()`、`canClose(children)` 等纯判断）；`ChannelAccountConfigPayload`（规范 JSON 与幂等键） |
| `integration` | `PlatformIntegrationClient`（调用 Platform 新增的内部免会话合同 `IntegrationLookupApi.integrationExistence(code)`，`exists=false` → 不存在；任何失败 → ErrorException，不写库）；复用 `ChannelConnectorOutboundConfiguration.client(...)` 建第二个 Bean |
| `service/processor` | A：`ListChannelCostPlansProcessor`（只读、无锁）、`CancelChannelCostVersionProcessor`；B：`CreateChannelProcessor`、`UpdateChannelProcessor`、`ChangeChannelStatusProcessor`、账号 4 个、MID 3 个、能力 3 个；读接口简单委托 Service，不建 Processor |
| `ext/rest` | 5 个 Controller + Converter |

锁（`ChannelConnectorLocks`）。**2026-10-20 研发期核实：** Foundation `LockCommand.lock(key)` 实际使用 `lock:` 前缀 Key 的 SpinLock，`tryLocks(keys)` 使用原 Key 的 RLock，两者互不互斥。账号关闭与 MID 改绑必须多资源锁，因此渠道 / 账号 / MID / 能力的**所有**写入口统一使用 `LockCommand.tryLocks`（等待 10 秒、租约 60 秒；租约到期由条件 SQL 兜底），不再混用 LOCK；成本方案入口仍为既有单资源 LOCK（无多锁需求，Key 域独立）。

| 入口 | 锁 |
|---|---|
| 渠道更新 / 状态 | `channel:{channelId}` |
| 账号创建、MID 创建 | `channel:{channelId}`（与渠道关闭互斥，保证"关闭时无子项"的检查） |
| 账号更新 / 配置 / 状态 | `channel-account:{id}`；账号关闭另加 `channel:{channelId}`（tryLocks） |
| MID 改绑 | `channel-mid:{midId}` + `channel-account:{目标账号}`（tryLocks，与账号关闭互斥） |
| MID 状态 | `channel-mid:{midId}` |
| 能力创建 / 替换 / 启停 | `channel-mid:{midId}`（唯一组合在 MID 内） |
| 成本作废 | `channel-cost-plan:{planCode}`（既有） |

数据库保护：`channel_code`、`(channel_id, external_mid_code)`、六维组合三个唯一索引为最终防线；状态更新用 `WHERE status=?` 条件 SQL 并校验行数；成本作废 `WHERE status='APPROVED' AND effective_from>?`。

## 7. Operations BFF

- `configuration/ChannelConnectorClientConfiguration`：照 `CommercialClientConfiguration` 形态，服务名 `channel-connector-service`，`operations.channel-connector-test-base-url` 测试地址；6 个 `@Bean`（5 个管理合同 + 既有 `ChannelCostPlanApi`）；登记 `ChannelCostErrorCode.values()`。
- `configuration/ChannelRequestConfiguration`：`/channel-admin` 前缀严格 JSON（照 `CommercialRequestConfiguration`）。
- `integration/ChannelConfigurationClient`、`ChannelCostClient`（接口 + Impl）：命令把 `operator.accountId()` 填入 `X-Gateway-Actor-Subject`；成本命令把它填入 `makerId` / `checkerId` / `operatorId` 请求体。
- `ext/rest/ChannelConfigurationController`、`ChannelCostController`，前缀 `/channel-admin/v1`，路径与第 3 节一一对应，另加版本对比聚合路由（共 31 条，见第 8 节）。
- `service/processor/ChannelCostVersionComparisonProcessor`（`AbstractProcessor`）：读两个版本，按 `(scopeNo 六维)` 对齐范围、按 `componentCode` 对齐组成，输出 `added / removed / changed` 三组。
- 错误 detail 透传：商务 F9 线已把它下沉为 Foundation `HttpServiceClientOptions.withOwnerProblemDetail()`（`commercial/second-slice` 分支 c5f2c3f2），channel 客户端工厂直接开启该开关，不再复制 BFF 内包装类。
- 测试：`ChannelConnectorOwnerSimulator`（照 `CommercialOwnerSimulator`）、`channel-admin-boundary.json`、`ChannelBrowserAutoTest`（转发、授权拒绝、Maker/Checker 分离、业务拒绝、422 投影、依赖失败）、`ChannelAggregationAutoTest`（对比）。

## 8. Platform 注册块 92 与 Web 注册

权限（7）：`CHANNEL_CONFIG_READ`、`CHANNEL_CONFIG_MANAGE`、`CHANNEL_ACCOUNT_CREDENTIAL`、`CHANNEL_COST_READ`、`CHANNEL_COST_MANAGE`、`CHANNEL_COST_APPROVE`、`CHANNEL_CHANGE_READ`。

菜单与按钮（Web `pageCapabilities.ts` 与 `console-resource-capabilities.json` 同步）：

| componentKey | 路由 | 按钮 |
|---|---|---|
| `OPERATIONS_CHANNEL`（目录「渠道」，sortOrder 60） | — | — |
| `OPERATIONS_CHANNEL_CHANNELS` 渠道与账号 | `/channel/channels`，详情 `/:channelId` | `OPERATIONS_CHANNEL_CHANNEL_MANAGE` 维护、`OPERATIONS_CHANNEL_ACCOUNT_CREDENTIAL` 账号凭据 |
| `OPERATIONS_CHANNEL_MIDS` MID 与能力 | `/channel/mids`，详情 `/:midId` | `OPERATIONS_CHANNEL_MID_MANAGE` 维护 |
| `OPERATIONS_CHANNEL_COST_PLANS` 渠道成本 | `/channel/cost-plans`，详情 `/:planCode`、版本 `/:planCode/versions/:costVer` | `OPERATIONS_CHANNEL_COST_MANAGE` 维护、`OPERATIONS_CHANNEL_COST_APPROVE` 审批 |

路由 31 条（`/api/operations/channel-admin/v1/**` → BFF `/channel-admin/v1/**`）：渠道 5、账号 6、MID 5、能力 4、变更 1、成本 10（含 BFF 聚合的版本对比 `GET …/versions/{baseCostVer}/diff/{targetCostVer}`，研发期补入）。请求头、限流、超时、Body 上限照块 95（写 8192，账号配置 65536）。块内 ID 段 `9000000000000921xx` 权限、`922xx` 角色授权、`923xx` 资源、`924xx` 路由、`925xx–926xx` 请求头、`927xx` Query、`928xx–929xx` 限流。默认休眠（权限 ACTIVE、授权 REVOKED、资源与路由 DISABLED），`release/test-activate.sql` 单独激活。

Web：`src/channel/channel.ts`（路径、解码、命令、文案）、`ChannelParts.tsx`、`useChannelCommand.ts`、`AccountConfigurationModal.tsx`（凭据为 `Input.Password`，已配置显示掩码与末 4 位，"重新填写"）、`CapabilityEditor.tsx`（按目录渲染明细）、`CostVersionEditor.tsx`（范围 / 组成增删行）、`CostVersionDiff.tsx`；页面 `pages/ChannelChannels.tsx`、`ChannelMids.tsx`、`ChannelCostPlans.tsx`；`channel.test.ts` 覆盖解码、状态按钮可见性、表单校验与对比模型。

## 8.1 Pencil 页面设计

`operations-console-web/docs/operations-console.pen` 新增 15 个画板（名称前缀「渠道 · 」，位于画布最下方 y≈67380 起四行，x 0 / 1540 / 3080 / 4620），导出见 [assets/stage3/pencil](./assets/stage3/pencil/)（15 张 PNG 与 `export.pdf`）。样式沿用现有运营页（应用栏、左侧导航、`.page-heading`、表格、状态 pill、对话框）；导航新增一级「渠道」及三个子项。

| 画板 | 内容 | 对应范围 / 验收 |
|---|---|---|
| 01 渠道列表 | 筛选（编码 / 名称、状态）、渠道表（编码、名称、状态、账号数、MID 数、Platform 接入方、更新时间、操作）、分页 | B1、AC-B01、AC-V01 |
| 02 渠道详情｜账号 | 标题 + 状态 pill + 编辑 / 暂停 / 关闭；信息条（含 Platform 接入方链接）；标签页 技术账号 / MID / 变更记录；账号表（配置列显示 Token 末 4 位） | B1、B2、AC-B02、AC-V04 |
| 03 新建渠道 | 对话框：渠道编码（创建后不可改，须与接入方编码一致）、名称；提示激活前置条件 | B1、AC-V02 |
| 04 账号配置｜参数与凭据 | 对话框：配置状态条（已配置 · Token 末 4 位 · 保存人时间）、参数键值行、凭据键值行（密码型、已保存掩码、重新填写）、保存并加密 | B2、Q1、AC-B02、AC-V03 |
| 05 渠道激活被拒 | DRAFT 渠道详情，错误横幅显示 Owner detail「Platform 不存在同编码接入方 NI」，接入方链接「未登记 · 去登记」 | B6、Q6、AC-B01、C3 |
| 06 MID 列表 | 筛选（渠道、OE、状态、外部 MID）、MID 表（外部 MID、渠道、OE、当前账号、状态、能力组统计、激活时间、操作：详情 / 改绑账号 / 状态） | B3、AC-B03 |
| 07 MID 详情｜能力组与明细 | 信息条；能力组表（六维 + 状态 + 明细摘要 + 编辑明细 / 启停）；选中组的明细表（代码、类型、值、说明） | B4、AC-B04 |
| 08 新建能力组 | 对话框：六维条件（体系 / 国家 / 币种可 *，MCC 四位）；明细按目录渲染：BOOLEAN 开关、DECIMAL 输入、CODE 下拉 | B4、Q3、AC-B04、AC-V02 |
| 09 渠道成本方案列表 | 筛选（渠道、方案状态）、方案表（代码、渠道、状态、当前版本、当前生效、待审批数、更新时间、操作） | A1、AC-A01 |
| 10 方案详情｜版本列表 | 信息条；版本表（版本、状态、生效时间、范围 / 组成数、编制人、复核人、摘要、操作：详情 / 批准 / 驳回 / 对比 / 作废）；作废仅在未到期 APPROVED 行出现 | A2、A4、A5、AC-A03、AC-A04 |
| 11 版本编制｜范围与组成 | 生效时间；范围块（MID 下拉限本渠道、OE、国家、币种、支付方式、动作、合同引用）+ 组成行（代码、类型、基数、费率、固定费、上下限、舍入）；添加 / 删除范围与组成 | A3、AC-A02 |
| 12 版本对比 | 基准 / 对比信息条，图例（新增 / 修改 / 删除 / 未变），对比表按范围 + 组成对齐，行底色区分 | A6、AC-A05 |
| 13 渠道详情｜变更记录 | 变更记录标签页：时间、对象（类型 + ID）、变更类型、状态变化、操作者、原因 | B7、AC-B06 |
| 14 批准版本确认 | 对话框：生效时间、范围 / 组成、编制人、复核人、内容摘要（随请求提交）、复核意见；取消 / 驳回 / 批准 | A4、AC-A03 |
| 15 作废版本确认 | 对话框：警示（仅未到期 APPROVED 可作废，终态 INACTIVE）、必填原因（写入变更记录）、确认作废 | A5、Q4、AC-A04 |

未单独画的状态（空列表、加载失败、409 冲突、422 字段错误）沿用现有商务 / 财务页做法。

## 9. 与阶段 1 的偏差（需确认）

1. **B5 OE 校验退为格式校验。** client-master api 没有 OE 查询，commercial 也只做格式与覆盖校验；本期 channel-connector 校验 `^[A-Z]{2}[0-9]{3}$` 并要求账号与 MID 一致，页面手工输入 OE。client-master 日后提供查询再接入存在性校验，登记遗留。
2. **新增 `t_channel_config_change` 表**（B7 留痕 + A5 作废原因）。commercial 用自己的 BCR 表，Platform 审计只记控制台调用，不记 Owner 事实，因此 Owner 侧留痕表是必要的；请授权该结构变更。
3. **关闭规则：** 渠道关闭要求其下无非 CLOSED 的账号与 MID；账号关闭要求无非 CLOSED 的 MID 绑定。比阶段 1 更严格，避免半关闭的树；运营需先关子项。
4. **账号配置旧 Token 不回收**（Vault Client 无 Retire），登记遗留。
5. **研发顺序受 I1 / chart2 影响**（第 10 节）：channel-connector 两条线立即开工，BFF / Web 等 I1 合入 main 后开工。
6. **Platform 新增内部接入方存在性查询**（2026-10-20 用户决定）：阶段 1 的"不做 Platform 接口变更"例外，只加 `IntegrationLookupApi` 一个只读合同；备选的 BFF 前置检查、channel-connector 转发会话头、撤回 Q6 均被否决。新增 PLT 线（第 10 节）。

## 10. 研发与验证计划

| 线 | 范围 | 前置 | 执行 |
|---|---|---|---|
| CC-A | 变更记录表（PDManer、SQL、DataObject、Repository）、成本列表、作废、渠道存在校验、读接口 `listChannelMids`、测试 | 第 4 节授权 | Opus 5.5 agent，worktree |
| CC-B | 四组管理合同与 Processor、Vault Protect、Platform 接入方查询、测试（VaultMocker 增加 `expectProtect`、PlatformMocker 增加接入方响应） | CC-A 的变更记录表合入 | Opus 5.5 agent，worktree |
| REG | Platform 块 92 DML、`console-resource-capabilities.json`、本需求 `db/platform-support-service/registration-dml.sql`、`db/vault/dml.sql` | 第 3、8 节确认 | 主会话 |
| PLT | Platform `IntegrationLookupApi` 合同（主会话已写）、Controller、只读 Service、测试 | 第 9 节第 6 条决定 | Opus 5.5 agent，worktree（[tasks/PLT.md](./tasks/PLT.md)） |
| BW（合并原 BW-A / BW-B） | BFF 6 个 Client、2 个 Controller、对比 Processor、Web 三页、Platform capabilities.json 与块 92 追加、边界测试 | api 合同已冻结（CC-A + CC-B）；基线为 `commercial/second-slice` 4d8f4ec3（商务第二切片尚未进 main，先于它集成会冲突） | Opus 5.5 agent，worktree `.claude/worktrees/bw-channel`（[tasks/BW.md](./tasks/BW.md)） |

验证（阶段 5）：channel-connector `-pl …-test -am` 全绿（本机 `mor_channel_connector` 先执行新表 DDL）；BFF `verify`、Web `npm test && npm run build`；本机 e2e：用控制台接口登记 PayerMax mock 渠道 / 账号（Protect 真实 Vault）/ MID / 能力，候选查询与执行走到 Platform，`50-channel-connector-payermax-mock.sql` 与 `channel_setup.py` 退役；经 Nginx 运营入口做 API 与浏览器验收。

mor-test（阶段 6–7）：`mor_channel_connector` 执行新表 DDL；`mor_vault` 插入 PROTECT 规则；`mor_platform` 执行块 92 并激活；替换 channel-connector、ops-bff、ops-web 镜像（固定 sha256）；发布前先与 chart2（科目分级）和 I1 会话对齐 ops-bff / ops-web 的未合入改动，避免回退；合成渠道数据验收后清理。

跨会话冲突热点（提交时逐一核对）：`pageCapabilities.ts`、`ConsolePage.tsx`、`configurationModel.test.ts` 页面计数、`console-resource-capabilities.json`、`OperationsErrorStatusResolver.java`、`PlatformOperationsSimulator.java`、`OperationsConsoleBffApplicationAutoTest.java`、两个 README、`platform-support-initial-data.sql` 末尾追加位置。

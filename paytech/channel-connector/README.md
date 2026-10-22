# channel-connector

- spring.application.name：`channel-connector-service`
- 端口：18110；管理端口 19110；无 context path
- 数据库：`mor_channel_connector` / `channel_connector_app`
- 来源现有工程：`payment-orchestration-service`（`paytech-payment-channel` 与 web 中渠道侧代码，重构第 05 步拆出）

channel-connector 是面向外部第三方业务调用的唯一出口（[2026-10-20 决定](../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)，例外见[唯一出口例外清单](../../agent-docs/decisions/2026-10-22-unified-outbound-exception-list.md)）。渠道分支付渠道（`PAYMENT`）与服务渠道（`SERVICE`，2026-10-22 第二段），共用一张渠道身份表 `t_channel`、协议配置、凭据保管与执行引擎；支付渠道另有技术账号与 OE 绑定、MID、能力配置与按 OE 的候选查询、预计渠道成本配置（方案 / 版本 / 范围 / 组成的 Maker-Checker、到期切换与命中），支付入口在冻结坐标上于本进程执行标准 API 并把输出映射为已映射结论；服务渠道只经通用执行入口并按「调用方 × 渠道 × 标准 API」授权执行。2026-10-20 起（[统一对外渠道出口](../../requirements/20261020/channel-connector-统一对外渠道出口/technique.md)第一段）同时拥有自 Platform 迁入的渠道协议能力：标准 API、接入方、原子 API、公共脚本、标准 API 实现（流程）、执行引擎与沙箱验证，以及外部 API 管理接口；对支付渠道的出站请求由本服务发出。Payment 冻结三元组、推进支付状态；channel-connector 绝不重选三元组。开发规范与写接口自查沿用 [Payment 入口](../payment/AGENTS.md)列出的公共文档。

## 模块

| 模块 | 职责 |
|---|---|
| `paytech-channel-connector-api` | `ChannelCandidateQueryApi`、`ChannelOperationApi`、`cost.ChannelCostPlanApi`、`cost.ChannelCostResolutionApi`、渠道管理与 `externalapi.*`（7 个外部 API 管理接口）的 `@HttpExchange` 合同、DTO、稳定代码枚举（`ChannelOperation`、`ChannelOperationOutcome`、`ExecutionPurpose`、`ChannelPaymentStatus` 及迁入的 `ApiFieldDataType`、`ApiFieldLocation`、`ExecuteStatus`、`ScriptExecutionStage`、`VerifyCaseStatus`、`VerifyRunStatus`、`VerifyStepStatus`）与能力代码常量 |
| `paytech-channel-connector-protocol` | 渠道身份与协议能力（基础包 `com.paytech.channel.connector.protocol`）：`t_channel`、`t_standard_api`、`t_api_field`、7 张 `t_channel_*` 协议 / 沙箱表、`t_channel_call_grant` 与 `t_channel_config_change` 的 DataObject / Mapper / Repository；渠道创建、名称与出站地址修改、调用授权的 Processor；外部 API 管理与执行 Processor、执行引擎（Aviator 脚本、注册函数）、出站 `ext/outbound`（`ChannelHttpClient` 的两个执行舱、`ChannelAddressGuard`、`ChannelExecutionCompartments`）、沙箱验证补偿 Handler。对 core 公开 `ExecuteStandardApiProcessor`、`ChannelService`（渠道身份 `ChannelIdentity`、列表摘要、状态变更）、`ChannelCallGrantService` 与 `ChannelConfigChangeService`。不依赖 core |
| `paytech-channel-connector-core` | 支付扩展：账号、MID、能力四表与渠道成本四表的 DataObject / Mapper / Repository；渠道状态变更（激活要求已配置出站地址，关闭核对账号与 MID）、渠道 / MID / 成本方案列表（组合 protocol 渠道服务与本模块数据）、账号 / MID / 能力 / 成本 Processor；能力条件匹配与逐代码解析（只在此一处解析 `ACTION.*`、`AMOUNT.*`）、冻结坐标与 OE 一致性校验；支付入口与服务渠道通用执行入口的 Processor、PAY 输出映射（`PayOutputs`）、执行配置读取（Vault Reveal，无生产调用方）。依赖 protocol 的公开能力，不访问其 `*Impl`、Repository、Mapper、DataObject，SQL 不出现 protocol 的表 |
| `paytech-channel-connector-app` | 启动类、Controller（含 `ext/rest/externalapi` 外部 API 管理入口）、数据源 / MyBatis 扫描（core 与 protocol Mapper）、Snowflake 生成器、读卡客户端装配（`PaymentCardMaterialConfiguration`）、XXL-Job 入口、配置 |
| `paytech-channel-connector-test` | 隔离测试（真实 `mor_channel_connector`、渠道 / card / Vault DTO Mock）与架构门禁（R1–R15、渠道与协议表归属、模块级表归属、core 不访问 protocol 内部类型） |

## HTTP 合同

| 合同 | 路径 | 说明 |
|---|---|---|
| `ChannelCandidateQueryApi.queryMidCandidates` | `POST /channel-connector/v1/mid-candidates` | 入参 `entityNo` + `criteria{paymentMethodCode, schemeCode?, countryCode, currency, mccCode}`；返回启用候选 `{channelId, channelAccountId, midId, supportedOperations[], minAmount?, maxAmount?}`（ID、金额为十进制字符串）。空候选为 200 空列表；配置损坏 500；校验失败 422 |
| `ChannelMidApi.listChannelMids` | `GET /channel-connector/v1/mids?channelId&channelAccountId&entityNo&status&page&size` | 分页 MID 列表（`PagingResponseBody`），同库联查渠道编码、当前账号名称与能力组 / 启用组计数；按 `update_time DESC, mid_id DESC`；只读、无事务 |
| `ChannelConfigChangeApi.listConfigChanges` | `GET /channel-connector/v1/config-changes?targetType&targetId&page&size` | 分页查询 `t_channel_config_change` 中一个对象的人工变更记录，按 `id DESC`；只读、无事务 |
| `ChannelOperationApi.execute` | `POST /channel-connector/v1/operations` | 支付入口，只供 Payment、只接受支付渠道。入参冻结三元组 + `entityNo` + `operation` + `purpose` + `criteria?` + `amount?` + 标准 API `input`（支付工具句柄、尝试标识、回跳地址等，不含卡值与账号材料）；返回 `{outcome, paymentStatus?, redirectUrl?, channelTransactionRef?, channelStatus?, failureCode?, failureMessage?, rawOutputKeys[]}`。冻结渠道为服务渠道时 NOT_SENT（`INTEGRATION_CONFIG_INVALID`，与配置无效同口径）；冻结坐标不可用、OE 不一致或账号未配置为 NOT_SENT；protocol 保证未发出的失败（`StandardApiNotSentException`：标准 API 或实现不存在、配置无效、初始化失败、预算耗尽、执行舱繁忙 `CHANNEL_EXECUTION_BUSY`、输入不满足请求字段，`failureCode` 为原代码）为 NOT_SENT，其余失败为 ERROR |
| `ServiceChannelExecutionApi.execute` | `POST /channel-connector/v1/service-executions` | 服务渠道通用执行入口：请求 `{channelCode, standardApiCode, input}`，返回 `{executeStatus, outputs}`。依次确认调用方（已验证内部 Token 优先，`X-Caller-Service` 与 Token 不一致 400；internal-auth 关闭时信任该头；缺失或格式不符 400）、渠道（不存在 404，非 `SERVICE` 或非 ACTIVE 400）、ACTIVE 授权（否则 403 `CHANNEL_CALL_NOT_GRANTED`）、唯一 ACTIVE 凭据（注入 `input.channelAccount`，调用方自带的一律移除；没有则不提供账号材料），再在事务外、服务渠道执行舱中执行。未发出类失败：422 `VALIDATION_ERROR`、404 `STANDARD_API_NOT_FOUND` / `INTEGRATION_IMPLEMENTATION_NOT_FOUND`、500 `INTEGRATION_CONFIG_INVALID`、503 `INTEGRATION_INITIALIZATION_FAILED` / `INTEGRATION_BUDGET_EXHAUSTED` / `CHANNEL_EXECUTION_BUSY`（`ServiceExecutionErrorCode`）；任一校验失败都不发出外部请求 |
| `ChannelCallGrantApi` | `/channel-connector/v1/channel-call-grants`：`GET ?channelId&callerService&standardApiCode&grantStatus&page&size`、`POST`、`POST /{id}/status` | 服务渠道调用授权（运营维护，调用方 operations-console-bff）。新建要求服务渠道且已实现该标准 API（否则 400），同一「调用方 × 渠道 × 标准 API」只有一行（已存在 409，含现有授权 ID 与状态）；启停按 `expectedVersion` CAS，只停用不删除，再启用沿用原行并递增版本且要求仍有实现。写入 TRY_LOCK `lock:channel-call-grant:{channelId}`，同事务写 `CHANNEL_CALL_GRANT` 变更记录；仍有 ACTIVE 授权时删除标准 API 实现返回 409 |

标准 API 编码取渠道操作代码（PAY → `PAY`）；外部 API 管理接口中的接入方编码（`integrationCode`）即渠道编码（如 `PAYERMAX`）。执行在本进程、数据库事务外完成，按渠道类型进入两个执行舱之一（各自的并发许可与 JDK HttpClient，拿不到许可不排队）；出站前解析目标主机并拦截回环、私网、链路本地、保留与组播地址（部署放行网段除外），被拦截按配置无效未发出；每次原子 API 的 HTTP 请求结束写一条 INFO 访问日志（logger `channel.outbound.access`，字段 `callerService`、`channelCode`、`channelType`、`standardApiCode`、`apiCode`、`executeStatus`、`httpStatus`、`elapsedMs`、`requestId`，不含 URL、Header、报文与凭据）：账号配置由 `CHANNEL_ACCOUNT_CONFIG` 初始化函数以 `channel-connector-service` 身份 Reveal（Vault 用途 `CHANNEL_EXECUTION`），卡数据由 `PAYMENT_CARD` 初始化函数以同一身份凭一次性票据向 card-service 读取；流程总预算由渠道实现配置约束（原 Platform 30s 读超时取消）。执行配置读取（`GetChannelExecutionConfigProcessor`，返回渠道账号明文凭据）没有生产调用方，本步不发布 HTTP 入口。

### 外部 API 管理（2026-10-20 自 Platform 迁入）

6 个合同 `externalapi.StandardApiManagementApi`、`IntegrationApiManagementApi`、`IntegrationScriptManagementApi`、`IntegrationImplementationManagementApi`、`ExternalApiEditorApi`、`IntegrationVerifyManagementApi` 共 39 个接口，路径 `/external-api-admin/v1/**`、JSON 字段与状态码与原 Platform 合同一致（E01～E30、V01～V14；2026-10-22 第二段下线接入方 5 个 CRUD E06～E10，接入方并入渠道，名称、说明与出站地址经渠道管理接口维护），`/integrations/{code}/…` 中的 `{code}` 即渠道编码，只由 operations-console-bff 经私网调用。

- 鉴权：不再有 `X-Gateway-Session-Id` 与 Owner 权限复核（权限由 Gateway Route 与 BFF 实时 PLT-I02 授权负责，技术方案 S1）。22 个写命令（各对象的创建 / 修改 / 删除与发起沙箱运行）必带 `X-Gateway-Actor-Subject`（规范正十进制，缺失或格式不符 400）；查询与 4 个静态校验接口不带。
- 写入：全模块共用 Redis 资源锁 `external-api-config`（TRY_LOCK，等待 500ms、租约 30s），写事务 READ_COMMITTED、详情只读事务 REPEATABLE_READ，均 15s。每次写入同事务写一行 `t_channel_config_change`：`target_type` 为 `STANDARD_API` / `CHANNEL_API` / `CHANNEL_SCRIPT` / `CHANNEL_IMPLEMENTATION` / `VERIFY_ACCOUNT` / `VERIFY_CASE`（出站地址替换记为 `CHANNEL` / `CONFIGURATION`；`INTEGRATION` 只剩合表前已删除接入方的历史记录），`target_id` 为对象主键，`change_type` 为 `CREATE` / `UPDATE` / `DELETE`，`change_detail` 记原 Platform 审计的字段名与前后摘要（名称、编码、SHA-256），不含定义原文、参数或凭据。Platform 历史审计不迁移（S4）。
- 沙箱验证运行以本地补偿任务 `INTEGRATION_VERIFY_RUN` 异步执行（最多 2 次，第二次只收尾为 INTERRUPTED）。
- 变更记录查询接口 `ChannelConfigChangeApi` 的 `targetType` 接受全部 12 种写入类型（含 `CHANNEL_CALL_GRANT` 与外部 API 对象类型）。

## 构建与测试

在 `paytech/` 下使用 JDK 25：

```bash
./mvnw clean package -Dmaven.test.skip=true -pl channel-connector/paytech-channel-connector-app -am
./mvnw -pl channel-connector/paytech-channel-connector-test -am clean verify
```

测试前先人工审查 [测试 DDL](paytech-channel-connector-test/src/test/resources/init/ddl.sql)，用 root 应用到本机 `local-environment` MySQL（创建 `mor_channel_connector`、`channel_connector_app` 与 28 张表；已有 13 表的库依次执行 [2026-10-19 增量](docs/database/sql/changes/2026-10-19-channel-cost-tables.sql)、[2026-10-20 变更记录增量](docs/database/sql/changes/2026-10-20-channel-config-change.sql) 与 [2026-10-20 协议表增量](docs/database/sql/changes/2026-10-20-protocol-tables.sql)，已有 17 表的库执行后两个，已有 18 表的库只需最后一个；第一段 28 表结构的库按[第二段迁移](../../requirements/20261020/channel-connector-统一对外渠道出口/db/channel-connector/phase2-20-migrate.sql)合表改名（先跑 precheck，迁移后跑 verify，回退见 rollback）；协议表数据迁移另按技术方案第 3.4 节）。测试同时连接本机 `local-environment` Redis（资源锁，前缀 `channel-connector-mock`）。并行任务可用 `SPRING_DATASOURCE_URL` 指向隔离库 `mor_channel_connector_{后缀}`、`REDIS_KEY_PREFIX` 指定隔离前缀；测试基线只接受这两类库名。PDManer 规范模型见 [channel-connector.pdma.json](docs/database/channel-connector.pdma.json)（由 `scripts/pdmaner/generate-channel-connector-model.mjs` 生成），物理决策随 Payment 维护于 [PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md](../payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)。Nacos 模板见 [channel-connector-service.yaml](config/nacos/channel-connector-service.yaml)。

## 渠道成本配置（2026-10-19 自 billing 迁入）

需求：[billing 计费事实模型重构与渠道成本迁出](../../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/technique.md) 第 2 节。本服务保存预计渠道成本规则并提供命中；"组成 → 金额"由 billing 计算，本库不算金额、不按币种精度归整。

| 合同 | 路径 | 说明 |
|---|---|---|
| `ChannelCostPlanApi.createChannelCostPlan` | `POST /channel-connector/v1/channel-cost-plans` | 每个 Channel 一个方案，DRAFT，201；Channel 须存在于本库 `t_channel`，否则 400「渠道不存在」；服务渠道 400「服务渠道没有渠道成本」 |
| `ChannelCostPlanApi.listChannelCostPlans` | `GET /channel-connector/v1/channel-cost-plans?channelId&status&page&size` | 分页方案列表，同库联查渠道编码 / 名称、当前版本号与生效时间、PENDING_APPROVAL 版本数；按 `update_time DESC, id DESC` |
| `ChannelCostPlanApi.cancelChannelCostVersion` | `POST .../{planCode}/versions/{costVer}/cancel` | 只作废未到生效时间的 APPROVED 版本 → INACTIVE，`effective_to = effective_from`（空区间，命中与到期切换都不再选中）；同事务写 `CHANNEL_COST_VERSION / CANCEL` 变更记录；其他状态或已到生效时间 400 |
| `ChannelCostPlanApi.createChannelCostVersion` | `POST .../{planCode}/versions` | 完整范围与组成一次写入为 DRAFT 并冻结 `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1` 内容摘要；范围 MID 须在本库 `t_channel_mid` 存在、属于方案 Channel 且 OE 等于范围 `entityNo` |
| `submit` / `approve` / `reject` | `POST .../{planCode}/versions/{costVer}/submit\|approve\|reject` | Maker 提交；Checker ≠ Maker 并确认同一摘要；批准时已到生效时间即切换当前版本，否则 APPROVED |
| `getChannelCostPlan` / `getChannelCostVersion` | `GET .../{planCode}`、`GET .../{planCode}/versions/{costVer}` | 方案与版本摘要（版本号降序）、版本详情（范围按 scopeNo、组成按 componentCode 升序） |
| `ChannelCostResolutionApi.resolveChannelCost` | `POST /channel-connector/v1/channel-costs/resolutions` | 只读：ACTIVE 方案 → 覆盖 `asOfTime` 的唯一 ACTIVE / INACTIVE 版本（`[effective_from, effective_to)`）→ 六维精确命中唯一范围 → 组成；未命中 400 `CHANNEL_COST_NOT_RESOLVED`，多重命中 400 `CHANNEL_COST_AMBIGUOUS` |

费率与金额输出为去掉末尾零的普通十进制字符串；金额小数位不超过 8 位由入口格式保证，币种只校验格式（本库无币种表）。

**写入并发：** 六个写入口（含作废）与到期切换共用 Redis 资源锁 `channel-cost-plan:{planCode}`（`ChannelConnectorLocks`，LOCK 模式），事务内先锁方案行再锁版本行，状态以原状态条件更新、当前指针以方案 `version` CAS。

**到期切换 Job：** `ChannelCostVersionActivationJobHandler`（XXL Handler `channelCostVersionActivation`，建议 Cron `0 */5 * * * ?`）每轮读取至多 200 个已到生效时间的 APPROVED 版本，按方案逐个加锁、短事务切换（新版本 ACTIVE、原当前版本 INACTIVE 且 `effective_to` = 新版本生效时间、方案指针 CAS）；重复执行幂等。生效时间不晚于当前版本的到期版本计为失败并保持 APPROVED，Job 以 `handleFail` 标记失败等待人工处理。

**依赖与配置键：**

| 依赖 | 用途 | 配置键 |
|---|---|---|
| `paytech-redis-starter` | `AbstractLockProcessor` 资源锁（渠道成本、渠道配置与 `external-api-config`） | `spring.data.redis.*`（`CHANNEL_CONNECTOR_REDIS_HOST/PORT/PASSWORD`）、`redis.coordination.enabled=true`、`redis.key-prefix`（缺省 `spring.application.name`） |
| `paytech-snowflake-id` | 渠道成本、渠道配置变更记录与外部 API 配置主键 | `channel-connector.identifier.region-id` / `worker-id`（`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID` / `_WORKER_ID`，必填无默认） |
| `paytech-compensation-event`（含 `paytech-xxl-job-starter`） | 沙箱验证运行补偿任务 `INTEGRATION_VERIFY_RUN`；XXL 执行器承载补偿兜底扫描与到期切换 Job | `compensation.enabled=true`、`compensation.instance-id`（`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`）、`xxl.job.*`（调度中心地址与执行器参数由部署注入） |
| 渠道协议（protocol） | 外部 API 配置写锁与执行出站约束，自 Platform `ps.integration.*` 迁入，后缀与默认值不变；修改需重启 | `channel-connector.protocol.configuration.lock-wait-time`（500ms）/ `lock-lease-time`（30s）、`channel-connector.protocol.execution.allowed-schemes`（`[https]`，只有本机 Mock 加 http）/ `card-context-path`（`""`） |
| 执行舱与出站防护（2026-10-22） | 支付 / 服务两个执行舱的并发上限（拿不到许可不排队）；出站地址放行网段（CIDR，默认空，本机与测试环境的渠道 Mock 列在此处）；均在启动时读取，修改需重启 | `channel-connector.protocol.execution.payment-max-concurrency`（64）/ `service-max-concurrency`（16）/ `trusted-networks`（`[]`） |
| `paytech-card-api` | 卡直付执行读卡（`ChannelCardMaterialApi`，经服务发现 `card-service`，读超时 2s，不跟随重定向） | 无独立键；card 只允许 `channel-connector-service` 读卡（2026-10-22 第二段收回 Platform） |

## 渠道基础配置管理（2026-10-20，CC-B）

运营控制台经 operations-console-bff 调用的四组管理合同（仅私网）。写命令必带 `X-Gateway-Actor-Subject`（规范正十进制 Account ID，`ChannelConnectorHeaders.ACTOR_HEADER`），查询不带；业务拒绝 400 `BUSINESS_CHECK_FAILED`、字段校验 422、唯一冲突与并发 409。每次写入与业务变更同事务写一行 `t_channel_config_change`（经 `ChannelConfigChangeApi` 查询）。

| 合同 | 路径 | 说明 |
|---|---|---|
| `ChannelApi` | `/channel-connector/v1/channels`：`POST`、`GET ?channelType&status&keyword&page&size`、`GET /{id}`、`PUT /{id}`、`PUT /{id}/endpoints`、`POST /{id}/status` | 编码唯一（409），类型 `PAYMENT` / `SERVICE` 创建时必填且不可改；详情含类型、说明、出站地址与版本，列表另含出站地址数与 ACTIVE 授权数（只对服务渠道）。出站地址整组替换按版本 CAS（409），仍被原子 API 引用的地址代码不能删除（409），地址规则与私网 / localhost 拦截 422（字段位置到行），与原子 API 写入共用 `lock:external-api-config`。激活要求已配置出站地址（两类渠道相同，凭据与标准 API 实现不阻止激活），否则 400「渠道未配置出站地址」；关闭要求无未关闭账号与 MID |
| `ChannelAccountApi` | `/channel-connector/v1/channel-accounts`：`POST`、`GET`、`GET /{id}`、`PUT /{id}`、`PUT /{id}/configuration`、`POST /{id}/status` | 支付渠道的账号必须带 OE（只校验 `^[A-Z]{2}[0-9]{3}$`），服务渠道的账号即凭据、不得带 OE（Vault 负载 `entityNo` 为 null，`schemaVersion` 仍为 1），每个服务渠道最多一个 ACTIVE 凭据（激活第二个 409，激活时另锁所属渠道）；配置写入在事务外 Vault Protect（`CHANNEL_ACCOUNT_CONFIG_V1` / `REFERENCE` / 用途 `CHANNEL_CONFIGURATION`，幂等键 `channel-account-config.{id}.{规范 JSON SHA-256}`），再以短事务条件更新 Token；响应只给 `configured` 与 Token 末 4 位；激活要求渠道 ACTIVE 且已配置，关闭要求无未关闭 MID 绑定 |
| `ChannelMidApi`（写方法） | `/channel-connector/v1/mids`：`POST`、`GET /{id}`、`PUT /{id}/account`、`POST /{id}/status` | 服务渠道没有 MID（创建 400，能力组创建同样 400）；同渠道外部 MID 唯一（409）；账号须同渠道、同 OE、未关闭；激活要求渠道与当前账号均 ACTIVE，首次激活写 `activated_at`，关闭写 `closed_at`；详情含能力组与明细 |
| `MidCapabilityApi` | `/channel-connector/v1/mid-capabilities`：`GET /definitions`、`POST`、`PUT /{id}/details`、`POST /{id}/status` | 明细按 `channel-connector.capability-definitions` 校验（与候选查询的解析规则一致）；六维组合在 MID 内唯一（409）；整组替换同事务删旧插新；DISABLED 组不参与候选查询 |

**状态机：** 渠道、账号、MID 共用 `DRAFT→ACTIVE`、`ACTIVE⇄SUSPENDED`、`DRAFT|ACTIVE|SUSPENDED→CLOSED`，CLOSED 为终态；状态写入以原状态为条件并校验行数。

**写入并发：** 渠道 `channel:{id}`、账号 `channel-account:{id}`、MID `channel-mid:{id}`（渠道锁由 protocol `ChannelLocks` 定义，与 protocol 的渠道名称与说明修改共用；其余在 `ChannelConnectorLocks`；账号激活另锁所属渠道），全部经 `LockCommand.tryLocks` 获取（等待 10s、租约 60s）；账号关闭另锁所属渠道，MID 改绑同时锁 MID 与目标账号。单资源 `lock()` 的 Key 带 `lock:` 前缀且为 SpinLock，与 `tryLocks` 的 RLock 不互斥，所以同一资源的入口统一使用 `tryLocks`。

**遗留：** 账号重新配置后旧 Vault Token 不回收（Vault Client 无 Retire）；Protect 成功但写库失败时新 Token 成为孤儿，只记录账号 ID。

**补充（BW 合同缺口）：** `ChannelCostVersionResponseBody` 与 `ChannelCostVersionSummaryResponseBody` 增加 `costVerId`（版本主键，供按 `CHANNEL_COST_VERSION` 查变更记录）；版本摘要增加 `scopeCount` / `componentCount`（方案详情一次 LEFT JOIN 分组汇总）；`listChannelMids` 增加可选 `externalMidCode` 前缀筛选（`[A-Za-z0-9_.-]{1,128}`，通配符按文字处理，参数位于 `status` 之后、`page` 之前）。

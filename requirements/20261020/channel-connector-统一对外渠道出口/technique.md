# channel-connector：统一对外渠道出口 技术方案

> 状态：阶段 2–3 评审稿（2026-10-20），待用户确认。依据：[需求](./requirement.md)（阶段 1 已确认，Q1–Q8 按建议）与 [2026-10-20 统一对外出口决定](../../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)。

**规模（本次方案口径）：** 涉及 6 个系统 / 工程（channel-connector、platform-support、card、operations-console-bff、operations-console-web、tools/arch-rules），Vault 无变更。

- **第一段（等价迁移）**
  - 接口：44 个外部 API 管理接口原样迁入 channel-connector，BFF 与前端路径不变；删除 Platform 2 个内部接口（标准 API 执行、接入方存在性），新增接口 0。
  - 数据库：channel-connector 新增 10 张表（结构不变），`t_channel_config_change` 加 1 列；Platform 删除 10 张表。
  - 数据迁移与页面：迁移 10 表数据；页面不变。
- **第二段（合表与服务渠道）**
  - 接口：新增 5 个 Owner 接口（通用执行 1、调用授权 3、出站地址 1），其中 4 个经 BFF 转发；下线 5 个接入方 CRUD 接口。
  - 数据库：`t_payment_channel` 改为 `t_channel` 并加 6 列；7 张协议 / 沙箱表改名，`integration_*` 列随之改名；新增 `t_channel_call_grant`（10 列）；`t_channel_account.entity_no` 改为可空；`t_integration` 并入 `t_channel` 后删除。
  - 注册与页面：Platform 注册新增 1 个权限、4 条 Route，下线 5 条 Route；渠道详情页重构，画板数在第二段研发前另行确认。

## 1. 现状与差异

### 1.1 对外出站调用盘点（全仓库 main 源码）

只有 platform-support 直接请求外部；channel-connector、Payment、Gateway、client-master 等只调本系统服务。

| # | 位置 | 外部目标 | 凭据 | 处理 |
|---|---|---|---|---|
| O1 | Platform `integration/ext/outbound/ChannelHttpClient` | 支付渠道（PayerMax 等，地址来自接入方配置） | 渠道账号 Vault（`CHANNEL_EXECUTION`） | **迁入 channel-connector**；补出站地址防护（第 6.4 节） |
| O2 | Platform `PaymentCardMaterialConfiguration`（读卡） | card-service（内部） | 内部 Token | 随引擎迁入；Card 允许调用方改为 channel-connector |
| O3 | Platform delivery `SesEmailProvider` / `SesEventQueue` | AWS SES v2、SQS | 发送账号 Vault（`NOTIFICATION_DELIVERY`） | **例外**：Platform 通知自有能力，已有专项凭据与开关 |
| O4 | Platform delivery `WebhookHttpSender` | Client 自填的 Webhook 地址 | 签名密钥 Vault（`WEBHOOK_SIGNING`） | **例外**：目标是 Client 自己的地址，已有 `WebhookAddressGuard`（DNS 固定、拦截私网与保留地址） |
| O5 | Platform file-document `S3FileStorage` / `MinioFileStorage` | AWS S3 / 自建 MinIO | 配置或 AWS 默认凭据链 | **例外**：对象存储基础设施，浏览器经预签名 URL 直传 |

Gateway JWKS、Vault（本地 AES，未直接使用 KMS）、Nacos、MySQL、Redis、RabbitMQ、XXL-Job 都只访问内部，不在出口范围内。

### 1.2 需求项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| A1 Owner 迁移 | Platform `paytech-platform-support-integration`（主代码约 237 类：46 个 Processor、8 个 Service、10 个 Repository / Mapper、执行引擎、Aviator 脚本、14 个脚本函数和 4 个初始化函数、沙箱验证补偿 Handler）；控制器与审计在 platform-app | 全部在 channel-connector | 依赖 Platform 的 Operations 鉴权、审计、锁配置、Snowflake Bean 和事务管理器 | 第 3 节逐项替换 |
| A2 一个渠道一个身份 | `t_payment_channel`（channel-connector）与 `t_integration`（Platform）按编码对齐；前者排序规则大小写不敏感，后者为 `ascii_bin` | 一张 `t_channel` | 两库两表 | 第二段合表（第 5 节） |
| A3 进程内执行 | `ExecuteChannelOperationProcessor` 经 HTTP 调 Platform `/standard-apis/v1/{code}/execute`，读超时 30s；`ChangeChannelStatusProcessor` 经 `IntegrationLookupApi` 检查接入方 | 本进程调用引擎 | 跨服务 | 第一段改为模块接口调用 |
| D1 账号 Reveal | channel-connector **已有** `REVEAL CHANNEL_ACCOUNT_CONFIG_V1 [CHANNEL_EXECUTION]` 规则；Platform 侧规则只存在于需求片段，任何环境都未授予 | channel-connector 自行 Reveal | 无 | 引擎迁入后直接以 `channel-connector-service` 身份 Reveal，**Vault 不改** |
| D3 Card | `RevealChannelCardMaterialProcessor` 硬编码只允许 `platform-support-service`；票据由 Payment 申请，不绑定读取方 | 只允许 channel-connector | 代码常量 | 第一段同时允许两者（便于回退），第二段只留 channel-connector |
| C3 调用授权 | Platform 标准 API 注明"本期不做调用方授权"；channel-connector 默认 `internal-auth.mode=OFF`，没有入站调用方白名单 | 调用方 × 渠道 × 标准 API 白名单 | 缺表和接口 | 第二段新增（第 6.2 节） |
| C4 隔离 | 只有一个 `ChannelHttpClient` | 两类渠道并发分开 | 缺 | 第二段（第 6.3 节） |
| E3 访问日志 | channel-connector MVC 日志已对 `/` 全路径只记元数据；引擎没有逐次出站日志 | 每次对外调用一条结构化日志 | 缺 | 第二段（第 6.5 节） |
| E2 门禁 | arch-rules R1–R14 不检查出站客户端 | 业务服务不得新增直连第三方的客户端 | 缺 | 第二段新增 R15（第 6.6 节） |
| 出站地址防护 | `ChannelHttpClient` 只限制 scheme（默认 https），**不拦截私网或保留地址**；Webhook 已有 Guard | 统一出口不能被配置成访问内网 | 安全缺口 | 第二段补齐（第 6.4 节） |
| F 页面 | 外部 API 两页已由[控制台体验优化](../operations-控制台体验优化/)挂到「渠道」菜单下（菜单迁移 SQL 已写、未提交）；渠道详情按 `channelId` 分为账号、MID、变更三个页签，接入方详情按 `integrationCode` 分为五个页签 | 渠道详情一处维护 | 两套详情 | 第一段页面不动；第二段合并（第 7 节） |

### 1.3 系统影响

| 系统 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|
| channel-connector | 新模块 `-protocol`、10 表、44 个管理接口、引擎、补偿 Handler；第二段合表、通用入口、授权、隔离、防护 | 是 / 是 / 是（新增 `channel-connector.protocol.*` 键，补偿目录增加 `INTEGRATION_VERIFY_RUN`） | 本进程承担出站，执行线程隔离必须到位 |
| platform-support | 删除 integration 模块、app 中 9 个 Controller 与 3 个 Converter、`standardapi`/`externalapi` 两个 api 包、读卡配置、10 表、测试 | 是 / 是（删表）/ 是（删 `ps.integration.*`、补偿目录项） | 历史审计事件留在 `t_audit_event` |
| card | 读卡允许调用方 | 是（常量） | 部署顺序：先于 channel-connector 执行卡支付 |
| operations-console-bff | 7 个 Client 改指 channel-connector；第二段新增授权与出站地址转发，下线接入方 CRUD | 是 / 否 / 是 | Owner 合同不再带 `X-Gateway-Session-Id` |
| operations-console-web | 第一段不变；第二段合并渠道详情 | 第二段 | 需先确认画板 |
| tools/arch-rules | `table-ownership.yaml` 10 表换 Owner、计数测试、channel-connector 模块列表、R15 | 是 | — |
| Platform 注册数据 | 第一段无；第二段新增 1 权限、4 Route，下线 5 Route | 第二段 DML | 动态 Gateway 快照需重新发布 |
| Vault | 无 | 否 | 服务渠道沿用 `CHANNEL_ACCOUNT_CONFIG_V1`（第 6.1 节） |
| Payment / reconciliation | 无（`ChannelOperationApi` 合同不变） | 否 | Payment 读超时 35s 仍大于渠道流程预算 |

## 2. 模块与依赖

channel-connector 现有 `-api`、`-core`、`-app`、`-test`。新增一个业务能力模块 **`paytech-channel-connector-protocol`**（渠道协议）：

| 模块 | 拥有的表 | 职责 |
|---|---|---|
| `-protocol` | 第一段：Platform 迁入的 10 表与 `t_channel_config_change`；第二段：`t_channel`、7 张改名后的协议 / 沙箱表、`t_standard_api`、`t_api_field`、`t_channel_call_grant`、`t_channel_config_change` | 渠道身份与出站配置、标准 API、原子 API、脚本、流程、执行引擎、沙箱验证、调用授权、变更记录 |
| `-core` | `t_channel_account`、`t_channel_mid`、`t_mid_capability*`、成本四表 | 支付扩展：账号、MID、能力、候选、成本；支付执行入口与通用执行入口的 Processor |

依赖方向 `core → protocol`，单向。

**拆模块的理由（必要性门禁）**
- 执行引擎是独立完整的能力，有自己的表、Aviator 运行时和注册函数。
- 核心模块只需要窄接口："执行某渠道的某个标准 API""查询渠道身份""记录变更"。
- 如果合在一个模块里，架构测试无法阻止核心逻辑直接读写协议表。

**凭据与卡数据的传递，以及为什么没有环依赖**
- 引擎不读账号表：调用方 Processor 把账号身份和 Vault Token 作为执行输入传给引擎，引擎中的 `CHANNEL_ACCOUNT_CONFIG` 初始化函数负责 Reveal，方式与现在的交接方案一致。
- 卡票据同样由调用方作为输入传入。
- 因此 protocol 模块不依赖 core。

**两项随之调整的归属**
- `t_channel_config_change` 移到下层的 protocol 模块：渠道级变更由 protocol 记录，core 的账号、MID、成本写入在同一事务里调用 protocol 公开的变更记录服务。
- 第二段渠道身份表 `t_channel` 也归 protocol：渠道创建、改名和状态变更由 core 的 Processor 组合 protocol 公开的渠道服务完成；激活和关闭检查涉及账号与 MID，由 core 在同一事务里检查。

## 3. 第一段：等价迁移

### 3.1 搬迁映射

| Platform 现位置 | channel-connector 新位置 |
|---|---|
| `paytech-platform-support-integration`（`core/dal`、`domain`、`domainservice`、`service/processor`、`ext/outbound`、`ext/eventhandler`） | `paytech-channel-connector-protocol` 对应包，基础包 `com.paytech.channel.connector.protocol` |
| platform-app `ext/rest/externalapi/*Controller` 与 3 个 Converter | channel-connector-app `ext/rest/externalapi/` |
| `platform-support-api` `externalapi/*`（7 个接口、23 个请求、35 个响应）与 `api/enums` 中 7 个外部 API 枚举 | `paytech-channel-connector-api` `externalapi/*`、`api/enums`；**路径 `/external-api-admin/v1/**` 不变** |
| `standardapi/StandardApiCommandApi`、`IntegrationLookupApi` 与 `StandardApiExecutionErrorCode` | 删除；执行与存在性检查改为进程内调用，未发出类错误码转为 protocol 模块内部的异常类型 |
| `PaymentCardMaterialConfiguration` | channel-connector-app `configuration/`，调用方身份 `channel-connector-service` |
| `ps.integration.configuration.*`、`ps.integration.execution.*` | `channel-connector.protocol.configuration.*`、`channel-connector.protocol.execution.*`（键名后缀与默认值不变：锁等待 500ms、租约 30s、`allowed-schemes=[https]`、`card-context-path`） |
| Platform `compensation-event-catalog.yaml` 中的 `INTEGRATION_VERIFY_RUN` | channel-connector 补偿目录（服务已依赖补偿组件，`t_compensation_event` 已建表） |

### 3.2 Platform 耦合的替换

| Platform 机制 | 替换方式 | 说明 |
|---|---|---|
| `OperationsManagementPolicyService` 在每个 Processor 内复核会话与 `EXTERNAL_API_READ/MANAGE`（44 处） | 与 channel-connector 现有渠道管理一致：由 Gateway 按 Route 校验权限，BFF 实时调 PLT-I02 授权；channel-connector 只读取 `X-Gateway-Actor-Subject`（写必带，规范正十进制） | 去掉一层 Owner 复核，见第 9 节 S1 |
| `@Audit` 与 `OperationsAuditService` 写 Platform `t_audit_event`（字段级摘要，流程只记 SHA-256） | 写 `t_channel_config_change`，同一事务。`target_type` 增加 `STANDARD_API`、`INTEGRATION`、`INTEGRATION_API`、`INTEGRATION_SCRIPT`、`INTEGRATION_IMPLEMENTATION`、`VERIFY_ACCOUNT`、`VERIFY_CASE`；`change_type` 增加 `DELETE`；新增 `change_detail JSON NULL`，存 Platform 审计原有的字段名与摘要，不存值 | Platform 的历史审计不迁移，留在原表 |
| Redis 锁 `external-api-config`（TRY_LOCK） | 同名同模式，使用 channel-connector 的 Redis 前缀 | 本段锁粒度不变 |
| `operationsIdGenerator`（Platform Snowflake，与认证共用） | channel-connector 的 `SnowflakeIdGenerator`（region / worker 独立） | 迁移数据保留原 ID；生成器位不同，新旧 ID 不会冲突 |
| `ExternalApiTransactions`（Platform 事务管理器；写 READ_COMMITTED、查询 REPEATABLE_READ，15s） | channel-connector 的 `TransactionTemplate`，隔离级别与超时不变；去掉 `audit.observeTransaction` | — |
| `PersistenceConfiguration` 中的 MapperScan 与 4 个 TypeHandler | channel-connector 的 MyBatis 配置 | — |
| Platform `/external-api-admin` 日志只记元数据 | channel-connector 已对 `/` 全路径只记元数据 | 无需改动 |

### 3.3 执行链路

```text
Payment ──HTTP──> channel-connector ExecuteChannelOperationProcessor（core，冻结三元组校验，只读快照）
                        └─进程内─> protocol StandardApiExecutionProcessor（事务外）
                                     ├─ CHANNEL_ACCOUNT_CONFIG：Vault Reveal（channel-connector 身份）
                                     ├─ PAYMENT_CARD：card-service 读卡（channel-connector 身份）
                                     └─ ChannelHttpClient ──> 外部渠道
```

- 未发出类失败与现在 Platform Problem 的 NOT_SENT 集合一致：标准 API 不存在、渠道实现不存在、配置无效、初始化失败、预算耗尽、校验失败。其余失败为 ERROR（结果未知）。PAY 输出映射 `PlatformPayOutputs` 改名为 `PayOutputs`，逻辑不变。
- 超时：取消 30s 的 Platform 读超时，流程总预算继续由渠道实现的配置约束（PayerMax 为 25s）；Payment 的 35s 读超时不变。
- 激活检查：`ChangeChannelStatusProcessor` 的"Platform 存在同编码接入方"改为调用 protocol 公开查询"同编码接入方存在"，语义不变。

### 3.4 数据迁移（第一段）

1. **冻结写入**：mor-test 无真实交易。通知运营停止外部 API 配置写入；校验与沙箱运行也一并停止。
2. **导出导入**：用 `mysqldump --no-create-info` 导出 `mor_platform` 的 10 张表，导入 `mor_channel_connector`。结构 DDL 按第 4 节单独执行，ID 与 JSON 原样保留。由 root 一次性人工执行，应用不跨库访问。
3. **核对**：逐表比对行数与 `CHECKSUM TABLE`，抽查 `definition_json`、`flow_definition_json` 的 SHA-256。
4. **Platform 删表**：第一段 mor-test 验收通过后，执行独立的删表 SQL（删表前再备份一次）。验收通过之前保留原表，便于回退。

### 3.5 部署顺序与回退

| 步骤 | 动作 | 回退 |
|---|---|---|
| 1 | 部署 card：读卡允许 `platform-support-service` 与 `channel-connector-service` | 回滚镜像 |
| 2 | 第 3.4 节 1–3 | 目标库 10 表 `TRUNCATE` |
| 3 | 部署 channel-connector 与 ops-bff | 回滚两个镜像；Platform 旧模块仍在，旧表未删 |
| 4 | 回归：PayerMax 沙箱用例重跑、本机或 mor-test 支付执行、44 个管理接口 | — |
| 5 | 部署删除了 integration 的 platform-support，执行删表 SQL | 恢复镜像，并用第 3.4 节第 4 步的备份恢复 10 表 |

## 4. 第一段数据库变更

- **channel-connector**：`db/channel-connector/ddl-phase1.sql`。
  - 10 张表的 DDL 从 `platform-support-schema.sql` 第 804–970 行原样复制，表名、列、索引、注释都不变。
  - `ALTER TABLE t_channel_config_change ADD COLUMN change_detail JSON NULL COMMENT '...'`。
  - PDManer 模型 `channel-connector.pdma.json` 由生成脚本同步。
- **platform-support**：`db/platform-support-service/drop-integration.sql` 删除 10 张表；Platform PDManer 与完整 Schema 同步删除这 10 张表。
- **注册数据**：第一段不涉及。Gateway Route 指向 BFF，BFF 路径不变；`EXTERNAL_API_READ/MANAGE` 权限继续由 Platform 授权。
- **arch-rules**：`table-ownership.yaml` 中 10 张表的归属由 platform-support 改为 channel-connector，`TableOwnershipAutoTest` 的计数随之调整：Platform 57 → 47，channel-connector 18 → 28。

## 5. 第二段：合表、改名与服务渠道

### 5.1 表结构

| 变更 | 内容 |
|---|---|
| `t_payment_channel` → `t_channel` | 新增 `channel_type VARCHAR(16) NOT NULL`（`PAYMENT`/`SERVICE`，创建后不可改）、`description VARCHAR(512) NULL`、`endpoints JSON NULL`、`version INT NOT NULL`、`created_by BIGINT NULL`、`updated_by BIGINT NULL`。`channel_code` 排序规则改为 `ascii_bin`，入口格式 `[A-Z][A-Z0-9_]{1,63}`。`uk_channel_code` 不变 |
| 协议与沙箱表改名 | `t_integration_api` → `t_channel_api`，`t_integration_script` → `t_channel_script`，`t_integration_standard_api` → `t_channel_standard_api`，`t_integration_verify_{account,case,run,run_step}` → `t_channel_verify_{...}`；`integration_id` 改为 `channel_id`，`integration_*_id` 主键改为 `channel_*_id`，唯一索引同步改名。`t_standard_api`、`t_api_field` 不变 |
| `t_integration` | 数据并入 `t_channel` 后删除 |
| `t_channel_account.entity_no` | 改为可空：`PAYMENT` 必填，`SERVICE` 必须为空（应用层校验） |
| 新增 `t_channel_call_grant` | `call_grant_id`、`caller_service VARCHAR(64)`、`channel_id`、`standard_api_id`、`grant_status`（`ACTIVE`/`DISABLED`）、`version`、`created_by`、`updated_by`、`create_time`、`update_time`；唯一 `uk_channel_call_grant(caller_service, channel_id, standard_api_id)` |

不建物理外键和 CHECK，引用关系与取值由应用校验，遵守数据库规范。

### 5.2 数据合并规则

1. `t_integration` 与 `t_payment_channel` 按编码匹配（编码先统一为大写后再比较）：
   - 匹配上的合为一行，**保留 `t_payment_channel.channel_id`**，因为 Payment 冻结的三元组和成本、MID 都引用它；`description`、`endpoints`、`version` 等取自接入方，`channel_type=PAYMENT`。
   - 只有渠道、没有接入方的：`endpoints` 为空。
   - 只有接入方、没有渠道的：新建渠道行，`channel_id` 沿用 `integration_id`，状态为 `DRAFT`；类型在第二段发布清单中逐行列出由你确认，默认 `PAYMENT`。
2. 协议与沙箱表的 `integration_id` 按第 1 步的对应关系改写为 `channel_id`。
3. 沙箱运行历史中的 `case_snapshot_json`、`config_versions_json` 只是历史快照，不改写；其中的旧 ID 在说明中注明。
4. 执行前生成对应关系表供核对，执行后逐表核对行数与引用完整性（每个 `channel_id` 都存在于 `t_channel`）。

### 5.3 渠道规则

| 规则 | 支付渠道 | 服务渠道 |
|---|---|---|
| 账号 | 可多个，必须绑定 OE | 0 或 1 个 ACTIVE，不绑定 OE |
| MID、能力、成本 | 有 | 创建接口一律拒绝（400） |
| 激活前置 | 出站地址已配置（取代"Platform 存在同编码接入方"） | 出站地址已配置 |
| 执行入口 | 只经支付入口 | 只经通用入口，且必须有授权 |
| 沙箱验证 | DRAFT 也可以运行（激活前验证） | 同左 |

## 6. 第二段：新能力

### 6.1 服务渠道凭据

- 沿用 Vault 数据类型 `CHANNEL_ACCOUNT_CONFIG_V1` 与用途 `CHANNEL_CONFIGURATION`（Protect）/ `CHANNEL_EXECUTION`（Reveal），**不新增 Vault 规则**。
- 负载 schema 的 `entityNo` 改为可空，服务渠道为 `null`。channel-connector 的写入和读取校验同步调整，`schemaVersion` 仍为 1，因为 Vault 只校验 JSON 格式、不校验这份 schema。
- 页面与第一段的账号页一致：只写不读，显示末 4 位。

### 6.2 通用执行入口与调用授权

| 接口 | 合同 |
|---|---|
| `POST /channel-connector/v1/service-executions` | 请求 `{channelCode, standardApiCode, input}`，返回 `{executeStatus, outputs}`。请求和响应结构沿用原 Platform 执行合同（[内部接口](../../20260914/platform-support-外部API配置与执行/internal-api.md)第 2、3 节），`integrationCode` 改为 `channelCode` |
| `GET /channel-connector/v1/channel-call-grants?channelId&callerService&page&size` | 分页查询授权 |
| `POST /channel-connector/v1/channel-call-grants` | 新建授权（写必带操作人），同事务记录变更 |
| `POST /channel-connector/v1/channel-call-grants/{id}/status` | 启用或停用授权 |

**通用执行的校验顺序**（任一不满足都不发出外部请求）：
1. 确认调用方身份。
2. 渠道存在、类型为 `SERVICE`、状态为 `ACTIVE`；支付渠道返回 400。
3. 存在 ACTIVE 授权，否则返回 403 `CHANNEL_CALL_NOT_GRANTED`。这是唯一新增的错误码，消费方要区分"未授权"和"配置问题"。
4. 取该渠道唯一的 ACTIVE 账号；没有账号时不提供账号材料。
5. 调用引擎，未发出类失败按第 3.3 节处理。

**调用方身份的确认**：internal-auth 开启时以已验证 Token 的调用方为准，Header 与 Token 不一致返回 400；关闭时信任 `X-Caller-Service`，与 Card、Vault V1 现行的过渡信任一致。生产必须开启 internal-auth，见第 9 节 S2。

### 6.3 支付与服务隔离

- 按渠道类型分开两个执行舱，各有独立的 JDK `HttpClient` 实例和信号量并发上限：
  - `channel-connector.protocol.execution.payment-max-concurrency`，默认 64；
  - `channel-connector.protocol.execution.service-max-concurrency`，默认 16。
- 拿不到许可时立即返回 503，不排队，避免服务渠道的慢调用占满 Tomcat 线程。
- 外部请求超时继续由渠道实现的预算控制。

### 6.4 出站地址防护

参照 `WebhookAddressGuard` 为 `ChannelHttpClient` 增加地址防护：
- DNS 解析结果固定后再连接，拦截回环、私网、链路本地和保留地址。
- 配置 `channel-connector.protocol.execution.trusted-networks`，默认空；本机和 mor-test 的渠道 Mock 地址放在这里。
- 不跟随重定向，这一点现在已经满足。
- 被拦截时按配置无效处理，结果为 NOT_SENT。

### 6.5 对外调用访问日志

每次原子 API 的 HTTP 请求结束后，打一条 `INFO` 结构化日志，字段：`callerService`、`channelCode`、`channelType`、`standardApiCode`、`apiCode`、`executeStatus`、`httpStatus`、`elapsedMs`、`requestId`。不记 URL Query、请求头、请求体和响应体。

沙箱运行另有自己的运行与步骤记录，保持不变。

### 6.6 架构门禁 R15

在 `tools/arch-rules` 新增 R15，两条规则：
- 外部 SDK 依赖只允许出现在白名单模块：AWS SDK（`software.amazon.awssdk`）、MinIO、OkHttp、Apache HttpClient 5，白名单为 platform-support-delivery 与 platform-support-file-document。
- 直接调用 `java.net.http.HttpClient#send/sendAsync` 只允许出现在 channel-connector-protocol 的 `ext/outbound`。

经 `HttpServiceClientFactory` 构造的内部 RestClient 不受影响。白名单与第 1.1 节的例外清单一一对应，新增例外须同步修改两处。

门禁只能拦截常见写法，不能替代代码评审和网络层出口强制（Q6 已决定另立需求）。

## 7. 第二段：BFF 与页面

- **BFF**：
  - 新增 `/channel-admin/v1/channel-call-grants`（3 个）和 `PUT /channel-admin/v1/channels/{id}/endpoints`（1 个）。
  - 下线 `/external-api-admin/v1/integrations` 的 5 个 CRUD；其余外部 API 管理接口以 `{code}` 作为渠道编码，路径保持不变。
- **注册数据**：在第二段开工时读取 mor-test 线上 `mor_platform` 后选定 ID 块。
  - 新增权限 `CHANNEL_CALL_GRANT_MANAGE`，绑定 `PLATFORM_ADMIN`；
  - 新增 4 条 Route 及其请求头和 Guard；
  - 停用 5 条接入方 CRUD Route；
  - 停用 `OPERATIONS_EXTERNAL_INTEGRATIONS` 页面资源。
- **页面**：
  - 渠道列表增加类型筛选，新建时选择类型。
  - 渠道详情页签：基本信息（类型、出站地址）、技术账号、MID（仅支付渠道）、协议（原子 API、脚本、标准 API 实现）、沙箱验证、调用授权（仅服务渠道）、变更记录。
  - 原接入方页的组件迁入对应页签；旧路由 `/external-api/integrations/:code/...` 跳转到对应渠道详情。
  - 「标准 API」页保留在「渠道」菜单下。
  - 第二段研发前先产出 Pencil 画板供确认，确认后再研发。

## 8. 研发与验证计划

| 段 | 线 | 内容 | 验证 |
|---|---|---|---|
| 一 | P1 channel-connector | 新增 protocol 模块搬迁、第 3.2 节替换、执行改为进程内、10 表 DDL、`change_detail` | Platform 现有 integration 测试（约 30 个类）迁到 channel-connector-test 后全部通过；新增执行链路入口测试（Payment 入口 → 引擎 → 渠道 Mock，含读卡与 Vault Mock） |
| 一 | P2 Platform / Card / BFF / arch | Platform 删除模块与表；Card 同时允许两个调用方；BFF 7 个 Client 改指 channel-connector；arch-rules 表归属 | 各服务全量 auto；`ExternalApiBrowserAutoTest` 等 BFF 测试改用 channel-connector 模拟端 |
| 一 | 本机 e2e | 用本机迁移后的数据跑 PayerMax 沙箱用例与支付执行 | 与迁移前结果一致 |
| 二 | Q1 合表改名 | 第 5 节 DDL 与合并 SQL、DataObject 与 Mapper 改名、渠道规则 | 合并 SQL 用 ROLLBACK 试跑并核对；入口测试覆盖两类渠道规则 |
| 二 | Q2 通用入口与安全 | 第 6 节全部 | 授权 / 类型 / 状态 / 账号的场景矩阵（AC-05、06）；隔离（AC-09）；防护拦截私网；日志字段；R15 正反用例 |
| 二 | Q3 BFF / Web / 注册 | 第 7 节 | BFF auto、前端单测与构建、浏览器走查 |

执行由 Opus 5.5 agent 完成，主会话负责设计与把关。每段研发后都按[写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)第 11 节自查。

验收映射：AC-01–03 → 第一段；AC-04–11、AC-V01–V03 → 第二段；AC-12 → 两段都要满足。

## 9. 设计中的取舍（请确认）

| 编号 | 取舍 | 建议 |
|---|---|---|
| S1 | 外部 API 管理接口迁移后，Owner 不再逐 Processor 复核会话和权限，改为"Gateway 校验 Route 权限 + BFF 实时调 PLT-I02"，与现有渠道管理一致。另一个选项是 channel-connector 也回调 Platform 复核，但这样会把对 Platform 的依赖带回管理链路 | 采用一致做法；作为补偿，第二段把管理接口的调用方限制为 `operations-console-bff`（internal-auth 开启时生效） |
| S2 | 通用入口在 internal-auth 关闭时信任 `X-Caller-Service` 请求头 | 接受作为过渡（与 Card、Vault V1 一致），生产前必须开启 internal-auth。mor-test 是否开启在第二段发布清单中确认 |
| S3 | 第一段保持原表名，第二段再合表改名（数据迁移两次） | 采用。第一段只做纯搬迁，出了问题容易定位；第二段改名和语义变化一起做 |
| S4 | Platform 的历史审计事件不迁移 | 采用。历史仍可在 Platform 审计查询中查到，新变更记录在渠道变更记录里 |
| S5 | 沙箱验证账号的凭据仍以明文 JSON 存库（现状如此，只用于沙箱） | 本需求不改，登记为遗留项 |
| S6 | 第一段 Card 同时允许两个调用方 | 采用，便于第一段回退；第二段收回 Platform 的权限 |

## 10. 阶段 4 及以后需要的授权（本阶段不执行）

- 本机 SQL：在 `mor_channel_connector` 执行第一段 DDL 并导入数据，在 `mor_platform` 删除 10 张表。开工时给出确切文件并请求授权。
- mor-test 的备份、SQL、镜像替换与回归按阶段 6 清单单独确认。
- 提交仍使用私有索引，单独确认。

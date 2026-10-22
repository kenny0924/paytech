# P2 报告：第二段（合表改名、服务渠道、通用入口与安全；后端 + BFF + 注册）

worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-ccu2`。依据：任务卡 [P2.md](P2.md)、[technique.md](../technique.md) 第 5–8 节、[P1-report.md](P1-report.md)。

## 1. 结论

- 任务卡第 1–6 节的后端、BFF、注册数据、架构门禁与迁移 SQL 均已实现。`-am test-compile` 通过的模块：channel-connector（app、test）、card（app、test）、platform-support（app、test）、operations-console-bff（app、test）、tools/arch-rules。
- 不依赖数据库的测试已实际运行并全部通过（第 6 节）。依赖数据库的入口测试**未运行**，原因是约束禁止连接数据库。第 7 节给出 `_ccu2` 隔离库的初始化与运行命令。
- 没有执行任何 SQL，没有连接任何数据库，没有执行 install、deploy、提交或建分支，没有改动 `operations-console-web/`。

## 2. 改动清单（按系统）

### 2.1 数据库模型与迁移

- 模型与生成：
  - 新增 `scripts/pdmaner/channel-connector/channel-model.mjs`，用于 `t_channel` 与 `t_channel_call_grant`。
  - 修改 `protocol-tables.mjs`、`protocol-model.mjs`、`generate-channel-connector-model.mjs`。
  - 重新生成 `paytech/channel-connector/docs/database/channel-connector.pdma.json` 与 `docs/database/sql/channel-connector-schema.sql`，共 28 张表。
  - 测试 `init/ddl.sql` 中的 `t_channel` 段逐字复制自 schema。
- 改名对照：
  - `t_payment_channel` 与 `t_integration` 合并为 `t_channel`，新增列 `channel_type`、`description`、`endpoints`、`version`。
  - 7 张 `t_integration_*` 表改名为 `t_channel_api`、`t_channel_script`、`t_channel_standard_api`、`t_channel_verify_account`、`t_channel_verify_case`、`t_channel_verify_run`、`t_channel_verify_run_step`。
  - `integration_id` 列一律改为 `channel_id`。
  - 新增 `t_channel_call_grant`，唯一键 `uk_channel_call_grant`（channel_id、caller_service、standard_api_code）。
  - `t_channel_account.entity_no` 改为可空。
  - 列注释按卡修正：`t_standard_api` 改为指向 `t_channel_call_grant`；`t_api_field.api_id` 改为指向 `t_channel_api`；去掉"Platform Support 数据库"的旧表述。
- 节点测试：
  - `channel-connector-protocol-model.test.mjs` 重写，9 个用例；`payment-channel-query.test.mjs` 同步。
  - `payment-model.test.mjs` 与 `payment-scenario-doc.test.mjs` 原先直接读取 channel-connector 模型文件，现改为读取生成器的表分区（偏差 X10）。
- 迁移 SQL，位于 `db/channel-connector/`：`phase2-10-precheck.sql`、`phase2-20-migrate.sql`、`phase2-30-verify.sql`、`phase2-rollback.md`，说明见第 8 节。
- 表归属：`paytech/tools/arch-rules` 的 `generate-table-ownership.mjs`、`table-ownership.yaml` 与 `TableOwnershipAutoTest` 已同步，channel-connector 仍为 28 张表（减 `t_integration`，加 `t_channel_call_grant`，其余改名）。

### 2.2 channel-connector

- **api**
  - `PaymentChannelApi` 改名为 `ChannelApi`。
    - 列表增加 `channelType` 过滤。
    - 新增 `PUT /{channelId}/endpoints`。
    - 创建请求要求 `channelType`（PAYMENT 或 SERVICE），可带 `description`；更新请求整组替换名称与描述。
    - 响应增加 `channelType`、`description`、`endpoints`、`version`；摘要增加 `endpointCount` 与 `activeGrantCount`（支付渠道为 null）。
  - 新增 `grant/ChannelCallGrantApi`，提供列表、创建、状态变更，带对应 DTO。
  - 新增 `serviceexecution/ServiceChannelExecutionApi`，路径 `POST /channel-connector/v1/service-executions`。
  - 新增枚举 `ChannelType`、`ChannelCallGrantStatus`、`ServiceExecutionErrorCode`。
  - 新增 `ChannelConnectorHeaders.CALLER_HEADER`。
  - `ChannelConfigChangeApi` 的 targetType 白名单扩展到 12 种（D9）。
  - `CreateChannelAccountRequestBody.entityNo` 去掉 `@NotBlank`。
  - 删除 `IntegrationManagementApi` 及 6 个 DTO。`StandardApiReference` 保留，BFF 的转换器仍在使用。
- **protocol**
  - `Integration*` 系列的 DO、Mapper、Repository、Service 改名为 `Channel*`，XML 中的表名与列名同步修改。
  - 新增渠道身份的完整链路：`Channel` DO，`ChannelMapper` 及 XML，`ChannelRepository` 及实现，`ChannelService` 及实现。
  - 新增授权链路：`ChannelCallGrant` 的 DO、Mapper、Repository，`ChannelCallGrantService` 及实现。
  - 新增领域对象：`ChannelIdentity`、`ChannelSummary`、`ChannelCallGrantView` 等。
  - 新增锁常量 `core/constant/ChannelLocks`。
  - 新增 Processor：`CreateChannel`、`UpdateChannel`、`UpdateChannelEndpoints`、`CreateChannelCallGrant`、`ChangeChannelCallGrantStatus`。
  - `DeleteImplementationProcessor`：存在 ACTIVE 授权时返回 409。
  - `ChangeTargetType` 新增 `CHANNEL_API`、`CHANNEL_SCRIPT`、`CHANNEL_IMPLEMENTATION`、`CHANNEL_CALL_GRANT`。`INTEGRATION` 仅保留用于读取历史记录。
  - 执行链路：
    - `ExecutionSnapshot` 带 `channelCode` 与 `channelType`；`ExecuteStandardApiRequest` 带 `callerService`。
    - 执行前依次做 DNS 地址防护和执行舱许可检查。
  - 新增 `ext/outbound/ChannelAddressGuard` 与 `ChannelExecutionCompartments`。`ChannelHttpClient` 按渠道类型使用独立的 HttpClient，发送前再校验一次地址。
  - `AtomicApiExecutor` 输出访问日志，logger 名 `channel.outbound.access`。
  - `IntegrationEndpointValidator` 在保存和加载时都经过地址防护。
  - 新增执行配置：`payment-max-concurrency`、`service-max-concurrency`、`trusted-networks`。
  - 删除接入方的 CRUD：Processor、Request、View，以及 `IntegrationLookupService`、`IntegrationManagementService`。
  - 代码与注释中的"接入方"统一改为"渠道"。
- **core**
  - 删除 `PaymentChannel*`，改为调用 protocol 的 `ChannelService`。
  - 状态激活要求出站地址非空。
  - 账号规则：
    - 创建时校验 OE：PAYMENT 必须带 OE，SERVICE 必须不带。
    - 激活服务渠道账号时先锁渠道，已有 ACTIVE 账号则返回 409。
  - SERVICE 渠道创建 MID、能力组、成本方案一律返回 400。
  - 支付入口 `ExecuteChannelOperationProcessor` 遇到 SERVICE 渠道直接拒绝，见第 4 节。
  - Vault 负载允许 `entityNo` 为 null，写入和读取两侧同步调整，`schemaVersion` 不变。
  - 新增 `ListChannelsProcessor`、`ListChannelMidsProcessor`、`ListChannelCostPlansProcessor`，并去掉 MID 与成本方案 XML 中对 `t_payment_channel` 的 JOIN。
  - 新增 `ExecuteServiceChannelProcessor`。
- **app**
  - Controller：`ChannelController`（改名），新增 `ChannelCallGrantController`、`ServiceChannelExecutionController`。
  - 新增 `ChannelCallerResolver`，逻辑同 `CardCallerResolver`，另加调用方格式校验。
  - 删除 `IntegrationManagementController`。
  - 错误映射新增 `ServiceExecutionErrorCode`。
  - `application.yml` 与 `config/nacos/channel-connector-service.yaml` 增加新的配置键。
- **test**
  - 改名：`ChannelApiTest`。
  - 新增：`ChannelEndpointsApiTest`、`ChannelCallGrantApiTest`、`ServiceChannelRulesTest`、`auto/serviceexecution/ServiceChannelExecutionTest`、`ExecutionCompartmentIsolationTest`、`ChannelAddressGuardTest`（22）、`ChannelExecutionCompartmentsTest`。
  - `ExecuteChannelOperationTest` 增加服务渠道拒绝用例和访问日志断言。
  - 删除 `IntegrationManagementTest`。
  - 基础设施：新增 `AccessLogProbe`、`ServiceChannelSamples`、`AbstractServiceExecutionTest`；`ExternalApiMocker`、`ChannelDataMocker`、`ChannelTestMapper` 等同步修改。
  - `application-mock.yml`：`service-max-concurrency: 2`，`trusted-networks: [127.0.0.1/32]`。
  - `ChannelConnectorArchitectureTest` 增加表归属断言。
- 同步修改 README，以及 `verification/local-e2e/run-service.sh`（见第 5 节）。

### 2.3 card（子 agent）

- `RevealChannelCardMaterialProcessor` 只允许 `channel-connector-service`，删除过渡注释；`CardApiConstants`、`ChannelCardMaterialApi`、`RevealCardMaterialRequest` 的文档同步。
- `ChannelCardMaterialRevealTest.test_platformCallerRejected` 改为断言 Platform 调用方返回 404；`CardInternalAuthRequiredTest` 同步。

### 2.4 tools/arch-rules（子 agent）

- 新增 `ExternalEgressRule`（R15），注册到 `ArchitectureRules`。
- 新增 `ExternalEgressRuleAutoTest`，共 5 个用例，包含故意违规的反例；配套夹具和 `software.amazon.awssdk.fixture.SdkClientStub`。
- Reactor 测试新增 `test_externalSdkDependency`。
- 新增决定附件 [`agent-docs/decisions/2026-10-22-unified-outbound-exception-list.md`](../../../../agent-docs/decisions/2026-10-22-unified-outbound-exception-list.md)，并在 2026-10-20 决定中加入链接。

### 2.5 Platform 注册数据（子 agent，块 129）

- `platform-support-initial-data.sql` 末尾追加块 129，ID 均为 18 位 `900000000000129xxx`：
  - 权限 129101 `CHANNEL_CALL_GRANT_MANAGE`（资源 CHANNEL_CALL_GRANT/MANAGE，等级 HIGH）。
  - 角色授权 129201，授予 `PLATFORM_ADMIN`。
  - 按钮资源 129301，父节点 92302。
  - 4 条 Route：

    | ID | Route | 权限 |
    |---|---|---|
    | 129401 | list | 92101 读取 |
    | 129402 | create | 129101 |
    | 129403 | status | 129101 |
    | 129404 | endpoints PUT | 92102 管理 |

  - 请求头 129501–129520，查询参数 129001–129007（其中 129007 是给 Route 92401 增加的 `channelType`），Guard 129701–129724。
  - 停用 Route 71006–71010 与资源 71303、71305，菜单 92302 改名为"渠道"。
- 需求目录新增 `db/platform-support-service/phase2-registration.sql`：
  - 第 1 段为只读检查，第 2 段与块 129 相同，第 3 段为 mor-test 激活。
  - 文件头注明执行后必须重新发布 Gateway Snapshot，并附回退方式。
- `console-resource-capabilities.json` 去掉 `OPERATIONS_EXTERNAL_INTEGRATIONS` 组件。Platform 的 Java 测试不需要修改：测试 DDL 不加载初始数据，也没有统计注册行数的断言。

### 2.6 operations-console-bff（子 agent）

- `ChannelConnectorClientConfiguration` 改为注册 `ChannelApi` 与 `ChannelCallGrantApi`，去掉 `IntegrationManagementApi`。
- `ChannelConfigurationController` 新增 4 个转发接口；渠道列表透传 `channelType`；变更记录的 targetType 白名单为 12 种。
- 删除接入方的 CRUD Controller、Client、Models 与转换方法。
- 对前端的 JSON 合同与任务卡逐字段一致：endpoints 为空时返回 `[]`，不返回 null；sandbox 缺省为 false。BFF 校验 Owner 回执，不一致时返回 502。
- 测试：`channel-admin-boundary.json` 共 34 条，新增 H06、R01–R03；`external-api-boundary.json` 删除 E06–E10 后共 39 条；`ChannelBrowserAutoTest` 新增 5 个用例；README 同步。

## 3. 与任务卡或 technique 的偏差及理由

| 编号 | 偏差 | 理由与影响 |
|---|---|---|
| X1 | 执行时的"解析并固定地址"做法：先解析域名并校验每个结果，再依靠 JVM `InetAddress` 缓存，让 HttpClient 连接时取到同一结果。没有做到套接字级固定 | JDK `HttpClient` 不能指定连接用的 IP（Platform 的 `WebhookAddressGuard` 用的是 HttpClient5 的 DnsResolver）。如果在校验与连接之间缓存恰好过期且 DNS 改变，存在很小的窗口。仍保留：不跟随重定向、发送前再校验一次。真正的出口强制要靠网络层（需求 Q6） |
| X2 | 出站地址的地址类错误返回 **422**（字段错误 `endpoints[i].baseUrl`），没有用 400 | 与同接口的静态校验保持同一种错误外形，前端可以定位到具体行。BFF 与 P2-web 已按 422 处理 |
| X3 | 支付入口访问日志中的 `callerService` 固定为 `payment-service` | 支付入口现有合同不带调用方，唯一调用方是 Payment。沙箱验证为 `VERIFY`，通用入口为解析出的真实调用方 |
| X4 | protocol 中与 Wire 合同绑定的 Processor、Request、View 名字保留 `Integration*`，例如 `CreateIntegrationApiProcessor`、`IntegrationChildSupport` 和 `integrationCode` 请求字段 | 这些名字与 `/external-api-admin/v1/integrations/{code}/…` 的 Wire 一一对应，改名会牵动 BFF 与前端合同；DO、Mapper、Repository、Service 与表已全部改名。历史快照的键 `integration` / `integrationCode` 和 `FlowDefinitionValidator` 的摘要字段 `integrationApis` 保持不变，因为历史数据不改写 |
| X5 | 注册函数 `ChannelAccountConfigFunction` 的 `entityNo` 参数改为非必填 | 服务渠道的凭据没有 OE。支付渠道仍然必须有 OE，由账号写入校验和 Vault 负载保证 |
| X6 | 沙箱验证账号 `t_channel_verify_account.entity_no` 仍为必填字符串 | 卡片没有要求修改；服务渠道的沙箱验证账号目前需要填一个占位值。是否放开留给后续需求决定 |
| X7 | 新建授权使用锁 `channel-call-grant:{channelId}`，删除标准 API 实现使用 `external-api-config` 锁，两者不互斥 | 并发时可能出现"实现刚删除、授权刚建成"。后果是执行时按"实现不存在"处理，结果 NOT_SENT，不会发出请求；授权可以由 Owner 停用。要彻底消除需要多锁组合，留作后续 |
| X8 | 服务渠道"最多一个 ACTIVE 账号"由激活时的渠道锁加先查后写保证，没有唯一索引 | 部分唯一索引需要生成列，模型规范没有这种形态。锁租约失效的极端情况只能靠复核发现 |
| X9 | R15 白名单在 delivery 与 file-document 之外，又加了 `paytech-acquiring-app` 与 `paytech-counterparty-app`（只放行 Apache HttpClient 5，即 I1、I2） | 盘点发现这两个应用用 HttpClient5 作为内部 RestClient 的传输层，目标全部是 `lb://` 内部服务，不是对外出口，已在例外清单中逐项说明 |
| X10 | DB 子 agent 修改了 `payment-model.test.mjs`、`payment-scenario-doc.test.mjs`（不属于本卡范围） | 两者原先直接读取 channel-connector 模型中的 `t_payment_channel`，合表后会失败；现改为读取生成器的表分区，断言意图不变 |
| X11 | Platform ID 使用 18 位（`900000000000129xxx`）；PUT endpoints 的请求体上限设为 65536 | 卡片第 5 节要求 18 位格式。16 项地址再加 URL 可能超过默认上限 |
| X12 | 新增 `failureCode=CHANNEL_EXECUTION_BUSY`（NOT_SENT，503），会出现在支付入口的结果中 | 卡片要求执行舱满时支付入口结果为 NOT_SENT，现有 NOT_SENT 原因中没有合适的值。Payment 的 `PayActionActor` 对任何 NOT_SENT 都按失败处理并透传 failureCode，不需要改 Payment，但 Payment 侧没有针对这个值的测试 |
| X13 | 被地址防护拦截的请求仍会写一条访问日志，其中 `httpStatus` 为 null，`executeStatus` 为 NOT_SENT | 便于排查被拦截的配置；日志不含 URL |
| X14 | 新建渠道（`CreateChannelProcessor`）不加锁 | 并发由唯一键 `uk_channel_code` 兜底，与原实现一致 |
| X15 | 技术方案 S1 提到的"Owner 级调用方限制"（例如只允许 BFF 调用渠道写接口）没有实现 | 卡片没有列出这一项；内部认证仍按现有方式处理 |

## 4. 关键口径（卡片要求在报告中写明）

- **支付入口遇到 SERVICE 渠道**：`ExecuteChannelOperationProcessor` 在只读校验阶段拒绝，结果为 `NOT_SENT` + `INTEGRATION_CONFIG_INVALID`，与现有"配置无效"的口径一致，外部请求数为 0（`ExecuteChannelOperationTest.test_serviceChannelRejected`）。
- **通用入口的校验顺序与状态码**：

  | 顺序 | 校验 | 失败结果 |
  |---|---|---|
  | 1 | 调用方：Token 优先；Header 与 Token 不一致 400；内部认证关闭时信任 `X-Caller-Service`；缺失 400 | 400 |
  | 2 | 渠道存在 | 404 |
  | 3 | 渠道为 SERVICE 且 ACTIVE | 400 |
  | 4 | 存在 ACTIVE 授权 | 403 `CHANNEL_CALL_NOT_GRANTED` |
  | 5 | 账号：取唯一 ACTIVE 账号，注入为 `input.channelAccount`，不含 entityNo；调用方自带的 `channelAccount` 会被移除；没有账号就不提供账号材料 | — |
  | 6 | 在事务外执行 | — |

  未发出类失败映射为 Problem：

  | 原因 | 状态码 |
  |---|---|
  | 标准 API 不存在 | 404 |
  | 实现不存在、配置无效、初始化失败、预算耗尽 | 500 |
  | 执行舱忙 | 503 |

  其余失败返回 `executeStatus=ERROR`。所有校验失败时外部请求数为 0。
- **执行舱**：各自独立的信号量与 HttpClient，拿不到许可时立即失败，不排队。

  | 入口 | 拿不到许可时 |
  |---|---|
  | 通用入口 | 503 `CHANNEL_EXECUTION_BUSY` |
  | 支付入口 | NOT_SENT |
  | 沙箱验证 | 步骤结果为 PROBLEM 503 |

## 5. 本机与 mor-test 的 Mock 地址配置

| 环境 | 配置位置 | 值 | 说明 |
|---|---|---|---|
| 自动测试 | `paytech-channel-connector-test/src/test/resources/application-mock.yml` | `trusted-networks: [127.0.0.1/32]`、`service-max-concurrency: 2` | 测试用 HTTP 端点位于 127.0.0.1 |
| 本机端到端 | `verification/local-e2e/run-service.sh` | 环境变量 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_TRUSTEDNETWORKS=127.0.0.1/32` | 本机 PayerMax Mock 地址为 `http://127.0.0.1:18190` |
| mor-test | `paytech/channel-connector/config/nacos/channel-connector-service.yaml` | `trusted-networks: []`（默认） | mor-test 没有渠道 Mock，NI、Geidea、PayerMax 都使用公网 UAT 地址。**发布前**需确认 mor-test 库中已有渠道的出站地址里没有私网或回环地址，否则保存会被拦截、执行结果为 NOT_SENT |

## 6. 实际运行的验证（worktree，JDK 25，`-o`）

| 命令或范围 | 结果 |
|---|---|
| `./mvnw -o -q -pl channel-connector/…-app,…-test,card/…-app,…-test,platform-support/…-app,…-test,operations-console-bff/…-app,…-test,tools/arch-rules -am test-compile` | 通过 |
| tools/arch-rules 全部测试（含 R15 正反用例与表归属） | 68/68 |
| `ReactorArchitectureAutoTest`（含 `test_externalSdkDependency`） | 4/4 |
| channel-connector：`ArchitectureAutoTest`、`ChannelConnectorArchitectureTest`、领域与纯 Java 测试（含 `ChannelAddressGuardTest` 22、`ChannelExecutionCompartmentsTest` 1） | 77/77 |
| platform-support `ArchitectureAutoTest` | 1/1 |
| card `ArchitectureAutoTest` | 1/1 |
| operations-console-bff 全量 | 2025/2025 |
| `node --test scripts/pdmaner/channel-connector-protocol-model.test.mjs scripts/pdmaner/payment-channel-query.test.mjs` | 10/10 |
| `node scripts/pdmaner/generate-channel-connector-model.mjs --check` | 通过（28 张表） |
| `node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check` | 通过 |
| payment-model、payment-scenario-doc、card-model 节点测试 | 各有 1 个失败，与 HEAD 相同，属于既有问题，与本卡无关 |

## 7. 未完成项

1. **依赖数据库的入口测试未运行**：channel-connector 全量（含新增的渠道、出站地址、授权、服务渠道规则、通用执行、执行舱隔离等）、card 读卡入口，以及可选的 platform-support auto。命令见第 9 节，需要主会话执行。
2. **迁移 SQL 未演练**。建议在 mor-test `mor_channel_connector` 的 mysqldump 副本上按第 8 节顺序演练，再在 mor-test 执行。
3. 文档未同步：
   - `CURRENT_IMPLEMENTATION.md`：实施与部署状态应在上线后由主会话登记。
   - `PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md` 中对 `t_payment_channel` 的描述。
   - Platform 数据库文档中对 integration 的描述。
   - `t_channel_callback` 等表注释中的"Payment 管理的支付服务商"字样。
4. 模型没有给 `t_channel_call_grant` 画到 `t_standard_api` 的关系线，因为会多出一个不需要的索引；应用层已校验标准 API 已被该渠道实现。
5. 块 71 的激活语句如果重新执行，会把已停用的 5 条接入方 Route 重新启用。mor-test 只执行 `phase2-registration.sql`，不要重放块 71。
6. 偏差 X6、X7、X8、X15 的后续是否处理，需要 Owner 决定。
7. `/channel-connector/v1/config-changes` 仍然要求 `targetType` + `targetId`，不支持按 `channelId` 查询（见第 13 节第 4 项）。

## 8. 迁移 SQL 说明与预期核对结果

执行顺序：备份 → `phase2-10-precheck.sql`（只读）→ `phase2-20-migrate.sql` → `phase2-30-verify.sql`（只读）→ 全部符合后手工删除两张辅助表。三个文件都写死了 `USE mor_channel_connector`，在副本库演练时需要先用 sed 替换库名。

- **precheck**，第 1–5 段期望都是 0 行：
  1. 编码格式，要求 `[A-Z][A-Z0-9_]{1,63}`。
  2. 同一张表内转大写后重复。
  3. 两张表之间转大写后相同但原字节不同。
  4. ID 冲突，包括接入方原 ID 等于另一个合并后渠道的 ID。
  5. 悬挂的 `integration_id`。

  第 6 段输出对应关系表，`match_type` 取 MATCHED、INTEGRATION_ONLY 或 CHANNEL_ONLY。第 7 段为提示：迁移前各表行数，以及无法映射的历史 INTEGRATION 变更记录。
- **migrate**：
  1. 守卫：按 precheck 第 1–5 段的条件重新计数，不为 0 就故意触发主键冲突，mysql 客户端随即终止。
  2. 建持久辅助表 `tmp_phase2_channel_map` 与 `tmp_phase2_row_count`。
  3. 改结构：改名、列、索引、注释。这一步会隐式提交。
  4. 在一个事务内完成合并与引用改写。
     - 按大写匹配。MATCHED 保留支付渠道的 `channel_id`，说明、出站地址、版本取自接入方。
     - INTEGRATION_ONLY 新建为 DRAFT、PAYMENT 的渠道，`channel_id` 沿用 `integration_id`。
     - 其余渠道 `version=1`。
     - 变更记录：`INTEGRATION_*` 改为 `CHANNEL_*`；能映射的 `INTEGRATION` 记录改为 `CHANNEL` 并改写 target_id，不能映射的保留原值。
     - 历史快照 JSON 不改写。
  5. 收尾：去掉回填用的默认值，删除 `t_integration`。
- **verify 期望**：
  1. `table_count=28`，`legacy_count=0`。
  2. 每张表迁移前后行数相等，`t_channel_call_grant` 为 0 行。
  3. 引用完整性 0 行。
  4. 对应关系与 precheck 第 6 段逐行一致，`problem` 列全部为 NULL，`t_channel` 中没有对应关系之外的行。
  5. 变更记录不再出现 `INTEGRATION_API`、`INTEGRATION_SCRIPT`、`INTEGRATION_IMPLEMENTATION`；剩余 `INTEGRATION` 的行数等于 precheck 第 7 段 `unmapped_integration_rows` 的合计。
  6. 结构：
     - `t_channel` 共 12 列，顺序与 schema 一致，`channel_code` 为 ascii_bin，`channel_type`、`version` 没有默认值。
     - `entity_no` 可空，任何表都没有 `integration_*` 列。
     - 新索引存在，旧索引（`uk_payment_channel_code`、`uk_integration_*` 等）不存在。
- **回退**：DDL 不能在事务中回滚，所以只能从执行前的备份恢复，见 `phase2-rollback.md`。
- **Platform 注册**：执行 `db/platform-support-service/phase2-registration.sql`，先看第 1 段只读检查，再执行第 2 段，mor-test 再执行第 3 段激活，完成后重新发布 Gateway Snapshot。

## 9. 主会话执行：隔离库初始化与测试运行

> 先确认本机 MySQL 13306、Redis 16379 已启动；card 的测试还需要 RabbitMQ 5672。隔离库名加后缀 `_ccu2`，测试基线的 D12 守卫只接受精确库名或 `<库名>_{后缀}`，所以不会碰共享的 `mor_*` 库。

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR-ccu2/paytech
set -a; . /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a
MYSQL='docker exec -i -e MYSQL_PWD='"$MOR_MYSQL_ROOT_PASSWORD"' mor-local-mysql-1 mysql -uroot'

# 1) 初始化隔离库（建库、本库授权、全量建表）
sed 's/mor_channel_connector/mor_channel_connector_ccu2/g' \
  channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql | eval $MYSQL
sed 's/mor_card/mor_card_ccu2/g' card/paytech-card-test/src/test/resources/init/ddl.sql | eval $MYSQL
# 可选：Platform（删除第 4 行 DROP DATABASE）
sed -e '/^DROP DATABASE/d' -e 's/mor_platform/mor_platform_ccu2/g' \
  platform-support/paytech-platform-support-test/src/test/resources/init/ddl.sql | eval $MYSQL
# 核对表数：28 / 8 / 47
for db in mor_channel_connector_ccu2 mor_card_ccu2 mor_platform_ccu2; do
  echo "$db: $(echo "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db';" | eval $MYSQL -N)"; done

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
LOCALENV=-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
JDBC='?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'

# 2) channel-connector 全部测试
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_channel_connector_ccu2$JDBC" REDIS_KEY_PREFIX=channel-connector-ccu2 \
  ./mvnw -o -pl channel-connector/paytech-channel-connector-test -am test $LOCALENV \
  -Dtest='com.paytech.channel.connector.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 3) card：读卡入口与内部认证
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_card_ccu2$JDBC" REDIS_KEY_PREFIX=card-ccu2 \
  ./mvnw -o -pl card/paytech-card-test -am test $LOCALENV \
  -Dtest='ChannelCardMaterialRevealTest,CardInternalAuthRequiredTest' -Dsurefire.failIfNoSpecifiedTests=false

# 4) 可选：platform-support auto（确认 console-resource-capabilities.json 修改后其余能力不受影响）
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/mor_platform_ccu2$JDBC" REDIS_KEY_PREFIX=platform-support:ccu2 \
  ./mvnw -o -pl platform-support/paytech-platform-support-test -am test $LOCALENV \
  -Dtest='com.paytech.platform.support.auto.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false

# 5) 用完删除隔离库（可选）
echo 'DROP DATABASE mor_channel_connector_ccu2; DROP DATABASE mor_card_ccu2; DROP DATABASE mor_platform_ccu2;' | eval $MYSQL
```

迁移演练（可选，在副本上执行，不碰 mor-test 原库）：

```bash
# 在 mor-test 主机：mysqldump mor_channel_connector > before.sql；建副本库 mor_channel_connector_rehearsal 并导入
for f in phase2-10-precheck phase2-20-migrate phase2-30-verify; do
  sed 's/USE mor_channel_connector;/USE mor_channel_connector_rehearsal;/' \
    requirements/20261020/channel-connector-统一对外渠道出口/db/channel-connector/$f.sql | mysql -uroot -p; done
```

## 10. 写接口示例第 11 节自查

新写入口包括：创建渠道、修改渠道、出站地址、新建授权、授权状态变更；以及改造过的入口：渠道状态、账号创建与激活、MID、能力、成本方案创建、删除实现。通用执行入口不写库。

| 检查项 | 结果 |
|---|---|
| API 与 Body | DTO 均为 class，带中文字段注释和 Hibernate Validator；写接口必带操作人请求头。`channelType` 只出现在创建请求中，更新请求不接受。通用执行入口的调用方由 `ChannelCallerResolver` 解析 |
| Controller / Converter 只做适配 | `ChannelController`、`ChannelCallGrantController`、`ServiceChannelExecutionController` 只做转换和委托 |
| Processor 只访问本模块的 Service / Repository | protocol 的 Processor 调用本模块 Service；core 经 protocol 公开的 `ChannelService` 访问渠道身份。架构测试断言 core 不访问 protocol 的 Repository、Mapper、DataObject、`*Impl`，并新增表归属断言 |
| Context 与 DataObject | 新增 `UpdateChannelContext`、`UpdateChannelEndpointsContext`、`ChangeChannelCallGrantStatusContext`，持有领域对象；HTTP 与跨模块边界不暴露 DO（R9 通过） |
| Service 不互调 | 渠道写入与变更记录由 Processor 在同一个本地事务中依次调用本模块 Service（core 继续用 D1 的 `ChannelConfigTransactions`） |
| 事务与外部调用 | 写入都是只访问本库的短事务。Vault Protect 在锁内、事务外；通用执行先做只读校验，再在事务外执行；DNS 解析只在执行前做，不在事务中。R7 通过 |
| 条件 SQL 与行数 | 出站地址按 `version` CAS，授权状态按 `version` + 原状态 CAS，渠道状态带 from 条件，都检查影响行数，0 行时返回 409 |
| 锁 | 授权的新建与启停用 `AbstractLockProcessor` + TRY_LOCK，Key 为 `channel-call-grant:{channelId}`。修改渠道、账号激活用 `channel:{id}`（tryLocks，10s / 60s）。出站地址与删除实现用 `external-api-config`，与原子 API 编辑互斥，以保护地址引用。数据库保护：唯一键加 CAS。残留问题见 X7、X8 |
| 原请求比较与防重 | 没有业务防重；授权的重复组合由唯一键返回 409，原因含现有状态与授权 ID；渠道编码重复返回 409 |
| Idempotency-Key | 不涉及，没有引入幂等组件 |
| 变更记录 | 每次写入都在同一事务中写一条：渠道写 `CHANNEL`（出站地址为 `CONFIGURATION`，只记 `ENDPOINTS_DIGEST`，不含地址原文），授权写 `CHANNEL_CALL_GRANT` |
| 配置、Mock、多余抽象 | 新配置集中在 `ExternalApiExecutionProperties`。生产代码中没有 Mock 分支，也没有伪造成功。新增抽象只有地址防护、执行舱和授权链路 |
| 测试 | 覆盖矩阵：授权 / 类型 / 状态 / 账号且外部请求数为 0（`ServiceChannelExecutionTest`）、执行舱隔离（`ExecutionCompartmentIsolationTest`）、地址防护（`ChannelEndpointsApiTest`、`ChannelAddressGuardTest`）、访问日志字段及不含报文与凭据（`AccessLogProbe`）、R15 正反用例、服务渠道规则（`ServiceChannelRulesTest`）、出站地址 CAS 与引用保护。不使用 `when().thenReturn`、`@MockBean`、spy。依赖数据库的用例只编译通过，尚未运行 |

## 11. R15 盘点

完整表格见 [唯一出口例外清单](../../../../agent-docs/decisions/2026-10-22-unified-outbound-exception-list.md) 的"R15 盘点"一节，摘要如下：

| 位置 | 命中 | 处理 |
|---|---|---|
| platform-support-delivery（POM，`integration/provider`、`integration/webhook`） | AWS SDK、HttpClient5 | 白名单 O3（SES / SQS）、O4（Client Webhook，`WebhookAddressGuard`） |
| platform-support-file-document（POM，`storage/s3`、`storage/minio`） | AWS SDK、MinIO、OkHttp | 白名单 O5（对象存储） |
| acquiring-app、counterparty-app（POM 与 `HttpClientConfiguration`） | HttpClient5 | 白名单 I1、I2，只用于内部 `lb://` RestClient 传输（偏差 X9） |
| `build/paytech-dependencies` | MinIO 版本、AWS BOM | 不在范围：只做版本管理，不引入依赖 |
| `vault/paytech-vault-test` | Enforcer 禁止规则文本中出现 `awssdk:kms` | 不在范围：这是规则文本，不是依赖 |
| platform-support-test `StorageObjectMocker` | MinIO / AWS | 不在范围：`-test` 模块 |
| channel-connector-protocol `ext/outbound/ChannelHttpClient` | `HttpClient#send` | 规则二唯一允许的位置 |
| 各服务 `*Configuration`：构造 JDK HttpClient 交给 `JdkClientHttpRequestFactory` | 只构造，不直接 send | 不命中：目标为内部服务 |
| foundation 与各 `-test` 模块中约 50 个测试类 | `send` / `sendAsync` | 不在范围：测试代码 |

## 12. `git -C /Users/kenny/Desktop/Workspace/Code-MoR-ccu2 status --short --untracked-files=all` 摘要

不计 `operations-console-web/` 共 482 条（`operations-console-web/` 另有 38 条，属于 P2-web agent）。

| 区域 | M | D | ?? |
|---|---:|---:|---:|
| channel-connector（api / protocol / core / app / test / docs / config / README） | 196 | 91 | 121 |
| operations-console-bff | 14 | 9 | 0 |
| card | 6 | 0 | 0 |
| platform-support（初始数据、capabilities json） | 2 | 0 | 0 |
| tools/arch-rules（含 reactor-test） | 6 | 0 | 20 |
| scripts/pdmaner | 7 | 0 | 1 |
| agent-docs/decisions | 1 | 0 | 1 |
| requirements 本需求目录 | 1（`run-service.sh`） | 0 | 6（5 个迁移 / 注册 SQL 与 rollback 说明；`tasks/P2-web-report.md` 属于 P2-web） |

- channel-connector 的删除与新增，大部分来自 `Integration*` → `Channel*` 的改名（git 未暂存，所以显示为 D + ??）。
- 本报告是新增文件，生成上面的计数之后才写入，因此不在计数中。
- 没有暂存或提交任何内容。

## 13. 主会话与 P2-web 的确认项（2026-10-22）

| 项 | 实际结果 |
|---|---|
| 注册 ID 为 18 位 | 已满足：块 129 全部为 `900000000000129xxx`，例如权限 `900000000000129101`、Route `900000000000129401`–`129404` |
| 1. 出站地址 PUT 返回完整渠道详情 | 已满足：Owner `ChannelApi#updateChannelEndpoints` 与 BFF `ChannelConfigurationController#updateChannelEndpoints` 都返回 `ChannelResponseBody`，与 GET 详情同结构。错误：版本冲突与引用保护 409（引用保护的 detail 列出原子 API 编码）；地址类与静态校验错误 422，field 形如 `endpoints[1].baseUrl`，下标从 0 开始 |
| 2. 授权新建与状态变更返回单条授权 | 已满足：Owner 与 BFF 都返回 `ChannelCallGrantResponseBody`（任务卡第 6 节外形）；重复组合 409，detail 含现有状态与授权 ID |
| 3. 协议配置与沙箱验证的写操作继续按 `OPERATIONS_EXTERNAL_STANDARD_API_MANAGE` 判断 | 已满足：块 129 只停用接入方页面 71303、按钮 71305 和 Route 71006–71010。按钮 71304 `OPERATIONS_EXTERNAL_STANDARD_API_MANAGE`、权限 71102 及其角色绑定、Route 71011 及之后的协议与沙箱接口都没有改动。块 129 不为此新增按钮。新按钮 129301 `OPERATIONS_CHANNEL_CALL_GRANT_MANAGE`（「调用授权」）挂在页面 92302 `OPERATIONS_CHANNEL_CHANNELS` 下，该页面已改名为「渠道」 |
| 4. config-changes 按 `channelId` 查询该渠道下全部对象 | **未做，列为遗留**。原因：`t_channel_config_change` 只有 `target_type` + `target_id`，没有 `channel_id`。用 join 从对象表推导渠道会丢掉已删除的原子 API、脚本和实现的历史，审计视图会不完整。要做完整需要：给变更记录表加 `channel_id` 列和索引；约 35 个写入点同步写入；迁移时回填历史（已删除对象仍然无法回填）。这超出了小成本的范围，建议单独立项 |
| P2-web 第 4 条：验证账号 `entityNo` 可能为 null | 当前不会为 null：`t_channel_verify_account.entity_no` 仍是 NOT NULL（偏差 X6），前端放宽解码没有问题；渠道账号中服务渠道的 `entityNo` 为 null |


## 14. 需库测试修复记录（2026-10-22）

主会话在 `_ccu2` 隔离库跑了需库测试（clean 构建）：channel-connector 375 个中 373 个通过，card 9/9，Platform 全部通过（通知表的既有漂移另行处理）。2 个失败的修复如下。

| 编号 | 问题 | 根因 | 修复 | 修复人 |
|---|---|---|---|---|
| F1 | `ExternalApiRepositorySmokeTest` 重复键识别失败 | `ChannelApi`、`ChannelScript`、`ChannelImplementation` 三个 Repository 仍按旧索引名 `uk_integration_*` 判断重复键；合表改名后索引已改为 `uk_channel_api_code`、`uk_channel_script_code`、`uk_channel_standard_api` | 三个 Repository 改为识别新索引名 | 主会话 |
| F2 | `ServiceChannelExecutionTest.test_accountMaterial` 第 202 行：期望 200，实际 422 | 通用入口 `ExecuteServiceChannelProcessor.inputWithAccount` 注入账号材料时写了 `"entityNo": null`。标准 API 的请求字段只声明了 `channelAccount.channelCode`、`channelAccountId`、`accountVaultToken`；`FieldSchema` 对未声明的字段和 null 值都会拒绝（422）。而且服务渠道账号本来就不绑定 OE，`ChannelAccountConfigFunction` 也要求传入的身份不带 `entityNo`，与负载中的 null 对应。注入这个键本身就是错的 | 不再注入 `entityNo` 键，并在方法注释中写明原因。没有改动产品校验，也没有改测试 | 本 agent |
| F3 | Platform 注册中 3 处 ID 为 17 位 | 注册 SQL 与 `platform-support-initial-data.sql` 块 129 引用块 92 的权限与 Route 时，写成了 `90000000000092101`、`90000000000092102`、`90000000000092401`，少了一位 0 | 改为 18 位 `900000000000092101`、`900000000000092102`、`900000000000092401` | 主会话 |
| F4 | 迁移 SQL 写入的注释出现乱码 | mysql 客户端默认字符集不是 utf8mb4 | `phase2-10/20/30` 与 `phase2-registration.sql` 开头补 `SET NAMES utf8mb4`；演练验证修复后的结构与注释和 schema 完全一致 | 主会话 |

修复 F2 后，在 `mor_channel_connector_ccu2` 运行了 `-Dtest='ServiceChannelExecutionTest,ExecuteChannelOperationTest,ExternalApiRepositorySmokeTest,ExecutionCompartmentIsolationTest'`，结果：

| 测试类 | 结果 |
|---|---|
| ServiceChannelExecutionTest | 8/8 |
| ExecuteChannelOperationTest | 16/16 |
| ExternalApiRepositorySmokeTest | 4/4 |
| ExecutionCompartmentIsolationTest | 1/1 |

第 7 节第 1 项（需库测试未运行）至此已由主会话完成。

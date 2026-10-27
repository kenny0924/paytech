# 阶段 2 核对笔记：channel-connector（SERVICE / 标准 API / Mock）与 Vault（数据类型 / 访问规则）

范围：requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md §7.3（:110-116）、§7.6（:134-139）。只读核对，2026-10-27。
路径简写：CC = paytech/channel-connector；CCAPI = CC/paytech-channel-connector-api/src/main/java/com/paytech/channel/connector/api；
CCPROTO = CC/paytech-channel-connector-protocol/src/main/java/com/paytech/channel/connector/protocol；V = paytech/vault。

## 结论速览（影响 §7.3 / §7.6 修订）

1. **新增标准 API 与 Mock 渠道都不需要改 channel-connector 代码或表**：标准 API、原子 API、脚本、实现、渠道、调用授权全部经运营管理接口写入（现有标准 API 均无初始化 DML，只在库中）。§7.3 的"变动点"实质是**配置数据 + 环境部署**，不是代码。
2. **"SERVICE 渠道请求体不记全文"已经满足**：入站 MVC 日志对全部路径只记元数据（CC 应用配置 `metadata-only-path-prefixes: [/]`），出站 HttpClient 不打印报文，访问日志只记编码与状态。唯一会落库原文的是**沙箱验证运行**的 trace_json（请求体只遮蔽卡值 / 受控句柄，证件号会原样保存）。§7.3"日志"行可改为"无需改代码；沙箱验证用例不得使用真实证件号"。
3. **mor-test 没有可达的合成外部服务**：trusted-networks 为空、只允许 https，第二段发布时明确"服务渠道在 mor-test 没有可达的合成服务"。S_SCREENING_MOCK 要在 mor-test 跑通，需要新部署一个 Mock 服务容器 + 环境覆盖（allowed-schemes / trusted-networks）——这是部署变动点，需求未列。
4. **compliance 目前没有任何出站 HTTP 客户端基础设施**（无 http-client-support、无 vault-client、无 InternalTokenClientOptions），需新增依赖与配置。
5. **Vault 访问规则 caller 名**：client-master 实际 caller 是 `client-master-service`（不是旧的 `client-contract-service`）；compliance 为 `compliance-service`。
6. **Vault 不返回 masked**：Protect 只返回 token；掩码由调用方自算（现有 Email/Phone 即如此）。需求里 `identifier_masked_value` 须由 client-master 计算。
7. **STABLE 唯一性只看 (data_type, 精确原值)**：同一号码在不同证件类型 / 国家会得到同一个 Token。`t_party_identity_key` 的唯一键必须同时含证件类型与国家，或调用方把类型/国家编进规范化原值（后者会改变 Token 语义，需决定）。
8. **VAULT_SERVICE_ARCHITECTURE.md:112 原文禁止 CCT 把"法律标识"放入 Vault 之外的首期两类型**，需修订该行（决定 2026-10-27 第 ⑤ 条已登记例外，文档未改）。
9. **需求内部用途码不一致**：§7.2 写 compliance Reveal 用途 `COMPLIANCE_CHECK / COMPLIANCE_REVIEW`，§7.6 只列 `COMPLIANCE_REVIEW`；`RISK_SCREENING_SEARCH_TERM_V1` 只列了类型，没列 compliance 的 PROTECT（及是否 REVEAL）规则与 token_mode / data_class。

---

## channel-connector

### 1. 标准 API 定义与注册

- 表 `t_standard_api`：CC/docs/database/sql/channel-connector-schema.sql:310-322。列 standard_api_id、standard_api_code（ascii_bin，`uk_standard_api_code`）、standard_api_name、description、version（乐观锁）、created_by/updated_by、时间。表注释写明"服务渠道的调用方授权见 t_channel_call_grant"。
- 表 `t_api_field`：channel-connector-schema.sql:324-339。标准 API 与原子 API 共用；`direction` ∈ STANDARD_API_REQUEST / STANDARD_API_RESPONSE / INTEGRATION_API_REQUEST / INTEGRATION_API_RESPONSE；`field_location` ∈ HEADER/BODY/QUERY/PATH；`field_path` 点分 + `[]` 数组；`data_type` ∈ STRING/BOOLEAN/INTEGER/DECIMAL/OBJECT/ARRAY；`required_flag`（"响应字段只在执行状态为 SUCCESS 时校验"）；uk (api_id, direction, field_location, field_path)。**没有枚举取值约束列**——`subjectType(PERSON/ORG)`、`screeningTypes[]` 的取值只能靠调用方/渠道脚本约束。
- 字段规则校验：CCPROTO/domainservice/validator/ApiFieldValidator.java:30-36（每方向最多 128 项、BODY 最多 16 段）、:108-126（子字段要求父容器先定义：`a.b` 要 `a` 为 OBJECT，`a[].x` 要 `a` 为 ARRAY）。故 `hits[].listTypes[]` 需逐层声明 hits(ARRAY)→hits[](OBJECT)→hits[].listTypes(ARRAY)→hits[].listTypes[](STRING)。原子 API 输出允许 OBJECT/ARRAY：CCPROTO/domainservice/validator/IntegrationApiDefinitionValidator.java:67。
- 管理接口 `StandardApiManagementApi`：CCAPI/externalapi/StandardApiManagementApi.java:29（`/external-api-admin/v1`），列表 :40、详情 :52、创建 :63（`POST /standard-apis`，需 `X-Gateway-Actor-Subject`）、整体修改 :76（PUT 按版本）、删除 :90（仍被实现时 400）。只由 Operations BFF 私网调用（:25）。
- 创建 DTO：CCAPI/externalapi/request/CreateStandardApiRequestBody.java:21-62：standardApiCode（`[A-Z][A-Z0-9_]{0,63}`）、standardApiName（≤128）、description（≤512）、requestFields / responseFields（List<ApiFieldBody>，必传、≤128）、reason（1-500，进审计）。ApiFieldBody 字段：fieldLocation、fieldPath（≤512）、fieldName（≤128）、dataType、required、description（`find … ApiFieldBody.java` :19-58）。
- 现有标准 API 清单：
  - 代码常量只有支付操作：CCAPI/enums/ChannelOperation.java（PAY、AUTHORIZE、AUTHENTICATE、CAPTURE、VOID、REFUND、QUERY→PAY_QUERY）。
  - **仓库内没有任何 `INSERT INTO t_standard_api` 的初始化 DML**（全仓 grep 为空），标准 API 全由管理接口创建。
  - 本机 e2e 备份 local-environment/e2e/backup/20261022T151611Z-card3ds-stage5/mor_channel_connector_v2e2e.sql：PAY、PAY_QUERY、REFUND、REFUND_QUERY、S5P2_ECHO_PING；渠道 PAYERMAX_MOCK（PAYMENT/ACTIVE）、S5P2_ECHO（SERVICE/ACTIVE），授权 1 行。
  - mor-test：测试标准 API `S7P2_PING` 保留、验收渠道 `S7P2_SVC` 已 CLOSED（agent-docs/CURRENT_IMPLEMENTATION.md:185）；PAY 已到 v5（:77）。
- 落地建议：SCREENING_SEARCH 与 6 个"只登记定义"的标准 API 走管理接口（或 harness 脚本调用管理接口），不新写 DML；若坚持 DML，需按 `uk_api_field_path` 与父容器规则手写字段行，并自行分配 ID（无先例）。

### 2. SERVICE 渠道

- `ChannelType`：CCAPI/enums/ChannelType.java:11-16（PAYMENT / SERVICE，创建后不可改）。表列 `t_channel.channel_type`：channel-connector-schema.sql:11；`endpoints` JSON（endpointCode/baseUrl/sandbox；保存时拦回环、私网、localhost，执行时解析后再拦，trusted-networks 放行）：:14。
- 技术账号：`t_channel_account.entity_no` 服务渠道必须为空、每个服务渠道最多一个 ACTIVE 账号（channel-connector-schema.sql:26-30 注释）。**无凭据的 Mock 渠道可以不建账号**：执行时没有 ACTIVE 账号就不注入账号材料（CC/paytech-channel-connector-core/src/main/java/com/paytech/channel/connector/service/processor/ExecuteServiceChannelProcessor.java:36、:87-94、:106-108）。
- 渠道管理接口：CCAPI/channel/ChannelApi.java:30（`/channel-connector/v1/channels`）、出站地址 PUT :95、状态 :109；账号 CCAPI/channel/ChannelAccountApi.java:29、配置 :93、状态 :108。BFF 侧前缀 `/channel-admin/v1`。激活只要求已配置出站地址；账号激活要求渠道已 ACTIVE（requirements/20261020/channel-connector-统一对外渠道出口/verification/stage5p2-report.md:142-148）。
- 通用执行入口 `ServiceChannelExecutionApi`：CCAPI/serviceexecution/ServiceChannelExecutionApi.java:23（`/channel-connector/v1`）、:33 `POST /service-executions`；服务发现名 `channel-connector-service`、无 context path、不经 Gateway（:15）；不处理 Idempotency-Key，调用方不得自动重试，200+ERROR 与超时按结果未知（:17）；"保证未发出"的 Problem 列表 :18-21。
  - 请求：CCAPI/serviceexecution/request/ExecuteServiceChannelRequestBody.java:17-32（channelCode、standardApiCode、input JsonNode；input 按标准 API 请求字段校验，未声明字段拒绝，:28）。
  - 响应：CCAPI/serviceexecution/response/ServiceChannelExecutionResponseBody.java:16（executeStatus = SUCCESS/FAIL/ERROR）、:22（outputs，ERROR 时为空对象）。不返回轨迹与原始报文（:8）。
  - 错误码：CCAPI/enums/ServiceExecutionErrorCode.java:11-31（STANDARD_API_NOT_FOUND、INTEGRATION_IMPLEMENTATION_NOT_FOUND、INTEGRATION_CONFIG_INVALID、INTEGRATION_INITIALIZATION_FAILED、INTEGRATION_BUDGET_EXHAUSTED、CHANNEL_EXECUTION_BUSY、CHANNEL_CALL_NOT_GRANTED 403）；注释 :9 要求消费方在 HttpServiceClientFactory 显式注册本枚举。
  - 处理顺序：ExecuteServiceChannelProcessor.java:55-70（渠道存在/SERVICE/ACTIVE → 授权 → 账号 → 事务外执行）；本入口不写库（:40）。
- 调用授权：表 `t_channel_call_grant` channel-connector-schema.sql:463-477（caller_service `[a-z][a-z0-9-]{1,63}`、channel_id、standard_api_id、grant_status ACTIVE/DISABLED；uk (caller_service, channel_id, standard_api_id)；只停用不删除）。接口 CCAPI/grant/ChannelCallGrantApi.java:26（`/channel-connector/v1/channel-call-grants`），列表 :40、新建 :58（渠道须 SERVICE 且标准 API 已被该渠道实现，否则 400；重复 409）、启停 :73。DTO CCAPI/grant/request/CreateChannelCallGrantRequestBody.java:14-30（channelId 十进制串、callerService、standardApiCode）。授权需在实现之后创建。
- X-Caller-Service：常量 CCAPI/ChannelConnectorHeaders.java:15；解析 CC/paytech-channel-connector-app/src/main/java/com/paytech/channel/connector/ext/rest/ChannelCallerResolver.java:12-50（有已验证 Token 以 Token 为准、Header 不一致 400、Token purpose 非 GENERAL 视为缺失；internal-auth OFF 或 DUAL 无 Token 时信任 Header；缺失或格式不符 400）。Controller 委托：…/ext/rest/ServiceChannelExecutionController.java:15-24。
  - 授权行应为 `compliance-service`（compliance 的 spring.application.name，paytech/compliance/paytech-compliance-app/src/main/resources/application.yml:7）。

### 3. 渠道实现方式（原子 API + 脚本 + 流程）与 Mock 参照

- `t_channel_api`（原子 API，definition_json 含参数、传输、编解码、脚本引用、状态规则、按 SUCCESS/FAIL 的输出映射）：channel-connector-schema.sql:341-355。
- `t_channel_script`（REQUEST/RESPONSE 阶段受限 Aviator 脚本，复杂签名靠注册函数，脚本不读凭据明文）：:357-373。
- `t_channel_standard_api`（渠道对标准 API 的实现，flow_definition_json：预算、初始化项、入口、节点、返回出口、条件连线，无环）：:375-388。
- 管理路径（stage5p2 脚本实测）：原子 API `POST /external-api-admin/v1/integrations/{channelCode}/apis`、实现 `POST …/integrations/{channelCode}/implementations`（requirements/20261020/channel-connector-统一对外渠道出口/verification/stage5p2_service.py:291-297）；接口定义 CCAPI/externalapi/IntegrationApiManagementApi.java:27、IntegrationImplementationManagementApi.java:25、IntegrationScriptManagementApi.java:31。
- **回显探针 S5P2_ECHO / S5P2_ECHO_PING（Mock 筛查渠道的直接模板）**：全部经管理接口配置，不是 SQL。
  - 脚本常量与 payload：stage5p2_service.py:32-38（CODE/STD/API/CALLER）、标准 API 字段 :161-170、原子 API definition（transport endpointCode/pathTemplate/超时、JSON 编解码、statusRules、outputs.SUCCESS）:173-188、流程（initializers CHANNEL_ACCOUNT_CONFIG、nodes、returns SUCCESS/ERROR、edges）:191-207、建渠道→写出站地址→激活→账号→标准 API→原子 API→实现 :234-303、授权 :306-318。
  - 回显服务：同目录 stage5p2_echo_server.py（标准库、127.0.0.1 随机端口）；报告 stage5p2-report.md:134-163。
  - **无凭据版本**：测试样例 LOOKUP（无初始化项、无凭据）CC/paytech-channel-connector-test/src/test/java/com/paytech/channel/connector/test/builder/ServiceChannelSamples.java:17、:41-80 —— S_SCREENING_MOCK 应照此（不建账号、流程无 initializers）。
- 沙箱验证：`endpoints[].sandbox=true` 的地址才允许验证运行（channel-connector-schema.sql:14）；表 t_channel_verify_account/case/run/run_step :390-461；验证运行 trace_json 保存"脱敏后"的请求/响应原文 ≤64 KiB（:453）。**已知遗留**：`t_channel_verify_account.entity_no` 仍 NOT NULL，服务渠道沙箱账号需占位 OE（agent-docs/CURRENT_IMPLEMENTATION.md:187）。

### 4. 日志策略

- 入站：CC/paytech-channel-connector-app/src/main/resources/application.yml:56-60 `web.logging.metadata-only-path-prefixes: [/]`——全部入站路径（含 service-executions）只记元数据，注释说明原因。Foundation 实现：paytech/foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/logging/WebLoggingProperties.java:12-16。
- 出站：CCPROTO/ext/outbound/ChannelHttpClient.java:22-31、:67-80 无报文日志（传输失败只报固定文字）；ChannelRequest.toString 为 redacted（CCPROTO/ext/outbound/ChannelRequest.java:35）。
- 访问日志 `channel.outbound.access`：CCPROTO/domainservice/execution/AtomicApiExecutor.java:52、:101-114，只记 callerService/channelCode/channelType/standardApiCode/apiCode/executeStatus/httpStatus/elapsedMs/requestId，"不记录 URL、查询串、请求头、报文与凭据"。
- 流程日志：CCPROTO/domainservice/execution/FlowEngine.java:51、:88、:104、:112、:174 只记编码、状态与原因。
- 沙箱 trace：AtomicApiExecutor.java:117-121 只对卡值 / 受控句柄遮蔽；ExecutionTrace.java:14-15 只脱敏认证类 Header。→ 普通字段（证件号、姓名）在验证运行 trace_json 中原样保存。
- **没有按渠道 / 按 API 关闭日志的开关**，也不需要：正式执行本就不记报文。
- 消费方侧：Foundation `HttpServiceClientFactory` 不记报文（paytech/foundation/paytech-http-client-support/src/main/java/com/paytech/foundation/httpclient/api/HttpServiceClientFactory.java:33-37）；payment 出站配置明确"出站客户端不记录报文"（paytech/payment/paytech-payment-app/src/main/java/com/paytech/payment/configuration/PaymentOutboundConfiguration.java:29）。compliance 自身入站日志策略需另核（I01 请求将带 Token 等）。

### 5. 测试设施

- channel-connector-test 外部 HTTP DTO Mock：独立 Mock HTTP 服务上的合成渠道 `ChannelMockController`（前缀 `/channel/**`，按 ChannelMocker 配置响应、记录收到的请求、4xx/5xx 经 MockHttpFault）CC/paytech-channel-connector-test/src/test/java/com/paytech/channel/connector/test/mock/ChannelMockController.java:22-63；服务入口测试基类 …/test/base/AbstractServiceExecutionTest.java:28-77（渠道出站地址指向 `mockHttpServer.baseUrl()+/channel/api`，调用方头）；测试配置放行 http 与 127.0.0.1：CC/paytech-channel-connector-test/src/test/resources/application-mock.yml:147、:151；Vault Mock：…/test/mock/MockVaultController.java:19（implements VaultHttpService）。既有服务入口测试：…/auto/serviceexecution/ServiceChannelExecutionTest.java、ExecutionCompartmentIsolationTest.java。
- 本机 e2e 假外部服务：
  - PayerMax 合成服务 `PayerMaxMock.java` + `start.py`（requirements/20261014/platform-support-PayerMax接入/assets/harness/README.md:5-11），e2e 进程目录 local-environment/e2e/payermax-mock.pid、runtime/payermax/。
  - 回显服务 stage5p2_echo_server.py，运行目录 local-environment/e2e/runtime/stage5p2-echo/（stage5p2-report.md:304）。
  - 本机 channel-connector 需带 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http` 与 `…_TRUSTEDNETWORKS=127.0.0.1/32`（stage5p2-report.md:63）；默认值在 CC/config/nacos/channel-connector-service.yaml:56-65（allowed-schemes [https]、trusted-networks []）。
- mor-test：trusted-networks 为空，"服务渠道在 mor-test 没有可达的合成服务"（requirements/20261020/channel-connector-统一对外渠道出口/release/phase2-release.md:20、:106）；test-environment/compose.applications.yaml 中无 Mock 服务。→ §7.3"仅 local / mor-test"的 mor-test 部分需要新增 Mock 容器（固定私网 IP）+ channel-connector 覆盖 env（http 与该 IP/32），并在发布清单登记"不得放行其他私网"。

### 6. 消费方接入

- **当前没有任何业务服务调用 service-executions**（全仓 grep 仅 channel-connector 自身与测试）。
- API jar：`com.paytech:paytech-channel-connector-api`（CC/paytech-channel-connector-api/pom.xml:7-12）；已被 payment、billing、reconciliation、operations-console-bff 依赖。
- 客户端样例：PaymentOutboundConfiguration.java:38-59（`HttpServiceClientFactory.create(client, internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), "channel-connector-service")).createClient(…Api.class)`）、:98-114（服务发现 + JDK HttpClient、禁止重定向、连接 2 s）。读取超时须大于 channel-connector 流程预算。
- X-Caller-Service 在 internal-auth OFF 时必带：参照 card 的 `defaultHeader(CALLER_HEADER, SERVICE_ID)`（paytech/card/paytech-card-app/src/main/java/com/paytech/card/configuration/PlatformClientConfiguration.java:23、:51）；或在 typed 接口方法参数显式传入（ServiceChannelExecutionApi.java:35 该参数 required=false）。
- 注册合同错误码：`HttpServiceClientFactory.create(client, options, ServiceExecutionErrorCode.values())`（ServiceExecutionErrorCode.java:9）。
- compliance 现状：app 依赖仅 mvc-web-starter、internal-auth、mybatis、snowflake、compensation-event、messaging、nacos-discovery（paytech/compliance/paytech-compliance-app/pom.xml:24-92）；**无 paytech-http-client-support、paytech-vault-client、loadbalancer 传输与 InternalTokenClientOptions**——均需新增。client-master 的 `InternalCallTokenOptions` 可作参照（paytech/client-master/paytech-client-master-app/src/main/java/com/paytech/client/master/configuration/ClientDraftConfiguration.java:102-109、:130-143）。

### 7. CURRENT_IMPLEMENTATION.md

- 第二段（渠道分类、服务渠道通用入口、调用授权）：agent-docs/CURRENT_IMPLEMENTATION.md:175-187；mor-test 部署 :185（channel-connector `sha256:798940ea…`，块 129 已激活）；遗留 :187（verify_account.entity_no NOT NULL、单渠道 ACTIVE 凭据无唯一索引 X8、Owner 级调用方限制未做 X15、mor-test trusted-networks 为空、注册后须重发 Gateway Snapshot）。
- 第一段（外部 API 自 Platform 迁入）：:190-198；遗留 :198（internal-auth 开启时 channel-connector 无 Token 来源无法启动，mor-test 未开启）。
- 之后 channel-connector 镜像还经 CYBS_NI（:127-145，`sha256:5f22b55b…`）与 3DS（:77，`23679d45`）更新。
- 其他：client-master 与 channel-connector 共用 Snowflake worker 26（:20）。

---

## Vault

### 8. Schema 与 DTO

- Schema：paytech/vault/docs/database/sql/vault-schema.sql
  - `t_vault_record` :1-18：token、data_type、token_mode、`value_fingerprint BINARY(32)`（STABLE 必填）、密文；**`uk_vault_record_stable (data_type, value_fingerprint)` :15** 即 STABLE 唯一键。
  - `t_vault_data_type` :62-78：data_type_code（不可改）、token_mode REFERENCE/STABLE（不可改）、data_class STANDARD/SECRET、value_format TEXT/JSON/REGEX、value_pattern（RE2 整值匹配）、description、manual_protect_enabled / manual_reveal_enabled、status ACTIVE/DISABLED（只停用不删）、version。
  - `t_vault_access_rule` :80-93：caller_service、action PROTECT/REVEAL、data_type_code、purpose_code、status；uk (caller_service, action, data_type_code, purpose_code)。
  - `t_vault_policy_change` :95-110：policy_version_no 单调递增唯一、change_type（SEED/DATA_TYPE_CREATE/…）、target_type/key、before/after_snapshot、reason、actor_id（初始化为 SYSTEM）、request_id。
- 指纹：HMAC(`StableMaterial(dataType, value)`)，只看数据类型 + 精确原值（V/internal/biz/cipher.go:50-61、V/internal/biz/canonical.go:15-27）；同值复用记录 V/internal/biz/protect.go:252-254。**Vault 不做规范化**，调用方负责。
- Protect 授权：请求 tokenMode 必须等于数据类型配置，否则 TOKEN_MODE_NOT_ALLOWED（V/internal/biz/protect.go:61-74）；值格式 REGEX 不符返回 VALUE_PATTERN_MISMATCH（V/internal/biz/errors.go:27）。
- 策略从库全量刷新，默认 10 s（V/internal/conf/config.go:28-29、:92；V/internal/bootstrap/bootstrap.go:64-69）——DML 生效无需重启。
- Java 客户端 DTO（paytech/vault/paytech-vault-client/src/main/java/com/paytech/vault/client/）：
  - `ProtectCommand(dataType, tokenMode, value, purpose, idempotencyKey)` ProtectCommand.java:19（value ≤32 KiB UTF-8，idempotencyKey 1..128 安全 ASCII）；`ProtectResult(token)` ProtectResult.java:11（43 字符 Base64URL）。
  - `RevealQuery(token, purpose)` RevealQuery.java:13；`RevealResult(value)` RevealResult.java:11。
  - `TokenMode` REFERENCE / STABLE：TokenMode.java:9-14。
  - Wire：`POST /vault/v1/records/protect`（Header X-Caller-Service、Idempotency-Key、X-Request-Id）、`/reveal`：internal/VaultHttpService.java:15-24；Header 强制：internal/VaultRequestHeaders.java:16-70。
  - **响应无 masked 字段**（ProtectResponse 只有 token，internal/wire/ProtectResponse.java:4；OpenAPI V/api/openapi/v1.yaml:146-179 无 mask）。

### 9. 现有数据类型 / 规则 DML

- requirements/20261014/operations-console-Vault数据类型与手动加解密/db/vault/dml-test.sql:10-30：4 类型（EMAIL_ADDRESS_V1、PHONE_E164_V1 均 STABLE/STANDARD，ID 9100000000000500-0503）、11 规则（**caller 为旧名 `client-contract-service`**，PROTECT/REVEAL × EMAIL/PHONE × `CLIENT_ONBOARDING_CONTACT`，:17-20）、policy SEED 版本 1。dml-local.sql:11-23 同构（ID 0100-0101 / 0200-0205）。
- requirements/20261018/test-environment-V2部署与数据迁移/sql/21-vault-upgrade.sql（mor-test）：:30-33 `client-master-service` PROTECT/REVEAL × EMAIL_ADDRESS_V1/PHONE_E164_V1 × `CLIENT_ONBOARDING_CONTACT`（ID 0701-0704）；channel-connector-service REVEAL CHANNEL_ACCOUNT_CONFIG_V1 CHANNEL_EXECUTION :36；版本号 `SELECT MAX(policy_version_no)+1 … FOR UPDATE` :23；旧 client-contract-service 规则保留 :12。
- 本机：local-environment/vault/policy.local.json 已不被读取；实际生效的是 requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/sql/30-vault-v2-callers.sql:17-26（client-master-service 四条，ID 0300-0303，版本 20）。local-environment 下没有 Vault 初始化 DML 文件。
- 最新"新数据类型 + 规则"样例（模板首选）：requirements/20261022/platform-support-数据查询服务/db/vault/dml.sql:1-40（头注释含影响、执行前核对、执行后核验、回滚；本机与 mor-test 同一文件；`SET NAMES utf8mb4`、`time_zone '+00:00'`、版本号取 MAX+1 FOR UPDATE）。
- 已用手工 ID 段：0100-0205（local）、0300-0306（local V2）、0500-0610（test）、0700-0707、0720、0730（CASH_ACCOUNT_NUMBER_V1，REGEX 样例 requirements/20261020/reconciliation-渠道资金对账切片1/db/vault/dml.sql:23）、0740-0742（FILE_STORAGE_CREDENTIAL_V1）、0750-0752。→ 本需求可取 9100000000000760 起。
- 未查询本机 / mor-test 实库当前行（只读核对未连库）；执行前须按样例做存在性预检。

### 10. VAULT_SERVICE_ARCHITECTURE.md

- :112 原文："Client & Contract 的 Party Identity 首期只注册两个 Vault Data Type：`EMAIL_ADDRESS_V1` 与 `PHONE_E164_V1`。两者都是单一标量 Schema；调用方按批准的 Email/E.164 规则产生 Canonical Bytes，Vault 只对准确字节执行 `REFERENCE/STABLE` 语义，不负责业务规范化或脱敏。禁止把整份 Party/Profile、姓名、生日、法律标识或地址包装成不透明 Payload。其他系统是否使用 Vault 仍按各自数据分类和 Data Type Catalog 独立审批，不能从 CCT 的简化规则反推。"
- :103 分类表把"身份证件号、护照号"列为 `RESTRICTED_IDENTITY` 允许类别；:109 KYC/KYB 结构化数据须先由 Owner 按 Schema 校验。
- 新增数据类型流程：:13（2026-10-14 决定：类型与规则存 Vault 库，每次变更追加 policy_change 产生新版本，**各环境用初始化 DML 写入，不做程序导入**，定时刷新；管理接口只接受 operations-console-bff 且需 X-Operator-Id）；:15（值格式 TEXT/JSON/REGEX，调用方规范化）。先例：每个新类型都配一份决定或在顶部登记例外（:19-25，Payment 账号、卡 DEK、通知凭据）。
- 决定已登记：agent-docs/decisions/2026-10-27-client-onboarding-global-kyc-kyb.md:18（第 ⑤ 条，LEGAL_IDENTIFIER_V1 STABLE 兼作唯一键）、:29（需修 VAULT 文档 CCT 限制）。设计对应 design-discuss/client-onboarding/design.md:646、:754。

### 11. Vault Java 客户端与 client-master 注入

- 模块：`com.paytech:paytech-vault-client`（paytech/vault/paytech-vault-client/pom.xml:6-11）；接口 VaultClient.java:4-29；自动配置 autoconfigure/VaultClientAutoConfiguration.java（caller 未配置时取 spring.application.name，:55-56）；属性 autoconfigure/VaultClientProperties.java:23-25（callerService、serviceId 默认 vault-service、connect/read timeout、baseUrl 仅 test、adminEnabled）。
- client-master 现用法：依赖 paytech/client-master/paytech-client-master-core/pom.xml:42；`spring.autoconfigure.exclude` 排除自动配置、由 ClientDraftConfiguration `@Import(VaultClientAutoConfiguration.class)` 条件引入（application.yml:24；ClientDraftConfiguration.java:39-44，仅 `onboarding.draft-enabled=true`）；Bean ClientDraftConfiguration.java:110；实现 paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/integration/impl/ClientDraftContactClientImpl.java:10-44（STABLE、随机 UUID 幂等键 :22-23、**掩码由客户端计算** :27、事务内调用抛 IllegalStateException :42-44、失败转 ONBOARDING_DEPENDENCY_UNAVAILABLE）。
- client-master Vault 配置：application.yml:145-148（未设 caller-service → `client-master-service`，:26）；测试 paytech/client-master/paytech-client-master-test/src/test/resources/application-mock.yml:31-32、:108-113（vault-service 指向测试 HTTP 服务，任何调用记为意外）。**client-master 测试目前没有 Vault DTO Mock 控制器**；可参照 channel-connector 的 MockVaultController（implements VaultHttpService）。
- compliance：无 vault-client 依赖，需新增；caller 将为 `compliance-service`。

---

## 对 §7.3 / §7.6 的修订建议（供阶段 2 采纳）

- §7.3 标准 API：写明"经 `/external-api-admin/v1/standard-apis` 创建（harness 脚本记录 payload），无代码 / 表变更"；字段按父容器规则逐层声明；无枚举约束能力。
- §7.3 渠道：S_SCREENING_MOCK 无账号、流程无 initializers（参照 LOOKUP）；本机用回显式 Python/Java Mock + trusted-networks 127.0.0.1/32 + http；mor-test 需新增 Mock 容器与 channel-connector env 覆盖（部署变动点）。授权行 `compliance-service × S_SCREENING_MOCK × SCREENING_SEARCH`，须在实现之后创建。
- §7.3 日志：无需改代码；沙箱验证用例与 trace 不用真实证件号；compliance 侧同样要确认入站/出站不记报文。
- §7.2 / 新增：compliance 增加 http-client-support、vault-client、服务发现传输、内部 Token 选项与 X-Caller-Service 默认头；注册 ServiceExecutionErrorCode。
- §7.6：caller 用 `client-master-service` / `compliance-service`；LEGAL_IDENTIFIER_V1（STABLE、STANDARD、TEXT 或 REGEX）明确规范化规则与 identity_key 唯一键含类型 + 国家；补全 RISK_SCREENING_SEARCH_TERM_V1 的 token_mode / data_class 与 compliance PROTECT（及是否 REVEAL）规则；统一 COMPLIANCE_CHECK / COMPLIANCE_REVIEW 用途；ID 段从 9100000000000760 起；本机与 mor-test 同一文件（数据查询样例）；同步修订 VAULT_SERVICE_ARCHITECTURE.md:112；masked 由 client-master 计算。

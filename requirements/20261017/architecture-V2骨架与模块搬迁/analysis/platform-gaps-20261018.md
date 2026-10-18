# Platform Support 功能缺口调研（refactor-main，只读）

Platform 的公共 API 都已有实现。所有 `*Api` 接口都有对应的 Controller，没有"只有接口"的 API。缺口在三处：没开工的整块领域、只做了 CLIENT 渠道的部分，以及只用配置密钥或本地实现的安全能力。

以下事实取自 `Code-MoR-refactor`（refactor-main），没有修改任何文件。

**路径缩写：**
- `P/` 指 `paytech/platform-support/`
- `CI` 指 `agent-docs/CURRENT_IMPLEMENTATION.md`
- `REQ` 指 `requirements-site/content/requirements.ts`
- `AUTH` 指 `docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md`
- `DBS` 指 `docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md`
- `CP` 指 `docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md`
- `APP` 指 `P/paytech-platform-support-app/src/main/resources/application.yml`

**通用代码事实：**
- **API 与 Controller：** `P/paytech-platform-support-api` 有 38 个 `*Api` 接口，`P/paytech-platform-support-app/.../ext/rest` 下各有一个 Controller 实现。
- **默认关闭：** 各能力由 `@ConditionalOnProperty` 控制，正式默认全部关闭。涉及的开关包括 operations / client 认证（`APP:94`、`APP:134`）、租户投影（`APP:138`）、通知（`APP:161`）、Webhook（`APP:202`）、内部 Token 签发端（`APP:235`）、入驻文件，以及 `internal-auth.mode=OFF`（`APP:222`）。
- **表与 Mapper：** PDManer 模型 `P/docs/database/platform-support.pdma.json` 有 70 张表，评审 Schema `platform-support-schema.sql` 有 57 张。有 13 张模型表既没有任何 Mapper，也不在 Schema 中：`t_audit_integrity_point`、`t_consumer_document`、`t_document_access_grant`、`t_document_delivery`、`t_document_template`、`t_document_template_translation`、`t_document_version`、`t_email_otp_attempt`、`t_email_otp_challenge`、`t_federated_identity`、`t_security_alert`、`t_security_alert_action`、`t_security_alert_event`。另有 `t_compensation_event` 不在 Platform 的 Mapper 里，由 Foundation 库访问。

## 按功能域

### 人类认证与会话

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| Operations 登录 / Refresh / 登出 / 改密 / Bootstrap / JWKS | REQ:349、REQ:366、REQ:386 | `OperationsAuthenticationApi`、`OperationsJwksQueryApi` | 已实现 | 有入口测试，位于 `auto/ext/http/operations/authentication` |
| CUSTOMER 邮箱注册、验证、密码重置、登录、工作区上下文 | REQ:379、REQ:380 | `ClientAuthenticationApi`，共 12 个路由 | 已实现 | 只有 CLIENT 渠道 |
| Google OIDC 登录与账号绑定 | AUTH:235-290，REQ:360 | 无。`t_federated_identity` 没有 Mapper，模型里也没有 `t_oidc_provider` | 未实现 | Client 本期已明确延后（REQ:379） |
| Partner 渠道认证、Cookie、Audience | AUTH:79、AUTH:376 | 无 Partner 端点，仓库无 `partner-console-bff` | 未实现 | `GatewayRouteChannel.PARTNER` 只是枚举值 |
| 生产 KMS 签名 / HMAC | AUTH:104 | `LocalHumanAuthenticationCryptography.java:25` 有 TODO | 部分 | 本地注入私钥 |
| 密码泄漏语料校验 | AUTH:222 | `OperationsPasswordPolicy.java:17` 有 TODO | 部分 | 本地集合只用于演示 |
| 邮箱变更 | AUTH:293 | 无 | 未实现 | |

### Tenant IAM

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| Client 成员、邀请、角色 | REQ:361、REQ:382 | `ClientTeamApi`、`ClientInvitationApi` | 已实现 | 有测试 |
| Operations 租户查询与安全暂停 / 恢复 | REQ:361 | `TenantAccessManagementApi`，9 个路由 | 已实现 | 没有入口测试类 |
| CCT 租户投影 PLT-I17 | REQ:381 | `TenantProjectionApi`，调用方是 client-master | 部分 | 查询只接受 CLIENT（`GetTenantProjectionRequest.java:14`） |
| Partner 租户成员与邀请 | REQ:361 | 无 | 未实现 | |
| PLT-I02 授权 | REQ:367 | `OperationsAuthorizationApi`、`ClientAuthorizationApi` | 已实现 | 没有 Partner BFF 调用方 |

### 公共 API 控制面

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| Target / Route / Guard 管理 | CP:512-604，REQ:362 | `GatewayConfigurationManagementApi`，Guard 嵌在 Route 请求体中 | 已实现 | 没有入口测试，只有 `GatewayRouteIdempotencyModeTest` |
| 全局 Snapshot（PLT-I20） | CP:619-730 | `GatewaySnapshotQueryApi` | 已实现 | mock Profile 下健康检查为 DOWN（02a 报告第 6 节第 8 项） |
| Console Resource | CP:430、REQ:363 | `ConsoleResourceManagementApi` | 部分 | 新建固定为 OPERATIONS（`CreateConsoleResourceProcessor.java:82`），Client / Partner 菜单未做 |
| Permission、API Scope 管理 | REQ:362 | `PermissionManagementApi`、`ApiScopeManagementApi` | 已实现 | 没有入口测试（CI:335） |
| Console Navigation（PLT-I21） | REQ:385 | 只在 `OperationsAuthorizationApi` 里 | 部分 | 只服务 Operations |

### API Credential

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| PLT-I03 凭证校验 | REQ:368 | `ApiCredentialValidationApi`，调用方是 Gateway | 已实现 | 有 4 个测试类 |
| Client 自助创建 / 轮换 / 撤销 | REQ:350 | `ClientApiCredentialApi` | 已实现 | |
| Operations 查看与紧急撤销 | REQ:350 | `ApiCredentialManagementApi` | 已实现 | 没有入口测试 |
| Integration Readiness（PLT-I13） | REQ:377，DBS:55 | 无 | 未实现 | CI:151 写明 Webhook 条件未实现 |

### 通知（Email / SMS / 模板 / 供应商）

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 普通 Email 受理、补偿投递、主备账号 | REQ:354、REQ:374 | delivery 模块的 7 张表 | 已实现 | |
| 内部 HTTP 提交（PLT-I09） | REQ:374 | `NotificationCommandApi` | 部分 | 没有任何业务系统调用方，也没有状态查询接口（CI:450） |
| 供应商 | — | 只有 `SesEmailProvider` | 部分 | SES 仍在沙箱，生产 AWS 资源未建（CI:415） |
| SES 投递事件 | CI:407 | `NotificationEventPollJob` | 已实现 | 没有监控面板 |
| 模板、账号、路由、记录管理 | CI:425 | `NotificationManagementApi`，17 个路由 | 已实现 | |
| SMS | DBS:147 | 只有枚举 `NotificationMedium.java:14` | 未实现 | `SubmitNotificationRequest.java:53` 写明不属于本期 |

### Webhook

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 受理、签名投递、重试、DEAD、Replay | `agent-docs/decisions/2026-10-15-platform-webhook-delivery.md` | 5 张表，`WebhookEventApi` 由 acquiring 调用 | 已实现 | Vault 签名规则只在 mor-test 登记（CI:151） |
| Operations 代配置、Client 自助 | 同一决定 D-02 | `WebhookManagementApi`、`ClientWebhookApi` | 已实现 | |
| 争议 RFI 事件（PLT-04） | REQ:352 | `WebhookEventType.java` 只有 8 种订单和退款事件 | 未实现 | |

### 文件与文档

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 入驻上传、下载、校验，审核下载 | REQ:369 | `OnboardingFileApi`、`OnboardingFileVerificationApi`、`ReviewFileApi`，存储用 S3 / MinIO | 已实现 | |
| 通用 Tenant 文件读写，供各业务系统使用（PLT-I04、PLT-07） | REQ:355、REQ:369 | 无通用 API | 部分 | 只覆盖入驻场景 |
| 病毒扫描与 DLP | DBS:117 | 只有 `FormatFileScanner`，注释在第 27 行 | 未实现 | 类型有 ANTI_MALWARE 和 DLP，但没有扫描器 |
| 消费者凭证（PLT-10、PLT-I10、PLT-I14） | REQ:357、REQ:375、REQ:378，DBS:117 | 无。凭证相关 6 张表既无 Mapper 也不在 Schema | 未实现 | |

### 外部 API 集成与渠道标准 API

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 标准 API、接入方、原子 API、脚本、流程配置，编辑器与沙箱验证 | `agent-docs/decisions/2026-09-21-platform-integration-http-execution.md`、`agent-docs/decisions/2026-10-13-platform-standard-api-flow.md` | 7 个 externalapi API，10 张表 | 已实现 | |
| 标准 API 执行 | 同上 | `StandardApiCommandApi`，调用方是 channel-connector | 已实现 | |
| 渠道账号 Reveal 初始化函数 | `agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md:37` | `ChannelAccountConfigFunction` | 已实现 | CI:320 写的"未实现"已过期 |
| 读卡票据 | V2 设计 05 节 | `PaymentCardFunction` | 已实现 | |
| 各渠道已付款链路 | CI:262、CI:268 | 配置已有 | 部分 | Alipay+、Toss、PayerMax 已付款链路未实测，PayBy DIRECTPAY 的 PAY 依赖卡直付 |
| 渠道异步回调（CHANNEL_CALLBACK） | CP:144 | 无 | 未实现 | 各渠道需求都把回调列为不在范围 |

### 审计与安全

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| Operations 尽力审计与审计查询（PLT-I06） | CI 中 AUDIT-01 | audit-security 模块（审计链、事件、属性、安全事件） | 已实现 | |
| 其他系统和 Vault 写入审计（PLT-I05、PLT-08） | REQ:356、REQ:370 | 无 HTTP API | 未实现 | |
| 审计完整性点 | DBS:198 | `t_audit_integrity_point` 无 Mapper，不在 Schema | 未实现 | |
| 安全告警 | DBS:200-202 | `t_security_alert*` 三张表无 Mapper，不在 Schema | 未实现 | |
| 业务 Email OTP（PLT-11、PLT-I11） | REQ:358、REQ:376 | `t_email_otp_*` 两张表无 Mapper | 未实现 | |

### 内部 Token（D26）

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 签发端与 JWKS | V2 决定 D26 | `InternalTokenIssueApi`、`InternalJwksQueryApi`，8 个测试类 | 部分 | 只用配置注入的 STATIC 密钥（`APP:237`），prod 拒绝启动 |
| 签名密钥表驱动 | 02a 报告第 4 节 | Schema 中 `t_jwt_signing_key` 没有 `key_usage` 列 | 未实现 | SQL 草案未执行（CI:79） |
| REQUIRED 模式 | CI:78 | 所有服务的模式都是 OFF | 未实现 | 需先 DUAL 观察，期限未定 |
| 02a 待确认项 | 02a 报告第 6 节 | — | 待确认 | 共 11 项，例如签发端依赖人工认证开关、调用方 Header |

### 可靠性（补偿 / XXL）

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| 补偿任务 | AGENTS | `P/compensation-event-catalog.yaml` 中 4 类任务（FILE_SCAN、INTEGRATION_VERIFY_RUN、NOTIFICATION_DELIVERY、WEBHOOK_DELIVERY） | 已实现 | |
| XXL 扫描 | CI:130-134 | 只在 mor-test 配置 | 部分 | 本机没有 XXL Admin，重启后约有 1 分钟调度失败 |
| MQ 发布与消费 | V2 决定 C5 | 只装配了编解码，没有 AMQP（`APP:249`） | 未实现 | |
| 旧命令幂等组件 | AGENTS 请求幂等决定 | `OperationsCommandIdempotencyService` 和 `t_command_idempotency` 仍在 | 部分 | 无调用方，属于待迁移的死代码 |

### 运营控制台相关 BFF 支撑

| 能力 | 设计来源 | 代码证据 | 状态 | 备注 |
|---|---|---|---|---|
| Operations BFF | `agent-docs/APPLICATION_ARCHITECTURE.md` | 消费 19 个 Platform API | 已实现 | |
| Client Console BFF | 同上 | 消费 7 个 API | 已实现 | |
| Partner Console BFF | `agent-docs/APPLICATION_ARCHITECTURE.md:52` | 无 | 未实现 | |
| 币种参数接口 | 02a 报告第 5 节 | `CurrencySpecQueryApi` | 部分 | 无任何调用方 |

## 未实现与部分实现汇总

**未实现：**
1. Google OIDC 登录与绑定。
2. Partner 渠道全链路，包括认证、BFF、租户成员与邀请、租户投影和菜单。
3. 业务 Email OTP。
4. 消费者凭证的生成与查询。
5. Integration Readiness 查询。
6. 文件病毒扫描与 DLP 扫描器。
7. 其他系统和 Vault 写入审计的接口。
8. 审计完整性点与安全告警。
9. SMS。
10. 争议 RFI 类 Webhook 事件。
11. 渠道异步回调。
12. 内部 Token 签名密钥表驱动与 REQUIRED 模式。
13. Platform 的 MQ 发布与消费。
14. 邮箱变更。

**部分实现：**
1. KMS 和密码泄漏语料仍为本地实现，代码中有 TODO。
2. 通知内部接口没有业务调用方，也没有状态查询。SES 仍在沙箱，生产 AWS 资源未建。
3. 通用 Tenant 文件接口只覆盖入驻场景。
4. 租户投影和 Console Resource 只做了 CLIENT 或 OPERATIONS。
5. 内部 Token 只有 STATIC 密钥。
6. 各渠道已付款链路未实测，PayBy DIRECTPAY 的 PAY 待卡直付。
7. XXL 扫描只在 mor-test 配置。
8. 旧命令幂等死代码未清理。
9. Webhook 签名的 Vault 规则只在 mor-test 登记。
10. 币种接口无调用方。
11. 入口测试有缺口：Permission、API Scope、Console Resource、网关配置、Operations 凭证管理和租户访问没有入口测试类。
12. 覆盖率门禁未达标：Platform 的 coverage-gate 仍低于 0.90（CI:98）。
13. 部署状态：多数需求只在本地提交未推送。V2 只在 refactor-main，未部署 mor-test。

**台账过期：** CI:320 关于 Platform 初始化函数"未实现"的说法已被代码中的 `ChannelAccountConfigFunction` 取代。CI:1054 的"64 表、33 表 Schema"也与当前的 70 张模型表和 57 张 Schema 表不符。

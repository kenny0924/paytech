# Platform Support 数据库设计规范

> **文件多存储并存（2026-10-22，第二段）：** 依据[第二段方案](../../requirements/20261022/platform-support-文件多存储并存/segment2.md)与[文件上传、引用与下载原则决定](../decisions/2026-10-22-file-upload-address-principles.md)，入驻文件的上传、下载改由 client-console-bff / operations-console-bff 调内部文件接口，业务系统只保存 Platform 签发的文件地址 `/files/v1/{fileId}`。Platform 删除 `t_file_upload.owner_system / owner_type / owner_ref_id` 与 `t_file_owner_ref`，文件不再记录业务归属，只记来源系统与 `reason_code`；本文下列关于入驻文件 Owner 申请＋主体、Owner Ref 授权、PLT-I04 入驻租户解析的描述均被取代。下载授权可带人工操作人，由 Platform 写下载审计。增量 SQL 见 `paytech/platform-support/docs/database/sql/changes/2026-10-22-file-owner-removal.sql`，执行状态只见当前台账。

> **文件多存储并存（2026-10-22，第一段）：** 依据[需求](../../requirements/20261022/platform-support-文件多存储并存/requirement.md)与[技术方案](../../requirements/20261022/platform-support-文件多存储并存/technique.md)，文件图组新增 `t_file_storage_instance`（存储实例：类型 MINIO/S3、地址、区域、私有证书、凭据方式、密钥 Vault Token `FILE_STORAGE_CREDENTIAL_V1`）、`t_file_storage_bucket`（存储桶与启用检查结果）、`t_file_storage_route`（来源系统 → 桶、扫描策略、上传期限，默认路由 `*`）三张表；`t_file_upload`、`t_file_object` 增加 `source_system`、`reason_code`（现有记录回填 `platform-support` / `CLIENT_ONBOARDING_MATERIAL`），`t_file_object` 增加 `idx_file_source`；`t_file_upload` 的 `tenant_id`、`uploader_id` 与 `owner_*` 改为可空（内部文件接口的上传没有租户与 Owner，纯系统上传没有上传人）。实例代码即记录上的 `storage_provider`、桶名即 `storage_container_ref`，被记录引用后只能停用；读取永远按记录坐标，不受路由影响。内部系统之间不做文件级权限，文件只记来源系统与原因。第二段再删除 `owner_*` 与 `t_file_owner_ref`。增量 SQL 见 `paytech/platform-support/docs/database/sql/changes/2026-10-22-file-storage-management.sql`（未执行），执行状态只见当前台账。

> **渠道协议迁出（2026-10-20）：** 依据[统一对外出口决定](../decisions/2026-10-20-channel-connector-unified-outbound.md)与[技术方案](../../requirements/20261020/channel-connector-统一对外渠道出口/technique.md)，下一条所述 integration 图组 10 表（`t_standard_api`、`t_api_field`、`t_integration*`、验证四表）的 Owner 改为 channel-connector（`mor_channel_connector`，模块 `paytech-channel-connector-protocol`），Platform Schema 由 57 张减为 47 张；下一条保留为字段与历史设计参照，不再表示 Platform 拥有这些表。2026-10-22 第二段在 channel-connector 内把 `t_integration` 并入 `t_channel`、7 张 `t_integration_*` 改名为 `t_channel_*`，与 Platform 无关；Platform 只保留运营控制台的注册数据（块 71、92、129）。代码与 SQL 执行状态只见[当前台账](../CURRENT_IMPLEMENTATION.md#channel-connector-phase2-20261022)。

> **工作流并入（2026-10-22）：** 依据[工作流并入 Platform 决定](../decisions/2026-10-22-workflow-into-platform-support.md)与[技术方案](../../requirements/20261021/platform-support-工作流/technique.md)第 4.2、6 节，PDManer 新增图组 `workflow`（10 张表），当前 Schema 由 47 张设计为 57 张；字段与规则见第 16 节。本条只描述设计，SQL 执行、代码与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

> **Integration 后继（2026-09-21，2026-10-13 修订）：** [归属决定](../decisions/2026-09-21-platform-integration-http-execution.md)将受控外部 API 配置和执行纳入 Platform；[标准 API 与流程决定](../decisions/2026-10-13-platform-standard-api-flow.md)改为标准 API 加渠道同步流程。六表见[数据设计](../../requirements/20260914/platform-support-外部API配置与执行/data-design.md)与[物理设计](../../requirements/20260914/platform-support-外部API配置与执行/physical-design.md)，2026-10-13 已进入 PDManer（图组 integration），SQL 未执行；本项是下文“无通用技术配置”的窄例外，不扩展任意配置中心。2026-10-14 同图组增加沙箱验证四表（验证账号、用例、运行、运行步骤），保存验证证据与非敏感沙箱账号材料，依据[沙箱验证决定](../decisions/2026-10-14-platform-integration-sandbox-verification.md)。未因此更新正式模型数量，实际状态见当前台账。

> **Notification 局部后继（2026-09-21）：** [Delivery 需求](../../requirements/20260920/platform-support-Delivery领域规划/requirement.md)及[七表字段](../../requirements/20260920/platform-support-Delivery领域规划/data-design.md)取代本文前序九表、Revision、消息快照、Callback/Suppression、通知 Hash 与领域 Lease/Fencing 描述；未涉及的 Platform 领域保持原合同。当前生成统计、SQL 执行与验证状态只见[当前台账](../CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920)。下列前序总量/拓扑保留历史参照，不是当前模型统计。

> **Client Console 入驻目标补充（2026-09-14）：** 用户确认同一账号只有一份未完成 Client 申请。本期上传仍禁止浏览器指定 Tenant，并在原 PLT-I04 唯一候选规则上补充 CCT 申请授权与目标 Client 一致性核对，详见[本期数据评审稿](../../requirements/20260914/client-console-注册与客户入驻/data-design.md#5-邮箱链接会话与文件)。投影未就绪或存在残留歧义时保持拒绝，不能因数量限制跳过核对；Partner、普通 Tenant 文件规则不变。本条不表示数据库或运行合同已迁移。

> **Client API Scope 后继契约（2026-09-13）：** 本文的公共控制面基线之外，新增独立 Scope 目录及两项查询索引，以[已确认字段与关系](../../requirements/20260913/platform-support-ClientAPI接入管理/data-design.md)为准；原 Credential/Grant 表与 PLT-I03 认证语义保持。本文保留原阶段统计/摘要作为历史证据；当前生成统计、实施和环境状态只见[当前台账](../CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。

最后更新：2026-09-04

> **2026-09-05 操作审计专项决定：** 既有 Operations HTTP 操作改为 Controller 注解声明、业务现场采集、独立事务尽力写入，允许丢失且审计故障不改变业务结果。具体范围与前序取代关系见[审计决策](../decisions/2026-09-05-platform-controller-best-effort-audit.md)。审计自身的链/事件/属性原子性和业务身份/幂等保护继续有效；这不是全 Platform 审计降级、模型迁移或已实施声明。

> **2026-09-04 物理实施规则：** 依照 [数据库设计手册](../DATABASE_DESIGN_GUIDE.md)，SQL 不创建 CHECK 或 FOREIGN KEY，保留主键、唯一索引与 NOT NULL。本文及模型中的 FK/复合 FK/CHECK 均表示逻辑关联与业务不变量，不再要求物理约束。已实现的 22 表、273 字段与 PDManer 的结构及完整中文注释一致；其余 41 表不提前落库。当前认证、租户授权、Snapshot/JWKS 和审计通过代码、完整 Tuple 查询、本地事务、锁及 CAS 保证一致性。

状态：**V1 已确认并已生成；PDManer 4.9.3 应用内人工评审待完成**

> **2026-09-05 跨主题对齐：** 本文拥有 Platform 业务事实，不单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。当前生成模型中的旧横切字段是 [当前实现台账](../CURRENT_IMPLEMENTATION.md) 记录的迁移债务；新增实现必须遵循 [文档治理路由](../DOCUMENT_GOVERNANCE.md)，不得复制旧契约。

> **当前权威：** 本文受 [Platform Support 57 表精简设计](../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)、[统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 与 [统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 约束。57 表阶段仍是所有未受影响领域的直接前序；2026-09-01 公共控制面基线增加六张最小表后为 63 表、822 字段、123 个唯一索引、99 个访问索引、107 条本库关系、5 条 1:1、7 个 Diagram Groups、31 条复合 FK、85 条 CHECK，以及恰好 36 个以 `_hash` 结尾的字段。早期 92/97 表和旧 Gateway Policy/Release 结构只保留为历史。

> **实施边界：** 完整 63 表当前只批准逻辑文档、Manifest、Policy、自动测试和 `platform-support.pdma.json`。除独立批准的 Phase A 21 表手工 SQL/PLT-I20、Operations PLT-I22 JWKS 与 Nacos Discovery 切片外，它不授权其余数据库 SQL、Java 模块、登录/Token 签发、CUSTOMER JWKS、KMS 私钥操作、Secrets/Vault、Redis、MQ、BFF、Gateway 消费或部署实现。

## 1. 系统定位

Platform Support 是六个核心系统之一，拥有全局人工 Account、Tenant IAM、API Credential、文件与消费者文档、通知、Client Webhook、审计安全和本地可靠执行事实。它不是共享数据库，也不拥有 Client/Partner 主档、Order、Payment、Risk Case、Ledger、Tax/Fiscal 或 Settlement 状态。

Gateway 和入口应用位于六个核心系统之外：Gateway 负责入口、最小 JWT/API Key 校验、公共 Route Snapshot 执行、Redis 限流、Request ID 与访问日志；BFF 负责渠道固定、Cookie/Origin/CSRF、PLT-I02 调用和展示适配。Platform 拥有公共 Catalog 当前配置，但 Gateway/BFF 不拥有 Platform 或核心系统业务事实；动态 Runtime 切换仍是后续实现。

## 2. 数据所有权

### 2.1 Platform 拥有

- `OPERATIONS` 与 `CUSTOMER` Realm 下的全局人工 Account、Password Credential、Google Federated Identity、Authentication Challenge、Session、Refresh Generation 和签名公钥元数据；
- `INTERNAL`、`CLIENT`、`PARTNER` Access Tenant 投影，以及 Tenant Member、Invitation、Role、Permission 与授权关系；
- CLIENT Tenant-owned API Credential 与显式 Scope；
- 业务 Email OTP，不作为登录 OTP/MFA；
- 文件上传、对象存储引用、扫描策略/要求/结果，以及运营维护的存储实例、存储桶与写入路由（2026-10-22；Owner 引用已于第二段删除）；
- Consumer Document、Document Version、Delivery 与 Access Grant；
- Notification Provider、Template、Route、Request、Delivery、Attempt、Callback 与 Suppression；
- Webhook Endpoint（含订阅事件类型）、Signing Secret 引用、Event、Delivery 与 Attempt（2026-10-15 后继合同见第 9 节）；
- Audit Hash Chain、完整性点、Security Event/Alert；
- 六类外部 HTTP 入口的受控 Target、Route、Header/Query Allowlist、全局 Guard 参数，以及 Operations/Client/Partner Console Resource；
- Operations 工作流：流程定义与版本、动作模板、流程实例、节点 / 动作运行、验证记录、审批、转交与通知回调（2026-10-22，见第 16 节）；
- 本服务的 Compensation Event 与 Command Idempotency。

### 2.2 只保存稳定引用

- CCT 的 Client、Local Partner、Application 与资格事实；
- Commerce 的 Order、Refund、Order Context、Legal File 和 Consumer Reference；
- Finance 的 Fiscal Event/Record；
- Payment、Risk 与其他系统的业务事件键；
- Vault Value Token、KMS/Secrets Manager Key Ref、对象存储 Object Ref；
- BFF/Gateway Request、Actor、Session 与 Trace 标识。

所有引用均不建立物理 FK。Platform 内部 Tenant/Owner 一致性通过完整关联 Tuple 查询、唯一键、本地事务和锁证明，不能仅凭外部传入的 ID 授权。

## 3. 精确 63 表拓扑

| Diagram Group | 表数 | 精确物理表 |
| --- | ---: | --- |
| 身份、授权与接入 | 18 | `t_user_account`、`t_password_credential`、`t_federated_identity`、`t_access_tenant`、`t_tenant_member`、`t_tenant_invitation`、`t_authentication_challenge`、`t_auth_session`、`t_refresh_token_generation`、`t_jwt_signing_key`、`t_role`、`t_permission`、`t_tenant_member_role`、`t_role_permission`、`t_api_credential`、`t_api_credential_scope`、`t_email_otp_challenge`、`t_email_otp_attempt` |
| 文件与消费者文档 | 11 | `t_file_upload`、`t_file_scan_policy`、`t_file_scan_requirement`、`t_file_object`、`t_file_scan_result`、`t_document_template`、`t_document_template_translation`、`t_consumer_document`、`t_document_version`、`t_document_delivery`、`t_document_access_grant` |
| 通知与 Email | 9 | `t_notification_provider`、`t_notification_request`、`t_notification_delivery`、`t_notification_attempt`、`t_notification_route`、`t_notification_provider_callback`、`t_notification_suppression`、`t_notification_template`、`t_notify_template_translation` |
| Client Webhook | 5 | `t_webhook_endpoint`、`t_webhook_signing_secret`、`t_webhook_event`、`t_webhook_delivery`、`t_webhook_attempt`（2026-10-15 由 8 表收敛，见第 9 节） |
| 审计与安全 | 8 | `t_audit_chain`、`t_audit_event`、`t_audit_event_attr`、`t_audit_integrity_point`、`t_security_event`、`t_security_alert`、`t_security_alert_event`、`t_security_alert_action` |
| 公共 API 控制面 | 6 | `t_gateway_target`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_console_resource` |
| 可靠执行 | 2 | `t_compensation_event`、`t_command_idempotency` |
| 工作流（2026-10-22 新增，不计入上方 63 表历史统计） | 10 | `t_workflow_definition`、`t_workflow_definition_version`、`t_workflow_action_template`、`t_workflow_instance`、`t_workflow_node_run`、`t_workflow_action_run`、`t_workflow_verification`、`t_workflow_approval`、`t_workflow_transfer`、`t_workflow_callback` |

受控出站 API 定义自 2026-10-20 起由 channel-connector 管理（见顶部迁出说明），Platform 不再持有相应表；Platform 不建立 Gateway Policy/Release/Snapshot/Ack、任意通用技术配置、通用 Dictionary、通用 Job、Inbox/Outbox、Retention、Legal Hold、Archive Workflow 或自动销毁表。

## 4. 通用物理规则

### 4.1 主键、时间、命名与并发

- MySQL 8.0 / InnoDB；表使用 `t_`，唯一索引使用 `uk_`，访问索引使用 `idx_`，技术名称不超过 32 字符。
- 每表恰有一个服务侧 Snowflake `BIGINT` 主键，禁止 `AUTO_INCREMENT`。
- UTC 时刻使用 `DATETIME(6)`，业务日期使用 `DATE`；有效期采用 `[effective_from,effective_to)`。
- `version` 只用于真实可变 Root、活动 Slot、Lease/Fencing 或乐观锁对象；追加式 Event/Attempt/Result 不机械增加 `version/update_time`。
- 不使用以 `_ver` 结尾的物理表名。业务历史通过必要的 `document_version`、`webhook_endpoint_revision`、`webhook_signing_secret`，或同表 `revision_no/rotation_no` 表达。
- 状态终结代替通用软删除；MVP 默认保留数据，不自动归档、匿名化或销毁。

### 4.2 Revision 策略

以下配置采用“每次修订插入同一表新行”的单表 Revision：

- File Scan Policy；
- Document Template 与 Translation；
- Notification Provider、Route、Template 与 Translation。

业务键 + `revision_no` 唯一；ACTIVE 行使用 `active_slot=1` 的条件唯一语义。DRAFT 可编辑，ACTIVE 内容冻结，后续修改创建新 Revision。`version` 是该行并发 CAS，不是业务 Revision 号。

必须独立保留的历史只有：

- Document Version：真实可下载文件、模板/翻译、Renderer、替代链与活动 Slot；
- Webhook Signing Secret：轮换宽限、Vault 引用与历史 Attempt 签名锚点（端点 URL 不保留修订历史，Attempt 记录实际请求地址）。

### 4.3 敏感数据

- Password 与 API Key 只保存 Argon2id PHC verifier；明文只在创建或输入时短暂存在。
- Authentication Challenge、Refresh Token、业务 OTP 只保存 lookup/HMAC/Key Version 等不可逆验证材料。
- Google authorization code、Access/Refresh/ID Token、OIDC client secret、KMS private key、Webhook signing secret 值、本地 Ciphertext/DEK、PAN/CVV/Track/PIN 永不入库。
- Email、URL、消息正文等需恢复值使用稳定 Vault Token；Masked Value 只为批准展示。Blind Index 仅在确有等值查询/抑制消费者时保留。
- File 内容只在加密对象存储；数据库保存高熵 Object Ref、Object Version、Content Hash、大小、MIME、分类和非秘密 Key Ref。

### 4.4 精确 Hash Allowlist

最终模型只有以下 36 个 `*_hash` 字段；任何新增 Hash 都必须先指出当前消费者与失败语义：

| 消费者 | 允许字段 |
| --- | --- |
| 来源投影/认证/业务 OTP | `access_tenant.source_payload_hash`、`authentication_challenge.active_scope_hash`、`email_otp_challenge.subject_ref_hash`、`email_otp_challenge.context_ref_hash`、`email_otp_attempt.ip_hash`、`email_otp_attempt.device_hash` |
| File/Document | `file_object.content_hash`、`consumer_document.commerce_source_payload_hash`、`consumer_document.fiscal_source_payload_hash`、`consumer_document.consumer_ref_hash`、`document_access_grant.consumer_ref_hash` |
| Notification | `notification_request.source_payload_hash`、`notification_attempt.request_hash`、`notification_provider_callback.payload_hash` |
| Webhook | `webhook_event.source_payload_hash`、`webhook_event.payload_hash` |
| Audit/Security | `audit_chain.previous_partition_hash`、`audit_chain.last_event_hash`、`audit_event.previous_event_hash`、`audit_event.attribute_root_hash`、`audit_event.event_hash`、`audit_event.source_payload_hash`、`audit_event.payload_hash`、`audit_event_attr.value_hash`、`audit_integrity_point.start_event_hash`、`audit_integrity_point.end_event_hash`、`audit_integrity_point.root_hash`、`audit_integrity_point.content_hash`、`security_event.source_payload_hash`、`security_event.event_hash` |
| 可靠执行 | `command_idempotency.request_hash` |

PHC verifier、HMAC、Blind Index、Secret Fingerprint 与 KMS Signature Ref 使用表达其具体安全语义的字段名，不伪装为泛化 Hash。不存在 Scope Set、Reason Set、Requirement Config、Evaluation Evidence、Route/Template Content、响应正文或可推导集合 Hash。

## 5. 身份、Tenant IAM 与认证

### 5.1 Realm、Channel 与登录方式

- Realm 恰为 `OPERATIONS`、`CUSTOMER`；Channel 恰为 `OPERATIONS`、`CLIENT`、`PARTNER`。
- Operations 仅支持 Email + Password。Bootstrap Admin 只用于首次初始化；初始化完成后创建普通 Email Human Admin 并关闭 Bootstrap。
- Client/Partner 使用 CUSTOMER Account，支持 Email 注册、Password 登录和 Google Authorization Code + PKCE + state + nonce。
- Google `provider_code` 当前固定为 `GOOGLE`；Issuer、Client ID、Redirect URI、Discovery/JWKS 位于静态部署配置，不建 Provider 表。
- Google 返回相同且已验证 Email 不自动绑定既有 Account。必须先以现有 Password 或既有 Google Identity 重新认证，再显式创建 Federated Identity。

### 5.2 Account、Credential、Challenge 与 Session

- Account 是全局登录身份，不带 Tenant 所有权。`login_name` 只允许唯一 Bootstrap Account 使用并固定为 `admin`；普通 HUMAN Account 的 `login_name` 必须为空，登录邮箱使用 Realm 内唯一的稳定 Vault Token + Masked，原始 Email 不入库。
- Password Credential 与 Account 为 `1:0..1`。Google-only Account 可无 Password；创建/变更 Password 增加 Credential Version 并使旧 Session Snapshot 失效。
- Authentication Challenge 用于注册 Email、Password Reset/Email Change 和 Invitation Accept；保存活动 Scope Hash、高熵 Secret HMAC、Key Version、Attempt 限制和替代链。
- 业务 Email OTP 只用于 Email Ownership、Consumer Contact 或低风险业务验证；活动唯一性由 Tenant/Subject/Purpose/Context 明细 Tuple + `active_slot` 实现，不需要再复制 Scope Hash。
- Account Context 不带 Tenant Claims；Tenant Context 必须同时带 Tenant、Member、Channel，且 Channel 与 Tenant Type 一致。Session 的 `(account_id, realm)` 必须命中同一 Account，Tenant Context 的 `(tenant_member_id, tenant_id, account_id)` 必须命中同一 Member；因此不能把 Account A 的 Session 绑定到 Account B 的 Tenant Member。一个 Access Token 只绑定零个或一个当前 Tenant Member。
- Tenant Switch 不修改原 Session：PLT-I16 新建固定 Tenant Context Session，撤销当前 Channel 旧 Refresh Family，签发不复用旧 Session 的 Audience Token，并由 BFF 替换对应 `__Secure-*-refresh` Cookie。
- Refresh Token 每次轮换为新 Generation。旧 Generation 已消费后再次提交且 HMAC 正确，必须把整个 Session Family 置为 `COMPROMISED` 并撤销当前 Generation。
- `t_jwt_signing_key` 按 Realm/KID 管理 RS256 生命周期；数据库只保存 KMS Ref、Canonical Public RSA JWK 和验证窗口，不保存私钥或重复 JWK Hash。`OPERATIONS` 与 `CUSTOMER` 使用独立 Key Ring；Client/Partner 共用 `CUSTOMER` Key Ring，但 Token 仍以 `client-console/CLIENT` 与 `partner-console/PARTNER` 区分 Audience/Channel。

### 5.3 Tenant 与授权

- Access Tenant 类型为 `CLIENT`、`PARTNER`、`INTERNAL`。Client/Partner 使用 CCT `owner_ref_id + source_version + source_payload_hash`；Internal 使用 Platform 保留的本地 Owner 标识（当前初始化值 `owner_ref_id=1`），其 `source_version/source_payload_hash` 为空。
- `owner_status` 是 CCT 来源状态，`security_status` 是 Platform 安全开关；只有两者都允许时 Tenant 才可授权。
- 同一 CUSTOMER Account 可同时成为多个 Client/Partner Tenant Member。Member、Invitation、Tenant Member Role 的复合 FK 锁定同一 Tenant，禁止跨 Tenant 角色授予；Invitation 接受结果的 `(accepted_member_id, tenant_id, accepted_account_id)` 必须命中同一 Member，不能把接受账号与新成员错配。
- Platform Role 使用 `tenant_scope=PLATFORM + scope_key=PLATFORM`；Tenant Role 使用精确 Tenant Scope Key。`t_role.scope_key` 由 `tenant_scope + tenant_id` 派生，不接受调用方输入；`t_tenant_member_role` 通过 `(tenant_member_id, tenant_id)` 与 `(role_id, role_scope_key)` 两条复合 FK，以及 `role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)` CHECK，阻止授予其他 Tenant 的 Role。核心系统仍在资源使用点执行自己的资源授权。
- Invitation Accept 在一个本地事务中验证 Email Challenge、Tenant/Role 当前状态，创建唯一 Member，授予一个初始 Role 并关闭活动 Invitation。
- CCT-I15 `START_OR_RESUME` 先创建 DRAFT Application，并通过 PLT-I17 的 `tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash` 创建 PENDING Tenant/Member/`TENANT_ONBOARDING_OWNER`。同版本同 Hash 幂等，同版本异 Hash 冲突，旧版本拒绝。
- PENDING Tenant 不签发普通 Tenant Context Token。入驻材料由 client-console-bff 调 Platform 内部文件接口上传（2026-10-22 第二段起不再解析入驻 Tenant），CCT-I15 `SUBMIT` 冻结文件地址与哈希后才登记 Risk。CCT 批准时 Platform 撤销临时 Onboarding Role 并授予 `TENANT_OWNER`。

## 6. API Credential、派生 Readiness 与 Gateway 边界

### 6.1 API Credential

- API Credential 直接属于 ACTIVE CLIENT `tenant_id`，不复制 CCT Client ID；当前产品不为 PARTNER/INTERNAL Tenant 创建或使用 API Credential。
- 明文 Key 只返回一次；持久化 `key_prefix + key_verifier_phc`。Scope 以 `t_api_credential_scope` 行为唯一授权事实，不保存 Scope Hash。
- Rotation 创建新 Credential 并以 `rotated_from_id` 连接；撤销/过期使用状态与时间，不额外建立 Credential Event。
- PLT-I03 必须把 Credential JOIN 到同一 ACTIVE CLIENT Access Tenant，并只读取该 Credential 的当前 effective ACTIVE Scope；验证 PHC verifier 后返回可信 `tenant_id + tenant_type=CLIENT + owner_ref_id + credential_id + effective scopes`。`owner_ref_id` 只从已验证的 Access Tenant 行读取，Gateway、业务服务或调用方不得覆盖，也不得从 Key Prefix 推断 Tenant、Client 身份或权限。

### 6.2 PLT-I13 派生 Integration Readiness

Readiness 不是持久化业务事实。PLT-I13 在一个一致查询快照内同步派生：

1. 精确 Access Tenant 存在，Owner 与 Security 状态都允许；
2. 至少一个目标 ACTIVE API Credential 未过期；
3. Credential 拥有调用场景要求的全部 ACTIVE Scope；
4. 要求 Webhook 时，存在订阅了所需事件类型的 ACTIVE Endpoint（Readiness 的 Webhook 条件尚未接入，见第 9 节）；
5. （2026-10-15 起订阅并入 Endpoint，本条由第 4 条覆盖）；
6. 缺少必需 Credential/Scope/Webhook 事实返回 `NOT_READY`；Owner 或 Security 限制返回 `RESTRICTED`；歧义、数据不一致、超时或数据库错误返回技术依赖失败，全部 Fail-Closed。

当前只支持 CLIENT Tenant。成功业务响应仅包含 `status=READY | NOT_READY | RESTRICTED`、`checked_at`，并仅在非 READY 时包含稳定 `reason_code`；技术依赖失败不返回业务状态。响应不返回 Tenant/Credential/Webhook 资源 ID，也不存在 Evaluation ID/No、Current Pointer、Requirement Config、Version、Hash、证据快照或 Readiness Changed Event。

### 6.3 Gateway 与限流

所有外部 HTTP API 使用一个 Platform-owned 公共 Route Catalog；内部服务间 HTTP、MQ、XXL-Job、数据库和 Vault 调用绕过 Gateway，不创建 Route，也不消耗公共 Guard Bucket。当前数据库只保存全局当前配置：

- `t_gateway_target` 保存稳定 Target Service Code、Nacos Discovery Service Name、Channel 与启停状态；按[数据库路由决定](../decisions/2026-09-11-gateway-database-routing.md)，Gateway 从完整快照读取这些配置并执行通用校验，不维护第二份业务身份/路径名单；配置维护者负责目标业务监听与开放资格，数据库不能指定 Host、URI、Port、Secret 或脚本；
- `t_gateway_route` 保存六类 Channel、精确 Method/Path、Target、Security Profile、Authorization Mode、Cookie Mode 与传输上限；`(target_id, channel)` 复合 FK 保证 Route 与 Target 同 Channel；
- `t_gateway_route_header` 与 `t_gateway_route_query_param` 只允许完整名称，不接受通配、前缀、可信身份 Header 或任意 Query；
- `t_gateway_guard_rule` 是 Route 当前聚合子行，保存全局 Rate/Concurrency 参数。`TENANT` 只表示认证后把可信 `tenant_id` 加入 Redis Key；控制面表没有 `tenant_id`，不提供 Tenant Override，Redis Counter/Lease 也不入库；
- `t_console_resource` 只保存 `OPERATIONS|CLIENT|PARTNER` 的 `MENU|BUTTON` 当前树；`permission_id` 与 Human Route 一样独立引用 `t_permission`，但 Console Resource 与 Route 不互相引用；同 Channel Parent 由复合 FK 保证；
- 只有 Target、Route、Console Resource 三个独立根使用 `version`。Header、Query、Guard 作为聚合子项整体替换，不建 `*_ver`、Release、Snapshot、Rollback、Ack、Runtime Counter 或新 `*_hash` 表/字段。

授权关系必须保持分离：Human Route 的 `permission_id` 由 BFF 调 PLT-I02，沿 `Permission <- Role Permission <- Role <- Tenant Member Role <- Tenant Member` 读取当前授权；Client API Route 的 `required_scope_code` 与 PLT-I03 返回的 Effective Credential Scope Code 比较，不外键到某一 Tenant Credential Grant；Checkout 与 Channel Callback 使用各自 Resource/Profile 校验。`t_role.scope_key` 仍是由 Platform/Tenant Scope 派生的隔离键，不是 Client API Scope String。

Snapshot 只投影 ACTIVE Target 及其 ACTIVE Route/子项；DISABLED Target 是 Kill Switch，其 ACTIVE 子 Route 保留管理事实但不进入 Snapshot。Snapshot 没有数据库表；PLT-I20 每次都在一致性读取和完整校验后返回全量 typed DTO，不计算或返回 ETag，也不处理 `If-None-Match`/`304`。当前已部署 Operations Gateway 仍使用加固后的静态 Catalog；未来动态版本必须完整验证并原子替换 Snapshot，失败保留 Last Valid，冷启动无有效 Snapshot 不 Ready。Redis 不可用或决策不确定继续按 Gateway 权威 Fail-Closed/有界恢复；当前 Operations Redis 身份组件仍是 raw canonical socket IP 与 exact validated JWT `sub`，Redis Guard 不做 HMAC，只有 Access Log 使用独立 HMAC 假名化。

## 7. File 与 Consumer Document

### 7.1 File

- 上传固定来源系统、上传原因、上传人、原存储坐标、展示名和扫描策略；上传授权由存储适配器签发，不保存额外 upload_token_hash。完成上传后生成不可变 File Object。2026-10-22 第二段起不再固定入驻 Owner 申请＋主体（原[2026-09-15 文件合同](../../requirements/20260914/client-console-注册与客户入驻/file-upload-closure-proposal.md)的归属部分已被取代）。
- File 冻结一个 Scan Policy Revision，Requirement 明确列出 Scanner/Type/最低版本/接受结果。Scan Result 由应用在本地事务中校验 File 与 Requirement 属于同一 Policy；模型关联不导出物理 FK。
- `AVAILABLE` 只表示所有要求已通过。后续显式重扫可将同一不可变内容改为 `QUARANTINED/UNAVAILABLE`，不覆盖旧 Result。
- 不建设通用 File ACL/Event。2026-10-22 起内部文件接口不做文件级权限：持有文件 ID 的内部服务都可读取，文件只记录来源系统与 `reason_code`，业务授权由调用方（BFF）完成；业务数据保存文件地址 `/files/v1/{fileId}`。下载授权带人工操作人时，Platform 写一条 `FILE_DOWNLOAD_AUTHORIZE` 审计（操作人、文件、调用服务，不记地址）。
- 上传创建时按来源系统的写入路由固定存储实例与桶（`storage_provider` / `storage_container_ref`）并冻结路由的扫描策略；完成、恢复、检查、下载只用记录上的坐标。只有 ACTIVE 桶接受新上传，ACTIVE / READ_ONLY 桶可读，DISABLED 桶或实例读写都不可用。
- （已取代，2026-10-22 第二段）原入驻阶段 PLT-I04 由 Platform 解析唯一 PENDING Tenant/Member/Onboarding Role 后落本地 `tenant_id`；现入驻上传只要求客户已登录，由 client-console-bff 调内部文件接口，`tenant_id` 为空，历史记录保留原值。

### 7.2 Consumer Document

- Consumer Document 本身合并 Commerce Request 与 Finance Fiscal 双输入。Generation Key、Tenant/Order/Business Ref/Document Type 唯一定位根；两个来源分别冻结 Event Key、Version 和 Payload Hash。
- `t_consumer_document` 依次冻结 `order_id`、`commerce_order_context_id`、`consumer_terms_file_id`、`refund_policy_file_id` 与 `privacy_notice_file_id`；Commerce Order Context 引用必须在后续 `FiscalRecordCreated` 到达前持久化。
- Commerce 输入冻结 Order Context 与三份 Legal File ID；接入时逐一用本库 `file_id + content_hash` 核对实际 File，不复制三份 Content Hash。
- 同来源键/版本、不同 Payload Hash 进入 `CONFLICT`，不得覆盖；双输入齐备后推进 `READY_TO_RENDER`。
- Document Version 同时承担版本事实与 Render Lease/Fencing。新 Version 只有在 File 可用并生成成功后才取得 `active_slot=1`；失败的新 Render 保留旧 ACTIVE Version。
- Credit Note 通过 `(corrects_document_id, tenant_id, order_id, consumer_ref_hash)` 复合自引用，只更正同 Tenant/Order/Consumer 的既有文档；Version 通过复合自引用只替代同 Document 的旧 Version。
- Document Delivery 同时锁定 `(document_ver_id, document_id)`、`(document_id, tenant_id)` 与 `(notification_delivery_id, tenant_id)`，保证 Version、Document、Tenant 和 Notification Delivery 属于同一所有权链。
- Access Grant 同时锁定 `(document_ver_id, document_id)` 与完整 `(document_id, tenant_id, order_id, consumer_ref_hash)`，只对准确消费者/订单/文档版本提供短期授权，不签发永久公开 URL。

## 8. Notification 与 Email

Notification 现行事实、七表字段、事务与恢复规则由 [Delivery 领域需求](../../requirements/20260920/platform-support-Delivery领域规划/requirement.md)及其链接的 technique/data-design/api 拥有；本文仅保留路由，避免继续维护一份相反的九表模型。

首期普通 Email 仅开放模块内 Processor；每条 Delivery 独立收件，规范化后重复邮箱拒绝。Request 保存默认信息，Delivery 保存差异和状态；模板按语言维护当前内容。UNKNOWN 查询原 Provider/请求号，公共补偿领取配合 Delivery CAS。Document Delivery 的通知租户归属应沿 Delivery → Request 检查，前序 `(notification_delivery_id, tenant_id)` 复合关系不再适用于七表目标。

- Dispute RFI 通知只负责发送与投递证据：Client 未响应时 Risk Case 保持 `EVIDENCE_REQUIRED`，不得自动认损；必须存在 Client/Operations 的显式授权，或 Channel 已验证的 `LOST` 结果，才由 Risk 决定并发布 `DisputeLost`。

## 9. Client Webhook

> **2026-10-15 后继合同：** 以 [Webhook 投递需求](../../requirements/20261015/platform-support-Webhook投递/requirement.md)、[技术方案](../../requirements/20261015/platform-support-Webhook投递/technique.md)与 [Platform Webhook 投递决定](../decisions/2026-10-15-platform-webhook-delivery.md)为准；此前 8 表的 Revision、Verification、Subscription、Payload Vault 存储与 Lease/Fencing 描述只作历史。

- Endpoint 是 CLIENT Tenant 下的稳定根，直接保存 HTTPS URL 明文与订阅事件类型数组；每个 Client 最多 5 个，由 Operations 代配置。
- 地址保存与每次发送都解析 DNS，全部地址必须是公网地址；私网、Loopback、Link-local、保留地址 Fail-Closed，连接只使用本次校验过的地址，不跟随重定向。不做 Challenge 握手。
- Signing Secret 值保存在 Vault（`WEBHOOK_SIGNING_SECRET_V1`，REFERENCE），本库只存 Token；同一 Endpoint 只有一把 ACTIVE，轮换后旧密钥默认 1 小时内仍参与签名（双 `v1`），立即作废时宽限为 0。
- Event 属于 Tenant，按 `(tenant_id, source_event_key, source_system)` 唯一；同来源身份内容不同 Fail-Closed。冻结 Envelope 只以 JSON 保存，发送前重新做 RFC 8785 规范化并核对 `payload_hash`；Retry/Replay 不回源重建。
- Delivery 按受理时的 ACTIVE 订阅生成；人工 Replay 直接创建新 Delivery，以 `replay_of_delivery_id + requested_by` 保留来源。重试节奏与执行租约由公共 `compensation_event` 承担，投递版本 CAS 保护结果回写。
- Attempt 在发送前登记为 PROCESSING，冻结请求地址、主签名密钥与宽限密钥；超时、断连按失败重试（Client 按 eventId 去重），不做结果查询。

## 10. Audit 与 Security

- Audit Event 按 UTC Date + Shard 串行进入 Hash Chain；Event Hash 覆盖 Previous Hash、Canonical Version、Attribute Root 与核心事实。序号跳跃或 Previous Hash 不匹配必须拒绝 Append/Seal。
- Audit Attribute 只允许公开/内部 `value_text`，或敏感 `value_vault_token + value_masked` 二选一；Value Hash 参与 Attribute Root，不保存通用 Vault 元数据。
- Integrity Point 保存区间起止 Hash、Root Hash、Signature Ref 与精确 `file_id + content_hash`，用于外部/WORM 证明；它不触发热数据删除。
- Security Event 覆盖认证、授权、Credential、OTP、Webhook、File 与平台技术安全，不替代 Risk 的交易风险/KYC/KYB。
- Alert 通过显式 Event Link 与 Action 历史推进 ACKNOWLEDGED/INVESTIGATING/CLOSED/FALSE_POSITIVE。
- Vault Reveal、文件读取、授权拒绝都进入统一 Audit Event/Attribute，不再建立平行审计链。

## 11. 可靠执行

- `t_compensation_event` 与业务变更在同一本地事务写入，commit 后执行。XXL-Job 只作唤醒/分片；数据库 Claim/Lease + `lock_version` 防止多实例并发完成。Broker Confirm 后删除成功事件。
- `t_command_idempotency` 以 Caller Scope + Operation + Idempotency Key 唯一；Request Hash 相同则返回稳定 Response Ref，不同则冲突。成功不保存 Response Hash，因为可按 Response Ref 重建最小响应。
- 不建设通用 Inbox/Outbox、中央 Compensation 库或通用 Job 平台。

## 12. 关键状态与失败语义

| 对象 | 正常路径 | 关键失败关闭 |
| --- | --- | --- |
| Account | PENDING_VERIFICATION → ACTIVE → DISABLED/CLOSED | Operations 连续 5 次 Password 失败锁定 30 分钟；Security/Owner 状态变化递增撤销版本 |
| Session/Refresh | ACTIVE + ISSUED → CONSUMED → next ISSUED | 正确旧 HMAC 重用使 Session Family COMPROMISED |
| Invitation | PENDING → ACCEPTED/EXPIRED/REVOKED/SUPERSEDED | Tenant/Role/Email Challenge 任一失效则不创建 Member |
| File | PENDING_SCAN → AVAILABLE | 显式重扫可 AVAILABLE → QUARANTINED/UNAVAILABLE |
| Consumer Document | WAITING_* → READY_TO_RENDER → RENDERING → GENERATED | 同键不同 Payload 进入 CONFLICT；新 Version 失败保留旧 ACTIVE Version |
| Notification | CREATED → PROCESSING → SENT/DELIVERED | UNKNOWN 先查询 Provider；晚到 COMPLAINT/HARD_BOUNCE 优先 |
| Webhook Endpoint | ACTIVE ↔ DISABLED | 地址解析到非公网拒绝保存；停用后未完成的 Delivery 取出时 CANCELLED |
| Webhook Delivery | CREATED → PROCESSING → SUCCEEDED/RETRY_WAIT/DEAD/CANCELLED | 尝试次数用尽记 DEAD；旧版本 CAS 拒绝提交；Replay 新建 Delivery |
| Audit Chain | OPEN → SEALED | Hash/序号/签名不匹配拒绝 Seal 并产生 Security Event |
| Gateway Target/Route | ACTIVE ↔ DISABLED | Target DISABLED 时全部子 Route 从下一份 Snapshot 排除；任何 Target 身份/发现名、Path、Profile/Mode、Permission/Scope 或限制校验失败都拒绝整份候选 Snapshot |

## 13. 接口与事件契约

| 契约 | 当前语义 |
| --- | --- |
| PLT-I01 / PLT-I15 | Operations 或 CUSTOMER 注册、Email 验证、Password/Google 登录、Refresh、Logout；浏览器只经对应 BFF |
| PLT-I02 / PLT-I16 / PLT-I18 | 当前权限校验、Account/Tenant Context 选择、Tenant Invitation 创建/接受/撤销 |
| PLT-I03 | Credential JOIN 同一 ACTIVE CLIENT Tenant 并校验 PHC，只返回该 Credential 的 ACTIVE Scope 与可信 `tenant_id + tenant_type=CLIENT + owner_ref_id + credential_id`；调用方不得覆盖 Owner，也不得从 Key Prefix 推断 |
| PLT-I04 | 内部文件接口（2026-10-22 第二段）：调用方服务经 BFF 上传、查询、下载，来源系统取调用身份；返回文件 ID 与文件地址、Content Hash 与 Scan Status，下载可带人工操作人并写审计 |
| PLT-I05 / PLT-I06 | 写入与授权查询统一 Audit，不接收敏感明文 |
| PLT-I07 / PLT-I08 | 2026-10-15 起：来源系统经内部 HTTP `POST /webhooks/v1/events` 提交冻结事件；Operations 经 `/webhook-admin/v1` 维护 Endpoint/订阅/密钥、查询 Delivery/Attempt 并 Replay |
| PLT-I09 | 创建 Tenant-owned、可追踪 Notification Request/Delivery |
| PLT-I10 / PLT-I14 | 幂等生成、授权查询 Receipt/Invoice/Credit Note 及 Version/Correction |
| PLT-I11 | 创建/验证业务 Email OTP，不作为登录 MFA |
| PLT-I13 | 仅为 CLIENT Tenant 从当前 Tenant/API Credential/Scope/Webhook 状态同步派生 `READY | NOT_READY | RESTRICTED + checked_at + 非 READY reason_code`；技术失败返回接口错误且不携带 `status`，不保存 Evaluation/Version/Hash |
| PLT-I17 | 以 `tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash` 幂等创建/推进 Access Tenant；同版本异 Hash 冲突、旧版本拒绝，ACTIVE 时撤销 Onboarding Role 并授予 Tenant Owner |
| Gateway Config / Console Navigation | 内部管理能力维护六表当前配置；Snapshot API 一致读取、完整校验并每次返回全量 typed DTO，不使用 HTTP 条件缓存；Navigation 按 Channel + 当前 Permission 返回 MENU/BUTTON，均不是公网 Route |
| PLT-I22 | Gateway 通过私网 HTTP 按 Realm 读取完整 JWKS；当前只实现 `OPERATIONS`，返回唯一 ACTIVE 与仍在验签窗口内的 RETIRING RSA 公钥并严格校验大小、数量、生命周期及 public-only JWK。未来 `CUSTOMER` 端点由 Client/Partner 共用 Key Ring，但两类 Token 的 Audience/Channel 仍严格分离 |

Platform 可发布 `WebhookDelivered`、`WebhookFailed`、`NotificationSent`、`ConsumerDocumentGenerated` 和 `FileStored`。Gateway 配置采用拉取完整 Snapshot，不发布配置激活事件；Readiness 仍在使用点同步读取当前状态。

## 14. 全局不变量

1. Account 是全局身份，Tenant Member 才是 Client/Partner 授权边界；任何 Tenant-owned 根必须带 `tenant_id`。
2. Operations 只允许 Email/Password；Client/Partner 至少支持 Email 注册和 Google 登录。
3. API Credential 的 verifier、Scope 与同一 ACTIVE CLIENT Tenant 必须同时有效；Owner 只取自该 Tenant 行且调用方不可覆盖，不存在 Client ID、Key Hash 或 Scope Hash 捷径。
4. Readiness 只同步派生；不存在持久化 Readiness Evaluation、Current Pointer 或发布事件。
5. 所有外部 HTTP API 进入一个全局 Route Catalog，内部调用绕过 Gateway；Platform 保存六表当前配置，Gateway 保存不可变 Runtime Snapshot，Limiter Counter/Lease 在 Redis。
6. 同来源键/版本、不同 Payload Hash 是冲突，不得覆盖既有业务事实。
7. ACTIVE Revision/Version 使用可执行唯一键/Slot；新版本失败不破坏旧活动事实。
8. Webhook 与 Notification 都使用公共补偿任务的 Claim/Lease 与 Delivery version CAS，不另设 Lease/Fencing 列。
9. File/Audit 完整性只使用有消费者的 Hash；重复 Hash 不取代 File/Document/Event 业务身份。
10. 所有跨库引用都无物理 FK；所有本库复合所有权关系必须有父唯一键与子左前缀索引，Session/Invitation、Document、Notification 和 Webhook 不得只按单列 ID 形成跨所有者绑定。
11. Secret/Token/Card 敏感明文不入库、日志、事件、文件或普通响应。
12. MVP 默认保留数据，不自动归档、匿名化或销毁；生产受监管数据前另做法律/合规设计。

## 15. 验证与人工门禁

原公共控制面阶段门禁记录：63 表分组 `18 + 12 + 9 + 8 + 8 + 6 + 2`、822 字段、123 个唯一索引、99 个访问索引、107 条本库关系、5 条 1:1、36 Hash Allowlist、31 复合 FK、85 条具名 CHECK sidecar 契约、六张且仅六张公共控制面表、无 Gateway `*_ver`/Release/Snapshot/Tenant Override/新 Hash、无通用 Config/持久化 Readiness、无 `_ver` 物理表、无 Platform `client_id`，以及场景对每表恰有一条满足 NN/CHECK/复合身份的有效字段映射。原公共控制面阶段 Model SHA-256 为 `d07cfbe2153490c79e17715b8e43ae4e1b6c6ea1436fe8a3a6e063ddaee455bb`，Structural Projection SHA-256 为 `18b9c0ea482073457ed5084d0eddc77e51508d6964d34679905667f8a80e4722`，CHECK Policy SHA-256 为 `6260b6db67d05e37a3361c32507363271bee61bd4ad9144358c5bc8a1e6a6f3d`。PDManer 4.9.3 JSON 没有可移植 CHECK 节点；完整名称、表达式与表绑定冻结在 `scripts/pdmaner/platform-support/model-policy.mjs`，独立 Oracle 冻结其摘要，这些表达式作为代码与场景测试的不变量依据，不生成物理数据库 CHECK。

PDManer 4.9.3 仍须人工打开并检查七个 Diagram、关系方向、中文显示名和索引，并运行 Validate；sidecar 作为代码与场景测试的业务不变量清单，不映射为物理 CHECK。未批准的表不得据此落库；已批准的 22 表切片遵循独立手工 SQL 实施边界。

## 16. 工作流（2026-10-22）

完整需求、配置结构与运行规则见[工作流需求](../../requirements/20261021/platform-support-工作流/requirement.md)与[技术方案](../../requirements/20261021/platform-support-工作流/technique.md)；本节只记录表职责与关键不变量。持久化 Owner 为业务模块 `paytech-platform-support-workflow`。

| 分类 | 表 | 职责 |
| --- | --- | --- |
| 配置 | `t_workflow_definition` | 流程定义根：编码唯一、ACTIVE / INACTIVE；`current_version_no` 指向最新发布版本，`draft_version_no` 指向唯一草稿，两者在定义锁加 `version` CAS 下切换 |
| 配置 | `t_workflow_definition_version` | 草稿或已发布版本：`graph_json`（≤ 256 KiB）、`start_form_json`（≤ 50 字段）；发布时写 `action_snapshot_json` 冻结引用模板的 ID、版本、类型与配置，此后不可变 |
| 配置 | `t_workflow_action_template` | 动作目录：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM 四类可用，DATA_QUERY 只预留、不能被发布引用；停用只拦新发布 |
| 运行 | `t_workflow_instance` | 一次运行：绑定发起时的已发布版本，冻结启动值（`start.*` 变量）与人员分配；RUNNING → COMPLETED / TERMINATED |
| 运行 | `t_workflow_node_run` | 节点的一轮运行：`(instance_id, node_key, round_no)` 唯一；`branch_key` 标记并行分支，`previous_node_run_id` 记录实际来源（START 与汇合为空），驳回沿它跳过网关定位上一 TASK |
| 运行 | `t_workflow_action_run` | 动作在一轮节点中的执行：`(node_run_id, action_key)` 唯一，以最后一次验证为准，VERIFIED 后不再接受提交 |
| 证据 | `t_workflow_verification` | 每次验证的结论、证据与原因；只追加 |
| 运行 | `t_workflow_approval` | 每位审批人一行：`(node_run_id, approver_id)` 唯一；ANY / ALL 放行，退回修改（RETURNED）与驳回（REJECTED）时其余行 WITHDRAWN |
| 证据 | `t_workflow_transfer` | 动作执行人或审批人的转交历史；只追加 |
| 投递 | `t_workflow_callback` | 状态变化按规则登记的一次邮件通知或 MQ 业务回调，与状态变化同事务写入并登记 `compensation_event`，提交后执行；PENDING / SENT / FAILED |

- 实例变量不建表：`start.*` 取实例启动值，`<actionKey>.*` 取该动作最新一轮 VERIFIED 的 `output_json`。
- 人员字段保存 `t_user_account.account_id`，只允许 ACTIVE 的 Operations 账号；同一人不能既提交某节点的动作又审批该节点，在审批决定时校验。
- 工作流只写本库，不复制业务对象、不调用业务写接口；业务回调只发布 `topic_platform_workflow_event`，订阅方执行前自行校验业务事实（D13）。
- 运行表不接入 BCR；配置表的修改由统一审计记录。
- 物理命名与技术方案的差异：`trigger` 是 MySQL 保留字，回调表的触发列命名为 `trigger_code`；`comment` 在模板保留字规则内，审批意见列命名为 `decision_comment`。

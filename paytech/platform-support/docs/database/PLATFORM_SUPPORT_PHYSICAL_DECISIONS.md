# Platform Support 物理数据库决策

> **2026-09-15 文件合同后继：** [已确认文件合同](../../../../requirements/20260914/client-console-注册与客户入驻/file-upload-closure-proposal.md)在 Upload 归属/存储恢复字段及补偿表范围内取代下文旧描述。新增原申请＋主体和存储目标，移除 upload_token_hash；补偿表按现行 Foundation 15 列合同单独同步，不再要求六库同时迁移。原字段/索引摘要仅保留历史背景；当前模型、验证和数据库执行状态见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

> **Client API Scope 后继契约（2026-09-13）：** 本文的公共控制面基线之外，新增独立 Scope 目录及两项查询索引，以[已确认字段与关系](../../../../requirements/20260913/platform-support-ClientAPI接入管理/data-design.md)为准；原 Credential/Grant 表与 PLT-I03 认证语义保持。本文保留原阶段统计/摘要作为历史证据；当前生成统计、实施和环境状态只见[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。

> **2026-09-11 Target/Route 后继决定：** [数据库路由范围决定](../../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)取代下文 Gateway 静态业务 Target/Discovery/Path 名单要求。`t_gateway_target` 仍保留原 10 列和索引，仅需同步表注释；模型、完整 Schema 与运行数据库的实际同步状态见[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)，本提示不表示模型已经重新生成。

最后更新：2026-09-05

> **2026-09-05 操作审计后继决定：** 下文 Audit/Idempotency 行中“业务与审计同事务”是前序实现约束，已在既有 Operations HTTP 操作范围内被[Controller 尽力审计决策](../../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)取代。业务幂等仍与业务共同提交，审计链/事件/属性仍在独立审计事务中原子追加。本文不表示代码或运行数据库已迁移；状态见当前实现台账。

> **2026-09-05 横切规范更新：** 本文件继续精确描述当前生成的 63 表逻辑模型，但不单独拥有 Vault、
> `compensation_event` 或测试基础设施契约。模型仍保留的旧 Vault 派生字段和
> `compensation_event.payload_hash` 属于 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 中
> VAULT-01 / COMP-01 的待迁移现状，不得用于新增实现。补偿表必须按
> [六模型对齐计划](../../../../docs/superpowers/plans/2026-09-05-compensation-event-six-model-alignment.md)
> 与其他五库一次性迁移；本文更新不表示 PDManer、SQL 或运行数据库已经变更。

> **物理约束规则更新：** 当前建库 SQL 不包含 CHECK 或 FOREIGN KEY，保留 PRIMARY KEY、UNIQUE 和 NOT NULL。下文的 31 条复合 FK、85 条 CHECK 统计是保留在模型/Policy 中的逻辑关联和业务不变量，并非运行库约束。历史“必须建物理 FK/CHECK”的要求已被项目数据库手册取代。

## 22 表代码约束与注释同步

当前实施范围是 22 表、273 字段；字段定义、顺序、默认值、表/字段/索引完整注释来自 PDManer。保留已有 Path/Method 的 `ascii_bin` 与 Header 的 `ascii_general_ci` 物理映射，避免改变精确路径匹配、Header 大小写语义和索引长度。63 表逻辑模型的结构与关系图不变。

| 原数据库保护 | 当前代码责任 |
| --- | --- |
| Account/Credential、Session 身份及 Realm/Channel | Operations 查询固定 Realm、Channel、PASSWORD；登录校验账号形状和 Argon2 参数；SessionPolicy 校验完整 Account/Member/Tenant、状态、版本与期限 |
| Member/Role/Permission 所有权 | 权限、导航、可授予权限查询检查完整 Member/Tenant 与 Role Scope Tuple；写入在固定 INTERNAL Tenant 锁内校验目标及当前权限，授权 SQL 再校验成员/角色边界 |
| Session/Refresh 活动 Slot 与轮换 | 受控写入、账号/会话行锁、条件更新及保留的唯一索引；重用旧 Generation 撤销 Family |
| Gateway 配置与公钥 | Snapshot 编译器验证整份候选及关联、Profile/Mode、数值范围；JWKS 校验公钥、Realm、Slot 与生命周期；管理命令未实现，手工数据也必须通过这些读取校验 |
| Audit/Idempotency | 同一本地事务写业务与审计；Chain 锁、序号/Hash 与 Request Hash 冲突检测，保留业务唯一键 |

当前 Schema 和唯一初始化 DML 仍在 `sql/`；既有库仅显式执行审阅后的
[`2026-09-04-code-owned-constraints.sql`](sql/changes/2026-09-04-code-owned-constraints.sql)。执行前备份并确认结构/数据，迁移只移除已知 25 个 FK、47 个 CHECK 并同步注释，不清表、不改业务数据、不重跑初始化。MySQL DDL 自动提交，不假设能用事务整体回滚。
`scripts/pdmaner/platform-support-schema.test.mjs` 对照完整模型校验 22 表投影；本地 Java 数据库测试要求已手工应用新 Schema，不能在测试启动时迁移。

状态：**63 表生成模型已确定性生成并通过自动门禁；PDManer 4.9.3 应用内人工评审待完成**

> **替代声明：
** [57 表精简设计](../../../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)
>
是所有未受影响领域的直接前序；[统一公共 API 动态控制面设计](../../../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
> 在其上增加六张最小当前配置表，并继续禁止旧 92/97 表阶段的 Gateway Policy/Release、通用技术配置和持久化 Integration
> Readiness。统一人工认证、Tenant IAM、Google 登录、Gateway 静态安全边界与 Redis Runtime 所有权继续有效。

> **渠道协议迁出（2026-10-20）：** integration 图组 10 表随[统一对外出口决定](../../../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)迁往
> channel-connector（`platform-support-schema.sql` 现为 47 表，`platform-support.pdma.json` 已删去 10 个实体与 integration 图）。下文 Manifest
> 与图组统计早于该迁移且与生成器已有漂移，不作为当前表数依据；当前表归属以 `paytech/tools/arch-rules` 的 `table-ownership.yaml` 为准。

## 1. 定位与实施边界

本文记录 [Platform Support 逻辑数据库规范](../../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md) 到 PDManer 4.9.3
模型的确定性映射。目标模型为 [platform-support.pdma.json](./platform-support.pdma.json)
，只能由 [Platform Support Generator](../../../../scripts/pdmaner/platform-support/generate-platform-support-model.mjs)
从共享模板、七组 Manifest 和显式 Policy 生成；禁止直接修改 JSON 绕过 Manifest、Policy 或独立 Oracle。

本文最初冻结逻辑/物理模型，不单独授权运行时实现。当前已由后续垂直切片授权并实现 21 表 Phase A 投影、MyBatis、PLT-I20 全量
Gateway Snapshot，以及从 `t_jwt_signing_key` 读取 Operations ACTIVE/有效 RETIRING 公钥的 PLT-I22 JWKS；数据库结构和必要初始数据分别由
`docs/database/sql/platform-support-schema.sql` 与 `docs/database/sql/platform-support-initial-data.sql` 人工执行。Java
启动时不建表、不迁移、不灌数，测试复用仓库 `local-environment` MySQL。服务以 `platform-support-service` 注册到 Nacos，`auto`
测试关闭发现；Nacos Config、认证命令、KMS 私钥操作及其余业务能力仍未实现。

## 2. 冻结物理基线

<!-- PLATFORM_SUPPORT_PHYSICAL_MANIFEST_START -->

```json
{
  "modelTotals": {
    "tables": 63,
    "fields": 822,
    "uniqueIndexes": 123,
    "accessIndexes": 99,
    "relations": 107,
    "oneToOneRelations": 5,
    "diagramGroups": 7,
    "compositeForeignKeys": 31,
    "checkConstraints": 85,
    "hashFields": 36
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "d07cfbe2153490c79e17715b8e43ae4e1b6c6ea1436fe8a3a6e063ddaee455bb",
  "structuralProjectionSha256": "18b9c0ea482073457ed5084d0eddc77e51508d6964d34679905667f8a80e4722",
  "checkPolicySha256": "6260b6db67d05e37a3361c32507363271bee61bd4ad9144358c5bc8a1e6a6f3d"
}
```

<!-- PLATFORM_SUPPORT_PHYSICAL_MANIFEST_END -->

七个 Diagram Group 固定为：

| 图组             | 表数 | 物理职责                                                                                       |
|----------------|---:|--------------------------------------------------------------------------------------------|
| 身份、授权与接入       | 18 | Account、Password/Google、Challenge、Session/Refresh、Signing、Tenant IAM、API Credential、业务 OTP |
| 文件与消费者文档       | 12 | Upload、Scan Policy/Requirement/Result、File、Document/Version/Delivery/Grant                 |
| 通知与 Email      |  9 | Provider、Template、Route、Request、Delivery/Attempt、Callback、Suppression                      |
| Client Webhook |  8 | Endpoint/Revision/Verification、Signing Secret、Subscription、Event、Delivery/Attempt          |
| 审计与安全          |  8 | Audit Chain/Event/Attribute/Integrity、Security Event/Alert/Action                          |
| 公共 API 控制面     |  6 | Target、Route、Header/Query Allowlist、Guard Rule、Console Resource                            |
| 可靠执行           |  2 | Compensation Event、Command Idempotency                                                     |

## 3. 确定性生成

- 完整克隆 `agent-docs/templates/pdmaner/template.pdma.json`，保留模板 Profile、DDL 模板、Domain、Naming Rule 与 UI 元数据，只替换
  Platform 模型内容。
- `dbConn`、`dbConns` 与默认连接全部清空；模型不保存 Host、Port、Username、Password 或环境凭据。
- Entity、Field、Index、Index Field、Correlation、Diagram、Diagram Cell ID 均以固定命名空间和逻辑路径确定性派生。
- 序列化固定两空格缩进与一个末尾换行；`--check` 逐字节比较且不写文件。
- 生产 Manifest 与测试 Oracle 独立；Oracle 固定表清单、统计、摘要、复合 FK、CHECK、Hash Allowlist 与敏感字段规则。

## 4. 通用物理映射

| 语义   | 物理决策                                                                                          |
|------|-----------------------------------------------------------------------------------------------|
| 数据库  | MySQL 8.0 / InnoDB                                                                            |
| 命名   | 表 `t_`、唯一索引 `uk_`、访问索引 `idx_`；技术名最长 32 字符                                                     |
| 主键   | 每表一个服务侧 Snowflake `BIGINT`；Java/MyBatis 映射 `Long`；`autoIncrement=false`                       |
| 时间   | UTC `DATETIME(6)` → `Instant`；业务日期 `DATE` → `LocalDate`；有效期 `[from,to)`                       |
| JSON | 只用于有固定 Schema Version 的公开配置/冻结 Payload；禁止 EAV/Key-Value 替代领域列                                 |
| Hash | SHA-256 小写十六进制 `CHAR(64)`，仅限 36 个明确消费者字段                                                      |
| 枚举   | 闭合字符串码在字段说明中列全；`model-policy.mjs` 以具名 Sidecar 表达式冻结单行不变量，其余由事务或 Snapshot 编译器校验；均不生成物理 `CHECK` |
| 并发   | 可变 Root/活动 Slot 使用 `version`；Worker 使用 Lease + 单调 Fencing/Lock Version                        |
| 删除   | 业务终态代替通用软删除；MVP 不自动归档、匿名化或销毁                                                                  |

所有关系都是 Platform 本库逻辑关系；外部系统、Vault、对象存储、Google 和 Redis 只保存稳定引用。Gateway Runtime 只消费
Platform Snapshot，不反向成为数据库父表。任何关系都不生成物理 FK；Owner 应用在本地事务中校验完整 Tuple，并以逻辑 `RESTRICT`
语义拒绝级联删除证据/历史。

## 5. Revision 与历史结构

模型中没有以 `_ver` 结尾的物理表。配置 Revision 使用同表新行：

- `t_file_scan_policy.revision_no`；
- `t_document_template.revision_no` 及其 Translation；
- `t_notification_provider.revision_no`；
- `t_notification_route.revision_no`；
- `t_notification_template.revision_no` 及其 Translation；
- `t_webhook_subscription.revision_no`。

ACTIVE 行以业务键 + `active_slot=1` 保证唯一。只有三类历史具有独立实体价值：

- `t_document_version`：每份可下载文件、Renderer、Template/Translation、替代链和活动 Slot；
- `t_webhook_endpoint_revision`：URL Vault Token、验证结果与历史 Delivery 锚点；
- `t_webhook_signing_secret`：Rotation Window、Secret Ref 与历史 Attempt 锚点。

普通 `version` 只是 CAS，不表示发布版本。追加式 Owner Ref、Scan Result、Delivery Link、Attempt、Callback、Webhook Event、Audit
Event 没有 `update_time`。

## 6. 精简与替代清单

以下旧物理名不属于当前模型；它们的消费者已被合并、派生、静态部署配置或统一 Audit/可靠执行覆盖。

### 6.1 身份与 Readiness

- `t_oidc_provider`：Google 元数据改为静态部署配置；Federated Identity 直接保存 `GOOGLE + provider_subject`。
- `t_credential_event`：Credential 当前状态、`rotated_from_id` 与统一 Audit 足够。
- `t_client_integration_readiness`、`t_integration_readiness_eval`、`t_integration_readiness_check`：PLT-I13 同步派生。

### 6.2 File、Document 与 Notification

- `t_file_scan_policy_ver`、`t_document_template_ver`、`t_notification_provider_ver`、`t_notification_template_ver`：改为同表
  Revision。
- `t_file_state_change`、`t_doc_generation_state_change`、`t_document_state_change`、`t_notify_delivery_state_change`
  ：当前状态 + Audit 足够。
- `t_document_generation`：双输入与生成状态合并到 Consumer Document。
- `t_document_render_job`：Render Lease/Fencing 合并到 Document Version。
- `t_notification_recipient`、`t_notification_message`：Recipient 与冻结消息合并到 Notification Delivery。

### 6.3 Webhook 与 Audit

> **2026-10-15：** Webhook 物理结构已由 [Platform Webhook 投递决定](../../../../agent-docs/decisions/2026-10-15-platform-webhook-delivery.md) 收敛为 5 表（Endpoint 含订阅、Signing Secret、Event、Delivery、Attempt），本文其余 Webhook 描述只作历史。

- `t_webhook_endpoint_ver` 被语义更明确的 `t_webhook_endpoint_revision` 取代。
- `t_webhook_secret_ver` 被语义更明确的 `t_webhook_signing_secret` 取代。
- `t_webhook_sub_state_change`、`t_webhook_delivery_state_change`：当前状态 + Audit 足够。
- `t_webhook_replay`：Replay 直接创建带来源和 Generation 的新 Delivery。
- `t_audit_archive_batch`：当前没有 Archive/Deletion 消费者；保留签名 Integrity Point。

### 6.4 Gateway 与通用配置

旧 Gateway Policy/Binding/Selector/Release/Ack 物理名继续移除：`t_gateway_policy`、`t_gateway_policy_ver`、
`t_gateway_policy_binding`、`t_gateway_policy_binding_ver`、`t_gateway_selector`、`t_gateway_release`、
`t_gateway_release_item`、`t_gateway_release_approval`、`t_gateway_release_state_change`、`t_gateway_instance_ack`。历史同名
`t_gateway_guard_rule` 的 Policy-bound 结构没有恢复；当前同名表是只属于 `t_gateway_route` 的最小 Rate/Concurrency 子表。

旧通用配置物理名全部移除：`t_config_namespace`、`t_config_item`、`t_config_version`、`t_config_change_set`、
`t_config_change_item`、`t_config_approval`、`t_config_release`、`t_config_release_state_change`。

当前公共控制面恰为 `t_gateway_target`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、
`t_gateway_guard_rule`、`t_console_resource`。它们保存全局当前配置，不保存 `tenant_id`、新 `_hash`
、Snapshot、Release、Rollback、Ack 或 Runtime Counter。只有 Target、Route、Console Resource 使用普通 `version`；三个 Route
子表整体替换并且没有 `status/version/update_time`。

Route 通过 `(target_id, channel)` 完整 Tuple 绑定同 Channel Target，Console Parent 通过 `(parent_resource_id, channel)`
表达逻辑自引用；Owner 写入口锁定 Root 并精确查询 Tuple，不创建物理复合 FK。Human Route/Console Resource 逻辑引用
Permission；Client API Route 只保存 `required_scope_code`，运行时与
PLT-I03 的 Effective Credential Scope Code 比较，不对 Tenant Scope 行建立错误关系。Target 身份和发现名由当前配置表拥有，
Gateway 按完整快照执行通用校验并绑定路由；Limiter Counter/Lease 属于 Redis，`TENANT` Guard 只在运行时 Redis Key 使用可信 `tenant_id`
。服务部署配置继续使用 Spring/Nacos/Secrets，领域配置留在所属核心系统，不建设通用配置控制面。

## 7. 精确 36 Hash Policy

| 分组                  | 字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Identity/OTP        | `access_tenant.source_payload_hash`、`authentication_challenge.active_scope_hash`、`email_otp_challenge.subject_ref_hash`、`email_otp_challenge.context_ref_hash`、`email_otp_attempt.ip_hash`、`email_otp_attempt.device_hash`                                                                                                                                                                                                                                                          |
| File/Document       | `file_upload.upload_token_hash`、`file_object.content_hash`、`consumer_document.commerce_source_payload_hash`、`consumer_document.fiscal_source_payload_hash`、`consumer_document.consumer_ref_hash`、`document_access_grant.consumer_ref_hash`                                                                                                                                                                                                                                          |
| Notification        | `notification_request.source_payload_hash`、`notification_attempt.request_hash`、`notification_provider_callback.payload_hash`                                                                                                                                                                                                                                                                                                                                                        |
| Webhook             | `webhook_endpoint_verification.challenge_hash`、`webhook_endpoint_verification.resolved_ip_hash`、`webhook_event.source_payload_hash`、`webhook_event.payload_hash`、`webhook_attempt.request_hash`                                                                                                                                                                                                                                                                                     |
| Audit/Security      | `audit_chain.previous_partition_hash`、`audit_chain.last_event_hash`、`audit_event.previous_event_hash`、`audit_event.attribute_root_hash`、`audit_event.event_hash`、`audit_event.source_payload_hash`、`audit_event.payload_hash`、`audit_event_attr.value_hash`、`audit_integrity_point.start_event_hash`、`audit_integrity_point.end_event_hash`、`audit_integrity_point.root_hash`、`audit_integrity_point.content_hash`、`security_event.source_payload_hash`、`security_event.event_hash` |
| Reliability（旧模型待迁移） | `compensation_event.payload_hash`（COMP-01 目标将移除）、`command_idempotency.request_hash`                                                                                                                                                                                                                                                                                                                                                                                                 |

本节的 36 Hash 统计是当前生成模型事实；完成 COMP-01 后必须重新生成并复核统计，不能继续把
`compensation_event.payload_hash` 当作目标契约。不再保存可推导集合、配置内容、Route/Template、响应正文或重复 File Content
Hash。Password/API Key PHC、Token/Challenge
HMAC、Blind Index、Secret Fingerprint 和 Signature Ref 保持专用命名，避免把不同安全语义混成 `_hash`。

## 8. 复合 Tuple 逻辑契约与关系

107 条图形 Correlation 全部为本库逻辑关系；31 条跨所有者复合 Tuple 契约额外写入 Child Entity 的
`properties.compositeForeignKeys`，并由
Oracle 检查父精确唯一键、子左前缀索引、类型和完整 Tuple。该属性名是当前模型格式，不授权生成物理 FK：

- Tenant IAM 7 条：Member Inviter、Session Account/Realm、Session Member/Tenant/Account、Invitation Inviter/Accepted
  Member/Tenant/Account、`t_tenant_member_role` Member/Role Scope；
- File/Document 11 条：Scan Result File/Policy、Requirement/Policy、Document Correction、Version
  Supersedes/Translation、Delivery Version/Document/Notification、Access Grant Version/完整 Subject、Audit Integrity File；
- Notification 4 条：Delivery Request、Route、Translation，以及 Callback Attempt/Provider Message/Delivery；
- Webhook 7 条：Delivery Event/Tenant/Event Type/Schema、Endpoint、Revision、Subscription/Endpoint/Event
  Type/Schema、Replay，以及 Attempt Delivery/Signing Secret。
- 公共 API 控制面 2 条：Route Target/Channel，以及 Console Resource Parent/Channel。

这些逻辑契约定义 Tenant、Owner、Schema、Revision 和历史来源一致性；实现必须在代码及事务中检查同一完整 Tuple，不创建真实 FK。
其中 `t_role.scope_key` 是由 `tenant_scope + tenant_id` 派生的规范键，不接受调用方输入；`t_tenant_member_role` 的
Member/Tenant 与 Role/Scope 两条复合 Tuple 契约配合 Sidecar 表达式
`role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)`，由 Tenant/Role Root 锁、精确查询和 CAS 阻止授予其他
Tenant 的 Role。

## 9. 85 条具名 Sidecar 逻辑契约

PDManer 4.9.3 当前 JSON 结构没有可移植 CHECK 节点；因此 85 个名称、表达式和表绑定完整冻结在
`scripts/pdmaner/platform-support/model-policy.mjs`，独立 Oracle 同时冻结数量、关键表达式/绑定和 Policy
摘要。sidecar 是代码和场景测试应覆盖的业务规则清单，不映射为真实数据库 CHECK，也不代表已实现所有 63 表功能。

这些 Sidecar 契约覆盖且仅覆盖单行可判定不变量：

- Realm/Channel/Context、Operations/Customer 登录方式、Google Provider、Access Tenant 来源 Tuple；
- Password/API Key PHC 状态、Challenge/OTP Secret/Attempt/活动 Slot、Refresh Generation、Signing Key 公钥形状；
- Role Scope、Invitation 接受、Credential Rotation/Expiry；
- File Source/Scan 状态、Policy Active Slot、Document 双输入/更正/Version 活动 Slot与 Lease；
- Notification Storage、Provider/Route/Template Revision、Delivery Lease、Attempt UNKNOWN、Callback 验签锚点、Suppression；
- Webhook Revision/Verification、Secret Rotation、Event INLINE/Vault XOR、Delivery Replay Tuple/Lease、Attempt结果；
- Audit Attribute XOR、Chain/Integrity 范围、Security Alert/Action；
- Gateway Target/Route 状态与普通版本、Route Human Permission/API Scope XOR 和数值上限、Rate/Concurrency Guard 单行形状与正数，以及
  Console Resource 单行形状/状态/版本；
- Compensation Lease 和 Command Idempotency 成功 Response Ref。

Channel/Profile/Mode 兼容矩阵、Target 身份与服务发现名称格式、上下游 Path Template 变量等价、Header/Query Token 与 Allowlist
语义，以及“每条 ACTIVE Route 至少有 Shared Rate + Shared Concurrency”均由 Platform 管理事务和 Gateway Snapshot 编译器校验，不属于
85 条单行 Sidecar 表达式。

跨行/跨表条件仍在事务中通过 `SELECT ... FOR UPDATE`、完整 Tuple 查询、唯一键和 CAS 实现，不依赖物理 CHECK/FK。Sidecar
不伪造“至少一个 Scope”“所有 Scan
Requirement 已通过”“当前 Tenant 状态”“Channel Callback 实际验签成功”“Readiness 全部满足”或完整 Route Aggregate 等集合事实。

## 10. 关键并发与失败语义

- Session 的 Account/Realm 与 Tenant Member/Tenant/Account 都由写入事务锁定相应 Root 并精确校验完整 Tuple；Invitation 的接受
  Account/Member/Tenant
  也必须是同一成员身份，禁止跨账号或跨租户拼接授权。
- API Credential 只属于 ACTIVE CLIENT Tenant；Scope 行是授权事实。PLT-I03 必须 JOIN 同一 ACTIVE CLIENT Tenant 并只读同一
  Credential Scope，Owner 身份只从已验证 Access Tenant 行读取，调用方不得覆盖或从 Key Prefix 推断。
- PLT-I13 只为 CLIENT Tenant 在一致快照内读取 Tenant、Credential、Scope、Endpoint Revision、Subscription；缺少配置返回
  `NOT_READY`，Owner/Security 限制返回 `RESTRICTED`，歧义、不一致、超时或数据库错误返回接口错误且不携带 `status`；所有非
  READY/技术失败均 Fail-Closed，不写 Evaluation。
- Consumer Document 对每一来源使用 Event Key + Version + Payload Hash 检测冲突；Correction 锁定同
  Document/Tenant/Order/Consumer，Delivery 与 Access Grant 锁定完整 Version/Document/Tenant/Subject；新 Render Version
  只有成功后取得活动 Slot，失败不清除旧 Slot。
- Notification Delivery 合并 Recipient/Message/Route/执行状态，并与 Request 共享同一 Tenant/Template；Provider `UNKNOWN`
  先查询，不盲重试。
- Webhook URL Revision 独立验证；Delivery 同时冻结 Event Type 并与 Event/Subscription Tuple 对齐；Replay 直接创建新
  Delivery，Generation 唯一；旧 Fencing Token 不能提交。
- Audit Chain append 锁定 Chain 行并校验 Previous Hash/Sequence；不匹配拒绝 Seal 并创建 Security Event。
- Compensation Event 与业务变更同事务；当前目标以 `(event_key,event_type,sub_event_type)` 唯一，`lock_version` 是 Claim
  CAS。可选 `payload_json` 最大 4 KiB UTF-8，只保存无法从本库权威事实恢复的最小非敏感 JSON Object，禁止 Vault Token；生成模型旧
  Source/Version/Schema/Hash/64 KiB 字段按 COMP-01 一次性迁移。Command Idempotency 只保存 Request Hash + 稳定 Response
  Ref，不保存 Response Hash。

## 11. 安全边界

- Account 是全局身份；`login_name` 只允许 Bootstrap `admin`，HUMAN 使用 Vault Email 且 `login_name` 为空。Tenant Member 才是
  Client/Partner 权限边界。Tenant-owned Document/Notification/Webhook 根与 CLIENT Tenant-owned API Credential 统一使用
  `tenant_id`，没有 Platform `client_id`。
- PENDING 入驻不签发普通 Tenant Context Token；PLT-I04 在 Account Context 以 `requested_by_account_id` 唯一解析 PENDING
  Tenant/Member/Onboarding Role 后写 File，CCT 最终 SUBMIT 才登记 Risk。Tenant Switch 新建固定上下文 Session、撤销旧
  Refresh Family 并替换 BFF Cookie。
- Google OIDC 元数据、Gateway 预编译安全档案、Limiter Redis Key/Counter/Lease、Snapshot
  Payload/Revision/Last-Known-Good 和服务部署配置不进入模型；六张当前公共控制面表只保存可维护配置，不保存这些运行时或发布事实。
- Vault Token 只用于批准 Reveal；无当前 Metadata CAS 消费者时不保存 Vault Record Version/Metadata Hash。
- PAN、CVV/CVC、Track、PIN/PIN Block、Password/API Key 明文、OAuth Code/Token、OIDC Client Secret、KMS Private Key、Webhook
  Secret 值禁止进入表、事件、日志或文件。
- MVP 默认保留数据；处理真实受监管数据前必须完成独立 Classification、Jurisdictional Retention、Legal Hold、Backup、Vault
  Lifecycle、Controlled Destruction 与法律/合规评审。

## 12. 自动与人工门禁

自动验证至少包括：

```bash
node scripts/pdmaner/platform-support-model.test.mjs
node scripts/pdmaner/platform-support-scenario-doc.test.mjs
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
```

原公共控制面阶段门禁冻结精确统计/摘要、63 表/822 字段/123 唯一索引/99 访问索引/107 逻辑关系/5 个 1:1/7 图组、36 Hash、31 复合 Tuple
契约、85 条具名
Sidecar 契约、敏感字段拒绝、恰好六张公共控制面表、无旧 Gateway Policy/Binding/Selector/Release/Ack/Snapshot/Tenant
Override、无通用 Config/持久化 Readiness、无 `_ver` 表、无 Platform `client_id`、场景逐表满足 NN/Sidecar/安全
Tuple，以及生成器无漂移。

PDManer 4.9.3 仍须人工打开模型，检查七个 Diagram、中文显示名、关系方向和索引，并运行应用内 Validate；同时人工复核 sidecar
名称/表达式/表绑定，以及 Owner 写入口、事务/锁/CAS 和仓库自有 `local-environment` 真实 MySQL 反例测试覆盖。sidecar 不生成任何
DDL CHECK/FK；只有获批切片才可编写经评审并手工执行的 Schema SQL 和运行时代码，禁止 Testcontainers 或应用启动时迁移。

# Platform Support 92 表平衡物理模型设计（历史记录）

> **2026-09-01 再次替代：** 本文后续 92/97 表、Version/State Change、旧 Gateway 与通用技术配置内容仅用于历史追溯。[57 表精简设计](../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md) 是未受影响领域的直接前序；[统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 与其 [PDManer 实施计划](../../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 定义当前六表后继。当前模型为 63 表 / 822 字段 / 123 个唯一索引 / 99 个访问索引 / 107 条关系 / 5 条 1:1 / 7 图组 / 31 条复合 FK / 85 条 CHECK / 36 个 `_hash` 字段。后文只用于追溯历史阶段，不得作为当前实现依据。

最后更新：2026-08-29

状态：**历史/已被替代。** 本文保留 92 表阶段的设计过程；当前认证与 Gateway 边界以 [2026-08-30 统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 和 [97 表模型](../../platform-support-service/docs/database/platform-support.pdma.json) 为准：1,183 字段、175 个唯一索引、126 个访问索引、157 条关系、19 条 1:1、8 图组、31 条复合 FK、92 条 CHECK。

> **历史读取规则：** 后文所有 92 表、外部 IdP-only、动态 Gateway Route/Upstream、Route Release Item 与旧 Guard 语义仅说明当时选择。2026-08-30 的已生成模型已经完成该物理修订；不要据此恢复删除表或当前语义。

## 1. 目标与阶段边界

Platform Support 继续作为六个核心系统之一，以一个服务和一套独立 MySQL 数据库拥有身份映射、平台授权、API Credential、业务 Email OTP、文件、消费者文档、通知、Client Webhook、审计安全、Gateway 保护配置和技术配置事实。它不是其他五个系统的共享业务数据库，也不拥有 Order、Payment、Risk、Tax、Ledger 或 Settlement 状态。

本轮只交付逻辑模型收敛、现实场景、物理决策、确定性 PDManer 生成器、独立自动校验和 `platform-support.pdma.json`。不创建 Migration、Java 模块、物理数据库、Nacos/Redis/MQ/XXL-Job/Vault 环境，也不发布外部资源。

## 2. 方案选择

采用 92 表平衡方案，而不是把 108 张书面逻辑表机械物理化，也不把独立生命周期强行合并成少量宽表。

| 方案 | 表数 | 结论 |
|---|---:|---|
| 原样物理化 | 108 | 包含重复授权/审计、无用通用字典和通用 Job 平台，MVP 成本过高 |
| 平衡方案 | 92 | 保留所有已批准接口和关键状态机，删除无独立事实或无当前消费者的结构；采用 |
| 激进合并 | 70～80 | 会混合 File/Document、Request/Delivery/Attempt、Release/Version 等不同生命周期；拒绝 |

92 张表按八个 Diagram Group 分布为：

| 图组 | 表数 | 职责 |
|---|---:|---|
| 身份、授权与接入 | 16 | IdP 映射、Tenant/Role/Permission、API Credential、Readiness 和业务 OTP |
| 文件与消费者文档 | 19 | 上传、扫描、不可变文件、双输入文档生成、版本、更正、投递和访问授权 |
| 通知与 Email | 14 | Provider/Route/Template、Request/Recipient/Message、Delivery/Attempt/Callback/Suppression |
| Client Webhook | 11 | Endpoint/Version/Secret/Subscription、Event、Delivery/Attempt、Replay |
| 审计与安全 | 9 | Hash Chain、不可变 Audit、完整性副本、Security Event/Alert |
| Gateway 保护配置 | 13 | Route/Policy/Binding 不可变版本、Release、审批、回执和 Last-Known-Good |
| Platform 技术配置 | 8 | Namespace/Item/Version、原子 Change Set、审批和 Release |
| 可靠执行 | 2 | 本地 Compensation Event 与命令幂等 |

## 3. 删除的 16 张表

| 删除项 | 数量 | 替代边界 |
|---|---:|---|
| `resource_grant` | 1 | Platform 只给出 Role/Permission 授权上下文；资源 Owner 系统按 Client/OE/Order 等事实再次校验，不维护跨系统资源 ACL |
| `file_access_grant`、`file_access_event` | 2 | 通用文件读取按调用方授权上下文、`file_owner_ref` 和 Owner 系统复核；消费者凭证仍使用专用 `document_access_grant`；访问统一写 `audit_event` |
| `document_source_ref` | 1 | `document_generation` 已冻结 Commerce 与 Finance 两组 Source Event/Version/Hash 及渲染证明；`document_version.source_set_hash` 绑定准确输入，避免第二份来源集合 |
| `notification_preference` | 1 | 当前没有可关闭通知偏好的批准用例；法定、安全和付款通知不能关闭，未来出现明确 Opt-out 契约时再专项设计 |
| `audit_access_event` | 1 | 审计查询本身使用普通不可变 `audit_event`，`action_code=AUDIT_QUERY` 并保存 Purpose/Result，不建立第二条审计链 |
| `dictionary_set/version/entry/translation/release` | 5 | 当前没有通用数据库字典消费者；稳定业务码由各事实模型和接口契约拥有，纯 UI 文案由前端本地化资产管理 |
| `job_definition/schedule/run/attempt/lease` | 5 | 已知异步动作由专用 Delivery/Render/Release 表与 `compensation_event` 承载；XXL-Job 只按代码配置兜底唤醒，不建设可自由编排 Job 平台 |

删除这些表不删除任何 PLT-I01～PLT-I14 接口，也不改变 Webhook、Notification、Consumer Document、Gateway、Audit 或 File 的权威状态机。

## 4. 关键模型修正

### 4.1 Vault 字段按消费者最小化

禁止给每个敏感字段机械复制 Token、Blind Index、Key Version、Masked、Record Version 和 Metadata Hash 全套字段。物理模型按实际用途选择：

- `user_identity.email`：Token 用于授权后的 Reveal，Blind Index/Key Version 用于同域等值查找，Masked 用于控制台展示；Display Name 只保留 Token 和 Masked。
- `email_otp_challenge.email`、`notification_recipient.address`：Token 用于一次投递，Blind Index/Key Version 用于防滥用或 Suppression，Masked 用于展示。
- `notification_message`、`webhook_event.payload`：只保存短期 Vault Token 与 Canonical Hash；到期 Retire 并清除 Token 指针，不在 MVP Destroy。
- `webhook_endpoint_ver.url`：Token 用于投递 Reveal，Masked 与独立 `host_name` 用于展示和 SSRF 校验；不提供 URL 等值搜索，因此不保存 Blind Index。
- `audit_event_attr`：默认只保存脱敏标量或 Token/Masked；没有按敏感值搜索用例，不保存 Blind Index。
- Vault Record Version/Metadata Hash 只在明确需要元数据并发或完整性比较的记录中出现；本模型不把它们当作默认 Token 附件。

所有 Token 仍使用 Pending Bind、Workload Identity、Resource/Field/Purpose 二次授权和敏感访问审计。PAN、CVV/CVC、完整磁道、PIN/PIN Block 永不进入 Platform、Vault、事件、文件或日志。

### 4.2 技术配置支持三种存储模式

`config_version.storage_mode` 固定为 `INLINE | VAULT_TOKEN | SECRET_REF`：

- INLINE：非敏感、短小、有类型和 Schema 的技术值保存于 `inline_value`；
- VAULT_TOKEN：需恢复的非卡敏感值只保存 `value_vault_token`；
- SECRET_REF：API/Provider/Webhook Secret、私钥和验证 Key 只保存 Secret Manager/KMS 引用。

三个载荷字段严格三选一。所有模式都保存 `value_hash`；需要控制台展示时使用 `value_masked`。配置仍只属于 Platform/Gateway 技术组件，不承载 Fee、Tax、Risk、MID、Routing 或 Settlement 领域规则。

### 4.3 文件重扫可撤销可用性

`file_object` 内容不可变，但安全判定可因显式重扫改变。状态机增加 `AVAILABLE -> QUARANTINED`；重新放行必须先显式进入新的 SCANNING 并产生完整 Requirement Result，再进入 AVAILABLE，隔离也可终止为 REJECTED。已签发的读取授权和短期 URL 必须在隔离时失效；首次 `available_time`、原 Scan Result 与 State Change 全部保留。

### 4.4 文档更正关系显式化

- `consumer_document.corrects_document_id` 表达 Credit Note 对原 Receipt/Invoice 的更正，必须同 Client/Order 且不能自引用；非 Credit Note 必须为空。
- `document_version.supersedes_version_id` 表达同一 Document 内新渲染版本替代旧版本，必须属于同一 Document 且不能自引用。
- `consumer_document.current_ver_id` 只指向本 Document 已 GENERATED 的版本；旧版本和文件永不覆盖。
- Consumer Document Root 使用 `CREATED/GENERATED/SUPERSEDED/CANCELLED`；创建后续渲染不会把 Root 倒退为 GENERATING。新 `document_version` 独立经历 `CREATED -> RENDERING -> GENERATED/FAILED`，成功切换 Current Pointer 后旧 Version 进入 SUPERSEDED；失败可在同一冻结输入下重试。新的 Credit Note 仍使用新 Generation 和新 Document。

### 4.5 并发与可靠性字段收紧

- 业务 `version` 只保留在可变聚合根、当前投影、Lease/Fencing 状态和需要 CAS 的记录。
- 追加式 Event、Attempt、Callback、Result、Version、Approval 和 State Change 不机械增加业务 Version。
- `compensation_event` 使用 `lock_version` 表达 Claim/Lease CAS，不再使用含混 `version`。
- `command_idempotency` 通过 `caller_scope + operation_type + idempotency_key` 唯一约束和状态条件更新，不增加机械 Version。
- 所有可空 Tenant/Public/Global 范围都使用非空 Canonical `*_scope_key`，不依赖 MySQL nullable unique 语义。

## 5. 核心数据流

### 5.1 Consumer Document 双输入汇聚

1. Commerce 发布 `ConsumerDocumentRequested`，Platform 以 `generation_key_version + document_generation_key` 创建或锁定 `document_generation`，冻结 Order Context 和三份 Legal File/Hash。
2. Finance 发布 `FiscalRecordCreated`，使用相同算法版本、Key、Scope 和业务引用；任一方可先到。
3. 同 Key 不同身份或 Hash 进入 `CONFLICT`，禁止最后写覆盖。
4. 双输入匹配后，同一本地事务将 Generation 置为 MATERIALIZED、写状态历史并创建唯一 `consumer_document`。
5. Render Job 通过 Lease/Fencing 生成 `document_version` 和不可变 `file_object`；文件完成冻结扫描策略的全部 Requirement 后才可 GENERATED。
6. Credit Note 或重新渲染分别通过 Document 自引用或 Version 自引用表达更正，原文件保留。

### 5.2 Notification 与 Webhook

Request/Event 冻结来源版本、Schema 和 Hash；Delivery 冻结实际 Route/Endpoint/Subscription/Provider Version；每次外部调用只追加 Attempt。Retry 留在原 Delivery，Webhook Replay 创建新 Generation Delivery。Provider Callback 必须先按冻结验证版本验签，再唯一定位 Attempt；失败验签只形成 Security/Audit，不推进业务状态。

### 5.3 Gateway Last-Known-Good

Route、Policy 和 Binding 都以不可变 Version 进入 Release Item。Release 在冻结 Item Set 后完成校验和 Maker-Checker；ACTIVATING 使用独立 Slot。只有要求的实例回执和 Content Hash 全部一致时才原子把旧 ACTIVE 置为 SUPERSEDED、新 Release 置为 ACTIVE。失败释放 Activation Slot，旧 ACTIVE 永远保留。

### 5.4 可靠异步执行

业务状态、状态历史和 `compensation_event` 同一本地事务提交；afterCommit 只唤醒 Dispatcher。Worker 使用 `lock_owner + lock_until + lock_version` 原子 Claim。XXL-Job 只扫描遗失唤醒；成功删除任务，失败进入 ERROR/FAILED。长期幂等证据保留在 Document、Webhook、Notification、File 等业务表，不建设 Inbox/Outbox 或中央任务库。

## 6. 物理与测试策略

- 从共享 PDManer 4.9.3 模板确定性生成，不手改最终 JSON；清空全部连接信息。
- Manifest 按八个图组拆分；实体、字段、索引、关系和 Diagram ID 由固定 Domain Tag 的 SHA-256 稳定派生。
- 测试 Oracle 不导入生产 Manifest，字面量冻结 92 表、八组、关键字段、28 条复合外键、关键 CHECK、完整 CHECK Policy 摘要、关系、敏感字段禁令和精确统计。
- 模型测试覆盖一个 Snowflake BIGINT 主键、UTC `DATETIME(6)`、审计分区 `DATE`、32 字符限制、`t_/uk_/idx_` 命名、本库 FK、关系左前缀、真 1:1、空连接、Vault/File Tuple、CHECK Policy、Diagram 覆盖和确定性摘要。
- 现实场景测试要求 92 张表逐表出现，并覆盖身份/API/OTP、文件重扫、双输入文档及更正、通知晚到回调、Webhook Replay、审计链、Gateway 回滚、三模式配置和 Compensation Claim。
- 自动测试通过后仍保留 PDManer 4.9.3 应用内打开、八图可读性和规范检查的人工门禁。

## 7. 明确不做

- 不恢复删除的 16 张表，不以 JSON/EAV/自由 SQL 绕过删除决定；
- 不建设通用 BCR、Inbox、Outbox、Workflow、Job 平台或中央幂等；
- 不实现通用 Retention、Legal Hold、Archive、Anonymization 或 Destruction；
- 不保存 Gateway Runtime Counter、服务实例、目标地址或部署拓扑；
- 不创建跨数据库外键或直接读取其他五个系统数据库；
- 不创建 Migration、Java、物理环境或外部发布。

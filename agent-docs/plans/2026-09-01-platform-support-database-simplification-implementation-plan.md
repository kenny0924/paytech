# Platform Support 57 表数据库精简实施记录

最后更新：2026-09-01

状态：**57 表历史阶段已完成；63 表统一公共 API 控制面后继模型已生成并通过专项门禁，最终共享文档与总门禁已完成；PDManer 4.9.3 应用内人工评审待完成**

> **后继说明：** [统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
> 只替代本记录的静态 Gateway 结论，并以
> [实施计划](../../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 在 57 表前序上新增六张最小当前配置表。
> 当前生成后继为 63 表 / 822 字段 / 123 唯一索引 / 99 访问索引 / 107 本库关系 / 5 个 1:1 / 7 图组，
> 31 条复合 FK、85 条具名 CHECK 和仍然恰好 36 个 `*_hash` 字段。下文 57 表统计和验证记录属于可审计历史，
> 不是当前物理快照。

## 1. 决策来源与边界

本记录实施 [57 表精简设计](../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)，并保留 [统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 的 Account/Tenant/Google/Session 契约。

目标是把 Platform Support 从历史 97 表收敛为消费者驱动的 57 表模型，删除机械 Version/State History、数据库 Gateway 控制面、通用技术配置与持久化 Readiness。当前只实施设计资产；没有创建 Migration、Java、数据库、Gateway/BFF/Redis 或部署资源，也没有 commit。

## 2. 57 表历史阶段的 TDD 实施顺序

1. 在独立 Oracle 与模型测试中先冻结精确 57 表、六组分布、36 Hash、Tenant-owned Root、无 `_ver` 表、无数据库 Gateway/Config/Readiness，确认旧 97 表模型失败。
2. 依次收敛 Identity、File/Document、Notification、Webhook、Audit/Reliability Manifest；每个子域先跑命名测试观察 Red，再实现到 Green。
3. 重写 Composite FK、CHECK、Secret/Hash/File Tuple Policy，生成模型并用独立摘要锁定。
4. 先把现实场景门禁改为 57 表和合并生命周期，确认旧场景 5 项失败；再逐表重写场景到 6/6 Green。
5. 重写逻辑权威与物理决策，只在最终主工作区基线上语义合并共享 requirements/导航。

## 3. 57 表历史生成基线

| 指标 | 最终值 |
| --- | ---: |
| Tables | 57 |
| Fields | 761 |
| Unique Indexes | 114 |
| Access Indexes | 92 |
| Local Relations | 100 |
| One-to-one Relations | 5 |
| Diagram Groups | 6 |
| Composite FK Contracts | 29 |
| Named CHECK sidecar Contracts | 74 |
| `*_hash` Fields | 36 |

摘要：

- Template SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`
- Model SHA-256：`9877c38099a0f269c2df843d27f0d179892dbce3de177112a69a74e7716ac528`
- Structural Projection SHA-256：`7f9e8810ed9cda2de6a60e3ad9e52364bdee72a1b12eff612169ab7900b5c1fb`
- CHECK Policy SHA-256：`787794b5b9bc8f88684744d08784c79e9ead13b1eecf1057f9ab46f27ad11adb`

Diagram Group 分布固定为 `18 + 12 + 9 + 8 + 8 + 2`。

## 4. 结构收敛结果

### 4.1 Identity、Tenant、API 与 Readiness

- 保留全局 Account、Password、Google Federated Identity、Challenge、Session/Refresh、Signing Key、Tenant IAM、API Credential/Scope 与业务 OTP，共 18 表。
- Google Provider Metadata 改为静态部署配置；Federated Identity 保存固定 `GOOGLE + provider_subject`。
- API Credential 改为 Tenant-owned，保存 Prefix + PHC Verifier；Scope 行是唯一授权事实。
- Readiness 改为 PLT-I13 同步派生，不再有 Evaluation、Current Pointer、Requirement Config 或事件。
- 业务 OTP 活动唯一性使用 Tenant/Subject/Purpose/Context 明细 Tuple，不重复 Scope Hash。
- Session 以 Account/Realm 与 Member/Tenant/Account 复合身份阻断跨账号成员绑定；Invitation 接受结果同样锁定 Account/Member/Tenant。
- 入驻先投影 PENDING Tenant，再由 PLT-I04 在 Account Context 解析 Onboarding Member 后上传；最终 CCT SUBMIT 才登记 Risk，激活时把 Onboarding Role 转为 Tenant Owner。

### 4.2 File、Document 与 Notification

- Scan Policy、Document Template、Notification Provider/Route/Template 使用单表 Revision 行。
- Consumer Document 合并双输入 Generation Root；Document Version 合并 Render Lease/Fencing。
- Document Correction、Delivery 与 Access Grant 锁定完整 Document/Version/Tenant/Order/Consumer 或 Notification 所有权 Tuple。
- Notification Delivery 合并 Recipient、Message、Route 与执行状态，并锁定 Request 的 Tenant/Template；保留 Attempt/Callback 的真实外部不确定性。
- 删除机械 State Change，Audit 是统一变更证据。

### 4.3 Webhook、Audit 与 Reliability

- Endpoint 保持稳定 Tenant Root；Endpoint Revision 和 Signing Secret 是必要历史。
- Delivery 显式冻结 Event Type 并与 Event/Subscription 的 Event Type/Schema 一致；Replay 直接创建带来源与 Generation 的新 Delivery，不再建立 Replay Root。
- Audit Archive Workflow 不存在；保留签名 Integrity Point 与精确 File Tuple。
- Command Idempotency 删除 Response Hash，只保存 Request Hash 与稳定 Response Ref。

### 4.4 Gateway 与 Config

- 57 表阶段把旧 11 张 Gateway Policy/Release 表和 8 张通用 Config 表全部移出 Platform 模型。
- 后继模型只恢复新的六表当前配置形态：`t_gateway_target`、`t_gateway_route`、
  `t_gateway_route_header`、`t_gateway_route_query_param`、Route-owned `t_gateway_guard_rule` 与
  `t_console_resource`；不恢复旧 Policy/Binding/Release/Snapshot/Ack/Tenant Override。
- Rate/Concurrency Counter、Bucket、Lease 和 TTL 继续只属于 Redis；Gateway 静态代码继续拥有 Approved Target
  Registry、Security Profile 实现、可信 Header 与绝对安全上限。

## 5. 历史物理名处置

以下旧名不在当前模型：

- Identity/Readiness：`t_oidc_provider`、`t_credential_event`、`t_client_integration_readiness`、`t_integration_readiness_eval`、`t_integration_readiness_check`；
- File/Document：`t_file_scan_policy_ver`、`t_file_state_change`、`t_document_template_ver`、`t_document_generation`、`t_doc_generation_state_change`、`t_document_render_job`、`t_document_state_change`；
- Notification：`t_notification_provider_ver`、`t_notification_recipient`、`t_notification_message`、`t_notification_template_ver`、`t_notify_delivery_state_change`；
- Webhook/Audit：`t_webhook_endpoint_ver`、`t_webhook_secret_ver`、`t_webhook_sub_state_change`、`t_webhook_replay`、`t_webhook_delivery_state_change`、`t_audit_archive_batch`；
- Gateway：`t_gateway_policy`、`t_gateway_policy_ver`、旧 Policy-owned `t_gateway_guard_rule` 形态、`t_gateway_policy_binding`、`t_gateway_policy_binding_ver`、`t_gateway_selector`、`t_gateway_release`、`t_gateway_release_item`、`t_gateway_release_approval`、`t_gateway_release_state_change`、`t_gateway_instance_ack`；后继模型复用 `t_gateway_guard_rule` 名称时，它只是 `t_gateway_route` 的最小当前子表；
- Config：`t_config_namespace`、`t_config_item`、`t_config_version`、`t_config_change_set`、`t_config_change_item`、`t_config_approval`、`t_config_release`、`t_config_release_state_change`。

其中 Endpoint/Secret 的历史能力分别由语义明确的 `t_webhook_endpoint_revision` 与 `t_webhook_signing_secret` 保留，因此旧名数量不是净减表数。

## 6. Hash 与约束

最终恰好 36 个 `_hash` 字段，完整清单和消费者见 [逻辑权威](../database/PLATFORM_SUPPORT_DATABASE.md#44-精确-hash-allowlist) 与 [物理决策](../../paytech/platform-support/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md#7-精确-36-hash-policy)。不再保存 Scope/Reason/Requirement/Evidence/Route/Template/Response 等可推导或无消费者 Hash。

57 表阶段的 29 条复合 FK 锁定 Session/Invitation 身份、Tenant、Role Scope、File Policy、Document Correction/Version/Translation/Delivery/Grant、Notification Request/Route/Translation/Callback 与 Webhook Event Type/Endpoint/Revision/Subscription/Replay/Secret，74 条具名 CHECK 契约冻结在 `model-policy.mjs` sidecar 中。后继模型另增加 Route Target/Channel 和 Console Parent/Channel 两条复合 FK，以及 11 条 Target/Route/Header/Query/Guard/Console 单行 CHECK，因此当前为 31 条复合 FK 与 85 条 CHECK。PDManer 4.9.3 JSON 没有可移植 CHECK 节点，未来 Migration 必须逐项映射为真实 CHECK。跨行集合事实由事务锁、唯一键、复合 FK 与 CAS 实现。

## 7. 57 表历史验证证据

最终自动化门禁：

```text
六系统 generate-*-model.mjs --check                     6/6 PASS
全量 PDManer/场景 node:test                             473/473 PASS
requirements build/test                                115/115 PASS
requirements lint                                       PASS
Markdown 相对链接（115 个文件、845 个链接）              845/845 PASS
root + platform-support-service git diff --check         PASS
冲突标记、旧统计、已删除表/字段语义扫描                   PASS
```

Finance 与 Risk 任务已分别完成只读交叉复核；Gateway 复核提出的接口、上下文与 CHECK 表述漂移已修正。追加反例门禁还会拒绝 PLT-I03 caller Owner 覆盖、Key Prefix Owner 推断、跨 Tenant Owner 返回，以及中英文追加式 Redis HMAC 相反语义。PDManer 4.9.3 应用内打开/Validate/Diagram 以及 sidecar CHECK 到未来 Migration 的人工映射检查仍待完成，本记录不宣称这些步骤已经执行。

## 8. 集成纪律

- Platform 专属 Manifest/Oracle/Test/模型/文档可在隔离 worktree 完整替换。
- `AGENTS.md`、根/agent-docs README、PROJECT_CONTEXT、DATABASE_DESIGN_GUIDE、database README、APPLICATION_ARCHITECTURE 与 requirements 文件必须从主工作区最新版本做语义增量，禁止用旧 worktree 整文件覆盖。
- 合并前向 Gateway、Risk、Finance 任务回报共享文件边界和最终统计；合并后请求只读全量门禁。
- 不恢复旧 Gateway Policy/Binding/Selector/Release/Snapshot/Ack/Tenant Override、Finance Funding、Risk 旧统计、
  CCT 旧 Application 身份或任何并发任务已删除语义；当前六表公共控制面必须按后继设计做语义增量，不能从旧
  92/97 表模型复制回来。

# Platform Unified Human Authentication PDManer Implementation Record

> **2026-09-01 已替代：** 本记录保留 97 表统一认证阶段的历史实施证据；[57 表精简设计](../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md) 是未受影响领域的直接前序，[统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 与其 [PDManer 实施计划](../../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 定义当前六表后继。当前模型为 63 表 / 822 字段 / 123 个唯一索引 / 99 个访问索引 / 107 条关系 / 5 个 1:1 / 7 图组 / 31 条复合 FK / 85 CHECK / 36 个 `_hash` 字段；后文只用于追溯历史阶段，不得恢复被删除的表或旧边界。

日期：2026-08-30
状态：设计资产和三个生成模型已完成自动验证；PDManer 4.9.3 应用内人工评审仍待完成。

## 历史阶段权威与范围

[统一人工身份认证与租户授权设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 仍是 Account、Realm、Channel、Tenant、CCT Partner Application 和 Risk Typed Namespace 权威。本记录所述编译期静态 Operations Route Catalog 仅是动态 Snapshot 切换前运行态历史；JWT/JWKS、Header/Cookie 与运行时硬保护边界继续由 Gateway hardening 拥有。

2026-08-29 Operations-only requirements 与 2026-08-27 Platform balanced design 只对其被取代的 IdP-only、无本地 Session、动态 Route/Upstream 发布和旧 Guard 语义保留历史价值；未受影响的 Platform 领域事实仍按现行逻辑模型、场景、物理决策和生成模型解释。

Platform 是全局人工 Account、Realm exactly `{OPERATIONS, CUSTOMER}`、Channel exactly `{OPERATIONS, CLIENT, PARTNER}`、Session/Refresh、签名元数据和 Tenant/member IAM 的事实拥有者。Operations 使用 OPERATIONS Realm/Channel；Client 和 Partner 使用 CUSTOMER Realm，并分别固定 CLIENT、PARTNER Channel。CCT 创建 Client/Partner 的 PENDING 业务 Application，随后版本化投影 Platform Tenant；`requested_by_account_id` 仅为外部稳定 Account 引用，不是 Party、Signatory 或法律身份。Risk 的 `source_namespace` 以 `CCT_LOCAL_PARTNER_APPLICATION` 隔离 Local Partner 主 Case 与关联人子 Case。BFF 仅是无状态渠道适配器；Gateway 只校验最小 JWT 与编译期静态 Route Catalog/Guard Profile，Platform 从不动态发布 Route、Guard 或运行时 limiter 状态，核心系统仍执行资源授权。

本记录不授权 Java、Migration、物理数据库、Platform/BFF/Gateway 运行时实现、KMS/Secret、部署或基础设施。

## 精确变表清单

Platform 从 92 表到 97 表：新增 9 表：`t_user_account`、`t_password_credential`、`t_oidc_provider`、`t_federated_identity`、`t_authentication_challenge`、`t_auth_session`、`t_refresh_token_generation`、`t_signing_key_metadata`、`t_tenant_invitation`；删除 4 表：`t_idp_connection`、`t_user_identity`、`t_gateway_route`、`t_gateway_route_ver`。以下 15 张既有表发生实质变更：

- `t_access_tenant`：新增 `owner_ref_type`；以 `owner_status`、可空 `source_version`、可空 `source_payload_hash` 和 `security_status` 取代单一 `status`，分离 CCT 业务状态与 Platform 安全状态。
- `t_tenant_member`：`user_id` 改为 `account_id` 并改指向 `t_user_account.account_id`；成员类型移除 `SERVICE`，状态移除 `INVITED`，`invited_by` 明确为 Member 引用，并相应替换唯一/访问索引。
- `t_role`：字段、索引和本库 FK 保持不变；其 `PLATFORM/TENANT` Scope 现在以 Account-backed Tenant Member 的授权语义解释，包含 PENDING Tenant 的 Onboarding Owner 与 ACTIVE Tenant 的 Owner 角色。
- `t_member_role`：字段、索引和本库 FK 保持不变；其 `tenant_member_id` 现在指向 Account-backed Member，因而随 Tenant Member 的 Account 重接线承载角色授权。该表是本历史阶段的原始命名，后续在当前模型中明确改名为 `t_tenant_member_role`。
- `t_gateway_guard_rule`：新增可空 `counter_dimension` 和非空 `rule_scope_key`；唯一约束从 `(policy_ver_id, rule_type)` 改为 `(policy_ver_id, rule_type, rule_scope_key)`，支持 `AUTHENTICATED_SUBJECT`、`CLIENT_IP`、`SHARED_ROUTE` 的累计边界。
- `t_gateway_policy_binding_ver`：删除 `route_id`、`route_scope_key` 及其 `t_gateway_route` FK，新增非空 `route_code`；相应唯一/访问索引改按 `route_code`，并将 `content_hash` 覆盖静态 Route Code。
- `t_gateway_release_item`：删除 `ROUTE` 类型、`route_ver_id`、Route Version FK 和关联唯一/访问索引；`item_type` 只保留 `POLICY/BINDING`。
- `t_file_upload`：`uploader_id` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_webhook_replay`：`requested_by` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_security_alert`：`owner_id` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_security_alert_action`：`actor_id` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_gateway_release`：`maker_id`、`checker_id` 两个 FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_gateway_release_approval`：`actor_id` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_config_change_set`：`maker_id`、`checker_id` 两个 FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。
- `t_config_approval`：`actor_id` FK 从 `t_user_identity.user_id` 改接 `t_user_account.account_id`。

CCT 新增 `t_partner_application`；`t_client_application` 增加跨系统稳定引用 `requested_by_account_id`；`t_application_document` 增加 `partner_application_id`，并改为 Client/Partner/OE 三个真实本库 `RESTRICT` FK 的 exactly-one-parent XOR。Risk 不增删表；只扩展既有 `t_due_diligence_case.source_namespace` 的闭合集合和对应 CHECK 分支为 `CCT_LOCAL_PARTNER_APPLICATION`，不改变字段、索引、关系、图组或 CHECK 行数。

## 已冻结的生成证据

| 模型 | 精确 totals | SHA-256 |
|---|---|---|
| Platform Support | 97 表、1,183 字段、175 唯一索引、126 访问索引、157 本库关系、19 条 1:1、8 图组、31 复合 FK、92 CHECK | model `1771350f53edfb8310367e043c9ca4cb8cf0e5ec74c8d4a2d6a447b3d3edc0fb`; structural `16b10d3041bed27addef792b065b24e35a7462a64a1a4c5d90e07478c41d6d3a`; CHECK policy `11f8696ddd3df3f928709be837306737c8f1b31870f4b1c050b0fcb8e52fccc4` |
| Client & Contract | 37 表、423 字段（331 required / 92 nullable）、57 唯一索引、44 访问索引、64 本库关系、7 条 1:1、6 图组 | model `7538ddc4020372994f1bbc52aeea7d7b907cc108b65e95ccc3282c35bee8409c`; structural `83cb4d442e937599ddb99d64090a039cafbb332807619507af27ea0197b8e7a4` |
| Risk & Compliance | 71 表、865 字段、106 唯一索引、74 访问索引、111 本库关系、9 条 1:1、8 图组、83 CHECK | model `445ed5828d0b7951f0755978676c102bfade023306a0ca311184bf259a4551e6`; structural `e8ad25426adcf5037fc334706c0342e2b5c3bb47d83f44d554098cda3b756d2c`; CHECK policy `b2a09dc39dab618af8e4d2400d898c185e1ee4789a7b31a6ec4137c91e82a25b` |

## 自动验证与人工门禁

以下命令在本记录更新后实际重新执行；未执行的测试不在此声明为通过。

```bash
node --test scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-scenario-doc.test.mjs scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-compliance-scenario-doc.test.mjs
```

结果：exit 0；144 pass，0 fail。

```bash
npm --prefix requirements-site test
```

结果：exit 0；112 pass，0 fail。

陈旧语义扫描实际搜索旧 CCT `36/404/54/43/61`、旧 Platform `92/1076/167/116/145/19/8`、`IdP-only`、Client/Partner authentication pending、dynamic Gateway Route、`t_user_identity`、`t_idp_connection`、`t_gateway_route_ver` 及两父 Application Document 表述。输出有 14 个语义命中，另有本行作为扫描说明的自匹配：`DATABASE_DESIGN_GUIDE.md` 的旧快照和实现记录第 10 行的 IdP-only/动态 Route 表述均明确为历史/已取代；设计说明中的 IdP-only 是取代声明；实现记录第 18、27–34 行及设计说明第 163、549 行是删除或 Account 重接线清单。没有 Client/Partner authentication-pending、two-parent Application Document 或仍有效的动态 Gateway Route 发布命中。

相对 Markdown 链接验证实际以 `189d549b11dfab01cd8b80fce965144cdc350474` 为基线执行零上下文 `git diff`：逐个解析八个 Task 12 文件新增行中的 Markdown 目标，跳过基线文件中已存在的同一链接和外部 URL，按源文件目录 `decodeURIComponent` 后解析，并以 `fs.existsSync` 验证目标。结果：23 个 Task 12 新增相对链接全部存在，exit 0，输出 `Task 12 introduced relative Markdown links OK: 23`。

```bash
git diff --check 189d549b11dfab01cd8b80fce965144cdc350474
```

结果：exit 0，无输出。

三套生成模型继续保留 PDManer 4.9.3 应用内打开模型、检查 Diagram 可读性并运行应用内规范检查的人工门禁。本记录不授权 Java、Migration、物理数据库、运行时、部署或基础设施。

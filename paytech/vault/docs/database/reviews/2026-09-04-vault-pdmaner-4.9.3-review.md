# Vault PDManer 4.9.3 自动门禁证据与待人工评审

日期：2026-09-04

## 结论

`vault.pdma.json` 已通过自动结构门禁；PDManer 4.9.3 应用内人工评审仍为 **PENDING**。本次没有打开、控制或检查 PDManer GUI，也没有生成 DDL、连接数据库或应用 Schema。后续 Go Server、Java Client、契约、测试和待人工执行 Schema DDL 的代码工作，来自用户 2026-09-04 的明确授权，而不是虚构的人工评审 PASS。

## 已验证的文件与自动结果

- 文件：[`vault.pdma.json`](../vault.pdma.json)
- SHA-256：`c9803868eabf0ad357a88443eda2ae8d30f88e39bafeb75b0f80888139de55f9`
- 运行位置：`/Users/kenny/Desktop/Workspace/Code-MoR`；该位置包含被根治理工作树忽略的独立 `vault-service` 仓库。
- `node --test scripts/pdmaner/vault-model.test.mjs`：PASS，11 tests passed、0 failed、0 skipped。
- `node scripts/pdmaner/vault/generate-vault-model.mjs --check`：PASS，`Vault model is up to date (3 tables, 30 fields, 3 unique indexes, 4 access indexes, 2 relations, 0 one-to-one relations, 1 diagram groups)`。
- `shasum -a 256 vault-service/docs/database/vault.pdma.json`：PASS，输出与上述批准 Hash 完全一致。

自动检查独立验证了精确的三表/30 字段、索引字段顺序、两条本库关系、敏感字段白名单、无凭据连接元数据、确定性 UUID、`vault_core` 图和生成无漂移。这是模型结构证据，不是 PDManer UI 证据。

## PDManer 4.9.3 应用内与 DDL 预览清单

以下项目由用户在之后的 PDManer 4.9.3 应用内人工评审及生成 DDL 预览中逐项核对；当前状态均为 **PENDING**：

| 项目 | 期望值 | 状态 |
| --- | --- | --- |
| Version | `4.9.3` | PENDING |
| Diagram group | `vault_core` | PENDING |
| Table order | `t_vault_record`, `t_vault_protect_request`, `t_vault_access_event` | PENDING |
| Field order/types/nullability | 与 `VAULT_PHYSICAL_DECISIONS.md` 第 2 节精确一致 | PENDING |
| Unique indexes | `uk_vault_record_token`, `uk_vault_record_stable`, `uk_vault_protect_request` | PENDING |
| Access indexes | `idx_vault_protect_record`, `idx_vault_access_record`, `idx_vault_access_caller`, `idx_vault_access_request` | PENDING |
| Relations | `protect_request.record_id -> record.record_id`; `access_event.record_id -> record.record_id` | PENDING |
| Connection metadata | empty | PENDING |

## 冻结的 DDL sidecar CHECK

PDManer 4.9.3 无可移植 CHECK 节点。未来人工 DDL 预览和 Schema DDL 必须逐项对照以下四条冻结契约；不需要第五条 CHECK：

```sql
CONSTRAINT ck_vault_record_mode CHECK (token_mode IN ('REFERENCE','STABLE'))
CONSTRAINT ck_vault_record_fingerprint CHECK (
  (token_mode='STABLE' AND value_fingerprint IS NOT NULL)
  OR (token_mode='REFERENCE' AND value_fingerprint IS NULL)
)
CONSTRAINT ck_vault_access_action CHECK (action IN ('PROTECT','REVEAL'))
CONSTRAINT ck_vault_access_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))
```

## 已接受限制

私有 HTTP 与必需 `X-Caller-Service` 只防止误用，不是强认证；获允许的内部调用方只要获得 Token，即可在获准的 Data Type/Purpose 下 Reveal。LOCAL AES 仅使本地实现、测试和验证可运行，不是生产 KMS；生产运行仍须等待真实 Key Provider、凭据、权限、轮换和灾备设计获批。这些限制不因 Go Server 或 Java Client 的先行实现而改变。

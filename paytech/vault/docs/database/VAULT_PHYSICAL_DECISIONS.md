# Vault 物理数据库决策

最后更新：2026-10-14
状态：**V1 六表模型（2026-10-14 增加策略三表，见第 8 节）/手工 Schema 与 local/test Go/Java 实现通过自动门禁；Nacos 实际烟测 NOT RUN，PDManer 应用内人工评审 PENDING；最终全分支审查单独记录**

> **2026-09-05 物理约束对齐：** 下文 FK/CHECK 数量是已人工应用 Vault Schema 的当前事实，不是新增数据库约束规范。项目已确认后续不新建物理 `FOREIGN KEY` 或 `CHECK`；现存约束只有在应用保护、反例测试、审核 SQL 和回滚方案齐备后才按 [物理约束移除计划](../../../../docs/superpowers/plans/2026-09-05-physical-constraint-removal.md) 迁移。文档更新不代表当前数据库已经移除它们。

本文是 [Vault V1 数据库设计](../../../../docs/superpowers/specs/2026-09-02-vault-database-design.md) 到 PDManer 物理模型的冻结映射。Vault 是通用的非卡敏感值保护基础设施：只保存受保护值和通用 `data_type`，不拥有任何业务语义。

## 1. 生成边界与冻结基线

模型 [vault.pdma.json](vault.pdma.json) 只能由 [Vault Generator](../../../../scripts/pdmaner/vault/generate-vault-model.mjs) 从共享模板、[Manifest](../../../../scripts/pdmaner/vault/manifest.mjs) 与 [Policy](../../../../scripts/pdmaner/vault/model-policy.mjs) 生成。不得直接修改 JSON；生产 Manifest 与独立测试 Oracle 分离。序列化固定为两空格缩进和一个末尾换行，`--check` 逐字节检查且不写文件。下列为 2026-09-04 三表冻结基线；当前六表基线见第 8 节。

```json
{
  "modelTotals": {
    "tables": 3,
    "fields": 30,
    "uniqueIndexes": 3,
    "accessIndexes": 4,
    "relations": 2,
    "oneToOneRelations": 0,
    "diagramGroups": 1
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "c9803868eabf0ad357a88443eda2ae8d30f88e39bafeb75b0f80888139de55f9",
  "structuralProjectionSha256": "dc7fd9e8cef258f482942cdb7f3f0ac09062103d41f4e2061e5fb6ac151fdd14"
}
```

完整克隆共享 PDManer 模板，只派生 Vault 模型内容；`dbConn`、`dbConns` 和默认连接均为空，因此模型不含主机、端口、用户名、密码、环境名或真实 KMS 键。实体、字段、索引、关系、图组和画布单元使用固定命名空间/逻辑路径派生稳定 UUID v5。唯一图组为 `vault_core`，按 `t_vault_record`、`t_vault_protect_request`、`t_vault_access_event`、`t_vault_data_type`、`t_vault_access_rule`、`t_vault_policy_change` 顺序分两行展示全部六张表。

物理目标是 MySQL 8.4/InnoDB；表名为 `t_` 小写 snake_case，唯一/访问索引分别为 `uk_`/`idx_`，所有物理标识最长 32 个字符。每张表的首字段是服务侧 Snowflake `BIGINT` 主键、非自增；全部字段显式无默认值。UTC 时刻均为 `DATETIME(6)`；可变表保留 `create_time`/`update_time`，追加不可变的访问事件与策略变更只有 `create_time`。

## 2. 表、类型与字符比较

| 表 | 物理字段（`NN` 非空，`N` 可空） | 职责 |
| --- | --- | --- |
| `t_vault_record` | `record_id BIGINT NN`; `token VARCHAR(64) NN`; `data_type VARCHAR(64) NN`; `token_mode VARCHAR(16) NN`; `value_fingerprint BINARY(32) N`; `ciphertext BLOB NN`; `encryption_nonce BINARY(12) NN`; `wrapped_dek BLOB NN`; `kms_key_ref VARCHAR(512) NN`; `create_time DATETIME(6) NN`; `update_time DATETIME(6) NN` | 权威不透明 Token、Stable 身份指纹和单记录信封加密材料。 |
| `t_vault_protect_request` | `protect_request_id BIGINT NN`; `caller_service VARCHAR(128) NN`; `idempotency_key VARCHAR(128) NN`; `request_hash BINARY(32) NN`; `record_id BIGINT NN`; `create_time DATETIME(6) NN`; `update_time DATETIME(6) NN` | 成功 Protect 命令的调用方幂等映射。 |
| `t_vault_access_event` | `access_event_id BIGINT NN`; `request_id VARCHAR(64) NN`; `trace_id VARCHAR(64) N`; `caller_service VARCHAR(128) NN`; `actor_id VARCHAR(64) N`; `action VARCHAR(16) NN`; `data_type VARCHAR(64) N`; `purpose VARCHAR(64) N`; `record_id BIGINT N`; `outcome VARCHAR(16) NN`; `result_code VARCHAR(64) NN`; `policy_version VARCHAR(64) N`; `reason_code VARCHAR(64) N`; `ticket_ref VARCHAR(64) N`; `create_time DATETIME(6) NN` | 每一次实际 Protect/Reveal 尝试的不可变本地审计证据；人工操作另记操作人、理由码与工单号。 |
| `t_vault_data_type` | `data_type_id BIGINT NN`; `data_type_code VARCHAR(64) NN`; `token_mode VARCHAR(16) NN`; `data_class VARCHAR(16) NN`; `description VARCHAR(256) NN`; `manual_protect_enabled TINYINT(1) NN`; `manual_reveal_enabled TINYINT(1) NN`; `status VARCHAR(16) NN`; `version INT NN`; `create_time DATETIME(6) NN`; `update_time DATETIME(6) NN` | 数据类型目录；编码与 Token 模式不可改，只停用不删除。 |
| `t_vault_access_rule` | `access_rule_id BIGINT NN`; `caller_service VARCHAR(128) NN`; `action VARCHAR(16) NN`; `data_type_code VARCHAR(64) NN`; `purpose_code VARCHAR(64) NN`; `status VARCHAR(16) NN`; `version INT NN`; `create_time DATETIME(6) NN`; `update_time DATETIME(6) NN` | 服务访问规则；`data_type_code` 由应用校验存在，不建物理 FK。 |
| `t_vault_policy_change` | `policy_change_id BIGINT NN`; `policy_version_no INT NN`; `change_type VARCHAR(32) NN`; `target_type VARCHAR(16) NN`; `target_key VARCHAR(256) NN`; `before_snapshot JSON N`; `after_snapshot JSON NN`; `reason VARCHAR(500) NN`; `actor_id VARCHAR(64) NN`; `request_id VARCHAR(64) NN`; `create_time DATETIME(6) NN` | 每次策略变更一条、版本号单调递增；快照只含元数据。 |

下列 `VARCHAR` 机器标识显式使用 `CHARACTER SET ascii COLLATE ascii_bin`，确保 ASCII 和大小写敏感比较：`token`、`data_type`、`token_mode`、两表的 `caller_service`、`idempotency_key`、`action`、`purpose`、`outcome`、`result_code`、`policy_version`，以及策略表与人工审计的 `actor_id`、`reason_code`、`ticket_ref`、`data_type_code`、`data_class`、`status`、`purpose_code`、`change_type`、`target_type`、`target_key`（共 28 列）。其余字符串字段未声明该专项排序规则，保持共享模板默认映射。`BINARY(32)` 仅用于服务器 HMAC 的 `value_fingerprint` 和 `request_hash`；`BLOB` 仅用于 `ciphertext`、`wrapped_dek`。完整不透明 `token` 是刻意持久化的数据库定位符，`wrapped_dek` 与非敏感 `kms_key_ref` 也按批准模型持久化；不存在敏感原值、明文 DEK、真实密钥或凭据列。

## 3. 索引、关系与 CHECK sidecar

| 表 | 唯一索引 | 访问索引 |
| --- | --- | --- |
| `t_vault_record` | `uk_vault_record_token (token)`；`uk_vault_record_stable (data_type, value_fingerprint)` | — |
| `t_vault_protect_request` | `uk_vault_protect_request (caller_service, idempotency_key)` | `idx_vault_protect_record (record_id)` |
| `t_vault_access_event` | — | `idx_vault_access_record (record_id, create_time)`；`idx_vault_access_caller (caller_service, create_time)`；`idx_vault_access_request (request_id)` |

两条本库 `n:1` 关系均以子表左前缀索引支持，当前人工 Schema 已映射为外键且不跨库：

- `vault_protect_request.record_id -> vault_record.record_id`
- `vault_access_event.record_id -> vault_record.record_id`

PDManer 4.9.3 JSON 没有可移植 CHECK 节点；以下四条具名 sidecar 契约冻结在 Policy，当前人工 Schema 已逐项包含真实 CHECK，后续手工变更仍须保持一致：

| 名称 | 表 | 精确表达式 |
| --- | --- | --- |
| `ck_vault_record_mode` | `t_vault_record` | `token_mode IN ('REFERENCE','STABLE')` |
| `ck_vault_record_fingerprint` | `t_vault_record` | `(token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL)` |
| `ck_vault_access_action` | `t_vault_access_event` | `action IN ('PROTECT','REVEAL')` |
| `ck_vault_access_outcome` | `t_vault_access_event` | `outcome IN ('SUCCESS','DENIED','FAILED')` |

## 4. Protect、Stable 与并发语义

Protect 的幂等命名空间是 `(caller_service, idempotency_key)`。同一键且相同 `request_hash` 返回原 Record Token 并追加成功访问事件；同键不同哈希追加 `DENIED / IDEMPOTENCY_KEY_CONFLICT`，绝不改变既有映射。`request_hash` 是独立 request-hash key 对长度分隔的 `caller_service + data_type + token_mode + canonical_value_bytes + purpose` 计算的 HMAC-SHA-256；HTTP attempt 数据不参与。

`REFERENCE` 的新逻辑命令总是新建随机 Token/Record，即使值相同。并发相同幂等键以 `uk_vault_protect_request` 选出唯一赢家；失败竞争事务整体回滚 Record/Event，再读取赢家并验证请求哈希。

`STABLE` 以同一部署环境内的 `(data_type, canonical value bytes)` 收敛。当前 local/test Vault 使用 LOCAL AES 从进程注入 Root 派生且用途隔离的 `stable-identity-key`（生产 KMS/Secrets Provider 未实现），按 `UTF8("vault-stable-v1") + UINT16_BE(length(UTF8(data_type))) + UTF8(data_type) + UINT32_BE(length(canonical_value_bytes)) + canonical_value_bytes` 计算 HMAC-SHA-256 `value_fingerprint`；调用方、Purpose 和幂等键不参与。`uk_vault_record_stable` 的重复键是预期并发路径：重读赢家并继续写调用方自己的 Protect Request。稳定身份密钥不在 V1 常规轮换；未来 KMS 包装密钥变更不改变 Token。

## 5. 加密、AAD 与访问失败关闭

每个 Record 独立生成 256-bit DEK 和 12-byte nonce，以 AES-256-GCM 加密最多 32 KiB 的规范值，将带认证标签的密文写入 `ciphertext`；仅 `wrapped_dek` 和非敏感 `kms_key_ref` 持久化。AAD 使用固定长度分隔编码，精确覆盖 `record_id + token + data_type + token_mode`。算法/编码是固定 V1 合约，不增加算法或密钥版本表；运行时加密键、HMAC 键、明文 DEK 与凭据不进入模型、配置文件、日志、事件或仓库；测试只允许明确标记的固定公开 Golden Vector 与临时生成、进程环境注入的 LOCAL AES Root。

Reveal 先校验基础请求，再按 Token 读取非敏感元数据并取得 Data Type，完成完整 Allowlist 校验后才调用 Key Provider（当前 LOCAL AES）解包、重建 AAD 并认证解密、写入并提交成功访问事件，**仅在审计提交成功后**返回明文。未知 Token、拒绝、KMS/完整性/认证标签失败均不返回明文；拒绝与失败在独立短事务中写审计。如果本地审计持久化不可用，Reveal 丢弃内存明文并 Fail-Closed。审计事件绝不保存 Token、原值、指纹、请求哈希、密文、nonce、封装 DEK、KMS 材料、响应体、自由文本异常或堆栈。

## 6. 明确排除与人工门禁

本 V1 的 Go Server、Java Client、内部 HTTP 契约和 LOCAL AES 实现以 [2026-09-05 文档治理与冲突决策](../../../../agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md) 和 [OpenAPI](../../api/openapi/v1.yaml) 为准；Schema DDL 仍为人工执行，不创建 Flyway、自动建表或生产部署资源。也不包含 Gateway/BFF/浏览器/外部 Client 访问、业务聚合/Owner/Tenant/Field 事实、Bind/Confirm/Copy/Retire/Destroy/Query/Batch/Manage/Rewrap API、mTLS/Workload Identity/数据库服务凭据、应用层请求/响应加密信封、Blind Index/Mask/Metadata/Record Version/算法或 Key Version 表、Inbox/Outbox/`compensation_event`/Platform Audit relay、审计链/WORM、自动 Retention/Legal Hold/匿名化/归档/销毁、分库分表或预留字段，以及任何 PAN、CVV/CVC、Track、卡到期日、PIN/PIN Block。

部署私有网络中的内部 HTTP、必需 `X-Caller-Service` 和 `caller_service + action + data_type + purpose` allowlist（2026-10-14 起存于策略表）是 V1 访问边界；缺失策略拒绝。Vault 不持有应用 X.509 证书；该 Header 是临时的 best-effort 调用者断言，不是强身份认证，不能被本模型描述为 mTLS/Binding 能力。

自动门禁覆盖精确总数、字段/索引/关系、敏感字段白名单、四条 CHECK、无凭据、确定性 UUID 和无生成漂移。仍必须在 **PDManer 4.9.3** 中人工打开模型，检查 `vault_core` 图、字段顺序、中文显示名、索引、关系方向、注释和 DDL，并人工复核四条 sidecar CHECK 的名称、表达式、表绑定及手工 Schema 映射。该 GUI 评审保持 PENDING，不因自动门禁而改为 PASS。2026-09-04 用户先批准代码/契约/测试和待人工执行 DDL，之后又明确批准只对已有空 `mor_vault` 人工应用三条 CREATE TABLE；该操作已完成，不授权重复 DDL 或新 Schema 变更。

## 7. 2026-09-04 实现与人工 Schema 证据

- 独立 Vault 实现基线为 `181fb8f`；Go 1.27.1 Server、OpenAPI、Java Client、LOCAL AES 和真实 MySQL/跨语言自动验证均已实现。完整命令、分阶段安全输出、任务提交及裁决见 [运行时审查记录](../reviews/2026-09-04-vault-runtime-final-review.md)。
- 明确批准后的人工执行只作用于原有空 `mor_vault`，MySQL 8.4.11，使用 Vault scoped account；`vault-schema.sql` SHA-256 为 `bd18057813f213b08061d68cad188940217e928aee14583fdbeacbf839ad37f6`。执行后三表/30 columns/3 PK/3 non-primary unique/2 FK/4 CHECK 精确匹配，初始行数为 0/0/0。没有初始化 DML、自动 Migration、其他库访问或额外字段。
- Task 11 修复后的完整 `scripts/verify.sh` 十阶段 PASS，Java 32 tests（23 Client + 9 E2E/前置/生命周期）零失败/错误/跳过，运行拥有行清理后为 0/0/0。每次后续验收仍须重新运行门禁，应用和测试永不执行 DDL。
- 真实 Nacos 3.1.2 smoke **NOT RUN**：缺少明确提供的客户端凭据；自动 SDK 边界测试或 manual source 编译不是发现/注销通过的证据。PDManer 4.9.3 GUI **PENDING**；controller-owned 全分支 spec/quality final review 尚待完成，以上述审查记录后续更新为准。
- LOCAL AES 只允许单实例 local/test，不轮换 Root/Key ID；没有生产 Key Provider、强 Workload Identity、业务接入、生产部署或 Retention/Destroy 能力。不得把本地闭环解释为生产/合规通过。

## 8. 2026-10-14 策略入库（六表基线）

依据 [2026-10-14 决定](../../../../agent-docs/decisions/2026-10-14-vault-policy-and-manual-access.md)与[需求目录](../../../../requirements/20261014/operations-console-Vault数据类型与手动加解密/)，新增 `t_vault_data_type`、`t_vault_access_rule`、`t_vault_policy_change`，访问事件增加 `actor_id`、`reason_code`、`ticket_ref` 与 `idx_vault_access_actor`。新表不建物理 FK/CHECK，枚举与引用由应用保证；原有 2 FK、4 CHECK 不变。

```json
{
  "modelTotals": {
    "tables": 6,
    "fields": 64,
    "uniqueIndexes": 6,
    "accessIndexes": 7,
    "relations": 2,
    "oneToOneRelations": 0,
    "diagramGroups": 1
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "6a0092340de7d089c4c6f36faf518c4fe7df9cbfd731e507f5d6a068a3ef1cf4"
}
```

- Generator DSL 增加 `TINYINT(1)`（开关）、`INT`（版本号）与 `JSON`（变更快照）；`status` 从禁止字段模式移出，核心三表仍由字段白名单锁定。PDManer 保留字规则只对 `t_vault_access_rule.action/status`、`t_vault_data_type.status` 放行。
- 2026-10-14 经授权只在本机 `mor_vault` 执行需求目录的 `ddl.sql` 与 `dml-local.sql`；其他环境未执行。PDManer 4.9.3 GUI 评审仍 PENDING。

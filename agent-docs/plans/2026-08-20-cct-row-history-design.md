# Client & Contract 通用行级变更历史设计

> **当前边界提示：** 两表 BCR 模型仍有效，但已经收敛为按 Record Type 白名单启用，不再覆盖每条结构化业务行；本文中的 44 表、持久化 Eligibility 和 Retention 结构已由 [2026-08-23 实时资格与 Retention 延期设计](./2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)取代。不得据此恢复已删除表或“所有 Child 都写 BCR/独立 CAS”的旧协议。

最后更新：2026-08-23
状态：行级历史部分仍适用；涉及旧物理模型的内容仅保留演进证据

## 1. 目标与 CCT 专项边界

本设计使用 `business_change_record + business_change_version` 保存 CCT 明确需要审计、追责或历史留档对象的不可变审计视图，用于溯源、展示和按聚合版本查看白名单覆盖内容；结构化业务表仍是当前事实源，本模型不是 Event Sourcing、Inbox、Outbox、完整聚合恢复源或在线查询模型。

CCT 的专项并发决定是：白名单内聚合根不再保存机械 `version`，根行对应的 `business_change_record.current_version` 同时是审计坐标和唯一普通聚合 CAS 令牌；外部命令携带 `expected_change_version`。受审计 Child 的 `current_version` 只是最近快照采用的 Root 版本坐标，不是独立 CAS Token；白名单外 Child 不创建 BCR。该规则只适用于 CCT，不自动改变其他五个系统仍可使用结构化 Root `version` 的策略。独立技术锁仅保留 `compensation_event.lock_version`。

### 1.1 CCT V1 白名单

`record_type` 只允许以下18类：

```text
PARTY, CLIENT, LOCAL_PARTNER, OPERATING_ENTITY,
PARTY_PROFILE, CLIENT_BUSINESS_PROFILE, COMMERCIAL_TERM_VERSION,
CLIENT_APPLICATION, OPERATING_ENTITY_APPLICATION, PARTY_RELATION,
CLIENT_ENTITY_AFFILIATION, ENTITY_ASSIGNMENT_RULE,
CLIENT_ENTITY_ASSIGNMENT, ENTITY_SOURCE_RELATION,
AGREEMENT, CONSUMER_LEGAL_DOCUMENT, SELLER_DISCLOSURE,
COMMERCIAL_TERM_PLAN
```

Root Type 为上述类型去除三个 Child Type 后的15类。固定 Child 映射为 `PARTY_PROFILE -> PARTY`、`CLIENT_BUSINESS_PROFILE -> CLIENT`、`COMMERCIAL_TERM_VERSION -> COMMERCIAL_TERM_PLAN`；其余 Record Type 均为自身 Root。

明确排除的业务表为：`individual_profile`、`organization_profile`、`party_identity_key`、`party_profile_identifier`、`party_address`、`party_contact`、`client_business_classification`、`application_document`、`agreement_signatory`、`agreement_document_ref`、`agreement_acceptance`、`agreement_operating_entity`、`commercial_term_scope`、`commercial_term_component`。它们依靠冻结结构化业务版本或追加式领域证据保留历史，不重复写 BCR。四张技术表 `business_change_record`、`business_change_version`、`compensation_event`、`command_idempotency` 不得自审计。新增表默认不接入，扩大白名单必须重新说明真实审计用途并更新模型契约。

## 2. 表模型

### 2.1 `business_change_record`

| 字段 | 语义 |
|---|---|
| `history_id` | 历史记录 Snowflake 主键 |
| `record_type` | 稳定业务记录类型，不直接采用可重命名物理表名 |
| `record_id` | 被审计业务行 ID |
| `aggregate_root_type` | 所属聚合根稳定类型 |
| `aggregate_root_id` | 所属聚合根业务 ID |
| `parent_history_id` | 同一聚合内直接父记录；根记录为空 |
| `current_version` | 该行最后一次审计快照采用的根修订；仅 Root 同时把它作为 Expected Version/CAS Token，Child 只作版本指针 |
| `create_time` | 记录创建时间 |
| `update_time` | `current_version` 最后推进时间 |

约束：

- `uk_bcr_record(record_type,record_id)` 唯一；按 `(aggregate_root_type,aggregate_root_id,history_id)` 查询整棵聚合，并索引 `parent_history_id`。
- 根记录的 Record Type/ID 必须等于 Aggregate Root Type/ID 且 Parent 为空；非根必须有同聚合 Parent。Parent 自关联使用 RESTRICT，禁止自指和环。
- Record/Root/Parent Tuple 创建后永久不可改。跨 Parent 或聚合移动必须在旧归属写 DELETE，并以新的业务 ID 在新归属写 CREATE。
- Root `current_version > 0`，只能通过 `expected_change_version` 条件 CAS 推进。白名单内 Child `current_version` 可以从 1 跳到 4、9 等本次 Root Version，不要求稠密或等于旧值加一，也不独立 CAS。

### 2.2 `business_change_version`

| 字段 | 语义 |
|---|---|
| `change_version_id` | 不可变明细主键 |
| `history_id` | 对应 `business_change_record` |
| `version_no` | 本次业务命令提交后的所属聚合根版本 |
| `operation_type` | `CREATE`、`UPDATE` 或 `DELETE` |
| `snapshot_json` | 白名单化的完整单行业务审计快照 |
| `create_by` | 非空 Human 或正式 System Actor ID |
| `create_time` | 不可变快照创建时间 |

`uk_bcv_history_ver(history_id,version_no)` 唯一，`version_no > 0`。明细只允许 INSERT，不含 `update_time`，普通业务权限不得更新或删除。`CREATE/UPDATE` 保存变化完成后的安全完整行视图；`DELETE` 保存删除前最后一份安全视图。Snapshot 最大 64 KiB，禁止反射或 `SELECT *` 序列化 DO。

## 3. 原子写协议

### 3.1 CREATE

创建受审计聚合必须在同一本地事务完成：插入结构化 Root/Child；插入 Root BCR 且 `current_version=1`；为 Root 写 Version 1 `CREATE` Snapshot；仅为白名单内且确实需要留档的 Child 插入 BCR 与 Version 1 Snapshot；登记以 Root Version 1 为 `source_version` 的 `compensation_event`。白名单外 Child 只写结构化表。任何一步失败全部回滚。

在既有聚合中新增 Child 时，先以根的 Expected Version 执行 Root CAS 得到 `next_version`。只有 Child Record Type 在白名单内才创建 BCR 和 `CREATE` Snapshot；Root 同时写相同版本的 UPDATE Snapshot。白名单外 Child 不补造历史行。

### 3.2 UPDATE 与跳号 Child

外部命令必须携带 `expected_change_version`，固定 `next_version=expected_change_version+1`。Repository 首先按业务状态与固定锁顺序锁定根，然后执行：

```sql
UPDATE business_change_record
SET current_version = :next_version,
    update_time = :now
WHERE record_type = :root_type
  AND record_id = :root_id
  AND current_version = :expected_change_version;
```

影响行数必须为 1。随后用期望旧业务状态更新结构化行，为 Root 写 `version_no=next_version` 的最终 Snapshot，并只为白名单内发生需留档变化的 Child 写最终 Snapshot。Root CAS 已串行化同聚合写入，因此 Child BCR 只按主键把 `current_version` 更新到根的 `next_version`，不接收 `child_expected_version` 或执行独立 CAS；Child 未参与中间根修订时可自然跳号。同一事务中对同一行的多次 SQL 必须收敛为一条最终 Snapshot，未变化或白名单外 Child 不写空洞记录。

Agreement/Consumer Legal/Seller Disclosure 不把 BCR 修订号复制到业务行或下游 Wire；交易 Proof 使用稳定的 Agreement、Agreement 覆盖关联、Legal 文件和 Seller Disclosure 标识。Assignment 四表都在 Record Type 白名单内并保留 BCR 历史，但交易 Proof 只使用稳定 `assignment_id/rule_id/affiliation_id/entity_source_relation_id`，不再把 Affiliation 或 Source Relation 修订号复制到 Assignment 或下游快照。

### 3.3 DELETE 与还原

删除白名单内 Child 时，先在本次根版本追加包含稳定身份和删除前安全字段的 `DELETE` Snapshot，再删除结构化行；BCR 永久保留。删除白名单外 Child 时按 Root CAS 与结构化规则执行，不补造 Snapshot。删除父业务行时，同一根版本必须为全部仍存活且已经接入 BCR 的后代追加 DELETE，禁止遗留孤儿审计节点。

查看聚合审计版本 `V` 时，为每条 BCR 选择 `version_no <= V` 的最大明细；没有命中或最新操作为 DELETE 的行排除，其余按不可变 Parent 组树。该结果只覆盖白名单对象，不等于完整业务聚合。恢复旧内容必须发起新业务命令并形成新的根版本，禁止覆盖当前业务表或复用已删除业务 ID。

### 3.4 System Command 与多 Root

定时激活、过期、撤销和系统回调必须作为 System Command，在锁内读取 Expected Version，并执行完全相同的 Root CAS/History/Event 协议；`create_by` 使用正式 System Actor，不能绕过历史。

一个本地事务修改多个聚合时，受影响 Root 必须按 `(aggregate_root_type,aggregate_root_id)` 确定顺序锁定和 CAS。每个 Root 独立计算自己的 `next_version`、写 Root/白名单内 Changed Child Snapshot，并用自己的 `next_version` 作为代表该 Root 的 Event `source_version`；共同 `_meta.triggerId` 只用于关联，不能伪造共享聚合版本。任一 Root CAS、业务校验、结构化更新、应写 History Snapshot 或 Event 登记失败，整个本地事务回滚。

Application Document 只属于 Application 聚合但不单独写 BCR；Party Profile 仍是 Party 聚合的受审计 Child；Client Business Profile 仍是 Client 聚合的受审计 Child。一次申请提交可在确定顺序下更新这些 Root，但不能改写既有 Aggregate/Parent Tuple。

## 4. JSON 信封、Integrity 与安全

`snapshot_json` 固定为：

```json
{
  "_meta": {
    "schemaVersion": 1,
    "triggerType": "COMMAND",
    "triggerId": "123456",
    "reasonCode": "CLIENT_SUSPENDED",
    "integrityAlgorithm": "SHA-256",
    "integrityHash": "64-lowercase-hex"
  },
  "data": {}
}
```

Integrity 输入 Domain 固定为 `CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1`，覆盖 Record/Root/Parent Tuple、`history_id/version_no/operation_type/create_by/create_time`，以及移除 `_meta.integrityHash` 后的完整 `_meta + data`，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 Hex。`BIGINT` 使用无前导零十进制字符串；Decimal 使用去无意义尾零且无指数的字符串，零为 `"0"`；时间固定为六位微秒 UTC 字符串。Snowflake ID 禁止写成 JSON Number。

每个 `record_type` 的代码级 Catalog 必须冻结允许的 Root/Parent Type、JSON Schema、字段白名单、数据分类和 Retention。Snapshot 禁止保存 Vault Reveal 明文、完整 Vault Token、Blind Index、密文、Secret、PAN/SAD、文件正文，以及 Active Slot、Lease、重试次数、技术锁等字段；敏感变化只保存获批 Masked Value、Metadata Hash、Record Version 或稳定 Changed 标记。Hash 不替代 INSERT-only 权限、Retention、Legal Hold 与 Platform 追加式审计。

## 5. 替换与保留边界

BCR 只补充白名单对象的通用审计，不吞并业务 Version、法律证据或技术执行记录：

- Party/Profile 与 Commercial Term 继续通过结构化 Version 和冻结子表表达可供业务查询的历史；BCR 只覆盖白名单中的 Root、Party/Business Profile 头和 Commercial Term Version，不复制其全部冻结 Child。
- Agreement、Consumer Legal 与 Seller Disclosure 在 Draft/审批/发布/终止阶段写自身 BCR；`agreement_signatory`、`agreement_document_ref`、`agreement_operating_entity` 冻结后由结构化行保留，`agreement_acceptance` 本身是追加式法律证据，均不重复写 BCR。
- Application Root 写 BCR；`application_document` 在 Draft 内由 Root CAS 保护，提交后冻结并以结构化行和 Submission Hash 保留，不单独写 BCR。
- Assignment 四表分别是白名单 Root；它们保存需要追责的来源、授权、规则和实际分配变化。
- `compensation_event` 与 `command_idempotency` 是技术执行记录，不进入 BCR；BCR 两表也不自审计。

状态 From/To 可比较同一白名单 Record 的相邻 Snapshot 得到；Reason、Trigger 与 Actor 分别由 `_meta` 和 `create_by` 保留。未进入白名单的结构化事实按自己的 Version、冻结或追加规则查询，不能从 BCR JSON 补齐。

## 6. 历史规模说明

77、68、66、57、55、48 与 44 是明确的历史阶段数字：最初状态表重构从历史 77 表降至历史 68 表，后续 Party Identity 精简曾形成历史 66 表，平衡型收敛曾形成历史 57 表，Assignment 精简前曾形成 55 表，Agreement/Legal 精简后形成 48 表，随后形成过 44 表阶段。它们都不是当前模型；当前权威目标是36表。历史数字只能用于解释演进，不得进入当前 Catalog、关系、状态或生成统计。

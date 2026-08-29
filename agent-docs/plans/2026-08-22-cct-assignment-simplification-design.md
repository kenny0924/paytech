# CCT 经营主体自动分配精简设计

> **历史阶段提示：** 本文保留该阶段 51 表 / 576 字段基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [44 表 Commercial Term 设计](./2026-08-23-cct-commercial-term-consolidation-design.md)。不得据此恢复旧 Assignment、Agreement/Legal 或 Pricing/Share Hash 结构。

最后更新：2026-08-22
状态：已实施并通过自动验证；PDManer 4.9.3 应用内检查仍为人工门禁

## 1. 目标

将 Client & Contract 的经营主体分配模型从 8 张表精简为 4 张表，同时保留当前真正需要的自动分配、来源证明、Client 使用资格和历史追溯能力：

```text
entity_source_relation
client_entity_affiliation
entity_assignment_rule
client_entity_assignment
```

本次采用“业务字段结构化保存、业务事实按行保留历史、跨系统交付边界统一 Hash”的原则。删除普通 Assignment 配置上的 Root/Version、Scope Hash、Content Hash 和派生 Owner Key，不删除 CCT 通用 `business_change_record/business_change_version`，也不影响 Agreement、Pricing、Partner Share、Eligibility 等其他领域确有业务意义的 Version 或 Hash。

## 2. 已确认的精简结论

| 原结构 | 结论 | 原因与替代方式 |
|---|---|---|
| `entity_source_relation.source_owner_key` | 删除 | `source_type + source_client_id/source_partner_id` 已完整表达来源；Owner XOR 继续由条件约束保证 |
| `entity_source_relation.client_source_basis` | 保留 | 区分 Client 自身法律主体 `SAME_PARTY` 与经审核关联主体 `VERIFIED_ASSOCIATION`，直接影响审核证据 |
| `uk_source_owner_time(operating_entity_id,source_owner_key,effective_from)` | 删除并替换 | 改为 `uk_source_effective(operating_entity_id,effective_from)`；当前唯一来源仍由 `uk_source_active(operating_entity_id,active_source_slot)` 保证 |
| `client_entity_affiliation.scope_hash` | 删除 | 用国家、币种、行业、MCC 的结构化组合唯一键代替 |
| `client_entity_affiliation.content_hash` | 删除 | Affiliation 行不再原地覆盖核心身份字段；跨系统完整性由 CCT `commercial_context_hash` 统一覆盖 |
| `client_entity_affiliation.hash_schema_version` | 删除 | 随 `content_hash` 一并失去意义 |
| `entity_assignment_policy_ver` | 删除 | 当前不建设规则集版本发布、回滚和历史重放平台；最终 Assignment 已冻结实际选择结果 |
| `assignment_policy_binding.scope_hash` | 删除 | Binding 整体合并进直接规则，不再保留中间 Scope Hash |
| `entity_assignment_rule` | 保留并重构 | 直接保存 Client + 精确交易范围到候选 Operating Entity 的自动分配规则，并由 Assignment 真实引用 |
| `client_entity_assignment.scope_hash` | 删除 | 用准确 Scope 结构化字段和组合唯一键代替 |
| `client_entity_assignment_ver` | 删除并合并 | 一行 `client_entity_assignment` 就是一份已生效分配事实；变化时结束旧行并新增一行 |

同时删除不再有当前消费者的：

```text
entity_assignment_policy
entity_assignment_policy_ver
assignment_policy_binding
client_entity_assignment_ver
```

## 3. 精简后的四张表

### 3.1 `entity_source_relation`：经营主体来源关系

完整字段：

```text
source_relation_id
operating_entity_id
source_type
source_client_id
source_partner_id
client_source_basis
status
active_source_slot
effective_from
effective_to
create_time
update_time
```

约束：

- `source_type` 仅为 `PLATFORM|CLIENT|PARTNER`，不允许 `DIRECT`；
- PLATFORM 要求两个来源 Owner ID 都为空；CLIENT 只允许 `source_client_id` 非空；PARTNER 只允许 `source_partner_id` 非空；
- CLIENT 必须填写 `SAME_PARTY|VERIFIED_ASSOCIATION`，非 CLIENT 必须为空；
- `status` 仅为 `ACTIVE|ENDED`，只有 ACTIVE 保存 `active_source_slot=1`；
- `uk_source_effective(operating_entity_id,effective_from)`；
- `uk_source_active(operating_entity_id,active_source_slot)`；
- Source 身份字段创建后不原地修改；来源变化时结束旧行并新增一行。

### 3.2 `client_entity_affiliation`：客户经营主体适用关系

完整字段：

```text
affiliation_id
client_id
operating_entity_id
scope_country
scope_currency
industry_code
mcc_code
status
active_slot
effective_from
effective_to
create_time
update_time
```

约束：

- 表达 Client 有资格在一个准确 Scope 下使用某个 Operating Entity，不代表最终已选中；
- 四个 Scope 字段都使用准确值：ISO 3166-1 alpha-2 国家、ISO 4217 币种、平台行业代码、四位 MCC；当前不支持 `*`；
- `status` 仅为 `ACTIVE|ENDED`，只有 ACTIVE 保存 `active_slot=1`；
- `uk_aff_scope_time(client_id,operating_entity_id,scope_country,scope_currency,industry_code,mcc_code,effective_from)`；
- `uk_aff_scope_active(client_id,operating_entity_id,scope_country,scope_currency,industry_code,mcc_code,active_slot)`；
- 核心身份与 Scope 创建后不原地修改，变化时结束旧行并新增一行。

### 3.3 `entity_assignment_rule`：经营主体自动分配规则

完整字段：

```text
rule_id
client_id
country_code
currency
industry_code
mcc_code
operating_entity_id
priority_no
status
active_slot
effective_from
effective_to
create_time
update_time
```

约束：

- 当前规则只服务明确的 Client，不设计 GLOBAL Scope、Policy Root、Policy Version 或 Binding；
- 国家、币种、行业、MCC 均为准确值，当前不设计通配规则；
- 同一 Client/Scope 可以为不同 Operating Entity 配置不同 `priority_no`，最小值优先；同优先级不允许并列；
- `status` 仅为 `ACTIVE|ENDED`，只有 ACTIVE 保存 `active_slot=1`；
- `uk_assign_rule_time(client_id,country_code,currency,industry_code,mcc_code,priority_no,effective_from)`；
- `uk_assign_rule_active(client_id,country_code,currency,industry_code,mcc_code,priority_no,active_slot)`；
- 规则业务字段创建后不原地覆盖；调整目标主体或优先级时结束旧行并新增一行。

### 3.4 `client_entity_assignment`：客户经营主体分配

完整字段：

```text
assignment_id
client_id
scope_country
scope_currency
industry_code
mcc_code
operating_entity_id
affiliation_id
entity_source_relation_id
rule_id
status
active_slot
effective_from
effective_to
create_time
update_time
```

约束：

- 一行就是一个已经验证并生效的自动分配事实，不再有 Root/Version 两层；
- `rule_id`、`affiliation_id`、`entity_source_relation_id`、`operating_entity_id` 均非空，并且必须指向同一个 Client、Scope 和 Operating Entity；
- 四个 Scope 字段均为准确值且不允许 `*`；
- `status` 仅为 `ACTIVE|ENDED`，只有 ACTIVE 保存 `active_slot=1`；
- `uk_assignment_time(client_id,scope_country,scope_currency,industry_code,mcc_code,effective_from)`；
- `uk_assignment_active(client_id,scope_country,scope_currency,industry_code,mcc_code,active_slot)`；
- 分配改变时结束旧行并新增新的 `assignment_id`；历史 Order 继续引用旧 ID。

## 4. 自动分配规则

一个 Client/准确 Scope 的 Assignment 由 CCT 自动生成：

1. 找出该 Client/Scope 当前有效的 `entity_assignment_rule`，按 `priority_no` 从小到大处理；
2. 规则目标 Operating Entity 必须为 ACTIVE；
3. 必须存在同一 Client、Operating Entity、准确 Scope 的 ACTIVE Affiliation；
4. 必须存在该 Operating Entity 唯一 ACTIVE Source Relation；
5. 第一条完整满足条件的规则生成或保持 ACTIVE Assignment；
6. 没有候选、候选并列、证据不一致或依赖已失效时，不得生成猜测结果；结束旧 Assignment，并使对应 Eligibility/CCT-I13 Fail Closed。

Rule、Affiliation、Source Relation、Operating Entity 状态或 Client Business Classification 变化时，触发者固定为 CCT 内部处理该变化的 Processor。该状态变更命令在同一本地事务内同步重算受影响的准确 Client/Scope，并在 Assignment 已生成、保持或结束后才提交；提交后的状态才可用于新交易。外部调用方、CCT-I11/I13 查询、定时任务和人工操作都不负责触发或补写 Assignment。当前不为此增加工作流、调度器、发布版本或额外事件表；无唯一候选时同一命令结束旧 Assignment，并保持 Fail Closed。

## 5. 跨系统证明

CCT-I11/I13 不再输出 Assignment Version、Assignment Scope Hash、Affiliation Revision、Source Relation Revision、Policy Version 或 Policy Binding。它返回并由 `commercial_context_hash` 覆盖：

```text
assignment_id
rule_id
affiliation_id
entity_source_relation_id
source_type
operating_entity_id
client_id
scope_country
scope_currency
industry_code
mcc_code
context_as_of_time
```

Commerce 在不可变 Order Snapshot 中冻结这些稳定 ID、来源类型、准确 Scope 和 CCT `commercial_context_hash`。Payment 使用 `assignment_id`，不再使用 `assignment_version_id`。中间业务表不保存 Hash，不表示缺少完整性保护：CCT Bundle、Commerce Order Snapshot 与 BCR 审计快照仍分别保留自己的边界 Hash。

## 6. 当前明确不做

- Assignment Policy Root、Policy Version、Binding 和规则集原子发布；
- GLOBAL 规则、Client Override 层级、通配 Scope、Fallback Slot、权重或脚本规则；
- Manual Assignment、审批流、未来定时发布和版本回滚；
- Affiliation/Assignment/Rule 的 Scope Hash、Content Hash、Hash Schema Version；
- Assignment Root + Version、Current Pointer 或额外乐观锁字段；
- Java、Migration、物理数据库或其他五个系统的 PDManer 物理实现。

出现明确业务需求后再独立评估以上能力，不提前为可能性保留空表或技术字段。

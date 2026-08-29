# CCT Commercial Term 合并与 Hash 精简设计

> **后续决策提示：** 本文的 Commercial Term 四表结构仍有效；其中 44 表统计、Eligibility 证明和 V3 `commercial_context_hash` 已由 [2026-08-23 实时资格与 Retention 延期设计](./2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)取代。当前使用 36 表与 V4 Hash。

最后更新：2026-08-23  
状态：Commercial Term 子模型仍有效；整体模型与 Wire 以最新设计为准

## 1. 决策摘要

Client Pricing 与 Partner Share 保持两个独立业务语义，但共用一套物理模型。原 8 张表合并为 4 张：

- `commercial_term_plan`
- `commercial_term_version`
- `commercial_term_scope`
- `commercial_term_component`

`term_type` 只保存在 Plan，取值为 `CLIENT_PRICING/PARTNER_SHARE`。下游接口仍使用 `client_pricing_*` 与 `partner_share_*` 两组业务字段，不把两类费用合并成同一个结果。

本次同时删除 Commercial Term 内部派生 Hash：Version 的 `hash_schema_version/content_hash`、Scope 的 `scope_hash`、Component 的 `canonical_hash`。它们由稳定 ID、不可变结构化行和完整结构化快照替代。跨系统整包防拼接仍保留 `commercial_context_hash`，Canonical Domain 升级为 `CODE-MOR:CCT:COMMERCIAL_CONTEXT:3`。

## 2. 物理模型

### 2.1 `commercial_term_plan`

字段按顺序为：

```text
term_plan_id
plan_code
term_type
client_id                 NULL
local_partner_id          NULL
status
create_time
update_time
```

规则：

- `CLIENT_PRICING`：`client_id IS NOT NULL AND local_partner_id IS NULL`。
- `PARTNER_SHARE`：`client_id IS NULL AND local_partner_id IS NOT NULL`。
- `term_type` 和 Owner Tuple 创建后不可修改。
- MVP 每个 Client 一份 Client Pricing Plan，每个 Local Partner 一份 Partner Share Plan。
- `plan_code` 仅在同一 `term_type` 内唯一，不无意扩大为全局唯一。

### 2.2 `commercial_term_version`

字段按顺序为：

```text
term_version_id
term_plan_id
agreement_id
version_no
status
active_slot               NULL
effective_from
effective_to              NULL
rounding_mode
create_time
update_time
```

规则：

- 状态为 `DRAFT/APPROVED/SCHEDULED/ACTIVE/SUPERSEDED/EXPIRED`，仅 `ACTIVE` 保存 `active_slot=1`。
- `rounding_mode=HALF_UP`；有效期采用半开区间。
- Client Pricing 必须引用同一 Client 的 CLIENT Agreement。
- Partner Share 必须引用同一 Local Partner 的 PARTNER Agreement。
- Version 进入 APPROVED 后，Version、Scope 与 Component 业务字段不可修改；调整条款创建新 Version。

### 2.3 `commercial_term_scope`

字段按顺序为：

```text
term_scope_id
term_version_id
operating_entity_id       NULL
country_code
currency
industry_code
mcc_code
payment_method
priority_no
create_time
update_time
```

规则：

- `operating_entity_id IS NULL` 继续表示所有被该 Agreement 覆盖的经营主体；其他维度继续使用批准的 `*` 通配值。
- Client Pricing 的 `payment_method` 使用支付方式目录或 `*`。
- Partner Share 当前不按支付方式区分，因此固定 `payment_method='*'`，不使用 NULL 表达第二种通配语义。
- 同一 Version 的 `priority_no` 唯一，最小 Priority 必须唯一命中。
- 因 MySQL 含 NULL 复合唯一键不能可靠阻止经营主体通配行重复，本阶段不增加派生 `scope_key/scope_hash`。封存或激活时在锁定 Version 后校验完整 Scope Tuple 不重复。

### 2.4 `commercial_term_component`

字段按顺序为：

```text
term_component_id
term_scope_id
component_code
component_type
trigger_code
calculation_base
rate                      NULL
fixed_amount              NULL
currency                  NULL
min_amount                NULL
max_amount                NULL
create_time
update_time
```

规则：

- 同一 Scope 内 `component_code` 唯一。
- `PERCENTAGE/FIXED` 沿用原金额、费率与币种矩阵。
- Client Pricing 使用受控 Client Fee 计算基数目录。
- Partner Share 固定 `calculation_base=ACTUAL_CLIENT_FEE`。
- 类型专属校验通过 Plan 的 `term_type` 在封存、激活和解析时 Fail Closed，不在四层重复冗余 `term_type`。

## 3. Hash 边界

### 3.1 删除

- Commercial Term Version：`hash_schema_version`、`content_hash`
- Commercial Term Scope：`scope_hash`
- Commercial Term Component：`canonical_hash`
- Commerce / Finance 的 `client_pricing_scope_hash/client_pricing_content_hash`
- Commerce / Finance 的 `partner_share_scope_hash/partner_share_content_hash`

这些字段只是同一结构化数据的中间摘要。Hash 不能代替数据本身，也不能单独保证不可变；保留它们会形成多层重算、版本升级和一致性维护成本。

### 3.2 保留

- CCT `commercial_context_hash`：证明一次 CCT-I13 输出是同一原子解析结果，升级为 V3。
- Commerce `snapshot_hash`：证明本地完整订单快照未被拼接或篡改。
- Finance `calculation_hash`：证明实际计算输入和结果。
- 文件、事件 Payload、Profile、Eligibility、Provider Cost、Tax、会计审批等具有独立消费者或签署/幂等意义的 Hash。
- Finance `economics_component.source_rule_content_hash` 作为跨规则族可空字段保留；Client Pricing / Partner Share 写 NULL，仍需要来源 Hash 的 Provider Cost 或 Tax 规则按各自契约填写。

### 3.3 V3 Canonical 输入

`CODE-MOR:CCT:COMMERCIAL_CONTEXT:3` 在 V2 其余 Proof 基础上，为 Client Pricing 和条件存在的 Partner Share 分别覆盖：

- Plan、Version、Scope 稳定 ID；
- Version No、有效区间、舍入方式；
- Scope 的经营主体、国家、币种、Industry、MCC、支付方式和 Priority；
- 每个 Component 的稳定 ID、Code、Type、Trigger、Calculation Base、Rate、Fixed Amount、Currency、Min、Max。

Component 按 `component_code`、`term_component_id` 稳定排序；可空属性显式为 JSON `null`，数字和时间继续使用项目统一 Canonical 规则。V2/V3 不得混算。

## 4. 下游结构化证据

- CCT-I05、CCT-I06、CCT-I13 仍返回两套语义结果，但每个组件返回完整结构化字段，不再返回 Component Hash。
- Commerce 增加统一的不可变商业条款头快照和组件快照，一笔 Order 的 Client Pricing 必有一组，Partner Share 仅在 `source_type=PARTNER` 时存在。
- Finance `economics_component` 补齐来源 Component 的结构化参数。计算必须使用原 Order 快照携带的参数，不回读当前 CCT 规则。
- 下游继续保留 `client_pricing_version_id/scope_id` 和 `partner_share_version_id/scope_id` 两组业务字段，不能用单个泛化 ID 覆盖两类同时存在的条款。

## 5. 明确不做

- 不合并 Client Fee 与 Partner Share 的业务含义、接口方法、会计科目或计算顺序。
- 不删除 Pricing / Share 业务 Version；费率需要按时间生效并保留历史。
- 不用 `owner_type + owner_id` 替代真实 Client / Local Partner 外键。
- 不新增泛化 Hash、Scope Key、规则 DSL、表达式引擎或真实数据库 Migration。
- 当前仓库没有生产数据，本轮不设计旧 8 表到新 4 表的数据迁移。

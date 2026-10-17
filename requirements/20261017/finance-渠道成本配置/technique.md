# Finance 渠道成本配置 — 技术方案

依据：[需求](./requirement.md)（D1—D5）、[阶段 2 现状分析](./delivery.md#阶段-2-现状差异与影响分析2026-10-17)、[Finance 数据库设计](../../../agent-docs/database/FINANCE_DATABASE.md) `channel_cost_*`。实现参照规则 Bundle（`ebb13bb`）的分层与锁写法。

## 1. 代码目录（Java Code 枚举，注册 TypeHandler）

| 枚举 | 取值 | 列 |
|---|---|---|
| `ChannelCostPlanStatus` | DRAFT、ACTIVE、INACTIVE | `t_channel_cost_plan.status`；本期不提供停用入口，INACTIVE 只作目录 |
| `ChannelCostVersionStatus` | DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE | `t_channel_cost_plan_ver.status` |
| `ChannelCostComponentType` | PROCESSING_FEE（处理费）、CROSS_BORDER_FEE（跨境费）、REFUND_FEE（退款费）、DISPUTE_FEE（争议费） | `t_channel_cost_component.component_type` |
| `ChannelCostCalculationBase` | TRANSACTION_AMOUNT（估算调用方传入的交易金额） | `calculation_base` |
| `AmountRoundingMode` | HALF_UP | `rounding_mode` |

组件类型只说明语义，算法对所有类型相同；Scope 已经按 `action_type` 区分支付/退款/争议，组件类型不参与匹配。

## 2. 接口设计（`ChannelCostPlanApi`，前缀 `/channel-cost-plans/v1`，内部开发接口）

| # | 方法与路径 | 请求体 | 结果 |
|---|---|---|---|
| C1 | `POST /` | `channelId, planCode` | 201 方案（DRAFT，无当前版本） |
| C2 | `POST /{planCode}/versions` | `makerId, effectiveFrom, scopes[{scopeNo, midId, entityNo, channelAgreementRef, countryCode, currency, paymentMethodCode, actionType, components[{componentCode, componentType, calculationBase, rateValue, fixedAmount, currency, minAmount, maxAmount, roundingMode}]}]` | 201 版本详情（DRAFT，`costVer` = 最大值 + 1，含 `contentHash`） |
| C3 | `POST /{planCode}/versions/{costVer}/submit` | `makerId` | 200 版本详情（PENDING_APPROVAL） |
| C4 | `POST /{planCode}/versions/{costVer}/approve` | `checkerId, approvedContentHash` | 200 版本详情（ACTIVE 或 APPROVED） |
| C5 | `POST /{planCode}/versions/{costVer}/reject` | `checkerId, approvedContentHash` | 200 版本详情（REJECTED） |
| C6 | `GET /{planCode}` | — | 方案（渠道、状态、当前版本号）与全部版本摘要（版本号降序） |
| C7 | `GET /{planCode}/versions/{costVer}` | — | 版本详情含按 `scopeNo` 排序的适用范围，范围内按 `componentCode` 排序的组成 |

字段格式（Bean Validation，违反为 422；业务枚举代码不合法为 400 `INVALID_REQUEST`）：
- `planCode`、`paymentMethodCode`、`actionType`、`componentCode`：`[A-Z][A-Z0-9_]{0,63}`；`channelId`、`midId`、`makerId`、`checkerId`：正整数十进制字符串；`entityNo`：`[A-Z]{2}[0-9]{3}`（与余额查询一致）；`countryCode`：`[A-Z]{2}`；币种：`[A-Z]{3}`；`channelAgreementRef`：1—128 非空白。
- 费率与金额均为十进制字符串：费率最多 10 位小数，金额最多 8 位小数。`approvedContentHash`：64 位小写十六进制。

业务校验（`BizCheckFailException`，400 `BUSINESS_CHECK_FAILED`）：
- C1：`channelId` 已有方案或 `planCode` 已存在时拒绝；以 `LOCK channel-cost-plan:{planCode}` 串行化，`uk_channel_cost_plan/code` 兜底，并发重复为 409。
- C2：方案存在；`scopes` 非空且每个范围的 `components` 非空；`scopeNo` 为正且不重复；六个匹配维度组合在版本内不重复；范围内 `componentCode` 不重复；费率与固定费至少一项；费率在 [0,1]；固定费、上下限非负；固定费与其币种成对出现，且固定费币种等于范围币种（本期不换汇）；上下限同时存在时下限不大于上限；范围币种在 `t_currency_spec` 中为 ACTIVE，固定费、上下限的小数位不超过该币种精度；同一方案已有 DRAFT 或 PENDING_APPROVAL 版本时拒绝。
- C3：仅 DRAFT，提交人 = 编制人。
- C4/C5：仅 PENDING_APPROVAL；Checker ≠ Maker；`approvedContentHash` 等于版本 `content_hash`。
- C4 批准：若 `effectiveFrom ≤ 当前时间`，新版本生效时间必须晚于原当前版本；本版本 ACTIVE；原当前版本 INACTIVE 且 `effective_to` = 本版本 `effective_from`；方案 `current_ver_id` 切换、DRAFT → ACTIVE、`version+1`（CAS）。未到生效时间保持 APPROVED，指针不变（到期切换不在本期）。写 `checker_id`。
- C5 驳回：REJECTED，写 `checker_id`。
- C2—C5 共用 `LOCK channel-cost-plan:{planCode}`（`AbstractLockProcessor`，持续等待）；事务内按“方案 → 版本”顺序 `FOR UPDATE`，状态迁移以原状态为条件。

## 3. 内容摘要 `CODE-MOR:FINANCE:CHANNEL_COST:1`

C2 创建时计算并写入 `content_hash`，之后不再变化。JSON 经 RFC 8785 规范化后取 SHA-256 小写十六进制：

```
{schema, planCode, channelId, costVer, effectiveFrom, makerId,
 scopes: [按 scopeNo 升序 {scopeNo, midId, entityNo, channelAgreementRef, countryCode, currency, paymentMethodCode, actionType,
   components: [按 componentCode 升序 {componentCode, componentType, calculationBase, rateValue, fixedAmount, currency, minAmount, maxAmount, roundingMode}]}]}
```

ID 与金额都为字符串；费率去掉末尾零后以普通十进制输出；金额按范围币种精度补齐；`effectiveFrom` 为 UTC 微秒 ISO-8601；空值输出 JSON null。不含任何生成的主键，同样内容在不同环境得到同一摘要。

## 4. 成本估算（`ChannelCostEstimationService`，领域服务，D3）

`ChannelCostEstimate estimate(ChannelCostEstimateCommand command)`；命令为 `channelId, midId, entityNo, countryCode, currency, paymentMethodCode, actionType, basisAmount, occurredAt`。只读，不开事务，可在调用方事务内执行。

1. **入参**：`basisAmount` 非负且小数位不超过币种精度；币种在 `t_currency_spec` 中为 ACTIVE。
2. **方案**：按 `channelId` 查方案，须存在且 ACTIVE。
3. **版本**（G8）：该方案中状态为 ACTIVE 或 INACTIVE、满足 `effective_from ≤ occurredAt < effective_to`（为空视为无穷）的版本恰好一个。APPROVED（未切换）、DRAFT、REJECTED 版本永不参与。
4. **范围**：版本内六个维度逐值精确相等的范围恰好一个（语义唯一键保证不会多于一个）。
5. **逐组成计算**（按 `componentCode` 升序）：
   - `raw = basis × rate（有费率时） + fixed（有固定费时）`，按 8 位小数 HALF_UP；
   - 有下限且 `raw < min` 取 `min`，有上限且 `raw > max` 取 `max`；
   - `posted = raw` 按币种精度与组件舍入方式归整。
6. **结果**：方案 ID、版本 ID/号、`contentHash`、范围 ID/号、`channelAgreementRef`、逐组成（组成 ID、代码、类型、计算基础、费率、固定费、币种、上下限、舍入方式、基数、`rawAmount`、`postedAmount`）与 `totalPostedAmount`。这些字段一一对应 `t_transaction_economic_component` 的 CHANNEL_COST 证据列（`source_rule_type=CHANNEL_COST`、`source_trigger_code=actionType`），每个组成写一行经济组件即可被过账引擎按 `CHANNEL_COST` 汇总。
7. 步骤 2—4 任一不满足即拒绝（`BizCheckFailException`），消息含渠道、MID、主体、币种、支付方式、动作与发生时间；不返回零成本。

## 5. 数据库与文档变更

- **DDL/DML**：无。四张表已存在且与模型一致。
- **文档**：
  - `FINANCE_DATABASE.md` `channel_cost_component` 行后补一句 V1 组件类型与计算基础目录（第 1 节）；
  - [交易自动记账依赖清单](../../20261016/finance-交易自动记账/dependencies.md)：X5 拆为“渠道成本配置（本需求已实现）”与“渠道清算账户维护（待 X6）”，新增 X6“Payment 提供 MID 权威元组查询接口（Channel、Channel Account、OE、币种），Finance 创建 `t_channel_account` 时核对”；
  - 阶段 5 更新 `CURRENT_IMPLEMENTATION.md` 与 `requirements/README.md` Finance 行。
- **测试清理**：四张表不预置数据，清理器全部清空。

## 6. 代码落位

- `paytech-finance-accounting`：枚举；4 个 DataObject、Mapper（XML）、Repository；`domain/ChannelCostVersionContext`、`domain/ChannelCostContentHash`、视图；`ChannelCostPlanService`（保存、状态迁移、查询）与 `ChannelCostEstimationService`（估算）各自 `interface + Impl`；`service/processor` 下 `CreateChannelCostPlanProcessor`、`CreateChannelCostVersionProcessor`、`SubmitChannelCostVersionProcessor`、`ApproveChannelCostVersionProcessor`、`RejectChannelCostVersionProcessor`；`AccountingLocks.channelCostPlan(planCode)`。
- `paytech-finance-api`：`ChannelCostPlanApi` 与请求/响应 Body。
- `paytech-finance-web`：`ChannelCostPlanController`、转换器，枚举注册到 `FinancePersistenceConfiguration`。

## 7. 测试与验收映射

| AC | 用例（入口） |
|---|---|
| AC-1 | 方案：创建 DRAFT、同渠道重复、同代码重复、查询（HTTP） |
| AC-2 | 版本：创建 DRAFT 且摘要为 64 位并可复算；费率 > 1、费率与固定费都为空、固定费缺币种、固定费币种与范围不一致、下限大于上限、金额超币种精度、币种未配置、范围序号重复、维度组合重复、组成代码重复、空范围/空组成、非法组件类型代码；已有待处理版本（HTTP） |
| AC-3 | 提交非编制人被拒；Maker 自审、摘要不一致被拒；到期批准 → ACTIVE、方案 ACTIVE；第二版本到期批准 → 原版本 INACTIVE 且失效时间正确；未来版本 → APPROVED、指针不变；驳回；已驳回不可批准（HTTP） |
| AC-4 | 估算：费率 + 固定费、触发下限、触发上限、多组成合计、HALF_UP 舍入、零基数只收固定费、按发生时间命中已停用的旧版本、APPROVED 版本不参与；无方案、无覆盖版本、无命中范围、币种未配置、基数超精度被拒（领域服务入口） |
| AC-5 | `clean verify` |

## 8. 阶段 4 多 agent 分工（建议）

- 主会话：共享骨架（枚举与 TypeHandler 注册、四表 DataObject 与 Repository 接口、锁名）。
- A：持久化实现、`ChannelCostPlanService`、摘要、5 个 Processor、C1—C7 接口、文档。
- C：`ChannelCostEstimationService` 与命令/结果模型。
- B：测试（Builder/Mocker/Checker、清理器、全部用例）。
- 主会话：转达合同细节、合并核对、`clean verify`。

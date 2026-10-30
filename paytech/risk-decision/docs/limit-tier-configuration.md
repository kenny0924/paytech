# 限额档位的决策流配置说明（REQ-04）

> 依据：[需求](../../../requirements/20261029/risk-decision-限额档位/requirement.md) B8、B11，[技术方案](../../../requirements/20261029/risk-decision-限额档位/technique.md) 第 8.3、10 节。本文写给风控配置员与发布执行人。代码只交付 TIER 名单、放行事件与档位数据；变量、规则集、流程版本与执行发布都在现有运营页面（或同一配置接口）创建，**激活带档位分支的发布就是开关**，需单独授权。
>
> 本文按阶段 4 实测的引擎约束编写，与技术方案第 10 节大纲不同之处见第 7 节。日 / 月额度按 UTC 自然日计算（用户 2026-10-30 确认方案 B）。

## 1. 前提（发布执行人逐项确认）

1. risk-decision 已部署本需求镜像；`mor_risk_decision` 已执行 `docs/database/sql/changes/2026-10-30-client-limit-tier.sql`，四张 TIER 名单存在：
   ```sql
   SELECT list_id, list_code, list_purpose, supported_subject_type, status FROM t_risk_list WHERE list_purpose = 'TIER';
   -- 300001 TIER_VERIFICATION / 300002 TIER_OBSERVATION / 300003 TIER_STANDARD / 300004 TIER_RESTRICTED，全部 CLIENT、ACTIVE
   ```
2. 放行事件已在写入：新镜像上线后，结论为放行的评估会多写一条 `PAYMENT_ATTEMPT_ALLOWED` / `CHECKOUT_ALLOWED` 事件。
3. 存量迁移已完成（见第 6 节），且迁移核对为 0 缺口。
4. 风控已给出各档位 × 币种的额度正式值。第 4 节的数值只是 TEST 示例。

## 2. 变量清单

| 变量编码（建议） | 风控点 | 类型 | 说明 |
|---|---|---|---|
| `PAY_TIER_IN_VERIFICATION` / `_OBSERVATION` / `_STANDARD` / `_RESTRICTED` | 支付尝试 `PAYMENT_ATTEMPT_RISK` | 名单命中（LIST，BOOLEAN），名单 `TIER_*`，主体 CLIENT，用途：规则条件 + 流程条件 | 档位分支条件 |
| `CO_TIER_IN_VERIFICATION` / … / `_RESTRICTED` | 结账 `CHECKOUT_PREPARE` | 同上 | 结账点档位分支条件 |
| `TIER_{TIER}_{CCY}_SINGLE` | 支付尝试 | 常量，NUMBER，量纲 MONEY，**币种来源 = 支付币种变量 `PAY_INPUT_CURRENCY`** | 单笔上限；`{CCY}` 是该值适用的币种（规则里再限定币种，见第 3 节） |
| `TIER_{TIER}_{CCY}_DAY` / `_MONTH` | 支付尝试 | 同上 | UTC 当日上限 / 近 30 个 UTC 自然日（含当日）上限 |
| `PAY_CLIENT_ALLOWED_DAY` / `PAY_CLIENT_ALLOWED_30D` | 支付尝试 | 实时累计（VELOCITY，NUMBER，MONEY，币种变量 `PAY_INPUT_CURRENCY`）：事件 `PAYMENT_ATTEMPT_ALLOWED`、主体 CLIENT、统计方式 **`DAILY_SUM_AMOUNT`（按 UTC 日汇总）**、窗口 86400 / 2592000、计入本次 | 同币种的 UTC 当日 / 近 30 日放行合计（含本次）；读日放行汇总表，至多 30 行 |
| `TIER_{TIER}_CURRENCIES` | 全局（不选风控点） | 集合，STRING，格式 `UPPER_ALPHA_3`，成员为币种代码 | 该档位允许的币种；STANDARD 可不建 |

命名约定是页面读取额度的依据：限额档位页（L10 / L2）只解析编码以 `TIER_` 开头的 ACTIVE 常量与集合，常量名中的 `{TIER}` 与 `{CCY}` 决定归档。不符合约定、币种不在集合内、或缺 SINGLE / DAY / MONTH 的项会出现在页面的"配置问题"中，不会被当成额度。

**为什么常量的币种来源是支付币种而不是固定币种**：规则保存时，引擎要求比较两侧金额的币种来源相同（`VariableConditionValidator.compatible`）。支付金额的币种来源是 `PAY_INPUT_CURRENCY`，所以固定币种常量（如固定 USD）不能与支付金额比较，保存即被拒。改为"币种来源 = 支付币种"后可以比较，但常量本身不再自带币种，币种限定必须写进规则条件（第 3 节）。这类常量必须属于支付尝试风控点。

## 3. 规则集（每个档位一个，首个终态命中即结束）

| 序 | 条件 | 结论 / 原因 |
|---|---|---|
| 1 | `PAY_INPUT_CURRENCY` NOT_IN `TIER_{TIER}_CURRENCIES`（STANDARD 档位不建本条） | DENY，原因"该币种不在本档位额度内" |
| 2 | AND（`PAY_INPUT_CURRENCY` EQ `"{CCY}"`，`PAY_INPUT_AMOUNT` GT `TIER_{TIER}_{CCY}_SINGLE`）——每个已配置币种一条 | DENY，原因"超出单笔额度" |
| 3 | AND（`PAY_INPUT_CURRENCY` EQ `"{CCY}"`，`PAY_CLIENT_ALLOWED_DAY` GT `TIER_{TIER}_{CCY}_DAY`）——每个已配置币种一条 | DENY，原因"超出当日额度" |
| 4 | AND（`PAY_INPUT_CURRENCY` EQ `"{CCY}"`，`PAY_CLIENT_ALLOWED_30D` GT `TIER_{TIER}_{CCY}_MONTH`）——每个已配置币种一条 | DENY，原因"超出 30 日额度" |

- 第 2 条中的 `EQ "{CCY}"` 必须写：常量的币种跟随支付币种，不加这一条，USD 的上限会被拿去比较 AED 金额。
- 日额度在 UTC 零点（迪拜 04:00）重新开始；30 日额度是近 30 个 UTC 自然日（含当日）的合计。累计只计风控放行的金额（含之后支付失败的），被拒的评估不占额度。
- 各币种分别设额度、分别累计，不换汇；某档位没有配置的币种：VERIFICATION / OBSERVATION / RESTRICTED 由第 1 条拒绝，STANDARD 放行（不设额度）。

## 4. 目标流程

决策流的 START 节点**只能有一条无条件出口**（`开始节点需要一条无条件出口`），所以档位分支放在 START 之后的一个"档位分支"规则集节点上：

```
START ─无条件─▶ 档位分支（规则集 TIER_GATE：只含不带终态动作的规则，不改变结论）
   ├─[PAY_TIER_IN_RESTRICTED = true]──▶ END(DENY，"受限档位")
   ├─[PAY_TIER_IN_VERIFICATION = true]─▶ 规则集 档位额度·验证 ─┐
   ├─[PAY_TIER_IN_OBSERVATION = true]──▶ 规则集 档位额度·观察 ─┼─[节点处理结果 = DENY]─▶ END(DENY，"超出档位额度")
   ├─[PAY_TIER_IN_STANDARD = true]─────▶ 规则集 档位额度·标准 ─┘        └─ELSE─▶ 原流程（block → auth → …）
   └─ELSE──────────────────────────────▶ END(DENY，"未完成承保 / 无交易额度")
```

- 档位分支节点的规则集可只含一条无动作规则（例如"支付金额 > 0"），它只产生轨迹，不影响结论。
- 支付尝试点：在原 `PAY_ATTEMPT_FLOW` v1 前接上档位层；原 block → auth 全部节点保持不变，作为额度规则集"ELSE"出口之后的路径。
- 结账点（`CHECKOUT_PREPARE`）：同样的档位层，额度规则集只放第 1 条（币种集合）。单笔上限只在支付尝试点判定（见第 7 节 C-1）。

### TEST 环境示例配置

阶段 4 的自动测试 `PaymentRiskLimitTierApiAutoTest` 按下面这套配置经真实配置接口创建并激活，可照抄到 TEST 环境：

| 变量 | 值 |
|---|---|
| `TIER_VERIFICATION_USD_SINGLE` | 50（MONEY，币种来源 `PAY_INPUT_CURRENCY`，风控点 `PAYMENT_ATTEMPT_RISK`） |
| `TIER_VERIFICATION_USD_DAY` / `_MONTH` | 250 / 400（同上） |
| `TIER_VERIFICATION_AED_SINGLE` | 200（同上） |
| `TIER_VERIFICATION_AED_DAY` / `_MONTH` | 1,000 / 3,000（同上） |
| `PAY_CLIENT_ALLOWED_DAY` / `_30D` | DAILY_SUM_AMOUNT，86400 / 2592000，计入本次 |
| `TIER_OBSERVATION_USD_SINGLE` | 2,000 |
| `TIER_STANDARD_USD_SINGLE` | 10,000 |
| `TIER_VERIFICATION_CURRENCIES` | {USD, AED} |
| `TIER_OBSERVATION_CURRENCIES` | {USD} |
| `TIER_STANDARD_CURRENCIES` | 不建（STANDARD 放行未配置币种） |

变量创建请求体（`POST /decision-variables/v1/variables`，经运营控制台的变量页面同样可建）：

```json
{"variableCode": "TIER_VERIFICATION_USD_SINGLE", "variableName": "验证档 USD 单笔上限", "sourceType": "CONSTANT",
 "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["RULE_CONDITION", "FLOW_CONDITION"],
 "formatCode": "NONE", "numericUnit": "MONEY", "currencyVariableCode": "PAY_INPUT_CURRENCY", "currentValue": "50",
 "status": "ACTIVE"}
```

```json
{"variableCode": "TIER_VERIFICATION_CURRENCIES", "variableName": "验证档允许币种", "sourceType": "COLLECTION",
 "valueType": "STRING", "pointCode": null, "usages": ["RULE_CONDITION", "FLOW_CONDITION"],
 "formatCode": "UPPER_ALPHA_3", "status": "ACTIVE"}
```

成员：`PUT /decision-variables/v1/variables/TIER_VERIFICATION_CURRENCIES/members`，`{"expectedVersion": "1", "members": [{"value": "USD", "displayName": "USD"}, {"value": "AED", "displayName": "AED"}]}`。

档位名单变量：`sourceType = LIST`、`valueType = BOOLEAN`、`list = {"listCode": "TIER_VERIFICATION", "subjectType": "CLIENT"}`，风控点为对应风控点。

示例验收结果（自动测试已覆盖）：
- 无档位 Client：DENY，路径 start → 档位分支 → 无档位拒绝；
- RESTRICTED：DENY，路径到受限拒绝，不进入额度规则集；
- VERIFICATION：USD 60 拒绝、USD 50 放行；同日 5 笔 USD 50 后再 10 拒绝（当日 260 > 250），被拒与重放不进汇总，AED 另行累计；SAR 拒绝；
- 30 日：昨日 300 计入 30 日、不计入当日，30 天前的不计入，第三笔使 30 日合计 450 > 400 拒绝；
- 同一 Client 同币种 8 笔并发放行后当日汇总为 8 笔之和；
- STANDARD：SAR 放行、USD 9,000 放行、USD 10,001 拒绝；
- OBSERVATION：USD 1,500 放行、2,500 拒绝、AED 拒绝；
- 把 `TIER_VERIFICATION_USD_SINGLE` 改为 100 后，同一发布下一次评估 USD 60 即放行（常量在评估时读当前值，无需新发布）；
- 结账放行同样写入日汇总（`CHECKOUT_ALLOWED`），但结账点不配置金额额度。

## 5. 激活前检查与激活

1. 迁移核对 0 缺口（第 6 节 SQL 在 commercial 只读导出名单上对照 `t_client_limit_tier`）。
2. 新流程版本建执行发布 → 校验 → 影子运行（激活的技术门槛，须 PASSED）。**影子运行不能用来核对迁移**：回放按样本的原评估时间判断名单成员，迁移之前的样本一律走"无档位拒绝"（阶段 5 本机：支付 36 / 37、结账 23 / 23 条放行 → 拒绝，路径全部为无档位）。迁移覆盖以第 1 步 SQL 为准，激活后按第 4 步做实时冒烟。
3. 限额档位页（L10 `GET /risk-limit-tiers/v1/tier-quotas`）`configurationIssues` 为空。
4. 激活。生效后立即对迁移名单中的每个 Client 做一笔额度内的实时评估（应放行），再用演示 Client 各验证一笔额度内、一笔超额、一笔未配置币种。

**回滚**：已作废的发布不能重新激活。回滚 = 用旧流程版本（`PAY_ATTEMPT_FLOW` v1、`CHECKOUT_BASELINE_ALLOW` v1）新建执行发布并激活；档位数据与名单保留不动。回滚发布的草稿在激活前准备好。

**调整额度**：改常量即时生效（下一次评估），无需新发布，权限沿用变量管理（`RISK_VARIABLE_MANAGE`），没有四眼；每次调整记在变更单中。

## 6. 存量迁移（发布执行人）

1. 只读导出 commercial 中有 ENABLED 支付方式开通项的 CLIENT 合同对应的 Client，并从 client-master 只读补全名称、编号、类型、辖区。
2. 临时设置 `RISK_LIMIT_TIER_MIGRATION_CALLERS=<执行方服务名>` 重启或刷新 risk-decision，私网调用：
   ```
   POST /risk-limit-tiers/v1/internal/tier-migrations
   X-Caller-Service: <执行方服务名>
   {"executedBy": "<执行人 Operations 账号 ID>", "reason": "REQ-04 上线前存量迁移",
    "clients": [{"clientId": "…", "clientDisplayName": "…", "clientNo": "…", "clientType": "ORGANIZATION", "jurisdictionCode": "AE"}]}
   ```
   每个 Client 返回 `CREATED` 或 `SKIPPED_EXISTING`；重复执行幂等。
3. 核对：导出名单中的每个 Client 都在 `t_client_limit_tier`，且在 `TIER_STANDARD`（或已有档位）名单中有生效成员。
4. 清空白名单。

## 7. 与技术方案大纲的差异（阶段 4 实测）

| 编号 | 差异 | 处理 |
|---|---|---|
| C-1 | 固定币种的 MONEY 常量不能与支付金额比较（保存规则即被拒） | 常量改为支付点、币种来源 = 支付币种，规则用 `PAY_INPUT_CURRENCY EQ "{CCY}"` 限定币种。结账点的金额币种来源是结账币种变量，同名常量不能复用，所以结账点不做单笔上限，只做档位 + 受限 + 币种集合；单笔上限在支付尝试点（实际扣款点）判定。主会话 2026-10-30 已批准（修改技术方案 S-11 / O-5） |
| C-2 | CLIENT 主体按明细的金额合计窗口最长 3600 秒，且没有 30 天窗口（单 Feature 200 ms 时限） | 用户 2026-10-30 选方案 B：新增日放行汇总表与统计方式 `DAILY_SUM_AMOUNT`，日额度改为 UTC 当日、月额度改为近 30 个 UTC 自然日；档位页"已用"读同一汇总 |
| C-3 | START 只能有一条无条件出口 | 档位分支挂在 START 后的"档位分支"规则集节点上 |

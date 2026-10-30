# 联合查询（Joint Query）讨论稿

状态：**已确认**（2026-10-29）：决定 [2026-10-29 联合查询](../../agent-docs/decisions/2026-10-29-data-query-joint-query.md)（Q1—Q7 按第 12 节建议）；需求 [`requirements/20261029/platform-support-联合查询/`](../../requirements/20261029/platform-support-联合查询/requirement.md)（已实施并部署 mor-test，2026-10-30）。依托 [2026-10-22 数据查询决定](../../agent-docs/decisions/2026-10-22-data-query-into-platform-support.md) 与 [数据查询技术方案](../../requirements/20261022/platform-support-数据查询服务/technique.md)；本稿只描述在 data-query 之上的增量。配套 [`joint-query.pen`](joint-query.pen) 四张画板：1 定位与概念；2 键类型图与走数例子（退款单号）；3 执行与护栏；4 数据、接口与待定问题。

> **2026-10-29 需求阶段 2—3 修订**（以需求 [technique.md](../../requirements/20261029/platform-support-联合查询/technique.md) 为准）：执行改为按跳分步（Gateway 10 秒上限）；联合查询配置改存模板版本新列 `joint_json`（行主体键类型、键参数、列标注），不写进 `parameters_json` / `columns_json`；链路图按行主体键类型的系统泳道定位（顺序取键类型 `sort_no`），不再按“第几层”分列；键类型表增加 `value_pattern`、`input_hint`。下文第 5—9 节保留讨论时的原文。

## 1. 目标与范围

运营输入任意一个单号（交易订单号、退款单号、支付意图 ID、渠道交易号……）并选择它的键类型，系统沿着各系统已登记的查询模板自动展开，把整条链路上的数据一次查出，并说明每条数据是怎么查到的。

- 首期只服务 Operations 控制台，调用方只有 `operations-console-bff`。
- 只读、只用于排查，结果不作为业务执行依据；数据源、只读校验、系统库拒绝与出参屏蔽全部沿用 data-query，不新增跨库例外。
- 不做查询记录（沿用 C14）。

## 2. 已定事项（2026-10-29 一轮）

| # | 结论 |
|---|---|
| J1 | 复用 data-query 模板：在模板的参数声明和列声明上加键类型标注，不另建“联合查询 SQL”。一份模板既可单独执行，也可参与联合查询。 |
| J2 | 键类型按业务语义定义，由 Platform 维护一个键类型注册表；不靠列名匹配。 |
| J3 | 输出列分三种角色：**链路键**（参与展开）、**维度键**（只展示，可点击另起一次联合查询，不自动展开）、普通列。默认不展开。 |
| J4 | 只有“恰好一个必填键参数”的模板参与联合查询；键参数建议为 LIST，同层同键类型的多个值合并为一次执行。 |
| J5 | 首期按键类型图自动推导，不做预设“链路方案”；执行前提供可达预览。 |
| J6 | 展开有护栏（层数、扇出、行数、模板数、总超时），触发即返回部分结果并标明截断位置；单个模板失败不影响其他节点。 |
| J7 | 入口由用户选择键类型，不做自动识别。 |
| J8 | 结果按系统 / 模板分组表格展示，并配链路图（真实图形画布）；每条数据带来源说明。 |

## 3. 概念

- **键类型（Key Type）**：一种业务标识的语义，如 `ORDER_ID`。属性：编码、名称、所属系统（Owner）、值类型（INTEGER / STRING）、说明。
- **键参数**：模板参数声明里绑定了键类型的那个参数，表示“这份模板能按这种键查”。
- **链路列 / 维度列**：模板列声明里绑定了键类型的列，表示“这份模板的结果里含有这种键”。
- **键类型图**：节点是键类型，模板 T 的键参数为 A、链路列含 B、C 时产生边 A →T→ B、A →T→ C。图由当前 ACTIVE 模板静态算出。
- **一次联合查询**：从 `(键类型, 值)` 出发在图上按层 BFS，`visited` 记 `(键类型, 规范值)`，同一个键值不重复查询。

## 4. 首批键类型清单（初稿，按现有表结构核对）

| 键类型 | Owner | 值类型 | 典型来源列 |
|---|---|---|---|
| `ORDER_ID` | acquiring | INTEGER | `t_commerce_order.order_id`；`t_refund_request.order_id`；`t_dispute_case.order_id`；payment `t_payment_intent.source_id`（按订单来源类型过滤） |
| `ORDER_NO` | acquiring | STRING | `t_commerce_order.order_no` |
| `CLIENT_ORDER_ID` | acquiring | STRING | `t_commerce_order.client_order_id`（只在单个 Client 内唯一，见 Q3） |
| `CHECKOUT_SESSION_ID` | acquiring | INTEGER | `t_checkout_session.checkout_session_id` |
| `REFUND_REQUEST_ID` | acquiring | INTEGER | `t_refund_request.refund_request_id`；`t_channel_refund.commerce_refund_request_id` |
| `REFUND_REQUEST_NO` | acquiring | STRING | `t_refund_request.refund_request_no` |
| `PAYMENT_INTENT_ID` | payment | INTEGER | `t_payment_intent`、`t_payment_attempt`、`t_dispute_case` |
| `PAYMENT_ATTEMPT_ID` | payment | INTEGER | `t_payment_attempt`、`t_payment_action`、`t_dispute_case` |
| `PAYMENT_ACTION_ID` | payment | INTEGER | `t_payment_action`、`t_channel_transaction_ref`、channel-connector `t_channel_interaction` |
| `CHANNEL_TRANSACTION_REF_ID` | payment | INTEGER | `t_channel_transaction_ref`、reconciliation `t_platform_txn` |
| `EXTERNAL_TRANSACTION_ID` | 渠道 | STRING | `t_channel_transaction_ref`、`t_channel_callback_item`、`t_platform_txn`、`t_channel_statement_line`（跨渠道可能重复，见 Q2） |
| `CHANNEL_REFUND_ID` | payment | INTEGER | `t_channel_refund`、`t_refund_request.channel_refund_id`、`t_payment_action` |
| `DISPUTE_CASE_ID` | dispute | INTEGER | `t_dispute_case` |
| `BILLING_FACT_ID` | billing | INTEGER | `t_billing_fact.id`；account `t_account_posting.fact_id`；ledger `t_accounting_instruction.economic_fact_id`；reconciliation `t_platform_txn.billing_fact_id` |
| `ACCOUNT_POSTING_ID` | account | INTEGER | `t_account_posting`；ledger `t_accounting_instruction.source_event_key`（ACCOUNT_POSTING 指令） |
| `JOURNAL_ENTRY_ID` | ledger | INTEGER | `t_journal_entry`、payout `t_payout.journal_entry_id` |
| `PAYOUT_ID` / `PAYOUT_NO` | payout | INTEGER / STRING | `t_payout`、`t_payout_execution` |

**维度键**（只展示、不自动展开）：`CLIENT_ID`、`ENTITY_NO`、`LOCAL_PARTNER_ID`、`CHANNEL_ID`、`CHANNEL_ACCOUNT_ID`、`MID_ID`、`CLEARING_ACCOUNT_ID`。

**多态引用列**（如 `t_billing_fact_ref(ref_type, ref_id)`、`t_payment_intent(source_type, source_id)`）：首期一列只绑定一个固定键类型，由模板作者在 SQL 里按类型过滤或拆列，例如：

```sql
SELECT f.id AS billing_fact_id, f.fact_type, ...,
       MAX(CASE WHEN r.ref_type = 'PAYMENT_INTENT' THEN r.ref_id END) AS payment_intent_id,
       MAX(CASE WHEN r.ref_type = 'REFUND_REQUEST' THEN r.ref_id END) AS refund_request_id
FROM t_billing_fact f JOIN t_billing_fact_ref r ON r.fact_id = f.id
WHERE f.id IN (SELECT fact_id FROM t_billing_fact_ref
               WHERE ref_type = 'ORDER' AND ref_id IN (#{orderIds}))
GROUP BY f.id
```

“按另一列的值决定键类型”的动态绑定先不做（Q4）。

## 5. 模板标注（data-query 增量）

**参数声明 `parameters_json`**：参数可加 `keyType`。

```json
[{"name":"orderIds","type":"LIST","elementType":"INTEGER","maxSize":200,"required":true,"keyType":"ORDER_ID"}]
```

**列声明 `columns_json`**：列可加 `keyType` 与 `keyRole`（`LINK` / `DIMENSION`）。

```json
[{"name":"payment_intent_id","label":"支付意图","keyType":"PAYMENT_INTENT_ID","keyRole":"LINK"},
 {"name":"client_id","label":"客户","keyType":"CLIENT_ID","keyRole":"DIMENSION"}]
```

**参与联合查询的条件**（保存时计算，不符合的不报错，只在警告里说明“不参与联合查询”的原因）：

1. 模板 ACTIVE；
2. 恰好一个必填参数带 `keyType`，其余参数都可选或有默认值；FRAGMENT 参数取默认值；
3. 键参数是 LIST（推荐）或与键类型值类型一致的标量；标量时每个值单独执行一次；
4. 至少有一个带 `keyType` 的列（否则它只是图上的“叶子”，仍可参与，只是不再展开）。

**保存校验（新增）**：

- `keyType` 必须是注册表中 ACTIVE 的键类型；参数元素类型与键类型值类型一致。
- 带 `keyType` 的列名必须出现在试运行的结果列中（沿用现有列声明同步逻辑，见 `templateSync.ts`）。
- 维度键类型的 `keyRole` 只能是 `DIMENSION`（注册表上标记，见第 8 节 `default_role`）。

## 6. 执行

### 6.1 流程（`ExecuteJointQueryProcessor`，无锁、无事务，所有查询都是外部调用）

```
输入：keyType, values[]（1..N，默认上限 20）
frontier = {(keyType, normalize(v))}；visited = frontier；depth = 0
while frontier 非空 且 depth < maxDepth:
    按键类型分组 frontier
    对每个键类型 K，找出所有以 K 为键参数的参与模板 T：
        用 K 在本层的全部值执行一次 T（LIST），超过 maxValuesPerKey 截断并记录
    本层所有 (K, T) 并行执行（受请求级并发与数据源连接池限制）
    对每个结果行：记来源 (T, K=v, 行号)；
        取 LINK 列的非空值 → (K', v')，未 visited 的进入 next
    frontier = next；depth++
返回：节点（模板执行）、边（来源）、各模板结果、截断与错误
```

- **值规范化**：键值统一按字符串传递和去重；INTEGER 键去掉前导零、校验 64 位范围；STRING 键原样（不 trim 大小写）。绑定参数时按模板参数类型转换。
- **同一模板可能在多层被触发**（不同键类型命中同一模板）：分别执行，结果行按模板主键去重的能力首期不做，界面按“模板 × 触发键”分组展示（Q5）。
- **回边**：退款 → 订单 → 该订单全部退款，属于正常展开；`visited` 保证不循环。
- **分页**：联合查询中每个模板只取第一页（`min(maxRowsPerTemplate, 模板 maxPageSize)`），超出的行标记“结果被截断”，用户可点开该节点跳转到单模板查询看全部。

### 6.2 护栏（Platform 配置项，默认值待定）

| 配置 | 默认 | 说明 |
|---|---|---|
| `maxDepth` | 6 | 最大展开层数 |
| `maxValuesPerKey` | 50 | 单层单键类型最多带多少个值去查 |
| `maxRowsPerTemplate` | 100 | 每次模板执行最多取多少行 |
| `maxTemplateExecutions` | 60 | 整次联合查询最多执行多少次模板 |
| `maxConcurrency` | 8 | 单次请求的并发执行数 |
| `totalTimeoutSeconds` | 30 | 整次超时；单模板仍受其 `timeoutSeconds` 约束 |

任何一项被触发：停止新的执行，已有结果正常返回，响应中 `truncations[]` 说明触发项与位置。

### 6.3 可达预览

`GET` 键类型图：给定入口键类型，返回从它出发可达的键类型与模板（按层），以及“不参与联合查询”的模板及原因。运营执行前、模板作者配置后都能看到链路是否断开。

## 7. 走数例子：输入退款单号

用户选择 `REFUND_REQUEST_NO`，输入 `RF2026…`：

| 层 | 触发键 | 模板（所属系统） | 新得到的链路键 |
|---|---|---|---|
| 0 | `REFUND_REQUEST_NO` | 退款申请 by 单号（acquiring） | `REFUND_REQUEST_ID`、`ORDER_ID`、`CHANNEL_REFUND_ID` |
| 1 | `ORDER_ID` | 订单（acquiring）；订单全部退款（acquiring）；支付意图 by 订单（payment）；计费事实 by 订单（billing）；争议 by 订单（dispute） | `PAYMENT_INTENT_ID`、其他 `REFUND_REQUEST_ID`、`BILLING_FACT_ID`、`DISPUTE_CASE_ID` |
| 1 | `CHANNEL_REFUND_ID` | 渠道退款（payment） | `PAYMENT_ACTION_ID` |
| 2 | `PAYMENT_INTENT_ID` | 支付尝试（payment） | `PAYMENT_ATTEMPT_ID` |
| 2 | `BILLING_FACT_ID` | 记账批次（account）；会计指令（ledger）；平台流水（reconciliation） | `ACCOUNT_POSTING_ID`、`CHANNEL_TRANSACTION_REF_ID` |
| 3 | `PAYMENT_ATTEMPT_ID` | 支付动作（payment） | `PAYMENT_ACTION_ID` |
| 3 | `ACCOUNT_POSTING_ID` | 会计指令与凭证（ledger） | `JOURNAL_ENTRY_ID` |
| 4 | `PAYMENT_ACTION_ID` | 渠道交易引用（payment）；渠道交互（channel-connector） | `EXTERNAL_TRANSACTION_ID` |
| 5 | `EXTERNAL_TRANSACTION_ID` | 渠道回调明细（channel-connector）；渠道账单行（reconciliation） | — |

结果里的 `CLIENT_ID`、`ENTITY_NO`、`MID_ID` 只展示，可点击另起一次查询。

## 8. 数据设计增量（`mor_platform`）

| 表 | 字段（类型简写） | 索引 |
|---|---|---|
| `t_query_key_type` 查询键类型（新增） | `key_type_id` PK；`key_type_code` VC64；`key_type_name` VC128；`owner_system` VC64；`value_type` VC32（INTEGER、STRING）；`default_role` VC32（LINK、DIMENSION）；`entry_allowed` TINYINT（是否出现在入口下拉）；`description` VC512 NULL；`status` VC32（ACTIVE、INACTIVE）；`version` INT；`created_by`、`updated_by`；`create_time`、`update_time` | `uk_query_key_type_code(key_type_code)`；`idx_query_key_type_status(status, update_time)` |

- 模板标注写在既有 `t_query_template_version.parameters_json` / `columns_json` 中，不新增列；键类型图在执行时由 ACTIVE 模板的当前版本计算，进程内缓存，模板或键类型变更时失效（沿用数据源连接池的多实例刷新方式）。
- 键类型被 ACTIVE 模板引用时不能停用。
- 键类型的初始数据（第 4 节清单）放在 Platform 初始化 DML 一个文件中，手工执行。

## 9. 接口增量

| Method + Path | 权限 | 说明 |
|---|---|---|
| GET `/data-query-admin/v1/key-types` | DATA_QUERY_READ | 列表 |
| POST / PUT `/data-query-admin/v1/key-types[/{id}]` | DATA_QUERY_TEMPLATE_MANAGE | 新建 / 修改（`expectedVersion`） |
| POST `/data-query-admin/v1/key-types/{id}/status` | DATA_QUERY_TEMPLATE_MANAGE | 启用 / 停用 |
| GET `/data-query/v1/key-graph?entryKeyType=` | DATA_QUERY_READ | 可达预览 |
| POST `/data-query/v1/joint-executions` | DATA_QUERY_EXECUTE | 执行；入参 `keyType`、`values[]`；出参 `nodes[]`（模板执行：模板、版本、触发键、列、行、截断、错误）、`edges[]`（来源行 → 键值 → 目标节点）、`truncations[]` |

- 执行沿用 C11：拥有 `DATA_QUERY_EXECUTE` 即可执行任意 ACTIVE 模板，因此联合查询不再单独加权限；是否单独加 `DATA_QUERY_JOINT_EXECUTE` 见 Q6。
- BFF 同路径转发，不做聚合（编排在 Platform data-query 模块内，避免 BFF 多次往返）。

## 10. 页面（operations-console-web，需先出 Pencil 画板）

- **联合查询页**（菜单“数据查询”下新增）：键类型下拉（`entry_allowed` 的键类型）+ 单号输入（支持多行粘贴）→ 执行；上半部分链路图（节点 = 模板执行，按系统着色，边标注键值），下半部分按系统 / 模板分组的结果表；维度键可点击另起查询；截断与错误在节点上标出；“可达预览”在执行前展示。
- **键类型管理**：列表 + 抽屉编辑。
- **模板编辑器**：参数与列声明增加键类型 / 角色选择；保存警告里显示“是否参与联合查询及原因”。

## 11. 不做

- 自动识别单号类型（J7）；预设链路方案（J5）；动态键类型列（Q4）。
- 查询记录、结果导出、跨模板按主键合并去重。
- Client / Partner 门户使用。

## 12. 待定问题

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 护栏默认值（第 6.2 节） | 先按表中数值，mor-test 跑真实链路后调整 |
| Q2 | `EXTERNAL_TRANSACTION_ID` 跨渠道重复 | 首期当作全局键展开，结果中显示渠道列供人工辨别；出现误连再加限定维度 |
| Q3 | `CLIENT_ORDER_ID` 只在 Client 内唯一，作为入口时可能命中多个客户的订单 | 允许作为入口，命中多条时全部展开并在结果顶部提示；或要求同时选择客户——请确认 |
| Q4 | 多态引用列是否需要“按另一列值决定键类型” | 首期由模板 SQL 拆列；模板变多再评估 |
| Q5 | 同一模板被不同键触发时结果是否合并 | 首期不合并，按触发键分组展示 |
| Q6 | 是否单独加联合查询权限 | 不加，沿用 `DATA_QUERY_EXECUTE` |
| Q7 | 首批模板由谁写 | 按 2026-10-28 客户上线决定“SQL 模板由 Owner 维护”，各系统提供本系统模板；首期由我们按第 7 节链路在 mor-test 上配置一套演示模板 |

## 13. 后续

本稿确认后：登记决定记录（或在 2026-10-22 数据查询决定上追加修订），在 `requirements/20261029/platform-support-联合查询/` 登记需求，按需求交付流程分阶段推进；页面先出 `operations-console.pen` 画板再写代码。

# 结账点运行期评估

- 日期：2026-10-16
- 状态：**阶段 1 已确认**（2026-10-16：Q1 由本会话实现 Commerce，其余按建议）；阶段 2 已确认（D1～D4 按建议；Q4 修订为沿用 `CHECKOUT_NOT_ELIGIBLE`，见 technique.md 第 4 节）；阶段 3 已确认（[design.md](./design.md)）；阶段 4 已确认；阶段 5 已确认；阶段 1—8 完成：mor-test 已部署 Risk（[验收记录](./release/test-acceptance.md)），代码已本地提交（未推送，见 delivery.md 第 9 节）
- 主入口 / Owner：Risk & Compliance（`CHECKOUT_PREPARE` 风控点、评估与发布）；调用方 Commerce（创建结账时的风控步骤）；关联 Operations（结账点执行发布页面）
- 依据：[风控标准目录](../../../agent-docs/RISK_CONTROL_STANDARDS.md)第 1.1 节（RCO-I03，接口状态“候选：R7 方向已确认；输入合同扁平化与旧候选发布迁移待其需求确定”）；[R7 统一评估接口](../../../agent-docs/decisions/2026-10-14-risk-payment-fraud-phase1.md)；[支付反欺诈一期](../../20261014/risk-支付反欺诈一期/design.md)
- 技术方案：[technique.md](./technique.md)；方案设计：[design.md](./design.md)；确认记录：[delivery.md](./delivery.md)

## 1. 背景与目标

结账风控点 `CHECKOUT_PREPARE` 目前**只能配置、不能运行**：运营可以为它维护规则集、决策流和变量，但没有评估器，规则从不执行。Commerce 创建结账时的风控步骤 `CheckoutRiskPort` 是一个桩，直接把订单 ID 当作评估 ID 返回，任何订单都会被放行。

**目标：**Client 通过 API 创建结账（正式订单落库前）时，Commerce 调用 Risk 统一评估接口，按结账点当前生效的执行发布真实评估；结论为拒绝或审核时不创建订单，失败或超时按失败关闭处理。这是支付评估之前的第一道拦截，能在消费者付款前挡住高风险的订单组合（Client、经营主体、国家、金额、行业）。

## 2. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| Commerce（系统） | Client 创建结账，正式订单落库前 | 带订单事实调用 Risk；放行则继续创建，拒绝/审核则返回明确错误且不落订单 |
| 风控运营 | 为结账点配置规则（如“某国家 + 某行业单笔金额超过阈值拒绝”），经执行发布激活 | 新的结账请求按生效规则判定；可用评估查询页查看任一结账评估的结论与解释 |
| 风控运营 | 修改结账规则前做影子运行 | 用最近的正式结账评估回放，看清结论变化后再激活 |
| Client（外部） | 订单被风控拒绝 | 收到稳定的错误码（不暴露规则细节），可据此提示用户 |

## 3. 本期范围（草案）

1. **Risk 结账评估器：**为 `CHECKOUT_PREPARE` 登记统一评估的输入合同与评估器（业务身份为 Commerce 预分配的 `order_id`），执行流程、保存评估与输入快照、支持重放、超时与失败，与支付评估一致。
2. **输入合同扁平化：**以 Commerce 创建结账时确实已有的事实定义字段（见第 5 节），替代旧结账合同中已不存在的字段；现有结账变量映射到新字段。
3. **执行发布支持结账点：**结账点也使用执行发布（创建、校验、影子运行、激活）；旧“测试候选”发布的处理见 Q3。
4. **结账输入快照：**按新合同重建结账快照（现有 `t_checkout_input_snapshot` 为旧结构且无代码写入）。
5. **Commerce 接入：**`CheckoutRiskPort` 改为调用 `RiskDecisionApi`，按结论放行或拒绝；结果未知时的处理见 Q5。
6. **评估查询：**结账评估可在已上线的“评估查询”页按评估 ID / 订单 ID 查看（复用现有接口，按风控点区分业务身份）。

## 4. 不做项

- 结账评估的累计事件与实时累计变量（如“同 Client 1 小时订单数”）：本期不写累计事件，后续需求再定。
- 审核（REVIEW）的人工处理流程：本期审核等同拦截，见 Q4。
- 消费者、网络证据类输入：创建结账发生在消费者交互之前，这些事实还不存在（RCO-I03 已确认）。
- 修改 Commerce Client API 的路由与请求结构；仅可能新增一个错误码（Q4）。

## 5. 已确认事实

| 事实 | 依据 |
|---|---|
| Commerce 创建结账在保存前依次调用商业事实、风控、税、文件四个端口；风控端口当前为桩（`request.getOrderId()`），注释“TODO(Risk)” | `CustomerCheckoutIntegrationConfiguration`、`CreateCheckoutProcessor#initializeCheckout` |
| 风控端口可用的事实：订单 ID/编号、Client、消费者国家、币种、净额、明细、上下文时间；商业事实中的经营主体编号、主体来源类型、Local Partner、行业、MCC；订单上还有商品金额、折扣金额；请求可带消费者引用 | `InitializationRequest`、`CommercialFacts`、`CommerceOrder`、`CreateCheckoutRequestBody` |
| Risk 结账输入合同 `ORDER_PREPARATION` v4 有 9 个规则字段：国家、locale、行业、MCC、币种、商品金额、折扣金额、净额、主体来源类型（另有 Client ID 仅作动作参数）；其中 locale 在 Commerce 创建结账时没有 | `DecisionSourceContracts` |
| Risk 运行期评估器只有支付尝试；执行发布只支持运行期风控点 | `service/evaluation/`、`CreateExecutionReleaseProcessor` |
| `t_checkout_input_snapshot` 为旧结构（含 assignment、协议、文件、消费者 Token 等大量非空列），没有任何代码读写 | Schema；代码检索 |
| mor-test 结账点有旧测试候选发布 10 个（DRAFT/VALIDATING）、流程 2、规则集 28、变量 17；没有部署 Commerce | Risk 阶段 7 只读核对 |
| 评估查询接口按评估所属风控点分派评估器，结账评估器实现“业务身份 → 评估”后即可查询 | `EvaluationReader` |

## 6. 前提与依赖

- Commerce 侧改动（`CheckoutRiskPort` 实现、错误码、测试）属于 Commerce 系统，见 Q1。
- mor-test 未部署 Commerce，端到端只能在本机验证；test 只能部署 Risk 侧。
- 结账点没有生效发布时评估返回 503，失败关闭会使所有创建结账失败，见 Q6。

## 7. 验收标准（草案）

| 编号 | 标准 |
|---|---|
| AC-1 | 结账点有生效执行发布时，统一评估接口按新输入合同评估并返回 ALLOW/REVIEW/DENY；评估、快照、Feature 结果、规则与流程轨迹落库 |
| AC-2 | 同一订单 ID 内容一致的重放返回原结果；内容不一致返回 409；缺字段/格式错误 422；未登记合同版本 400；无生效发布 503 |
| AC-3 | 结账点可创建执行发布、校验、影子运行（回放最近的结账正式评估）并激活；被生效发布引用的结账规则集不能删除 |
| AC-4 | 现有结账变量映射到新合同后，规则集与决策流的创作、校验、冻结照常；旧数据可读 |
| AC-5 | Commerce 创建结账：放行时创建成功并冻结评估 ID；拒绝/审核时返回约定错误码且不落订单；Risk 失败、超时、503、结果未知时按约定处理，不默认放行 |
| AC-6 | 评估查询页可按评估 ID、按订单 ID 查看结账评估的结论与解释 |
| AC-7 | 日志不记录请求体；结账输入不含受限值（若纳入消费者引用则同支付评估处理） |

## 8. 已决问题（2026-10-16）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | Commerce 侧由谁实现 | **用户确认：由本会话实现**（同 Operations 评估查询的做法：先查冲突、只改本需求文件、共享文件只改本需求的行）；Commerce 当前有其他会话在做 Customer 与存卡支付，阶段 2 先核对其未提交改动 |
| Q2 | 新输入合同的字段 | Client ID、经营主体编号、主体来源类型、Local Partner ID（可空）、消费者国家、币种、商品金额、折扣金额、净额、行业、MCC、明细行数；**去掉 locale**（Commerce 没有）；消费者引用暂不纳入（Q7）。合同版本记为 `ORDER_PREPARATION` v5 |
| Q3 | 旧测试候选发布与其页面 | 保留只读（历史可查），结账运行期只认执行发布；不迁移旧候选发布的内容，运营用现有决策流新建执行发布 |
| Q4 | 审核（REVIEW）怎么处理 | 结账没有人工审核流程，**审核与拒绝都拒绝创建**，Client API 返回同一个错误码（如 `CHECKOUT_RISK_REJECTED`，HTTP 422），不区分原因、不暴露规则；评估 ID 记录在 Commerce 日志与审计中 |
| Q5 | Risk 结果未知（超时、连接失败、5xx） | 创建结账请求内用**原订单 ID + 相同输入**重试一次（Risk 按业务身份重放）；仍未知则失败关闭，返回 503 类错误，订单不落库，Client 可用同一 `client_order_id` 重试 |
| Q6 | 没有生效发布时（503）全部拒绝 | 失败关闭不变；为本机与 test 准备一套“默认放行”结账基线流程（只有开始 → 放行），随部署由运营或本会话经接口激活，避免环境不可用 |
| Q7 | 是否纳入消费者引用 | 本期不纳入：结账规则以订单与商业事实为主，消费者维度在支付评估已有；纳入会带来受限值处理 |

## 9. 阶段安排

按默认 8 阶段。阶段 2 核对 Risk、Commerce、Operations 现状与 Commerce 的并行改动；阶段 3 给出合同、快照表 DDL、Commerce 接入与错误码、Operations 是否需要改页面；阶段 6/7 只部署 Risk 到 mor-test（test 无 Commerce），Commerce 端到端在本机验证。

# reconciliation 渠道资金对账切片 1

> 状态：阶段 1–3 已确认（2026-10-20），阶段 4 研发中。Q-03 已由阶段 3 D-01 修订为分段导入。确认记录见 [delivery.md](./delivery.md)；现状差异与方案在阶段 2、3 写入 [technique.md](./technique.md)。依据 [2026-10-20 对账决定](../../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)（Confirmed，两轮）与 [design-discuss/reconciliation](../../../design-discuss/reconciliation/design.md)。

## 1. 背景与来源

对账设计（2026-10-20）两轮讨论已确认：对账只认定事实，记账经 billing；模型 18 → 13 张；看不准的场景先挂异常，由人工选归类码处理（`CLASSIFY`）。决定第 24 条定了切片 1 范围。用户 2026-10-20 回复"开始登记切片 1 需求吧"。

当前事实（2026-10-20，源码与台账核对）：

| 项 | 现状 |
|---|---|
| `paytech/reconciliation` | 空壳：四个模块、19 个 Java 文件，只有 `t_channel_account` 的只读 DataObject / Mapper / Repository 与架构测试；没有接口、消费者、Job |
| `mor_reconciliation` | 旧 Finance 单体的 18 张表，Schema 在 `docs/database/sql/reconciliation-schema.sql`；`reconciliation.pdma.json` 仍由 Finance 生成器投影（`generate-oe-client-schemas.mjs`），带 name / describe 漂移 |
| 平台流水的来源 | billing 只有人工登记事实（`MANUAL_ADJUSTMENT`），**自动 PAYMENT 事实（billing 需求 B）未开始**，且 account 对自动 PAYMENT 行的映射待定（台账已列）；`BillingFactRecorded` 目前只有 account 消费 |
| Payment 事件 | `PaymentSucceeded` v2 只带外部交易号 `channelTransactionRef`，缺 `channelTransactionRefId`、`referenceType`；事件目录里没有 `RefundSucceeded` |
| billing 合同 | `FactRefType` 没有 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION`；`BillingFactType` 没有 `CHANNEL_BATCH_RECONCILED`、`RECON_ADJUSTMENT` |
| channel-connector | 已有 `ChannelMidApi`（`/channel-connector/v1/mids` 列表与详情），可用来核对 MID 的渠道与经营主体 |
| 文件上传 | Platform 只有入驻与审核文件接口，没有通用文件服务 |
| 控制台 | 没有对账页面；财务菜单已有基础资料、账本、调整、关账、规则包等页 |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 财务运营 | 在控制台维护清算账户和银行账户，上传渠道账单和银行对账单，看到哪些已对平、哪些对不上，并处置对不上的项 |
| 财务复核人 | 复核银行账户变更和异常处置，与提交人不得是同一人 |
| billing（下游） | 收到四类对账事件后能生成事实：渠道成本差额、账单对平、渠道到账、对账调整 |
| billing（上游，以后） | 支付事实上线后用清算账户解析接口取 `CLEARING_ACCOUNT` 引用 |

## 3. 本期范围

1. **模型：** `mor_reconciliation` 按设计第 6 节改为 13 张业务表 + 2 张公共消息去重表（字段级在阶段 3 定稿；2026-10-20 实施修订）；PDManer 模型脱离 Finance 生成器直接维护，加模型测试；Schema、测试 DDL、表归属规则同步。
2. **清算账户（UC-R01、R03）：** 新建、增减 MID、停用；每个 MID + 币种只能属于一个清算账户，挂 MID 前向 channel-connector 核对渠道和经营主体；列出已激活但还没归入清算账户的 MID；提供解析接口 `POST /reconciliation/v1/clearing-accounts/resolutions`。等待天数、成本容差、到账宽限天数作为清算账户上的配置。
3. **银行账户（UC-R02、R04）：** 新建、改用途、停用要经复核；账号先经 Vault Protect 再入库；同一经营主体 + 币种 + 用途只允许一个有效账户；提供 `GET /reconciliation/v1/cash-accounts/current`（只返回脱敏账号）。
4. **平台流水（UC-R05）：** 消费 `BillingFactRecorded`，只处理 PAYMENT（应收）和 REFUND（应付），按 `billing_fact_id` 幂等；其他事实类型忽略。
5. **渠道账单（UC-R06–R09）：** 控制台上传标准 CSV 账单；校验结构和"明细合计 = 账单头"，不通过即拒绝并给出原因；同一账单号允许更正重传（revision）；未对平的账单可以作废；逐行匹配、记实际费用、跨账单查重、开异常；全部行处理完且没有阻塞异常时账单对平，生成预期银行流水。
6. **银行对账（UC-R11–R13）：** 上传标准 CSV 银行对账单，校验期初加流水等于期末（不过就拒绝），检查与上一张是否接得上（接不上开不阻塞的提示异常）；按"金额相等、方向和币种一致、日期在窗口内"给一对一匹配建议；新预期银行流水生成时对未匹配流水重新出建议；运营可以确认、拒绝建议，或手工多选匹配，合计按方向带正负号且不超过流水金额。
7. **异常与处置（UC-R15、R16）：** 三种处置：重新匹配、接受时间差、归类；Maker 提交、Checker 复核、不得同人；批准时在同一本地事务内应用处置。归类按来源对象入账：账单行的归类随账单对平事件带出，平台流水、预期银行流水、银行流水的归类发对账调整事件。
8. **发给 billing 的事件：** `ActualChannelCostConfirmed`、`ChannelBatchReconciled`、`ChannelSettlementConfirmed`、`ReconAdjustmentApproved`，经本地补偿事件可靠发布，登记事件目录与补偿事件目录。
9. **账单行等待期满：** 一个 XXL 任务把等待期满仍未匹配的账单行转为 `CHANNEL_LINE_UNMATCHED` 异常（见 Q-04）。
10. **Operations 控制台：** BFF 与页面——清算账户、银行账户、渠道账单（上传、列表、详情、作废）、银行对账单（上传、流水、匹配建议与手工匹配）、异常（列表含账龄、详情、提交处置、复核）、平台流水查询、在途汇总；两个权限点：对账操作、对账审批；Platform 注册（权限、路由、菜单）。
11. **文档：** 标准账单 CSV 与银行对账单 CSV 的格式说明；`FINANCE_DATABASE.md` §4.5、§6.2 标注被取代；台账；design-discuss 状态。

## 4. 明确不做

| 项 | 说明 / 去向 |
|---|---|
| 平台有渠道无、应到未到的超期检测 | 切片 2（超期检测任务） |
| 未识别借方暂记 `BankLineSuspended` / `BANK_SUSPENSE` | 切片 2 |
| 拒付、重复扣款来源的平台流水，净额为借方的渠道账单 | 切片 2 |
| 出款、缴税的预期银行流水，`BankDebitMatched`、`LateBankDebitDetected` | 切片 3（依赖 payout、tax） |
| billing 生成 PAYMENT / REFUND 事实、`PaymentSucceeded` 补字段、`RefundSucceeded` | billing 需求 B 与 Payment 侧 |
| billing 消费四类对账事件、新事实类型、ledger 规则包归类码映射 | 见 Q-01 |
| 自动拉取渠道文件、银行 API、银行匹配自动确认、多币种结算 | 后续（决定第 22 条及 Q6、Q8） |
| 期末报告、异常分派与到期日 | 只做控制台查询与账龄（G13、G14） |
| 执行 SQL、部署、提交 | 按阶段单独授权 |

## 5. 验收标准

**功能**

| 编号 | 验收标准 |
|---|---|
| AC-01 | `mor_reconciliation` 为 15 张表（13 张业务表 + `t_consumed_event`、`t_consumed_aggregate_version`，2026-10-20 实施修订），PDManer、Schema、测试 DDL 逐字一致；表归属规则与架构测试通过；Finance 生成器不再投影 reconciliation |
| AC-02 | 清算账户：同一 MID + 币种重复归属被拒；MID 不存在、渠道或经营主体不一致被拒；未归属 MID 列表正确；解析接口命中唯一账户返回账户与经营主体，命中不到返回业务错误 |
| AC-03 | 银行账户：变更经复核才生效，提交人不能复核；库里只存 Vault Token 与脱敏号；同一主体 + 币种 + 用途第二个有效账户被拒；当前账户查询不返回明文 |
| AC-04 | 平台流水：PAYMENT、REFUND 事实各生成一条，方向正确；同一事实重复投递只生成一次；其他类型不生成；缺清算账户或渠道交易引用（或清算账户停用）时消息按可重试失败重投，最终进 `reconciliation.billing.dlq` 告警并留存，不丢弃；补建清算账户后由运维重放（2026-10-20 措辞按 technique §2.4 修正） |
| AC-05 | 渠道账单导入：格式或恒等式不通过整单拒绝并返回逐行原因；账单币种必须等于清算账户币种；同一账单号只允许一个未作废版本；已对平的账单不能更正或作废 |
| AC-06 | 匹配：按设计 F1 规则 1–7 覆盖完全匹配、部分匹配、金额超出、多条命中、跨账单重复、带交易号与不带交易号的费用行、调整行；新平台流水到达时重试等待中的行 |
| AC-07 | 实际费用与平台流水上的预估费用不同时发 `ActualChannelCostConfirmed`，相同不发；作废账单后其行的匹配和费用失效，平台流水回到未匹配 |
| AC-08 | 对平门禁四项都满足才对平；对平后生成一条预期银行流水并发 `ChannelBatchReconciled`，其中已归类调整行带归类码、带符号金额和处置 ID；净额与"应收 − 应付 − 费用 ± 调整"一致 |
| AC-09 | 银行对账单：期初加流水不等于期末整单拒绝；与上一张接不上时导入成功并开 `BANK_STATEMENT_GAP`；同一银行账户 + 银行交易号重复的流水不重复入库 |
| AC-10 | 匹配建议只给一对一；先到钱后有账单时，预期银行流水生成后自动出建议；手工多选匹配合计超出流水金额或跨银行账户被拒；每条确认的渠道匹配发一条 `ChannelSettlementConfirmed`，按匹配 ID 幂等 |
| AC-11 | 处置：三种类型各自生效；归类码只能选来源对象允许的那些；归类金额必须覆盖差异；批准同一事务内处置已应用、异常已解决；平台流水 / 预期银行流水 / 银行流水的归类各发一条 `ReconAdjustmentApproved`，账单行的归类不单独发 |
| AC-12 | 四类事件载荷符合设计第 8 节，登记事件目录与补偿事件目录；本地提交后发布，失败可重试 |
| AC-13 | 等待期满任务把仍未匹配的账单行转为异常，重复执行不重复开异常 |
| AC-14 | 只有对账操作权限的人调不了复核接口，只有对账审批权限的人调不了提交接口（Gateway 路由按权限点拦截）；reconciliation 拒绝提交人复核自己的提交（2026-10-20 阶段 2 调整，见 delivery 变更记录） |
| AC-15 | reconciliation、Operations BFF、Web 测试与架构规则通过；`npm --prefix requirements-site test` 结果与基线一致（既有失败单列） |
| AC-16 | 若做阶段 6–7：mor-test 用合成事实跑通"平台流水 → 上传账单 → 对平 → 上传银行对账单 → 确认匹配 → 处置一个归类异常"，四类事件出现在 MQ，合成数据清理 |

**视觉 / 交互**

| 编号 | 验收标准 |
|---|---|
| AC-V1 | 财务菜单下新增「对账」分组，七个页面入口；没有权限的入口不显示 |
| AC-V2 | 上传账单、对账单后显示结果：成功显示账单号与行数，被拒显示原因列表（行号 + 原因） |
| AC-V3 | 账单详情按行显示匹配状态、对应平台流水和实际费用；对平前显示门禁哪几项未满足 |
| AC-V4 | 异常列表按账龄排序，可按类型、状态、清算账户筛选；处置表单按来源对象只列可用的归类码；复核页显示提交人、内容与金额 |
| AC-V5 | 手工匹配可多选，实时显示带符号合计与剩余金额，超出时不能提交 |
| AC-V6 | 空列表有说明文字；业务错误显示后端 detail |

## 6. 问题与建议

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | billing 还不生成 PAYMENT 事实，对账事件发出后 billing 也没人接。billing 接收四类对账事件（新事实类型、ledger 规则包映射）放不放在本需求？ | **不放**，本需求只做对账侧，事件可靠发出、按合同可验收。billing 接收和 ledger 映射与 billing 需求 B（支付事实）一起做：没有 PAYMENT 事实时，单独记"账单对平"会让待清算应收出现负数，记了也没有意义。本机与 mor-test 用测试发布的 `BillingFactRecorded` 合成事实验收 |
| Q-02 | 对账消费者要读事实里的渠道交易引用，但 billing 合同还没有 `CHANNEL_TRANSACTION_REF` | 本需求带一处 billing 合同小改：`FactRefType` 加 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION`，引用行加两列可空的 `external_reference_type`、`external_transaction_id`，载荷带出；只改合同与表，不加生成逻辑。`mor_billing` 一条 ALTER |
| Q-03 | 没有通用文件服务，账单和对账单怎么上传？ | BFF 收 CSV（单文件上限 5 MB），把内容和 SHA-256 传给 reconciliation；reconciliation 存文件 Hash 和解析后的行，不存原文件。Platform 通用文件服务以后再接（设计里写的"Platform File 读取"本期不做） |
| Q-04 | 等待期满转异常要定时任务，但超期检测在切片 2 | 本期只做这一项检测（一个 XXL 任务），不然未匹配的账单行永远不会变成可处置的异常；其余检测留切片 2 |
| Q-05 | 平台流水上的"预估费用"取哪 | 取 `BillingFactRecorded` 里渠道成本组件行的金额；没有成本行按 0，阶段 3 按 billing 载荷核定 |
| Q-06 | 适用阶段与执行方式 | 1 需求 → 2 现状差异 → 3 设计（字段级 13 表、接口、事件载荷、CSV 格式、页面）→ 4 研发（Opus 5.5 多 agent 分线）→ 5 本机验收 → 6–7 mor-test → 8 提交；每阶段停下等确认，可以按你的意思合并 1、2 |

## 7. 依据

- [2026-10-20 对账决定](../../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)（第 17–24 条）、[design-discuss/reconciliation/design.md](../../../design-discuss/reconciliation/design.md)（F1–F3、UC-R01–R17、第 6–8 节、第 10 节切片 1）
- [2026-10-19 billing 决定](../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)、[billing 计费事实模型重构需求](../../20261019/billing-计费事实模型重构与渠道成本迁出/requirement.md)（遗留：需求 B）
- [2026-10-18 account / ledger 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)
- `paytech/reconciliation`、`paytech/billing/paytech-billing-api`、`paytech/channel-connector/paytech-channel-connector-api`（`ChannelMidApi`）

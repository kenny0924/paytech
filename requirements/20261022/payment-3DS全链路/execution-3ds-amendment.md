# Execution 设计修订：卡支付 3DS 改由 PAY 承载，补齐查单恢复

> 状态：**阶段 3 评审稿**（2026-10-22，需求 [payment-3DS全链路](./requirement.md)，方案见 [technique.md 第 3 节](./technique.md#3-方案接口与数据设计阶段-3-评审稿)）。用户确认后成为 [Execution 设计](../../20260913/payment-Execution设计/design.md) 中 3DS 与恢复部分的现行描述；原文按下方清单加指向行，不改写历史正文。

## 1. 修订内容

| 主题 | 原设计 | 修订后 |
|---|---|---|
| 3DS 由谁承载 | 风控放行且需认证时进入 AUTHENTICATE，由它等待挑战并取得可信认证结果，成功后再进入 AUTHORIZE 或 PAY | 卡流程不再使用 AUTHENTICATE。风控放行后 AUTOMATIC 直接进入 PAY，“是否要求 3DS”作为 PAY 的渠道输入（`payment.threeDsRequired`）；挑战发生在渠道的 PAY 调用内部，PAY 停在 PROCESSING 并记录交互，直到确认终态。AUTHENTICATE 动作类型与 Actor 保留，留给将来接入独立认证（MPI）时使用 |
| `CHALLENGE_REQUIRED` | 与 `THREE_DS_REQUIRED` 同属需认证 | 同样映射为 `threeDsRequired=true`；不区分强制挑战 |
| MID 3DS 能力 | 未使用 | 路由按 `SECURITY.3DS_MODE` 优先选择 SUPPORTED / REQUIRED；要求 3DS 而冻结的 MID 为 UNSUPPORTED 或未声明时，PAY 不发出渠道请求，以 `THREE_DS_CHANNEL_UNAVAILABLE` 失败 |
| 交互类型 | 只写 THREE_DS_CHALLENGE | 按渠道返回写 THREE_DS_CHALLENGE 或 REDIRECT；完成后保留历史值 |
| 恢复 | 只设计“UNKNOWN 可查询后回到 PROCESSING”，未实现；不建 QUERY 业务动作 | 不建 QUERY 业务动作（与 `execution-state-channel-recovery-amendment.md` 一致）。PAY 保存为 PROCESSING / UNKNOWN 时同事务登记 `PAY_RESULT_QUERY` 补偿任务；“确认支付结果”在 Intent 锁内、事务外调用渠道 `PAY_QUERY`，短事务按终态推进原 PAY 动作。入口三个：补偿任务（兜底，最长 24 小时后转人工）、收银台轮询触发的刷新（5 秒限频）、渠道通知（MQ `ChannelPaymentNotified`）。只采信查单结果，不采信回跳参数和通知内容 |
| 查单结果 | — | 成功且金额币种一致 → SUCCEEDED；FAILED / CLOSED → FAILED；无此交易且动作已超过 10 分钟 → FAILED；处理中 → 保持（UNKNOWN 改回 PROCESSING）；其他 → 不推进 |
| 渠道交易引用 | 结果事务插入 | 同一动作、同一外部编号已存在则跳过；同一动作出现不同外部编号视为系统错误 |
| 交互过期 | — | 过期不改变动作状态；查单得到失败才释放 Intent。仍处理中时不允许新建 Attempt |

## 2. 不变内容

- PAY_RISK、Risk 输入与认证要求的冻结方式不变。
- MANUAL 捕获仍为 PAY_RISK → AUTHORIZE（桩，acquiring 只传 AUTOMATIC），不在本需求范围。
- 跳转地址仍只在内存中，不落库、不进日志、MQ 或补偿 Payload。
- `PaymentSucceeded` 仍只在 PAY 成功的结果事务中登记一次。

## 3. 阶段 4 文档任务（加指向行或修正描述）

| 文件 | 位置 | 处理 |
|---|---|---|
| `requirements/20260913/payment-Execution设计/design.md` | 第 85—118、132 行（AUTHENTICATE 承担认证的流程图与说明） | 段首加指向本修订 |
| `requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md` | 第 14—39 行 | 段首加指向本修订 |
| `requirements/20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md` | 第 54、72、80 行 | 加指向：PAY 查单恢复已按本修订实现 |
| `requirements/20260913/payment-Execution设计/state-and-recovery.md` | 第 43、71、144 行 | 加指向 |
| `requirements/20260913/payment-Execution设计/internal-risk-action-amendment.md` | 第 25 行 | 加指向 |
| `requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md` | 第 32、61、65 行 | 加指向 |
| `requirements/20260913/payment-Execution设计/data-design.md` | 第 60—66、82、91 行 | 加指向；`interaction_type` 允许写入值改述 |
| `requirements/20260913/payment-Execution设计/mock-contract.md` | 第 33 行 | 加指向 |
| `requirements/20260913/payment-Execution设计/verification-plan.md` | 第 33、61、65 行 | 加指向 |
| `requirements/20260913/payment-Execution设计/requirement.md` | 第 50 行 | 加指向 |
| `paytech/payment/README.md` | 第 68 行（“放行按认证要求进入 AUTHENTICATE”） | 改为本修订描述 |
| `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md` | 第 130—132 行 | 改述 |
| `agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md` | 第 39、62、74、80 行 | 改述 |
| `requirements/20261014/payment-PayerMax卡直付对接/technique.md` | 第 125—127 行（“恢复本期不做”） | 加指向 |
| `requirements/20261017/architecture-目标架构V2/acquiring-sequence.md` | 第 71—101 行 | 只核对一致（回调经 GW → CHN → PAY、退避查单），无需修改 |

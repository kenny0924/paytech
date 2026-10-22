# payment：3DS 全链路 · 需求

> 状态：**阶段 1 已确认**（2026-10-22 用户“都按照建议来”：Q1—Q6 均按建议）。确认记录见 [delivery.md](./delivery.md)，技术方案见 [technique.md](./technique.md)（阶段 2 起填写）。
> 姊妹需求：[card-BIN库与卡属性](../card-BIN库与卡属性/requirement.md)（同日登记，可并行）。

## 1. 背景

现状（2026-10-22，主工作区 `a5479950`，代码证据见 [technique.md](./technique.md) 第 1 节）：

- **能跑的只有“渠道发起 3DS”的去程。** PAY 收到渠道 `PENDING + redirectUrl` 后，Action 停在 PROCESSING，记 `interaction_type=THREE_DS_CHALLENGE`、30 分钟过期；pay 返回 202 和 `nextAction`；acquiring 映射为 `REQUIRES_ACTION` 并把 URL 交给浏览器。PayerMax UAT 已验证到“返回跳转指令”。
- **“风控要求 3DS”一走就卡死。** Risk 在 ALLOW 节点输出 `THREE_DS_REQUIRED` 时，卡流程进入 AUTHENTICATE。该 Actor 是桩（TODO + 抛异常），Action 变 UNKNOWN，Attempt 永远 PROCESSING。mor-test 的 Risk 发布 R-11 有“金额 > 500 要求 3DS”，一旦生效，这些支付都会卡住。
- **回程全部缺失。** 持卡人完成验证后：没有回跳处理，没有渠道异步通知入口（Gateway 的 `CHANNEL_CALLBACK` 路由为 0），Payment 不传 `notifyUrl`，没有查单恢复；PROCESSING / UNKNOWN 的 PAY 不会收尾，交互过期也无人处理。
- **其他断点。** channel-connector 不往外传 `nextAction.type`，Payment 把所有跳转都标成 `THREE_DS_CHALLENGE`；commercial `require_3ds` 与 MID 能力 `SECURITY.3DS_MODE` 只是配置，运行时没人读；刷新页面后拿不到跳转 URL（[支付结果查询需求](../../20261015/payment-支付结果查询与回传/requirement.md) Q-01）。
- **收银台没有前端。** `checkout-web` 只有设计稿（画板 07「银行验证」），没有代码。

## 2. 目标

1. 需要 3DS 的卡支付，不论由风控要求还是渠道发起，都能完成“跳转验证 → 回到收银台 → 得到终态”。
2. 任何一笔停在 3DS 的支付最终都会收尾：成功、失败或过期，不会永远 PROCESSING。
3. 结果以渠道查询结果为准；回跳和异步通知只用于加快确认，不直接采信。

## 3. 概念

| 术语 | 含义 |
|---|---|
| 3DS 要求 | 本次 Attempt 是否必须做 3DS。来源：Risk `AUTHENTICATION_REQUIREMENT`；是否加上协议 `require_3ds` 见 Q2 |
| 渠道内 3DS | 3DS 由渠道在 PAY（授权或销售）调用内部发起：渠道先返回挑战跳转，持卡人验证后由渠道完成扣款。PayerMax、PayBy 都是这种 |
| 独立认证（MPI） | 先单独完成 3DS 认证、拿到认证结果，再带结果发起授权。对应现有 AUTHENTICATE Action |
| 交互 | Action 上等待持卡人操作的状态（类型 + 过期时间）；跳转 URL 只在内存中短暂存在，不落库 |
| 回程 | 持卡人完成或放弃验证后，Payment 得知结果并把 Action / Attempt 推进到终态的过程 |

## 4. 本期范围

| 编号 | 内容 |
|---|---|
| R1 | **3DS 要求进入 PAY。** `THREE_DS_REQUIRED` 时不再进 AUTHENTICATE 桩，而是由 PAY 把“要求 3DS”作为渠道输入传给渠道（渠道内 3DS）；不支持 3DS 的 MID 不参与路由（读 MID 能力 `3DS_MODE`）。卡流程定义与 Execution 设计相应修订 |
| R2 | **交互类型如实传递。** channel-connector 输出 `nextAction.type`；Payment 按渠道给出的类型记录交互（`THREE_DS_CHALLENGE` / `REDIRECT`），acquiring 两种都接受 |
| R3 | **查单恢复。** PAY 停在 PROCESSING（有交互）或 UNKNOWN 时，Payment 用可靠补偿任务按退避调用渠道 `PAY_QUERY`，得到终态后推进 Action / Attempt / Intent；交互过期后再查一次，仍无终态则按 Q4 收尾 |
| R4 | **回跳加速。** 持卡人回到收银台后，收银台轮询提交结果；轮询发现仍在验证中时触发一次立即查单（限频），不必等退避周期 |
| R5 | **渠道异步通知。** 经 Gateway `CHANNEL_CALLBACK` 路由进入 channel-connector：验签、去重、映射为标准通知后，以 MQ 通知 Payment；Payment 收到后立即查单确认，不直接采信通知内容。本期接入 PayerMax |
| R6 | **刷新页面后的处理。** 跳转 URL 不落库；刷新后收银台显示“银行验证中”并继续轮询；交互过期后显示失败并允许重新提交 |
| R7 | 测试：合成渠道覆盖挑战成功、失败、放弃、过期、通知先到、查询先到、重复通知；PayerMax UAT 测试卡人工走通一笔 3DS 成功 |

## 5. 不做项

| 项 | 原因 |
|---|---|
| 独立认证（MPI / AUTHENTICATE Action）与 AUTHORIZE（MANUAL 捕获） | 当前接入的渠道都是渠道内 3DS；AUTHENTICATE 保留为未来能力，本期不再被卡流程使用 |
| `checkout-web` 前端页面 | 仓库没有收银台前端，本期只保证接口；页面另立需求（见 Q1） |
| PayBy `hostChannelForm`（渠道 HTML 表单托管） | 依赖 PayBy 卡直付整体接入，另议 |
| MPGS / NI 的 3DS | 该渠道需求明确本期不含 3DS |
| 3DS 豁免（低额、TRA 等）与 3DS 结果参与定价 | 本期只做“要求 / 不要求” |
| 3DS 后补 CVV（`PROVIDE_CARD_INPUT`） | 当前渠道不需要 |

## 6. 业务规则（草案）

- B1 支付结果只采信渠道查询（或同步响应）给出的终态；回跳参数和异步通知只是触发查单的信号。
- B2 同一 Action 的通知、回跳查单、定时查单可能并发，推进必须幂等：终态只写一次，重复信号无副作用。
- B3 要求 3DS 时，渠道返回“无需验证直接成功”也接受（以渠道结果为准，风控只表达要求）。是否记录“要求但未发生挑战”见 Q5。
- B4 要求 3DS 时，如果冻结的 MID 不支持 3DS，Attempt 失败，原因为“无可用 3DS 通道”，不降级为无 3DS 支付（2026-10-22 按 D2 修订：路由仍在风控之前，只做优先选择）。
- B5 交互过期后最后查单一次：渠道已成功则按成功处理；渠道明确失败或无此交易则失败；仍处理中则按 Q4。
- B6 跳转 URL、渠道表单、通知原文中的敏感参数不进日志、MQ 或补偿 Payload。

## 7. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | Risk 输出 `THREE_DS_REQUIRED` 时，PAY 带“要求 3DS”调用渠道并返回挑战；不再产生 AUTHENTICATE Action，也不会卡在 UNKNOWN |
| AC-02 | （2026-10-22 按 D2 修订）路由优先选中 `SECURITY.3DS_MODE` 为 SUPPORTED / REQUIRED 的 MID；风控要求 3DS 而冻结的 MID 为 UNSUPPORTED 或未声明时，不发出渠道请求，Attempt 失败，失败代码 `THREE_DS_CHANNEL_UNAVAILABLE` |
| AC-03 | 渠道返回 `REDIRECT` 类型时，Payment 与 acquiring 如实返回该类型，收银台接口能拿到跳转 URL |
| AC-04 | 挑战成功后，不依赖通知，查单恢复任务在退避周期内把 Action、Attempt、Intent 推进到成功，并发出 `PaymentSucceeded` |
| AC-05 | 挑战失败或持卡人放弃后，查单得到失败，Attempt 失败、Intent 可重新提交 |
| AC-06 | 回到收银台轮询时触发立即查单；同一提交在限频窗口内不重复查单 |
| AC-07 | PayerMax 异步通知经 Gateway → channel-connector 验签后通知 Payment，Payment 查单确认并推进；验签失败的通知被拒绝并记录，不影响交易 |
| AC-08 | 同一结果的通知重复到达、通知与查单同时到达时，终态只写一次，`PaymentSucceeded` 只发一次 |
| AC-09 | 交互过期后按 B5 收尾；任何 3DS 支付都不会无限期停在 PROCESSING |
| AC-10 | （2026-10-22 按 D1 修订）刷新页面后提交查询显示“验证中”；交互过期后，查单得到失败 / 已关闭 / 无此交易则 Attempt 失败、允许重新提交，仍处理中则显示“支付结果确认中”、不允许重新提交，直到得到终态或 24 小时后转人工 |
| AC-11 | 日志、MQ、补偿 Payload 中没有跳转 URL 的敏感参数和通知原文中的卡数据 |
| AC-12 | PayerMax UAT 测试卡人工走通一笔 3DS 成功（跳转 → 验证 → 回到收银台 → 成功） |

## 8. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 收银台前端怎么处理？`checkout-web` 没有代码，“全链路”到浏览器需要页面 | **本期只做后端全链路**，用验收脚本模拟浏览器（打开跳转 URL、在 UAT ACS 页面完成验证、回到 returnUrl 后轮询）；收银台页面另立需求 |
| Q2 | 3DS 要求的来源只有 Risk，还是再加 commercial 协议的 `require_3ds`？ | **本期只用 Risk。** acquiring 的 commercial 端口仍是 Mock，`require_3ds` 的执行放进 commercial 二期执行链路 |
| Q3 | 风控要求 3DS 时走“渠道内 3DS”（R1），还是实现独立认证（AUTHENTICATE）？ | **渠道内 3DS**：现有渠道都是这种，改动最小；AUTHENTICATE 留给未来接 MPI 时再做。需同步修订 Execution 设计中“AUTHENTICATE 承担认证”的描述 |
| Q4 | 交互过期且最后一次查单仍“处理中”时怎么收尾？ | 保持 Action UNKNOWN、Attempt PROCESSING，继续低频查单直到渠道给出终态，最长 24 小时；超过后转人工处理（记录异常，不自动判失败，避免已扣款却判失败） |
| Q5 | 要求 3DS 但渠道未发起挑战就成功（无摩擦认证或渠道豁免），是否需要记录？ | 记录渠道返回的认证结果（如有：ECI、是否挑战），本期只记录不参与判断 |
| Q6 | 异步通知（R5）本期是否要做？ | **做，只接 PayerMax。** 只靠查单也能收尾，但通知能把确认时间从分钟级降到秒级；入站通道（Gateway 路由、channel-connector 验签与映射）建成后其他渠道复用 |

## 9. 建议适用阶段

阶段 1—8 全部适用：涉及 payment、channel-connector、acquiring、risk-decision（只读核对）、Gateway / Platform（`CHANNEL_CALLBACK` 路由注册）；可能有 DDL（认证结果字段、通知去重表）与注册 DML；要部署 mor-test，并用 PayerMax UAT 人工验收。

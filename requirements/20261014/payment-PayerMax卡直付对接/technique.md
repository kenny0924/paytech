# Payment 经 Platform 对接 PayerMax 卡直付：技术方案

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14 第 2 版；评审点按建议）
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：需求第 2 节所列文档；Payment `PayProcessor`、`ActionExecutionServiceImpl`、`ActionFlow`、`CardPayFlow`、各 ActionActor、`ActionResult`、`PayResponse`、`RouteServiceImpl`（硬编码）、`QueryMidCandidatesProcessor`、`GetChannelExecutionConfigProcessor`、`ChannelAccountConfigReader`、`CardServiceImpl`（Mock）、`paytech-payment-test` 测试设施；Platform `StandardApiCommandApi`、执行引擎与沙箱验证证据；Foundation `WebLoggingProperties`（按路径前缀只记元数据）与 `HttpServiceExchangeInterceptor`（出站客户端不记 Body）；PayerMax [Direct API - CARD](https://docs.payermax.com/en/202606-version/acquiring/start-integration/payment-acceptance/API/card.html)。

## 0. 用户意见（2026-10-14）

1. 卡在服务间传递使用卡 ID / Token；Platform 可以读取卡数据，但不能打印日志，只能存在于内存中。
2. 一步步来，风控先 Mock。
3. 路由按建议（最小真实路由）。
4. 恢复将来依赖渠道通知加重试，**本期不考虑**，先打通主链路。

## 1. PayerMax 卡直付要点

| 项 | 内容 |
|---|---|
| 接口 | `POST /orderAndPay`，`version=1.5`，外层与签名规则同收银台下单 |
| 关键字段 | `data.integrate=Direct_Payment`、`outTradeNo`、`totalAmount`、`currency`、`country`、`subject`、`terminalType=WEB`、`frontCallbackUrl`（3DS 回跳）、`notifyUrl`、`paymentDetail.paymentMethodType=CARD`、`paymentDetail.cardInfo{cardIdentifierNo, cardExpirationMonth, cardExpirationYear, cvv, cardHolderFullName}`、可选 `paymentDetail.buyerInfo{clientIp, userAgent, …}` |
| 结果 | `APPLY_SUCCESS` + `data.status=SUCCESS`：成功；`PENDING` + `redirectUrl`：需用户认证（3DS）；拒绝码同收银台下单 |
| 前提 | 商户自行处理卡数据须持 PCI DSS 认证并向 PayerMax 提交材料；UAT 是否需要开通直付待实测 |
| 签名 | 对含卡号的完整 Body 签名，签名方必须看得到卡号 |

## 2. D1 卡数据通道（按用户意见 1）

与渠道账号材料（[交接方案 C](../../20260914/platform-support-外部API配置与执行/material-handoff.md)）同一模式：**服务间只传卡 Token，Platform 执行时凭 Token 向卡 Owner 读取卡数据，卡数据只在 Platform 内存中以受控句柄存在。**

```text
Payment Execution（事务外，PAY Action）
  1. 校验冻结 Channel/Account/MID（不 Reveal）
  2. 调 Platform 标准 API PAY，input 带账号引用与 card.token（不含卡值）
        ↓
Platform 执行
  3. 初始化项 PAYMENT_CARD：调用 Payment Card 内部读卡接口（token + attemptId + 用途）
  4. Card 校验 Token 归属与当前允许用卡，返回 PAN/有效期/持卡人/CVV；CVV 领取即清除
  5. 结果写入 resources.card.*（全部为受控句柄）
  6. 节点把句柄绑定到声明为卡字段的渠道请求位置；sealJsonBody 生成最终字节并签名
  7. 执行结束丢弃句柄
```

| 边界 | 规则 |
|---|---|
| 服务间请求 | Payment → Platform 只有 Token，沿用现行执行接口，不新增卡入口 |
| Platform → Payment 读卡 | Payment 新内部接口 `POST /payment-card/v1/channel-card-materials/reveal`，该路径前缀配置 `web.logging.metadata-only-path-prefixes`（响应含卡值，MVC 日志只记元数据）；Platform 出站客户端本身不记 Body |
| Platform 内存 | 只以受控句柄存在；不缓存、不落库；脚本读不到卡值 |
| Platform 输出 | 日志、执行轨迹、沙箱证据、标准 API 响应、Problem 与脚本异常都不含卡值（第 3.3 节） |
| Card | 仍拥有卡存储、解密、CVV 暂存与清理、“是否允许用卡”的判断 |

这需要两项文档修订，随本需求一并提交评审：Card 需求 CARD-01（卡原值经 Card 读卡接口进入 Platform 执行内存，Platform 纳入卡数据环境）；Platform 内部接口第 66 行（`input` 仍不得含卡原值，卡只能以 Token 进入并由 `PAYMENT_CARD` 读取）。另起一份决定记录。

**调用循环：** Payment 同步等待 Platform 时，Platform 回调 Payment 读卡（Payment → Platform → Payment）。两个调用走不同线程，读卡接口只读库、不加 Payment 资源锁，不会和原 PAY 请求互锁；风险是线程池占用，读卡超时设为 2 秒、Platform 执行总预算覆盖。

## 3. Platform 改动

### 3.1 注册初始化函数 `PAYMENT_CARD`

| 项 | 内容 |
|---|---|
| 参数 | `paymentInstrumentToken`、`paymentAttemptId`（STRING，必填） |
| 返回 | `pan`、`expiryMonth`、`expiryYear`、`expiryYearShort`（两位年份）、`cardholderName`、`cvv`，全部为卡值句柄（类型 `CARD`，与凭据的 `PROTECTED_HANDLE` 分开） |
| 调用 | Platform → Payment 读卡接口，用途 `CHANNEL_EXECUTION`；事务外；超时 2 秒 |
| 失败 | Token 无效、不允许用卡、CVV 已被领取、超时：函数失败 → 503 `INTEGRATION_INITIALIZATION_FAILED`，保证未发出渠道请求 |

Platform 新增 Payment 内部 HTTP 客户端（`@HttpExchange`，服务发现名 `payment-orchestration-service`）。

### 3.2 卡字段绑定

- 原子 API 定义新增可选 `cardFields`（BODY 字符串字段键数组；实现时放在定义中而不是字段属性上，避免改字段表结构）。只有卡字段能绑定 `PAYMENT_CARD` 返回的句柄；句柄不能绑定其他字段、脚本参数、出口输出或状态规则（保存时校验，运行时再断言）。
- REQUEST 脚本看到的卡字段是不可读句柄；`sealJsonBody` 序列化最终字节时才展开，签名覆盖真实字节。
- 账号凭据句柄的现有规则不变。

### 3.3 遮蔽

| 位置 | 处理 |
|---|---|
| 执行轨迹 / 沙箱验证证据 | 请求 Body 另行生成遮蔽副本：PAN 保留前 6 后 4，其他卡字段为 `[CARD]`；签名 Header 保留 |
| Platform 日志 | 现状只记 ID、状态与耗时；增加检索测试 |
| 响应、出口、Problem、脚本异常 | 绑定规则保证不出现；测试断言 |

### 3.4 沙箱验证

验证账号凭据中以 `testCard.pan`、`testCard.expiryMonth`、`testCard.expiryYear`、`testCard.cardholderName`、`testCard.cvv` 录入测试卡（按用户此前决定，验证材料明文存 Platform 库，只录渠道测试卡）；验证运行时 `PAYMENT_CARD` 改为从验证账号读取测试卡（与验证账号替代 Vault 的机制相同），不调用 Payment。证据按 3.3 遮蔽。

## 4. 标准 API 与 PayerMax 配置

### 4.1 `PAY`（修改，草案）

- 输入：`channelAccount.*`（不变）；`card.token`（新增必填）；`payment.attemptId`、`payment.amount.value/currency`（不变）；`payment.country`（新增必填，来自 Intent `consumerCountryCode`）；`payment.returnUrl`（3DS 回跳，必填）；`payment.subject`、`payment.payerReference` 改为可选。
- 输出：`paymentStatus`（`SUCCEEDED`/`PENDING`/`FAILED`）、`nextAction.type`（`THREE_DS_CHALLENGE`）、`nextAction.redirectUrl`、`channelTransactionRef`、`channelStatus`、`failureCode`、`failureMessage`。
- `PAY_QUERY` 不变。

### 4.2 PayerMax 实现

- 原子 API `ORDER_AND_PAY`：`integrate` 改为 `Direct_Payment`；新增固定值 `terminalType=WEB`、`paymentDetail.paymentMethodType=CARD`；五个 `cardInfo` 字段声明 `sensitivity: CARD`；新增 `country`。
- 状态规则：`APPLY_SUCCESS` 且 `status` 为 `SUCCESS`、`FAILED`，或 `PENDING` 且有 `redirectUrl` → SUCCESS（流程按状态分出口）；拒绝码 → FAIL（补充卡相关码，以 UAT 实测为准）；其他 → ERROR。
- 流程：初始化 `channelAccount` 与 `card`；成功出口 `succeeded` / `challenge`（`nextAction`）/ `declined`；FAIL → `rejected`；默认 → `unknown`。
- `subject` 未提供时用固定文本兜底；`notifyUrl` 是否必填以 UAT 实测为准。
- 收银台版本被替换（本机 `PAYERMAX`、`PAYERMAX_MOCK`）；mor-test 不改（另行授权）。

## 5. Payment 改动

### 5.1 模块职责

| 模块 | 新职责 |
|---|---|
| Card | 读卡接口（第 2 节）：校验 Token 未过期未撤销、与 `attemptId` 绑定、该 Attempt 的 PAY Action 正在执行；返回卡值，CVV 领取后清除。Mock 实现见第 7 节 |
| Channel | `ChannelExecutionService`：按冻结 Channel/Account/MID 校验（沿用 `getChannelExecutionConfig` 规则，**不 Reveal**），返回 Platform 接入方编码、账号身份与 `accountVaultToken`；Platform 标准 API 客户端 |
| Execution | `PayActionActor`：组装 `PAY` 输入（含 `card.token`），经 Channel 调用 Platform，映射结果；`doInTransaction` 写渠道交易引用 |
| Routing | 最小真实路由（第 6 节） |
| Web / API | `PayResponse` 增加 `nextAction`；读卡接口 Controller 与 metadata-only 日志配置 |

渠道到 Platform 接入方编码的映射：阶段 3 核对 `t_payment_channel` 现有字段，需要 DDL 时单独出评审（评审点 T7）。

### 5.2 结果映射

| Platform 结果 | Action | 其他 |
|---|---|---|
| SUCCESS + `SUCCEEDED` | `SUCCEEDED` | 写 `ChannelTransactionRef`（`tradeToken`）；Attempt/Intent 成功 |
| SUCCESS + `PENDING` + `nextAction` | `PROCESSING` | 记 `interaction_type=THREE_DS_CHALLENGE`；`redirectUrl` 只放入本次 `PayResponse.nextAction`，不落库 |
| SUCCESS + `FAILED`、FAIL | `FAILED` | 记渠道响应码与描述 |
| ERROR、超时、5xx | `UNKNOWN` | 本期不恢复（第 5.3 节） |
| 404/422/500/503 Problem（保证未发出渠道请求） | `FAILED` | 配置或输入错误，不可能已扣款 |

### 5.3 恢复（本期不做）

> 2026-10-22 修订：PAY 查单恢复与渠道异步通知已实现（`PAY_RESULT_QUERY`、刷新接口、`ChannelPaymentNotified`），交互类型按渠道返回记录，见 [Execution 3DS 修订](../../20261022/payment-3DS全链路/execution-3ds-amendment.md)。

按用户意见 4，将来以渠道异步通知为主、重试查询为辅；本期 `PROCESSING`（等待 3DS）与 `UNKNOWN` 的 Action 保持原状，不自动推进。端到端验证中 3DS 场景只验证到“返回跳转指令、Action 为 PROCESSING”，认证后的结果由 Platform `PAY_QUERY` 单独核对。

## 6. 风控与路由

- **风控（用户意见 2）**：Payment 定义 Risk 端口与 HTTP 客户端（合同草案，待 Risk 确认），`PayRiskCheckActionActor` 经它调用；生产未配置时失败关闭，不加默认通过。`auto` 测试与本机端到端用测试侧 / harness 的 Risk Mock HTTP 服务返回“通过、无需认证”。
- **路由（用户意见 3）**：`RouteServiceImpl` 改为经 `QueryMidCandidates` 按 OE、支付方式、动作能力与金额筛选，取优先级最高的候选；不做健康度排序。

## 7. Card Mock

真实 Card 未实现。`payment.card.mock-enabled=true` 时：

- 允许的合成卡改为配置白名单（含 PayerMax UAT 测试卡号），其他卡拒绝；
- 申请工具时把卡材料保存在进程内存（按 Token，带过期时间），读卡接口领取后立即删除 CVV；不写库、不写日志；
- 生产默认关闭，未开启时读卡接口直接拒绝。

## 8. 测试

- Platform `auto`：`PAYMENT_CARD` 经测试 HTTP Mock 返回卡值；渠道 Mock 校验收到的卡字段与签名；遮蔽（轨迹、证据、日志检索）；绑定规则 422；读卡失败 503 且零渠道请求。
- Payment `auto`：从 `POST /payment-execution/v1/intents/{id}/pay` 入口，Platform 与 Risk 用测试 HTTP Mock；覆盖成功、3DS、拒绝、未知、Problem、重放；读卡接口的校验与 CVV 一次性领取；事务外调用断言；日志与库中无卡值。
- 本机端到端：Payment + Platform（`mor_platform_external_api`）+ Risk Mock + PayerMax UAT 测试卡；Payment 本机库准备一组 PayerMax 渠道/账号/MID（方式在阶段 4 前确认）。

## 9. 阶段划分

| 阶段 | 内容 |
|---|---|
| 1 | 本评审稿、决定与两处文档修订草案 |
| 2 | Platform：`PAYMENT_CARD`、卡字段绑定与遮蔽、Payment 客户端、沙箱验证测试卡；PayerMax 卡直付配置；`auto`；本机合成渠道与 UAT 沙箱验证（测试卡） |
| 3 | Payment：读卡接口与 Card Mock、Channel 校验服务与 Platform 客户端、`PayActionActor`、最小路由、Risk 端口、`PayResponse.nextAction`；`auto` |
| 4 | 本机端到端：PayerMax UAT 测试卡免 3DS 成功；3DS 返回跳转指令 |

每阶段结束等待确认。共享环境、提交、推送另行授权。

## 10. 评审点

| 编号 | 问题 | 状态 / 建议 |
|---|---|---|
| T0 | 代码归属 | 建议本会话修改 Payment（execution、card、channel、routing、web、api、test）与 Platform；Commerce、Risk、checkout-web 不改 |
| T1 | 卡数据通道 | **已定（意见 1）**：Token 传递，Platform 经 `PAYMENT_CARD` 读卡，仅内存 |
| T2 | 读卡接口 | 建议 `POST /payment-card/v1/channel-card-materials/reveal`，metadata-only 日志，CVV 一次性领取 |
| T3 | 卡字段绑定 | 建议原子 API 字段 `sensitivity: CARD`，句柄只能绑定 CARD 字段 |
| T4 | 收银台配置 | 建议本机 `PAYERMAX` 内 `PAY` 改为卡直付，收银台版本不保留 |
| T5 | 沙箱验证测试卡 | 建议存在验证账号中，明文（同验证账号决定），证据遮蔽 |
| T6 | 风控 | **已定（意见 2）**：Mock |
| T7 | 路由与渠道映射 | **路由已定（意见 3）**；接入方编码映射阶段 3 核对，需 DDL 时单独评审 |
| T8 | 503 等 Problem | 建议 Action `FAILED`（保证未发出请求） |
| T9 | 3DS 指令 | 建议只在本次 `PayResponse` 返回，不持久化 |
| T10 | 恢复 | **已定（意见 4）**：本期不做 |
| T11 | 范围 | 建议只做 AUTOMATIC（SALE）；MANUAL、退款对接、Commerce/checkout-web 后续 |

## 11. 阶段 2 实现记录（2026-10-14）

UAT 实测后对第 4 节的修正：

| 项 | 实测 | 处理 |
|---|---|---|
| `userId` | 卡直付同样必填（`PARAMS_INVALID: userId required`） | 标准 API `payment.payerReference` 改回必填，映射 `data.userId`；Payment 须提供不含个人信息的不透明付款人引用 |
| `buyerInfo` | 必须存在（`buyerInfo must be not null`）；提供 `clientIp` + `userAgent` 后受理 | 标准 API 新增可选 `payment.clientIp`、`payment.userAgent`，映射 `paymentDetail.buyerInfo`；见评审点 T12 |
| 被拒 | 已创建交易时外层 `code` 不是 `APPLY_SUCCESS`（`ISSUER_PAYMENT_REJECTED`、`PAYMENT_FAILED` 等），`data.status=FAILED` 且有 `tradeToken` | 有 `tradeToken` 时以 `data.status` 为准：`FAILED` 走 SUCCESS 分支的 `declined` 出口（`paymentStatus=FAILED`，`failureCode` 为外层码）；`SUCCESS`/`PENDING` 仍要求 `APPLY_SUCCESS` |
| 无 `data` 的拒绝 | 读取 `data.tradeToken` 前必须判断 `data` 存在，否则状态规则求值失败，明确拒绝会变成 ERROR | 规则先判 `http.responseBody.data != nil` |
| `version` | `1.5` 被接受 | 不变 |
| UAT 结果控制 | 由 `subject` 决定：`SUCCESS`/`FAILED`/`PENDING`/`3DS` | 验证用例按此编写 |

流程出口：`succeeded`、`challenge`（PENDING + `redirectUrl`，`nextAction.type=THREE_DS_CHALLENGE`）、`pending`（PENDING 无跳转）、`declined`、`rejected`、`unknown`。

| 编号 | 问题 | 建议 |
|---|---|---|
| T12（**已确认按建议**，2026-10-14） | PayerMax 卡直付要求 `buyerInfo`（实测 `clientIp`、`userAgent` 可满足）。Commerce 结账网络信息按项目约束不对外披露，传给收单渠道用于 3DS/风控是否允许 | 需要你确认：建议作为收单必需的最小披露（只传 IP 与 UA，经 Commerce→Payment 内部合同获得，不传设备指纹），在 Commerce 隐私设计中补记。PayerMax 不带 `buyerInfo` 会直接拒绝，未确认前 Payment 无法对 PayerMax 下单成功；本阶段 UAT 验证用的是文档保留网段的合成值（203.0.113.10） |

## 12. 阶段 3 实现记录（2026-10-14）

| 项 | 实现 | 与第 5、6 节的差异与原因 |
|---|---|---|
| 模块协作 | Execution 声明端口 `PaymentChannelClient`（MID 候选查询、执行渠道操作）与 `PaymentRiskClient`，由 Web 组合根实现（`PaymentChannelClientImpl`、`PaymentRiskClientImpl`） | Maven Enforcer 规定 Execution 只能依赖 Routing、Card，Routing/Channel/Card 互相隔离，只有 Web 能组合 Channel |
| Channel | 抽出 `AbstractChannelExecutionProcessor`（冻结坐标校验）；新增公开 `ExecuteChannelOperationProcessor`：校验坐标（不 Reveal）→ 补账号身份与 Vault 引用 → `PlatformStandardApiClient` 调 Platform；Platform 接入方编码沿用渠道编码 | 原执行配置读取（Reveal）保留不变 |
| Platform 结果判定 | 200 按 executeStatus；Problem 中 Platform 合同保证未发请求的码（标准 API/实现不存在、配置无效、初始化失败、预算耗尽、422）→ NOT_SENT → Action FAILED；其他失败 → ERROR → UNKNOWN | 5xx 时公共客户端不保留 code，从原始响应读取 |
| 路由 | Routing 改为纯选择 `RouteService.route(context, candidates)`：按动作能力与金额上下限取查询顺序第一个；候选由 Execution 经端口查询 | Routing 不能依赖 Channel |
| 风控 | `PayRiskCheckActionActor` 经端口调用 Risk；不可用或结论无效 → FAILED（失败关闭）；只接受 ALLOW/REVIEW/DENY | Risk 无支付评估接口：Web 中的 `PaymentRiskAssessmentApi` 为 Payment 侧合同草案（`TODO by CLAUDE`，待 Risk 确认后迁移到 risk-api） |
| 支付 | `PayActionActor` 组装 PAY 输入（`card.token`、attemptId、按币种精度的金额、国家、回跳地址、付款人引用、可选 IP/UA）；成功/3DS 待认证/拒绝/未知映射；3DS 指令只进本次 `PayResponse.nextAction` | pay 请求新增 `returnUrl`、`consumerReference`（必填）与 `consumerIpAddress`、`consumerUserAgent`（可选），均不落库 |
| 渠道交易引用 | `ActionResult.channelTransactionId`；`ActionFlow` 在结果事务内写 `t_channel_transaction_ref`（成功、待认证、拒绝都写） | 仓储补 ID 生成 |
| 读卡 | `RevealChannelCardMaterialProcessor`（Execution，拥有 Token 与 Attempt）：Caller 校验、Token 与 Attempt 绑定、当前动作为执行中的 PAY/AUTHORIZE，再由 Card 领取材料；Controller 实现 `ChannelCardMaterialApi`（全局日志已为 metadata-only） | — |
| Card Mock | 配置化测试卡号 `payment.card.mock-pans`，合成材料存进程内存，CVV 原子领取一次 | 仍为生产代码中的既有 Mock（`mock-enabled` 默认关闭） |
| 既有缺陷修复 | Execution 五个 Mapper 改为 resultMap 并逐列/逐参数指定 `CodeEnumTypeHandler`、UTC `InstantTypeHandler`（原 resultType 读不出下划线列与枚举）；`payCompleted`/`authorizeCompleted` 在当前动作已清空时条件改为 `IS NULL`（原条件恒不成立） | Execution 持久化此前从未在数据库上运行 |

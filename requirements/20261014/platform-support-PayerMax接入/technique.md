# PayerMax 接入技术方案

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14）：T1～T5、T7 按建议；T6 按用户意见修订为不区分接入形态的 `PAY` / `PAY_QUERY`
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：[原子 API 结构](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)、[流程结构](../../20260914/platform-support-外部API配置与执行/flow-definition-contract.md)、[内部接口](../../20260914/platform-support-外部API配置与执行/internal-api.md)、[账号材料交接](../../20260914/platform-support-外部API配置与执行/material-handoff.md)、[公共脚本与 HTTP 工作对象](../../20260914/platform-support-外部API配置与执行/public-script-http-context-design.md)、[研发计划第 3 步](../../20260914/platform-support-外部API配置与执行/dev-plan-step3.md)，以及现有引擎源码 `AtomicApiExecutor`、`ChannelRequestBuilder`、`HttpWork`、`ScriptFunctions`、`ChannelAccountConfigFunction`、`FieldSchema`、`JsonValues`。

## 1. PayerMax 协议要点

| 项 | 内容 |
|---|---|
| 地址 | UAT `https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway`，生产 `https://pay-gate.payermax.com/aggregate-pay/api/gateway` |
| 接口 | 下单 `POST /orderAndPay`，查询 `POST /orderQuery`；均为 JSON |
| 外层 | `version`（当前 `1.5`）、`keyVersion`（当前 `1`）、`requestTime`（RFC3339 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`，与当前时间相差须在两分钟内）、`appId`、`merchantNo`、`data` |
| 请求签名 | Header `sign` = Base64(SHA256WithRSA(商户私钥, **HTTP Body 原文**))；签名原文必须与实际发送字节完全一致 |
| 响应验签 | 响应 Header `sign`，用 PayerMax 公钥对**响应 Body 原文**验签 |
| 密钥 | RSA 2048，PKCS8，Base64 去掉首尾行与换行；商户公钥在商户后台上传，PayerMax 公钥从后台下载 |
| 外层 `code` | `APPLY_SUCCESS` 只表示受理，交易结果看 `data.status`：`SUCCESS`、`PENDING`、`FAILED`、`CLOSED` |
| 金额 | `totalAmount` 为 JSON **数字**，单位为元（主币种单位），精度按币种 |

## 2. 现状差异与引擎缺口

| # | 缺口 | 现状 | 影响 |
|---|---|---|---|
| G1 | 对最终字节签名 | REQUEST 脚本只能改 Body 对象，序列化在传输层；脚本拿不到发送字节 | 无法保证签名原文与发送字节一致。[原子 API 结构第 6 节](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md#6-脚本)已规定由注册函数“序列化并锁定请求体”，合同待定稿 |
| G2 | RSA | 只有 HMAC、SHA-256、Base64 | 无法签名与验签 |
| G3 | 协议时间 | 没有取当前时间的函数 | 无法生成 `requestTime` |
| G4 | 数字金额 | DECIMAL 只按十进制字符串出入：请求写成 `"10.00"`；响应中 JSON 数字 `10` 解码为 Long，不满足 DECIMAL 字段 | 请求金额类型不符合 PayerMax；查询响应 `totalAmount` 会被判为类型不符并记为 ERROR |

账号材料、出站 https、受控句柄、超时、状态规则、流程与 Problem 映射等已有能力足够，不改。

## 3. 引擎补能力（阶段 2）

### 3.1 `sealJsonBody(http)`（G1）

- 脚本函数，仅 REQUEST 阶段可用。把当前 `http.requestBody` 按本原子 API 的 JSON 编码规则（含第 3.4 节的 `decimalEncoding`）序列化为 UTF-8 字节，**锁定**到本次 HTTP 工作对象，返回同一份文本。
- 锁定后 `http.requestBody` 不可再写（写入即脚本异常 → 节点 ERROR）；传输层直接发送锁定字节，不再重新序列化。签名覆盖的就是发送字节。
- 只允许 `requestEncoding=JSON`；对 `FORM_URLENCODED`、`NONE` 调用报错。重复调用报错。
- 改动点：`HttpWork` 增加锁定字节槽位与锁定标记；`ChannelRequestBuilder.encode` 优先使用锁定字节；`ScriptFunctions` 注册函数；`RegisteredFunctionCatalog` 登记签名（E30 编辑器目录随之出现）。

### 3.2 RSA 函数（G2）

| 函数 | 参数 | 返回 / 行为 |
|---|---|---|
| `rsaSha256Sign(privateKey, message)` | `PROTECTED_HANDLE`（PKCS8 Base64，无首尾行）、STRING | SHA256withRSA 签名的 Base64（标准字母表，带填充） |
| `requireRsaSha256Signature(publicKey, message, signature)` | STRING（X.509 SubjectPublicKeyInfo Base64）、STRING、STRING | 验签通过返回 `true`；签名缺失、格式错误或不匹配都抛出固定文字异常，本节点记为 ERROR |

- 私钥只以受控句柄进入函数；公钥不是机密，作为普通参数。异常信息不带密钥、签名或报文。
- 验签用“失败即异常”而不是返回布尔，是为了让脚本不能把失败吞掉继续走状态规则。

### 3.3 `nowRfc3339()`（G3）

脚本函数，返回 UTC 毫秒精度 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`（如 `2026-10-14T07:29:00.123Z`），符合 PayerMax 要求的 `XXX` 偏移写法。时间取自注入的 `Clock`，测试可固定。

### 3.4 `decimalEncoding`（G4，评审点 T2）

原子 API `definition_json` 新增**可选**字段 `decimalEncoding`：`STRING`（默认，保持现状）或 `NUMBER`。

| 方向 | `NUMBER` 时 |
|---|---|
| 请求 | BODY 中 DECIMAL 值按 `toPlainString()` 写成 JSON 数字（不经浮点、不用指数）；对 JSON 编码与 `sealJsonBody` 同样生效 |
| 响应 | 解码后、RESPONSE 脚本之前，把**已声明为 DECIMAL** 的 BODY 响应字段（含 `[]` 数组路径）中的 JSON 整数转为 BigDecimal；其他位置不变。JSON 小数在任何模式下都精确解析为 BigDecimal（实现时顺带把解码改为 `USE_BIG_DECIMAL_FOR_FLOATS`，原先经 double 转换） |

- 只影响该原子 API 与渠道之间的报文；标准 API 对内输入输出仍是十进制字符串（项目第一方 JSON 规则不变）。
- 改动点：`IntegrationApiDefinitionValidator` 接受该字段；`ChannelRequestBuilder`、`AtomicApiExecutor.decode`；Operations 原子 API 编辑页“接口与 HTTP 传输”页签增加选择项（默认 STRING），并保证已有定义不带该字段时原样保存。

### 3.5 测试

- 纯 Java：RSA 签名/验签往返与篡改失败、错误密钥格式；`sealJsonBody` 锁定后写入失败、重复调用、非 JSON 编码；`decimalEncoding` 请求输出数字、响应整数与小数转换；`nowRfc3339` 格式。
- 入口测试（`StandardApiCommandApi` 真实 HTTP，渠道为测试专用 Mock HTTP 服务）：渠道 Mock 用测试公钥验证收到的 `sign` 与 Body 原文；返回带测试私钥签名的响应。覆盖签名正确、响应验签失败 → ERROR、锁定后脚本再改 Body → ERROR、数字金额出入。E30 目录包含新函数。

## 4. PayerMax 配置（阶段 3）

全部编码为本期草案。只读表达式剖面不支持数组字面量（`seq.list` 只在脚本剖面开放），集合判断用 `||` 连接的相等比较书写。

### 4.1 账号材料（Vault `CHANNEL_ACCOUNT_CONFIG_V1`）

| 位置 | 键 | 说明 |
|---|---|---|
| 身份 | `channelCode=PAYERMAX`、`channelAccountId`、`entityNo` | 与标准 API 输入核对 |
| `parameters` | `appId`、`merchantNo`、`keyVersion`、`payermaxPublicKey` | 普通值；公钥为 X.509 Base64 |
| `credentials` | `merchantPrivateKey` | PKCS8 Base64；只以受控句柄出现 |

### 4.2 接入方 `PAYERMAX`

出站地址 `PAYERMAX_GATEWAY` = `https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway`（本机库）。

### 4.3 公共脚本

| 编码 | 阶段 | 参数 | 源码（拟定） |
|---|---|---|---|
| `PAYERMAX_SIGN` | REQUEST | `privateKey`：PROTECTED_HANDLE | `json.set(http.requestBody, 'requestTime', nowRfc3339()); http.requestHeaders.sign = rsaSha256Sign(params.privateKey, sealJsonBody(http))` |
| `PAYERMAX_VERIFY` | RESPONSE | `publicKey`：STRING | `if http.responseStatus == 200 { requireRsaSha256Signature(params.publicKey, http.responseRaw, http.responseHeaders.sign) }` |

HTTP 200 的响应一律验签（含非 `APPLY_SUCCESS`），其他状态码本来就落入 ERROR，不验签（评审点 T5）。

### 4.4 原子 API

两者共用：`transport` POST、`PAYERMAX_GATEWAY`、连接 2000 ms、响应 10000 ms；`requestEncoding`/`responseDecoding` 为 JSON；`decimalEncoding=NUMBER`；`params`：`merchantPrivateKey`（PROTECTED_HANDLE，必填）、`payermaxPublicKey`（STRING，必填）；脚本依次 `PAYERMAX_SIGN`、`PAYERMAX_VERIFY`；固定值 `BODY:version='1.5'`。

**`PAYERMAX_ORDER_AND_PAY`**（`/orderAndPay`）

| 请求字段（BODY） | 类型 | 必填 | 来源 |
|---|---|---|---|
| `version` | STRING | 是 | 固定值 `1.5` |
| `keyVersion`、`appId`、`merchantNo` | STRING | 是 | 节点：账号 parameters |
| `requestTime` | STRING | 否 | 签名脚本写入（组装校验在脚本之前，因此声明为非必填） |
| `data` | OBJECT | 否 | 由子字段赋值生成。现行规则下必填父对象本身也要求被赋值（静态覆盖检查与运行时组装一致），因此声明为非必填；子字段仍按必填校验 |
| `data.outTradeNo` | STRING | 是 | 节点：`input.payment.attemptId` |
| `data.integrate` | STRING | 是 | 固定值 `Hosted_Checkout` |
| `data.subject` | STRING | 是 | 节点 |
| `data.totalAmount` | DECIMAL | 是 | 节点：`input.payment.amount.value` |
| `data.currency`、`data.userId`、`data.frontCallbackUrl` | STRING | 是 | 节点 |
| `data.country`、`data.language`、`data.reference`、`data.notifyUrl`、`data.expireTime` | STRING | 否 | 节点（输入未提供即省略） |

| 状态规则（按顺序） | 结果 |
|---|---|
| `http.responseStatus == 200 && http.responseBody.code == 'APPLY_SUCCESS'` | SUCCESS |
| `http.responseStatus == 200 && (code == 'PARAMS_INVALID' \|\| code == …)`，`code` 指 `http.responseBody.code`，逐个列出下方拒绝码 | FAIL |
| 其他 | ERROR |

拒绝码集合（明确未受理）：`PARAMS_INVALID`、`REQUEST_PARAM_INVALID`、`AMOUNT_INVALID`、`COUNTRY_INVALID`、`CURRENCY_INVALID`、`CONTRACT_INVALID`、`MERCHANT_INVALID`、`MERCHANT_APP_INVALID`、`SIGN_VERIFY_FAILED`、`PAYMENT_METHOD_NOT_EXIST`、`PAYMENT_METHOD_SUSPEND`、`AMOUNT_LIMIT`、`AMOUNT_LIMIT_MINIMUM`、`AMOUNT_LIMIT_MAXIMUM`、`ONBOARD_ERROR`。`ORDER_REPEAT`（同号已存在，可能是前一次已受理）、`SYSTEM_ERROR`、`REQUEST_TIMEOUT`、`UNEXPECTED_ERROR` 及未知码归 ERROR（评审点 T4）。

输出：SUCCESS → `redirectUrl`、`tradeToken`、`channelStatus`（`data.*`，必填）；FAIL → `errorCode`、`errorMessage`。

**`PAYERMAX_ORDER_QUERY`**（`/orderQuery`）

请求：外层同上，`data.outTradeNo`（必填）。响应声明：`code`、`msg`、`data.status`、`data.tradeToken`、`data.totalAmount`（DECIMAL）、`data.currency`、`data.resultCode`、`data.resultMsg`。

| 状态规则 | 结果 |
|---|---|
| `http.responseStatus == 200 && http.responseBody.code == 'APPLY_SUCCESS' && (status == 'SUCCESS' \|\| status == 'PENDING' \|\| status == 'FAILED' \|\| status == 'CLOSED')`，`status` 指 `http.responseBody.data.status` | SUCCESS |
| `http.responseStatus == 200 && http.responseBody.code == 'ORDER_NOT_EXIST'` | FAIL |
| 其他 | ERROR |

输出：SUCCESS → `channelStatus`、`tradeToken`、`amount`、`currency`、`resultCode`、`resultMsg`（后两项非必填）、`paymentMethodType`（非必填，取 `#data.paymentDetails[0].paymentMethodType`）；FAIL → `errorCode`、`errorMessage`。

### 4.5 标准 API（渠道无关，草案）

**不按接入形态区分标准 API（2026-10-14 用户提出并确认）：** 收银台、纯 API、APM 跳转是渠道的接入形态，不是对内能力。上游只调用 `PAY` 与 `PAY_QUERY`；“下单后需要用户完成跳转”由输出 `paymentStatus=PENDING` + `nextAction` 表达，与 Payment Execution 把交互事实放在原 Action、跳转地址只临时使用的模型一致。同一渠道将来同时支持多种形态时由渠道整合流程路由；现行流程结构的 `entry` 是单个节点、连线只能从节点出发，不能在入口按 `input` 分支，届时再选择“流程入口条件分支”或“不同接入方编码”（W6 已由 Payment 选择接入方），本期 PayerMax 只有收银台一种形态，不受影响。

**`PAY`**

| 输入 | 类型 | 必填 |
|---|---|---|
| `channelAccount.channelCode` / `channelAccountId` / `entityNo` / `accountVaultToken` | STRING | 是 |
| `payment.attemptId` | STRING | 是 |
| `payment.amount.value` | DECIMAL | 是 |
| `payment.amount.currency`、`payment.subject`、`payment.payerReference`、`payment.returnUrl` | STRING | 是 |
| `payment.country`、`payment.language`、`payment.notifyUrl`、`payment.expireSeconds` | STRING | 否 |

| 输出 | 说明 |
|---|---|
| `paymentStatus` | SUCCESS 必填：`SUCCEEDED`、`PENDING`（等待用户或渠道完成）、`FAILED`；PayerMax 收银台下单固定为 `PENDING` |
| `nextAction.type` | 声明为非必填（出口必填覆盖检查不看父对象是否存在，必填会迫使 FAIL 出口也赋值）；有 `nextAction` 时总是赋值，本期只有 `REDIRECT` |
| `nextAction.redirectUrl` | `type=REDIRECT` 时必有；PayerMax 收银台地址，只能临时使用、不得持久化 |
| `channelTransactionRef` | PayerMax `tradeToken`（SUCCESS 必填） |
| `channelStatus` | 渠道原始状态（SUCCESS 必填，排查用） |
| `failureCode`、`failureMessage` | FAIL 时返回，此时 `paymentStatus=FAILED` |

PayerMax 流程只在 `data.status=PENDING` 且有 `redirectUrl` 时走 `created` 出口（`PENDING + REDIRECT`）；`APPLY_SUCCESS` 但状态不是 `PENDING` 归 ERROR，由调用方查询确认。

`payerReference` 必须是不含个人信息的不透明引用（[项目约束](../../../AGENTS.md)），映射到 PayerMax `userId`。

**`PAY_QUERY`**

输入：`channelAccount.*`（同上）、`payment.attemptId`。输出：`paymentStatus`（SUCCESS 时 `SUCCEEDED`/`PENDING`/`FAILED`/`CLOSED`，由渠道状态映射；订单不存在的 FAIL 为 `NOT_FOUND`）、`channelTransactionRef`、`amount.value`、`amount.currency`、`paymentMethodType`、`failureCode`、`failureMessage`。

### 4.6 流程

| 标准 API | 初始化 | 节点 | 出口 |
|---|---|---|---|
| `PAY` | `channelAccount` = `CHANNEL_ACCOUNT_CONFIG`（必需） | `createOrder` → `PAYERMAX_ORDER_AND_PAY` | SUCCESS → `created`；FAIL → `rejected`；默认 → `unknown`（ERROR） |
| `PAY_QUERY` | 同上 | `queryOrder` → `PAYERMAX_ORDER_QUERY` | SUCCESS → `found`（`paymentStatus` 由表达式映射）；FAIL → `notFound`；默认 → `unknown` |

下单 ERROR 不接查询节点：查询拿不到 `redirectUrl`，补查成功也无法完成收银台跳转；调用方按“结果未知”稍后调用 `PAY_QUERY`（评审点 T3）。流程没有冲正、撤销类节点。

节点 `params`：`merchantPrivateKey` ← `resources.channelAccount.credentials.merchantPrivateKey`，`payermaxPublicKey` ← `resources.channelAccount.parameters.payermaxPublicKey`。

### 4.7 配置方式

[`assets/harness/configure.py`](./assets/harness/configure.py) 是本节配置的唯一执行来源，经 Web 代理 → Gateway → BFF → Platform 的真实管理接口保存（已存在则按版本修改）。本机合成与 UAT 用两个接入方 `PAYERMAX_MOCK`、`PAYERMAX`，内容相同、出站地址不同。保存后可在 Operations 外部 API 页面查看与编辑。

## 5. 本机联调（阶段 4）

- **库与服务：** 沿用 3e 的 `mor_platform_external_api` 与 [3e 启动器](../../20260914/platform-support-外部API配置与执行/assets/stage3e/harness/README.md)。Platform 以测试装配启动，出站允许 https 访问 PayerMax UAT。
- **账号材料：** 用户把 UAT `appId`、`merchantNo`、`keyVersion`、商户私钥、PayerMax 公钥写入本机私有文件（权限 0600，位于仓库外，路径由环境变量指定）。启动器读取后登记到 Vault Mock，不打印、不写库、不进仓库。
- **步骤：** 先用本机合成 PayerMax（`payermax_mock.py`：真实 RSA 与报文格式）跑完全部场景，再切到 UAT：下单 → 打印 `redirectUrl` → 用户在浏览器用 PayerMax 测试支付方式付款 → 查询，直到 `SUCCEEDED`；另测一笔不付款的 `PENDING`，以及一个不存在订单号的 `ORDER_NOT_EXIST`。
- **证据：** 回执 JSON 只记录场景、`executeStatus`、归一化状态、HTTP 状态与验签结果，不记录密钥、`redirectUrl` 中的 token 与报文原文。

## 6. 影响范围

| 系统 / 模块 | 改动 |
|---|---|
| `platform-support-integration` | 四项能力（第 3 节）、校验器、函数目录 |
| `platform-support-test` | 新增纯 Java 与入口测试、渠道 Mock 的 RSA 支持 |
| `operations-console-web` `src/externalApi/` | `decimalEncoding` 选择项（只改外部 API 相关文件） |
| 数据库 | **不涉及结构变更**；配置数据只经管理接口写入本机独立库，不产生需求级 SQL |
| Payment、Gateway、BFF、Vault 服务 | 不改 |

## 7. 风险

- **验签范围：** 文档未说明错误响应是否都带 `sign`。若 UAT 实测错误响应不带签名，HTTP 200 的明确拒绝会变成 ERROR；届时按实测调整 `PAYERMAX_VERIFY` 并记录。
- **拒绝码集合：** 依据公开错误码表人工划分，UAT 中出现未列出的码时按 ERROR 处理，再补充。
- **时钟：** `requestTime` 须在两分钟内，本机时间需准确。
- **UAT 网络：** 本机直连 UAT 依赖外网；UAT 可能对来源 IP 有白名单，需用户在 PayerMax 后台确认。
- **日志：** 内部执行接口沿用完整 Body 日志（W5 已知遗留）：`redirectUrl` 会出现在 Platform 日志中，私钥不会（只在 Vault Mock 与受控句柄中）。

## 8. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| T1 | 签名字节一致性的做法 | `sealJsonBody(http)` 序列化并锁定请求体，传输层发送锁定字节（落实原子 API 结构第 6 节） |
| T2 | 数字金额 | 原子 API 可选 `decimalEncoding: STRING/NUMBER`，默认 STRING；同时作用于请求编码与已声明 DECIMAL 的响应字段；需要改 Operations 编辑页 |
| T3 | 下单 ERROR 是否接查询节点 | 不接；调用方稍后调用 `PAY_QUERY` |
| T4 | 拒绝码划分 | 按第 4.4 节；`ORDER_REPEAT` 归 ERROR |
| T5 | 验签范围 | HTTP 200 一律验签；UAT 实测（2026-10-14）FAIL 响应也带 `sign` 并验签通过，保持不变 |
| T6 | 标准 API 编码与字段 | **修订（2026-10-14）：** 不按接入形态区分，用 `PAY`、`PAY_QUERY`；输出 `paymentStatus` + 可选 `nextAction`（第 4.5 节）。字段仍为草案，Payment 接入时可改 |
| T7 | 阶段 2 代码授权 | 授权修改 Platform `integration`、`api`、`test` 模块，以及 `operations-console-web/src/externalApi/` 下与 `decimalEncoding` 有关的文件；不含提交、推送、共享环境变更 |

# Cybersource（Network International）接入：技术方案

- 状态：第 1～6 节已确认（2026-10-22）；第 7 节为阶段 5 实施中的决定 F1 与调整（已按用户选择实施）
- 需求：[requirement.md](./requirement.md)

## 1. 现状差异与影响（阶段 2）

### 1.1 本机核对（2026-10-22）

1. **签名算法复算**（Python，脚本在会话 scratchpad）：用对接包 `test-vectors.json` 的测试专用密钥复算 6 个样本，逐字节一致：
   - `auth.digest`、`auth.hmac`；
   - 支付查询、退款、非 3DS 卡下单、退款查询四个请求的 `Digest`、`Signature` 和 `Date`。
2. **apitest 只读探测**：每组凭据发一次 `POST /tss/v2/searches`，查询一个不存在的单号 `PTTESTPROBE0001`，没有创建任何交易。

| 凭据 | 结果 |
|---|---|
| `PMAE01_CYBS_SAR` = `PMAE01_CYBS_USD`（同一组） | `201`，`totalCount=0`，**有效** |
| `PF_CYBS_NI_SAR`（PF 模式） | `201`，`totalCount=0`，有效 |
| AED、BHD、EGP、KWD、ZAR | `401 {"response":{"rmsg":"Authentication Failed"}}` |
| `TEST`（记录 1） | 没有凭据 |

响应约 1 秒。签名用的是本机实现，401 说明这 5 组凭据不属于 apitest，不是签名问题：同一实现对另两组成功。

3. **JDK 25 的 `HttpRequest` 允许设置 `Date` 头**（jshell 实测）。`Host` 头属于传输层，不能配置（`ChannelHttpClient.TRANSPORT_HEADERS`），由地址自动给出 `apitest.cybersource.com`，正好与签名串一致。

### 1.2 需求项差异

| 需求项 | 当前行为及证据 | 缺口 | 处理建议 |
|---|---|---|---|
| Digest：`SHA-256=` + Base64 | 只有 `sha256Hex`；`base64Encode` 只能编码 UTF-8 文本，不能把十六进制摘要转成 Base64 | **缺函数** | 新增 `sha256Base64(text)` |
| HMAC 密钥先做 Base64 解码 | `hmacSha256Base64(key, msg)` 把密钥当 UTF-8 文本使用（`ScriptFunctions.HmacSha256`） | **缺函数** | 新增 `hmacSha256Base64DecodedKey(key, msg)`：密钥是受控句柄，在句柄内解码，脚本拿不到密钥 |
| `Date`：RFC 1123 | 只有 `nowRfc3339`；Aviator 脚本不能格式化星期和月份名 | **缺函数** | 新增 `nowRfc1123()`：固定 `Locale.US` 和 GMT，修正旧系统依赖 JVM locale 的问题 |
| 签名串中的路径（`request-target`） | 脚本可读的只有请求头和请求体，没有最终 URL；路径变量在脚本之后才替换 | 无（配置规避） | 节点以脚本参数传入路径，如 `'/pts/v2/payments/' + id + '/refunds'` |
| 签名串中的 host | 脚本看不到出站地址 | 无（配置规避） | 渠道账号参数 `host`（沙箱填 `apitest.cybersource.com`），作为脚本参数传入 |
| 签名覆盖最终发送的字节 | `sealJsonBody(http)` 锁定请求体并返回发送文本（PayerMax 用法） | 无 | `sha256Base64(sealJsonBody(http))` |
| 卡字段 | `PAYMENT_CARD` 提供 `pan`、`expiryMonth`、`expiryYear`（4 位）、`cvv`、`cardholderName`；`cardFields` 声明的字段只在发送字节中展开 | 无 | 配置。另见 1.4 发现 |
| 金额与类型 | `decimalEncoding: NUMBER` 原样发送；固定值可以是布尔和字符串 | 无 | `totalAmount` 按官方类型发字符串；`capture` 发布尔值（修正旧系统发 `"true"` 字符串）。阶段 3 用写探测确认 |
| 查询结果是数组 | Transaction Search 返回 `_embedded.transactionSummaries[]`，每条又有 `applications[]` | 无 | 沿用 dLocal / Alipay+ 做法：RESPONSE 脚本用有界 `for` 挑出目标记录，写回响应体，再交给状态规则 |
| 退款、请款要渠道支付 `id` | 标准 API 只带我方单号 | 无（配置规避） | 两个节点：先 Search 取得 `id`，再退款或请款（dLocal 同样做法） |
| 标准 API | mor-test 已有 `PAY`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`、`AUTHORIZE`、`CAPTURE`（MPGS 需求） | 无 | 不改标准 API |
| Search 的索引延迟 | 未知 | 待测 | 阶段 3 写探测实测；查不到时按“处理中”或可重试处理 |
| 沙箱商户 | 只有 SAR / USD 一组和 PF 一组可用 | 无 | 用 `PMAE01_CYBS_USD`（见 E1） |

### 1.3 系统影响

| 系统 | 影响点 | 改动类型 | 依赖与风险 |
|---|---|---|---|
| channel-connector 代码（protocol 模块） | `ScriptFunctions` 新增 3 个函数，`RegisteredFunctionCatalog` 登记签名，自动测试用 `test-vectors.json` 的测试专用样本 | **代码** | 不改表、不改接口；运营编辑器的函数列表来自目录，自动可见 |
| channel-connector（mor-test 运行配置） | 新渠道 `I_CYBS_NI`：出站端点、脚本、原子 API、6 条流程、验证账号与用例 | 配置 | 统一出口第二段后，渠道要在运营控制台（或渠道接口）建立 |
| mor-test 部署 | channel-connector 镜像要换成含新函数的版本 | **镜像发布** | 该服务刚由统一出口会话发布过；基线取最新 main，并与其他会话错开 |
| Payment、card、Gateway、ops BFF / Web | 无 | 否 | — |
| 仓库 | 需求目录、harness、回执、`CURRENT_IMPLEMENTATION.md` | 文档与脚本 | 凭据不入库 |

### 1.4 发现（不在本需求范围）

- **现象：** `sealJsonBody(http)` 把发送文本作为普通字符串返回给脚本。请求体含卡字段时，返回的文本里就是卡号原值。
- **证据：** 实现中 `JsonValues.containsHandle` 只拦截凭据句柄，不拦截 `CardValue`；编码使用 `SecretRendering.WIRE`。
- **影响：** 按 PayerMax 卡直付的设计，这段文本只交给签名函数使用。但脚本作者在技术上可以把它写进请求头或其他位置，执行轨迹中的请求头不会遮蔽卡号。Cybersource 下单同样要对含卡号的请求体算摘要，沿用的是同一个模式。
- **可选加固**（需另行决定，本需求建议不做）：让 `sealJsonBody` 返回不可读的锁定体，只允许摘要和签名函数接收。

### 1.5 待确认的决定

| # | 决定 | 建议 |
|---|---|---|
| E1 | 沙箱商户 | `PMAE01_CYBS_USD`（与 SAR 同一组凭据，apitest 有效），币种 USD；PF 记录不用（不做 PF） |
| E2 | 新增函数的名称与语义 | `sha256Base64(text)`、`hmacSha256Base64DecodedKey(key, msg)`（key 必须是受控句柄）、`nowRfc1123()`（`EEE, dd MMM yyyy HH:mm:ss 'GMT'`，`Locale.US`，UTC） |
| E3 | 路径和 host 的传入方式 | 按 1.2：路径由节点传入，host 放在渠道账号参数中 |
| E4 | 授权沙箱写探测 | 阶段 3 在 apitest 用 USD 商户和 Cybersource 公布的测试卡，单号前缀 `PTTEST`，探测下单、预授权、请款、退款、Search 延迟和拒付触发方式 |
| E5 | 阶段划分 | 维持需求评审稿的 6 个阶段 |

## 2. 沙箱写探测结果（阶段 3，2026-10-22）

本机 Python 直连 `https://apitest.cybersource.com`，商户 `PMAE01_CYBS_USD`（处理器显示为 `fdiglobal`），测试卡 Visa `4111 1111 1111 1111`、12/2031、CVV 123，币种 USD，单号前缀 `PTTEST`。签名用阶段 2 已核对的实现。请求体只带必要字段：`clientReferenceInformation`、`processingInformation`、`orderInformation`（含假 `billTo`）、`paymentInformation.card`，不带 PF 和商户描述字段。

| 探测 | 结果 |
|---|---|
| 下单 `capture=true`（布尔值），`totalAmount="10.00"`（字符串） | `201 AUTHORIZED`，`responseCode=00`，有授权码 |
| 同一单号立即重发下单 | `400 INVALID_REQUEST / DUPLICATE_REQUEST`（15 分钟内同一 `code` 重复）；响应带一个新 `id` |
| 预授权 `capture=false` | `201 AUTHORIZED` |
| 过期卡（01/2020） | `201 DECLINED`，`errorInformation.reason=EXPIRED_CARD` |
| 卡号校验位错误 | `201 DECLINED`，`INVALID_ACCOUNT` |
| 金额 2000.05 | `201 AUTHORIZED`（沙箱不按金额触发拒付） |
| 部分退款 4.00（路径用支付 `id`） | `201 PENDING`，有 RRN |
| 部分请款 6.00（路径用授权 `id`） | `201 PENDING` |
| 第二次请款（5.00 或 4.00） | `400 INVALID_DATA`，`details` 要求 `captureOptions.captureSequenceNumber` / `totalCaptureCount`：**不带分次参数时一笔授权只能请款一次** |
| 同一单号重发退款或请款 | `400 DUPLICATE_REQUEST`（“transaction identifier … already been used in the last 60 days”） |
| 退款路径用授权 `id`（已部分请款） | `201 PENDING`：退款可以直接挂在授权 `id` 上 |
| 退款或请款时 `id` 不存在 | `400 INVALID_DATA`（请款这次耗时 11 秒） |
| Search 可见延迟 | 下单、退款、预授权、请款、拒付记录都在 **1～1.6 秒内**可查到 |
| Search 记录结构 | 顶层 `applicationInformation.{rCode,reasonCode,rFlag}`，加上 `applications[]`：<br>• 下单：`ics_auth` + `ics_bill` 都是 1/100；<br>• 预授权：只有 `ics_auth`；<br>• 请款：只有 `ics_bill`；<br>• 退款：`ics_credit` 1/100；<br>• 拒付：`ics_auth` 0/202，顶层 `rFlag=DCARDEXPIRED`；<br>• 重复请求：顶层 0/104、`rFlag=DDUPLICATE`<br>退款和请款另有 `processorInformation.eventStatus=Pending`（等批量送收单行） |

**结论：**
- 官方类型（布尔、字符串金额）可用；
- 查询延迟可以忽略；
- 拒付用过期卡触发；
- 请款只做单次；
- 重发统一返回 `DUPLICATE_REQUEST`，需要回查；
- 退款和请款同步返回 `PENDING`，即“已受理，待批量送收单行”。

新增沙箱交易：2 笔下单（其中 1 笔金额 2000.05）、1 笔预授权（已部分请款 6.00，另退 2 笔各 1.00）、1 笔退款 4.00、2 笔拒付，以及若干被拒的重发。沙箱交易不需要清理。

## 3. 设计决定

| # | 决定 | 依据 |
|---|---|---|
| C1 | 渠道 `I_CYBS_NI`，出站端点 `CYBS_API` = `https://apitest.cybersource.com`，标记沙箱。账号材料：参数 `merchantId`、`host`（签名串用，沙箱为 `apitest.cybersource.com`）；凭据 `merchantKeyId`、`merchantSecretKey`（都是受控句柄） | 阶段 2 E1、E3 |
| C2 | 公共请求脚本 `CYBS_HTTP_SIGNATURE`（REQUEST），参数 `merchantId`、`host`、`requestTarget`（节点传入的路径）、`keyId`、`secretKey`：<br>`body = sealJsonBody(http)` → `Digest = 'SHA-256=' + sha256Base64(body)`<br>→ `date = nowRfc1123()` → 拼 5 行签名串<br>→ `signature = hmacSha256Base64DecodedKey(params.secretKey, 签名串)`<br>→ 写 `Date`、`Digest`、`v-c-merchant-id`、`Signature` 四个头。<br>`keyid` 也是句柄，用受控拼接写入（见 C3） | 阶段 2 E2；样本已复算 |
| C3 | `Signature` 头里的 `keyid` 来自凭据句柄，脚本不能把句柄和普通字符串拼接。因此 `merchantKeyId` 改放**账号参数**（非凭据）：它只是密钥编号，单独不能签名，官方也把它当普通标识 | 引擎规则：受控句柄只能交给认证函数 |
| C4 | 单号：`clientReferenceInformation.code` 和 `transactionId` 都填我方单号（支付和预授权用 `attemptId`，退款用 `refundId`，请款用 `captureId`）。查询一律按 `code` 搜索 | 旧系统规则；探测证实 Search 按 code 可查 |
| C5 | 原子 API：<br>• `PAYMENT`：`POST /pts/v2/payments`，`capture` 由节点给出；<br>• `REFUND`：`POST /pts/v2/payments/{id}/refunds`；<br>• `CAPTURE`：`POST /pts/v2/payments/{id}/captures`；<br>• `SEARCH`：`POST /tss/v2/searches`，`save=false`、`limit=20`、`sort=submitTimeUtc:desc`。<br>`SEARCH` 带 RESPONSE 脚本 `CYBS_PICK`（参数 `application`），用有界 `for` 取第一条非重复记录（跳过 `rFlag=DDUPLICATE` 和顶层 reasonCode 104/105），在其中找指定 application，结果写回响应体 | 第 2 节；dLocal 做法 |
| C6 | **状态**：<br>• 下单和预授权：`AUTHORIZED` → `SUCCEEDED`；`DECLINED` 或 `AUTHORIZED_RISK_DECLINED` → `FAILED`（`failureCode=errorInformation.reason`，`failureMessage=errorInformation.message`）；`AUTHORIZED_PENDING_REVIEW` 和 `PENDING_REVIEW` → `PENDING`。<br>• 退款和请款：同步 `PENDING` → `SUCCEEDED`，`channelStatus` 记 `PENDING`。<br>• 退款查询和请款相关的查询：`ics_credit` / `ics_bill` 为 1/100 → `SUCCEEDED`，不等 `eventStatus=Transmitted` | 官方：退款和请款的 `PENDING` 就是受理成功、等批量；探测：查询立即显示 1/100。旧系统的灰度会让退款长期停在处理中，这里修正 |
| C7 | **重发**：<br>• 下单或预授权返回 `DUPLICATE_REQUEST` 时，回查 Search（code = `attemptId`），按查到的原记录返回；<br>• 退款或请款返回 `DUPLICATE_REQUEST` 时，按 code = `refundId` / `captureId` 回查；<br>• 回查不到时，下单和预授权返回 ERROR（结果未知），退款和请款返回 FAIL | 第 2 节；与 MPGS A4 一致 |
| C8 | 退款和请款先 `SEARCH`（code = `paymentAttemptId`，application 为 `ics_auth`）取原单 `id`，再调用。找不到原单 → FAIL/`FAILED`（`failureCode=PAYMENT_NOT_FOUND`）；原单授权失败 → FAIL/`FAILED`（`failureCode=PAYMENT_NOT_AUTHORIZED`） | 探测：退款可以挂在授权 `id` 上 |
| C9 | **请款只做单次**：第二次请款由渠道返回 `INVALID_DATA`，映射为 FAIL/`FAILED`。部分请款后剩余额度不能再请，这是本渠道与 MPGS 的差异 | 第 2 节；分次请款要加标准 API 字段，暂不做 |
| C10 | `PAY_QUERY`：<br>• 原记录 `ics_auth` 和 `ics_bill` 都是 1/100 → `SUCCEEDED`；<br>• `ics_auth` 的 rCode 为 0 → `FAILED`（`failureCode` 为顶层 `rFlag`，如 `DCARDEXPIRED`）；<br>• 只有 `ics_auth` 成功、没有 `ics_bill`（是预授权）→ FAIL/`NOT_FOUND`，`failureCode=NOT_A_PAYMENT`；<br>• 查无记录 → FAIL/`NOT_FOUND`，`failureCode=NOT_FOUND`。<br>`channelTransactionRef` 统一取 Cybersource `id`，修正旧系统查询与下单口径不一致 | 第 2 节 |
| C11 | `REFUND_QUERY`：<br>• 按 `refundId` 搜索，`ics_credit` 1/100 → `SUCCEEDED`，金额取 `orderInformation.amountDetails`；<br>• `ics_credit` rCode 为 0 → `FAILED`；<br>• 查无记录 → FAIL/`NOT_FOUND`；<br>• 记录不是退款 → FAIL/`NOT_FOUND`，`failureCode=NOT_A_REFUND`。<br>请求带上 `sort`（修正旧系统缺失） | 第 2 节 |
| C12 | 卡字段：`paymentInformation.card.{number, expirationMonth, expirationYear(4 位), securityCode}` 为 `cardFields`，取自 `PAYMENT_CARD`。`billTo` 本期发固定的假地址（与旧系统相同），标准 API 不加账单地址 | 旧系统做法；Payment 尚无账单地址 |
| C13 | 金额：`totalAmount` 用 `MONEY_MAJOR_UNITS` 按币种精度格式化后**以字符串发送**（官方类型），响应金额按字符串原样输出 | 第 2 节 |

## 4. 脚本函数（阶段 4 代码）

| 函数 | 签名 | 语义 |
|---|---|---|
| `sha256Base64` | `(text) → String` | UTF-8 字节的 SHA-256，标准 Base64（带 `=`） |
| `hmacSha256Base64DecodedKey` | `(key: 受控句柄, message) → String` | 句柄内对密钥做 Base64 解码，计算 HmacSHA256，输出标准 Base64。密钥不是合法 Base64 时抛固定文字的异常，不带值 |
| `nowRfc1123` | `() → String` | 当前 UTC 时间，`EEE, dd MMM yyyy HH:mm:ss 'GMT'`，`Locale.US`，使用注入的 `Clock` |

- 在 `ScriptFunctions` 实现，在 `RegisteredFunctionCatalog` 登记（运营编辑器的函数列表随之出现），不改表、不改接口。
- **自动测试：**
  - 扩展 `ScriptSigningFunctionsTest`，用 `test-vectors.json` 中测试专用密钥的 `auth.digest`、`auth.hmac` 样本逐字节断言，并覆盖固定时钟格式和非法 Base64 密钥；
  - 扩展 `StandardApiSignedChannelTest`，增加一条 Cybersource 式签名的端到端用例：测试 HTTP 端点按样本算法验 `Digest` 和 `Signature`，确认签名覆盖实际发送的字节。

## 5. 配置与流程（阶段 5，mor-test）

| 标准 API | 节点 | 返回 |
|---|---|---|
| `PAY` | 初始化（账号 + 卡）→ `PAYMENT`（`capture=true`）；`DUPLICATE_REQUEST` 时 → `SEARCH`（`attemptId`，`ics_auth`） | 按 C6；回查按 C10 的状态映射 |
| `AUTHORIZE` | 同上，`capture=false`；返回 `authorizationStatus` | 同上 |
| `PAY_QUERY` | `SEARCH`（`attemptId`） | C10 |
| `REFUND` | `SEARCH`（`paymentAttemptId`）→ `REFUND`（`id`）；`DUPLICATE_REQUEST` 时 → `SEARCH`（`refundId`，`ics_credit`） | C6、C7、C8 |
| `REFUND_QUERY` | `SEARCH`（`refundId`，`ics_credit`） | C11 |
| `CAPTURE` | `SEARCH`（`paymentAttemptId`）→ `CAPTURE`（`id`）；`DUPLICATE_REQUEST` 时 → `SEARCH`（`captureId`，`ics_bill`） | C6～C9 |

**验证账号**（材料从仓库外对接包读取）：

| 账号 | 商户 | 测试卡 |
|---|---|---|
| `SBX_APPROVED` | `PMAE01_CYBS_USD` | Visa 4111 1111 1111 1111，12/2031，CVV 123 |
| `SBX_DECLINED` | 同上 | 同卡，01/2020（过期 → `EXPIRED_CARD`） |

**用例：**

| 用例 | 账号 | 步骤与期望 |
|---|---|---|
| `SBX_PAY_REFUND_CHAIN` | APPROVED | `PAY` 10.00 → `SUCCEEDED` → `PAY_QUERY` → `SUCCEEDED`、10.00 USD → `REFUND` 4.00 → `SUCCEEDED` → `REFUND_QUERY` → `SUCCEEDED`、4.00 → 用同一退款号重发 → `SUCCEEDED`（C7 回查）→ 用支付单号查退款 → `NOT_FOUND`/`NOT_A_REFUND` |
| `SBX_PAY_DECLINED` | DECLINED | `PAY` → SUCCESS/`FAILED`、`EXPIRED_CARD` → `PAY_QUERY` → `FAILED` |
| `SBX_PAY_REPLAY` | APPROVED | `PAY` → `SUCCEEDED` → 同一单号重发 `PAY` → `SUCCEEDED`（C7） |
| `SBX_AUTH_CAPTURE_CHAIN` | APPROVED | `AUTHORIZE` 10.00 → `SUCCEEDED` → `PAY_QUERY` → `NOT_FOUND`/`NOT_A_PAYMENT` → `CAPTURE` 6.00 → `SUCCEEDED` → 第二次 `CAPTURE` 4.00 → FAIL/`FAILED`、`INVALID_DATA`（C9）→ 用第一笔请款号重发 → `SUCCEEDED`（C7）→ `REFUND` 2.00 → `SUCCEEDED` |
| `SBX_NOT_FOUND` | APPROVED | `PAY_QUERY`、`REFUND`、`REFUND_QUERY`、`CAPTURE` 都用不存在的单号 → FAIL，分别为 `NOT_FOUND`、`PAYMENT_NOT_FOUND`、`NOT_FOUND`、`PAYMENT_NOT_FOUND` |

**AC 核对：**
- 签名按 `test-vectors.json` 核对（阶段 4 的自动测试，加上运行轨迹中遮蔽后的签名头格式）；
- 凭据、卡号的检查方法与回归同 MPGS；
- MPGS 的 8 个用例和其他渠道的 8 个回归用例都要通过。

## 6. 实施步骤与恢复

1. **阶段 4（代码）：**
   - 在主工作区以最新 main 为基线实现第 4 节的函数和测试；
   - 运行 channel-connector 的 `auto` 测试（`-am`，本机 loopback 环境）；
   - 提交前我先复核，不改其他模块。
2. **阶段 5（mor-test）：**
   - 按 mor-test 发布惯例构建 channel-connector 镜像（固定 sha256、核对运行 JAR 内容），只替换 channel-connector；
   - 发布前通知统一出口会话错开；
   - 在运营控制台或渠道接口建立渠道 `I_CYBS_NI`（支付类）和端点，用 harness 写脚本、原子 API、流程和验证账号，运行第 5 节用例与回归。
   - 发布和提交分别请你授权。
3. **恢复：**
   - 镜像回退到发布前的 sha256；
   - 渠道配置可以逐项删除；
   - 新函数只新增，不影响已有脚本。

## 7. 阶段 5 决定 F1 与实施调整（2026-10-22）

**发现：** Cybersource 读接口最终一致。
- 下单后 1～3 秒内，Search、`GET /tss/v2/transactions/{id}`、`GET /pts/v2/payments/{id}` 都查不到；
- 连续两次 Search 的结果可能不一致；
- 写接口（按 `id` 退款、请款）立即可用。

因此 C8（先 Search 取原单 `id`）在紧接着下单时会偶发失败。

**F1（用户选择）：**
- 标准 API `REFUND` 增加可选字段 `refund.paymentChannelRef`，`CAPTURE` 增加 `capture.paymentChannelRef`，值为 Payment 保存的下单渠道引用；
- 修改时服务端复核了全部接入方流程，其他渠道不受影响。
- Cybersource 的 REFUND / CAPTURE 流程直接按该引用调用（取代 C8）。
- 流程引擎的入口节点固定，在第一次渠道调用前不能分支，所以无法“缺省时退回检索”。字段缺失时 PATH 无法构建，按引擎规则返回未发出（NOT_SENT），不会发出请求；与 MPGS 退款查询必须带 `paymentAttemptId` 同理。

**同范围调整：**

| 项 | 调整 |
|---|---|
| 查询（C10、C11）与下单重发回查（C7） | 改为检索链：查无记录时最多连查 4 次（每次约 0.4 秒），首个命中即停。流程校验只允许返回出口读取必经节点，所以每个检索节点有自己的返回出口（上限 16）。仍查不到时：查询返回 `NOT_FOUND`，下单回查返回 ERROR |
| 退款、请款重发回查（C7） | 按本单号沿检索链回查，仍查不到 → FAIL |
| 查询结果的金额 | 拒付记录的 `amountDetails` 为空，金额换算函数的参数必填，因此缺失时以 0 USD 占位；只有成功分支输出金额 |
| 拒付查询 `failureCode` | 取顶层 `rFlag`，如 `DCARDEXPIRED` |
| 沙箱用例 | 改为写、读两段：<br>• 4 个写用例（支付加按引用退款、拒付、预授权加请款加第二次请款被拒加按引用退款、不存在的单）；<br>• 写用例全部通过后，harness 等 10 秒，把实际单号和渠道引用写成字面值，再跑 3 个读用例（支付查询、退款查询、重发退款、用支付单号查退款、重发支付；拒付查询；预授权单查询、重发请款）。 |

**对 Payment 的约束：**
- 调用 Cybersource 的 REFUND / CAPTURE 必须传 `paymentChannelRef`；
- `PAY_QUERY` 在提交后几秒内返回 `NOT_FOUND` 不能视为终态，恢复查询应留出至少数十秒。

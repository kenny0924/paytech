# PayBy 卡直付对 Platform 卡能力的补充要求（交卡直付会话评审）

- 日期：2026-10-14
- 状态：**评审稿**；用户 2026-10-14 决定由 [Payment 经 Platform 对接 PayerMax 卡直付](../payment-PayerMax卡直付对接/requirement.md)所在会话一并评审与实现，PayBy 会话不改 Platform 代码
- 来源：[PayBy 接入技术方案](./technique.md)第 2、5 节；UAT 实测（同日，见[交付记录](./delivery.md)）
- 前提：沿用卡直付方案第 2、3 节已确认的卡数据通道（`PAYMENT_CARD` 读卡、`sensitivity: CARD` 字段、受控句柄、遮蔽）。本文只列 PayBy 额外需要的能力，不改变已确认的边界。

## 1. UAT 实测协议事实

| 项 | 事实 |
|---|---|
| 场景 | `POST /acquire2/placeOrder`，`paySceneCode=DIRECTPAY`；外层与签名同 PayBy 其他接口（`requestTime` 毫秒数字、`sign` = SHA256withRSA(Body 原文)） |
| 加密字段 | `paySceneParams.cardNo`、`holderName`、`cvv` 各自 `Base64(RSA/ECB/PKCS1Padding(PayBy 公钥, UTF-8 明文))`；每次密文不同 |
| 加密公钥 | 与响应验签同一把 PayBy 平台公钥（对接包 `channelPublicKey_bak`）；用对接包 `channelPublicKey` 加密时返回 `400 parameter decrypt failed: cvv`（带签名，`applyStatus=ERROR`） |
| 明文卡字段 | `expYear`（2 位，如 `31`）、`expMonth`（2 位，如 `05`） |
| 其他字段 | `threeDSecure`（`"true"`/`"false"` 字符串）、`saveCard="false"`、`platformType=WEB`、`realIP`、`redirectUrl`（必填）、可选 `email`、`customerId`（传则 PayBy 存卡） |
| 3DS | 测试卡 5123 4500 0000 0008（05/31，CVV 100），`threeDSecure` 为 `true` 或 `false` 都返回 `status=CREATED` + `interActionParams.threeDSecureDom`：一段约 450 字节的 HTML，`<form method="POST" action="https://sim-mock.test2pay.com/…">` 带隐藏字段 `amount`、`orderNo`、`currency`，并由内联脚本自动提交 |

## 2. 需要的能力

### A1 CARD 字段的公钥加密

- 原子 API 请求字段（`sensitivity: CARD`）新增可选属性，例如 `cardEncryption: { algorithm: RSA_PKCS1_V15_BASE64, publicKeyParam: "<原子 API STRING 参数名>" }`。
- 在序列化最终请求字节时（`sealJsonBody` 与传输层编码同一路径）对句柄明文按 UTF-8 加密后写入；脚本、出口、状态规则仍看不到明文与密文。
- 轨迹与沙箱证据中该字段与其他卡字段一样遮蔽为 `[CARD]`（密文也视为卡数据）。
- 保存时校验：只能用于 CARD 字段；`publicKeyParam` 必须是已声明的 STRING 参数；算法只开放 `RSA_PKCS1_V15_BASE64`。
- PayBy 需要对 `cardNo`、`holderName`、`cvv` 三个字段使用；`expYear`、`expMonth` 为普通 CARD 字段。

### A2 有效期格式

PayBy 要求 2 位年份与 2 位月份。若 `PAYMENT_CARD` 返回 4 位年份，需提供 CARD 字段格式选项（如 `cardFormat: YY` / `MM`），或让 `PAYMENT_CARD` 同时返回两种写法。请在读卡合同中定一种。

### A3 渠道表单托管（3DS 跳转）

用户提出（2026-10-14）：提供一个函数封装渠道返回的 form，再对外返回我们自己的 URL。这样标准 API `PAY` 的 `nextAction` 保持 `REDIRECT` + URL，与 PayerMax 一致，调用方不需要处理渠道 HTML。

| 项 | 建议 |
|---|---|
| 函数 | RESPONSE 脚本函数 `hostChannelForm(html)` → STRING（我们的跳转 URL） |
| 解析 | 只接受恰好一个 `<form>`；`method` 为 GET/POST；`action` 必须是 https 绝对地址；只提取 `type=hidden` 的 `name/value`；字段数与长度设上限；其他内容（脚本、样式）一律丢弃。解析失败 → 节点 ERROR |
| 存储 | 结构化结果（action、method、fields）存 Redis，随机不可猜的 Token 作键，短 TTL（建议 15 分钟），不落库；内容不含卡数据 |
| 公开页面 | `GET /channel-redirect/v1/{token}`：由我们的模板生成自动提交表单，值全部 HTML 转义，`Cache-Control: no-store`、严格 CSP（只允许该 form action 与内联提交脚本的 nonce）；Token 过期或不存在 → 404 |
| 暴露 | 作为公开 CHECKOUT 入口经 Gateway 路由（Platform 的公开路由配置）；路由登记与启用 SQL 随发布评审 |
| 待定 | 是否一次性使用（建议首次打开后标记已用，刷新时返回同一表单，避免重复提交）；`returnUrl` 回跳与结果确认沿用卡直付方案（本期不做恢复，靠查询） |

### A4 毫秒时间戳函数 `nowEpochMillis()`

- 用户 2026-10-14 确认：PayBy 签名脚本里用 `nowRfc3339()` 换算毫秒的 15 行过渡写法过于复杂，改为注册函数。
- 脚本函数（SCRIPT 类），无参数，返回 INTEGER：当前 UTC Unix 毫秒数；与 `nowRfc3339()` 共用注入的 `Clock`。
- 改动点：`ScriptFunctions` 注册、`RegisteredFunctionCatalog` 登记（E30 编辑器目录随之出现）；`auto` 测试用固定 `Clock` 断言返回值，并经入口测试确认写入请求体后序列化为 JSON 整数。
- 与卡能力同批实现、同批发布，因为改的是同一批文件。发布后 PayBy 会话把 mor-test 的 `PAYBY_SIGN` 改为 `json.set(http.requestBody, 'requestTime', nowEpochMillis());` 加签名一行，并回归 6 个 UAT 用例。

## 3. PayBy 会话在能力可用后的工作

能力合入并发布到 mor-test（发布另行授权）后，PayBy 会话：

1. 配置原子 API `PLACE_ORDER`（DIRECTPAY）与 `PAY` 流程：`CREATED` + `threeDSecureDom` → RESPONSE 脚本 `hostChannelForm` → `paymentStatus=PENDING`、`nextAction=REDIRECT`；`PAID_SUCCESS/SETTLED` → `SUCCEEDED`；`FAILURE` → `FAILED`；拒绝码按 PayBy 技术方案 T3。
2. 沙箱验证账号录入测试卡，跑：下单得到我们的跳转 URL → 浏览器打开并在 PayBy 模拟页选择成功 → `PAY_QUERY` 为 `SUCCEEDED` → 部分退款 → 退款查询；另跑模拟失败、待定两种结果。

# PayBy 接入交付记录

## 1. 阶段划分

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；UAT 直连探测 | **已确认**（2026-10-14） |
| 2 | mor-test 配置（除 `PAY`）：新标准 API `PAY_CANCEL`、`BALANCE_QUERY`，接入方 `PAYBY`、2 个脚本、5 个原子 API、5 条流程、验证账号 | **已交付，待确认** |
| 3 | UAT 验证（除卡直付）：6 个用例 | **已交付，待确认** |
| 4 | DIRECTPAY 卡直付：Platform 卡能力（卡直付会话，[补充要求](./card-capability-addendum.md)）发布到 mor-test 后配置 `PAY` 并用测试卡验证 | 等待依赖 |

## 2. 阶段 1 结果（2026-10-14）

- **用户决定：** 毫秒时间戳采用脚本换算（不改代码、不发镜像）；范围包括查询、退款、退款查询、余额，并按官网补齐下单等接口。
- **已读：** 对接包全部文件（凭据只按结构读取，未输出）、PayBy 官网 7 个页面、PayerMax 接入/退款/沙箱验证的方案与 harness、引擎函数白名单与 Header 处理源码。
- **UAT 直连探测**（本机 curl + openssl，未经 Platform；私钥只在 scratchpad 0700 目录，探测后删除）：
  - `getOrder`、`refund/getOrder`、`refund/placeOrder`（原单不存在）分别返回 `62004`、`62007`、`62004`；`getBalance(AED)` 成功。
  - `PAYPAGE` 下单 AED 1.00 → `CREATED` + `tokenUrl`；查询 `CREATED`；关单 → `FAILURE`；重复关单 → `62002`。USD 下单 → `code=0`、`status=FAILURE`。测试单号前缀 `MORPROBE`，均为 UAT 测试单，已关闭或失败。
  - 业务响应用 `channelPublicKey_bak` 验签通过；`channelPublicKey` 不通过；时间过早、JSON 格式错误无签名（后续实测：部分 `applyStatus=ERROR` 带签名，见第 3 节）。
  - 毫秒换算算法 Python 离线核对 20 万个时刻，0 差异。
- **未执行：** 未改代码、配置、数据库或 mor-test；未提交。

## 3. 阶段 1 确认（2026-10-14）

- 用户：“接 DirectPay，我们以卡支付为主。其余的按照你的建议来”。T2～T7 按建议；T7 授权经 mor-test 管理接口写入配置、录入验证账号并运行 UAT 用例。
- **DIRECTPAY 探测**（直连 UAT，公开测试卡 5123 4500 0000 0008）：UAT 已开通；三项卡字段须用 `channelPublicKey_bak` 加密，用 `channelPublicKey` 返回 `400 parameter decrypt failed: cvv`；`threeDSecure` 为 true/false 都返回 `CREATED` + 自动 POST 的 HTML 表单（`sim-mock.test2pay.com`）。探测订单已全部关闭。
- **Platform 代码归属（用户选择）：** 卡字段加密等能力并入卡直付会话（其 Platform 阶段 2 正在修改同一批文件），本会话只写[补充要求](./card-capability-addendum.md)，不改 Platform 代码。
- **3DS 表单（用户提出）：** 由函数封装渠道 form，对外返回我们自己的 URL；`nextAction` 保持 REDIRECT。设计写入补充要求 A3。

## 4. 阶段 2、3 结果（2026-10-14，mor-test）

- **核对：** mor-test 16 个容器 healthy，入口 `http://localhost:28080` 200；原有标准 API 4 个（`PAY` 仍为收银台字段），接入方只有 `PAYERMAX`。管理员登录信息只从不提交的 `test-environment/private/access.md` 读入 scratchpad 0700 运行目录，未输出。
- **写入（[configure.py](./assets/harness/configure.py)，经 Nginx → Gateway → BFF → Platform）：** 标准 API `PAY_CANCEL`、`BALANCE_QUERY`；接入方 `PAYBY`（`PAYBY_SGS` 沙箱）；脚本 `PAYBY_SIGN`、`PAYBY_VERIFY`；原子 API `GET_ORDER`、`CANCEL_ORDER`、`REFUND`、`REFUND_QUERY`、`GET_BALANCE`；实现 `PAY_QUERY`、`PAY_CANCEL`、`REFUND`、`REFUND_QUERY`、`BALANCE_QUERY`，全部校验通过。`PAY_QUERY`/`REFUND`/`REFUND_QUERY` 标准 API 与 `PAYERMAX` 未改。保存时修正三处：固定值 Header 须先声明字段、数组父级逐层声明、关单 `62002` 无 body 时的输出表达式；修正期间前序项按版本做了内容不变的更新。
- **验证账号：** `UAT_MAIN`（`PAYBY` / `900000000000000003` / `UAT01`），参数 `partnerId`、`paybyPublicKey`，凭据 `merchantPrivateKey`，从仓库外对接包读取，未打印。
- **UAT 用例（[uat_cases.py](./assets/harness/uat_cases.py)）全部 PASSED：**`UAT_PAY_QUERY_NOT_FOUND`、`UAT_CANCEL_NOT_FOUND`、`UAT_REFUND_NOT_EXIST`、`UAT_REFUND_QUERY_NOT_FOUND`、`UAT_BALANCE_AED`（669.91 AED）、`UAT_UNPAID_QUERY_CANCEL`（种子单 PENDING → 退款 `62015` → 关单 CLOSED ×2 → FAILED）。毫秒时间换算被 PayBy 接受；所有带签名响应验签通过。回执：[test-uat-receipt.json](./assets/test-uat-receipt.json)（不含密钥与渠道订单号）。
- **发现：** PayBy 响应金额不补小数位（`1.0`），标准 API 原样输出，下游须按数值比较（技术方案第 8 节）。
- **泄露检查：** 本需求目录与 Platform 近 2 小时日志（2760 行）中私钥片段 0 命中。
- **UAT 测试单：** 前缀 `MORPROBE`、`MORSEED` 的订单均已关闭或失败。
- **未执行：** 未改代码、镜像、SQL；未提交、推送。

## 5. 时间戳函数（2026-10-14）

- 用户认为签名脚本的毫秒换算过于复杂，应放进代码。确认新增注册函数 `nowEpochMillis()`，写入[补充要求 A4](./card-capability-addendum.md#a4-毫秒时间戳函数-nowepochmillis)，与卡能力同批实现与发布（同一批文件正在由卡直付会话修改）。mor-test 现行脚本保留到发布后替换，再回归 6 个用例。

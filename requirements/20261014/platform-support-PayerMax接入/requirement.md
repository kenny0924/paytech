# PayerMax 收银台支付与交易查询接入（本机联调）

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14，标准 API 按用户意见改为 `PAY` / `PAY_QUERY`）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[外部 API 配置与执行需求](../../20260914/platform-support-外部API配置与执行/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)、[Platform 读取渠道账号决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)、[研发计划第 3 步](../../20260914/platform-support-外部API配置与执行/dev-plan-step3.md)
- 技术方案：[technique.md](./technique.md)；确认记录：[delivery.md](./delivery.md)

## 1. 背景

Platform 已完成外部 API 配置与标准 API 执行引擎（第 1～3 步），目前只用本机合成渠道验证过。PayerMax 是第一个真实渠道。用户已有 PayerMax UAT 商户账号，希望先在本机把 PayerMax 接进执行引擎并联调 UAT，确认引擎能承载真实渠道协议。

协议依据：PayerMax 官方 OpenAPI（[New Version v1.0 中文](https://docs.payermax.com/api.html?docName=New%20Version&docVer=v1.0&docLang=cn)）、[签名规则](https://docs.payermax.com/en/202606-version/developer/signature-rules.md)、[交易状态与错误码](https://docs.payermax.com/en/202606-version/appendix/collection/transaction-status.md)。

## 2. 目标

1. 引擎补齐真实渠道需要的通用能力：对最终发送字节签名、RSA 签名与验签、协议时间、JSON 数字金额。
2. 在 Platform 配置 PayerMax 接入方，实现两个标准 API：
   - **`PAY` 支付**：PayerMax 实现调用 `orderAndPay`（`integrate=Hosted_Checkout`），返回 `paymentStatus=PENDING` 与 `nextAction`（REDIRECT 到 PayerMax 收银台）。标准 API 不按收银台/纯 API 等接入形态区分，形态由渠道整合流程决定。
   - **`PAY_QUERY` 交易查询**：调用 `orderQuery`，返回归一化后的支付状态。
3. 本机直连 PayerMax UAT 走通：下单 → 浏览器打开收银台用测试支付方式付款 → 查询得到成功状态。

## 3. 业务场景

| 场景 | 期望 |
|---|---|
| 正常下单 | `executeStatus=SUCCESS`，`paymentStatus=PENDING`，`nextAction` 为 REDIRECT + 收银台地址，`channelTransactionRef` 为 PayerMax 流水号 `tradeToken` |
| 参数、合同、币种、限额等明确拒绝 | `executeStatus=FAIL`，返回 PayerMax 错误码与描述 |
| 系统繁忙、超时、未知错误码、验签失败、网络异常 | `executeStatus=ERROR`（结果未知）；调用方之后用交易查询确认，下单流程不补查、不冲正 |
| 查询已支付 / 待支付 / 失败 / 关闭 | `executeStatus=SUCCESS`，`paymentStatus` 分别为 `SUCCEEDED` / `PENDING` / `FAILED` / `CLOSED` |
| 查询订单不存在 | `executeStatus=FAIL`，`errorCode=ORDER_NOT_EXIST` |
| 账号材料读取失败 | 503 Problem `INTEGRATION_INITIALIZATION_FAILED`，保证没有请求 PayerMax |

## 4. 本期范围

- Platform `platform-support-integration` 执行引擎的四项通用能力，以及对应的保存时校验与 `auto` 测试。
- Operations 外部 API 编辑页能编辑新增的 `decimalEncoding` 选项（若评审点 T2 选择该方案）。
- PayerMax 两个标准 API、接入方、两个原子 API、签名与验签两个公共脚本、两条流程；以可重复运行的本机 harness 经真实管理接口保存。
- 本机联调 UAT：沿用 3e 的独立库 `mor_platform_external_api` 与测试装配 Vault Mock，UAT 账号材料只放在本机 0600 私有文件里。

## 5. 不做

- 支付结果异步通知（`collectResultNotifyUrl`，需要公网回调地址）、页面回跳处理。
- 纯 API 支付、前置组件、Auth-Capture、退款、订阅、付款等其他 PayerMax 接口。
- Payment 侧接入（外部 API 研发计划第 4 步）；标准 API 字段是本期草案，Payment 接入时可调整。
- 共享或线上环境的 SQL、Vault 策略、网络白名单与部署；提交、推送。

## 6. 验收标准

1. 新增引擎能力的 `auto` 测试通过，Platform `auto` 全量不回归。
2. 本机合成 PayerMax（真实 RSA 签名/验签与 PayerMax 报文格式）走通下单成功、明确拒绝、结果未知、验签失败、查询四种状态与订单不存在。
3. 本机直连 PayerMax UAT：下单拿到可打开的收银台地址；付款后查询返回 `SUCCEEDED`；PayerMax 侧验签通过（无 `SIGN_VERIFY_FAILED`），Platform 对响应验签通过。
4. 私钥不出现在仓库、数据库、Platform 日志与响应中。

## 7. 待确认

见 [technique.md 第 8 节](./technique.md#8-评审点)。

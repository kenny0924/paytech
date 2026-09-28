# 创建 Intent 接口修订

2026-09-16，按用户“开发创建 intent 接口，使用规范”的要求，在既有 EX-I01 范围内应用当前 [Payment 写接口规范](../../../payment-orchestration-service/docs/development/processor-conventions.md)。本修订只细化创建入口，取代旧 EX-I01 的独立命令幂等、来源 Binding、通用状态历史、到期补偿任务和 IntentView 响应要求；不改动其他接口的 Header 或退款、终止等业务合同。实际构建、数据库与部署状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 接口

`POST /payment-execution/v1/intents`，私有 HTTP；本地应用加 `/payment` Context Path。Commerce 完成订单及冻结事实检查后调用，Payment 不反向调用 Commerce。

请求身份直接使用 `sourceType + sourceId`，对应 `uk_payment_intent_source`，不需要额外 `Idempotency-Key`。当前来源类型只有 `COMMERCE_ORDER`，来源 ID 为平台内部 Order ID，不是 Client 自定义订单编号。唯一性不按 Client 拆分；原来源已绑定另一 Client 时拒绝，不返回对方的 Intent ID。

| 字段 | 类型 | 要求 |
|---|---|---|
| clientId | String | 必填，正十进制 Long，无前导零 |
| sourceType | String | 必填，COMMERCE_ORDER |
| sourceId | String | 必填，正十进制 Long，无前导零 |
| entityNo | String | 必填，Commerce 冻结的 OE；两位大写国家代码 + 001–999 |
| clientPricingVersionId | String | 必填，CCT 冻结的客户定价版本，正十进制 Long |
| partnerShareVersionId | String / null | 与 platformChargeVersionId 同时提供或同时省略；非空时为正十进制 Long |
| platformChargeVersionId | String / null | 与 partnerShareVersionId 同时提供或同时省略；非空时为正十进制 Long |
| industryCode | String | 必填，Commerce 冻结的行业代码，最多 64 位 |
| mccCode | String | 必填，四位数字 |
| consumerCountryCode | String | 必填，两位大写国家代码，由 Commerce 冻结 |
| amount | String | 必填，正普通十进制，最多 16 位整数、8 位小数，且符合币种精度 |
| currency | String | 必填，三位大写普通货币代码，由公共 Money 校验 |
| expiresAt | String | 必填，UTC RFC 3339，最多微秒；首次创建时尚未到期 |

所有 ID 上限为 `9223372036854775807`。不接收 `sourceOrderContextId`、`contextFormat`、支付工具、capture mode 或渠道字段；不从当前 Scope ID 猜测定价版本，不引入商户或国家的隐式默认值。可空 ID 不接受空串，金额不接受 JSON 数值、指数、前导零或隐式舍入。

请求示例（引用为合成数据，到期时间需替换为实际订单期限）：

```json
{
  "clientId": "101",
  "sourceType": "COMMERCE_ORDER",
  "sourceId": "201",
  "entityNo": "AE001",
  "clientPricingVersionId": "301",
  "industryCode": "RETAIL",
  "mccCode": "5732",
  "consumerCountryCode": "AE",
  "amount": "100.00",
  "currency": "AED",
  "expiresAt": "2026-09-30T10:00:00Z"
}
```

成功返回 HTTP 200、`Cache-Control: no-store`，Body 仅包含：

```json
{"intentId":"601","status":"OPEN"}
```

首次创建为 OPEN，重复创建返回原记录当前状态；不会因为当前时间已超过 expiresAt 就在本接口中改写为 EXPIRED。状态推进与到期处理属于后续入口。`intentId` 可供后续工具准备及 pay 使用，创建接口不宣称支付已受理或收款已成功。

## 处理顺序

```text
Body 字段校验 → Converter 解析 ID、Money、时间
  → Processor.validate：校验两项 Partner 定价版本的成组关系
  → AbstractApiLockProcessor：锁定来源类型 + 来源 ID
  → 按来源查询原 Intent
      已存在：比较 Client、金额/币种、OE、定价版本、行业/MCC、国家、到期时间
              → 一致则返回原 Intent 当前状态
      不存在：校验首次创建有效期
              → 构造 CreateIntentContext(PaymentIntent)
              → PaymentExecutionService 一次本地事务插入 Intent
              → 从 Context 组装响应
```

Money 规范化金额，`100` 与 `100.00` 表示相同金额；到期时间解析为 Instant，等价微秒表示按同一时刻比较。两项 Partner 定价版本的成组关系只依赖请求，首次与重放都在 `validate` 检查。原记录比较发生在新建有效期检查之前，已过期、已支付或已取消的原记录仍可返回，修改任何冻结事实则冲突；不能将首次创建的有效期资格放入锁前 `validate`。

锁 Key 为 `intent-source:<sourceType>:<sourceId>`，公共父类增加 `lock:` 前缀。不同来源独立竞争，不锁整个 Client；原记录只读返回，因此不修改或占用其 Attempt。未取得锁直接冲突，调用方重试原请求。数据库唯一键继续兜底，保存冲突向上抛出；不在失败事务中吞异常或返回未提交对象。

只写 `t_payment_intent`，沿用现有 20 个字段和索引。新 ID 由 Repository 使用公共 IdGenerator 分配，状态 OPEN、version=1、两个 Attempt 指针为空，创建/更新时间使用同一 UTC 微秒时刻。插入 SQL 再限制 expiresAt 晚于数据库当前时间，避免等待期间已到期仍落库；影响行数不为 1 则失败。

## 代码入口与后续边界

- [API](../../../payment-orchestration-service/paytech-payment-api/src/main/java/com/paytech/payment/api/execution/PaymentExecutionApi.java)与 [RequestBody](../../../payment-orchestration-service/paytech-payment-api/src/main/java/com/paytech/payment/api/execution/request/CreateIntentRequestBody.java)拥有路径和字段约束。
- [CreateIntentProcessor](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/processor/CreateIntentProcessor.java)负责原请求比较、业务验证及完整 Context 构建。
- [PaymentExecutionServiceImpl](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/impl/PaymentExecutionServiceImpl.java)集中本地事务；原 Intent Repository/Mapper 按表扩展，不接收 Context。

不增加表、DDL、Mock Intent 或外部查询；不修改当前 pay 及其 Header 合同。真实 Commerce 适配、CCT 资格接入、工具准备和到期状态推进保持各自 TODO；该接口不为这些能力提供默认通过的替身。后续接入需按当前字段传入真实冻结事实。未接通 Owner、未进行数据库/Redis/HTTP 联调时，不能将编译通过当作端到端验收。

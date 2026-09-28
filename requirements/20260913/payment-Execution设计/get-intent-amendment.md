# Intent 查询接口修订

用户确认按 Intent ID 查询，并补充返回来源业务类型与来源业务 ID。本修订取代 EX-I02 的旧 IntentView 投影；其他接口按各自后继合同处理，实现与验证状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 接口

```http
GET /payment-execution/v1/intents/{intentId}?clientId=101
```

应用 Context Path 为 `/payment`。接口供可信内部系统查询；`intentId` 和 `clientId` 必填，为无前导零的正十进制 long 字符串，最大 `9223372036854775807`。

成功返回 HTTP 200，携带 `Cache-Control: no-store`。基础字段不合法返回 400；Intent 不存在或不属于请求 Client，统一返回 404。错误复用公共 Problem 合同。

## 响应

| 字段 | 含义 |
|---|---|
| intentId | 支付意图 ID，字符串 |
| sourceType | 来源业务类型，当前为 COMMERCE_ORDER |
| sourceId | 来源业务 ID，字符串；COMMERCE_ORDER 对应 Commerce 内部订单 ID |
| status | 当前持久化状态：OPEN、SUCCEEDED、CANCELLED 或 EXPIRED |
| activeAttemptId | 当前活动尝试 ID，字符串；没有时为空 |
| successfulAttemptId | 成功收款尝试 ID，字符串；尚未成功收款时为空 |

```json
{
  "intentId": "601",
  "sourceType": "COMMERCE_ORDER",
  "sourceId": "201",
  "status": "OPEN",
  "activeAttemptId": null,
  "successfulAttemptId": null
}
```

来源字段直接取自创建时冻结的 Intent，`sourceId` 不是 Client 自定义订单号。响应只包含以上六个字段，不沿用旧 IntentView 的 clientId、observedAt 或其他扩展投影。

`OPEN` 可以表示尚未支付、支付处理中或授权后等待捕获。上游不能将它直接解释为支付失败，也不能仅凭活动指针为空就判断下一次 pay 一定可以受理。GET 只返回已保存事实，不因查询时已过期而写入 EXPIRED。

## 调用与表影响

Controller → Converter → Execution 的 GetIntentProcessor → PaymentIntentRepository.findIntent。

Processor 从同一条 Intent 记录检查 Client 归属并组装响应，仅查询 `t_payment_intent`。不读取 Attempt/Action，不需要资源锁、事务、额外 Context 或多表 View，不推进支付流程。内部 Request/Response 保留模块边界，DataObject 不进入 HTTP 合同。

本轮验证沿用用户暂缓单元测试的安排，执行编译、差异与文档检查；真实 HTTP/数据库验证状态以当前台账为准。

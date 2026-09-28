# applyCardInstrument 工具申请接口

2026-09-16，按用户指定名称补齐当前工具申请入口，使用 [Payment 写接口规范](../../../payment-orchestration-service/docs/development/processor-conventions.md)。实现、验证和未完成能力以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)为准；本文件说明本次接口和代码组织，不表示真实 Card 或资金链已接入。

## 接口与边界

`POST /payment-instrument/v1/card-tokens`，本地应用完整路径为 `/payment/payment-instrument/v1/card-tokens`。Java 方法名为 `applyCardInstrument`；仅可信内部服务使用，不注册公共 Gateway 路由。

本次采用 Execution 的专用 `ApplyCardInstrumentProcessor`，按 Client、Intent、来源提交绑定工具；卡输入封装为 Card 模块的 `CardInput`，通过专用请求在内存中交给 CardService。Execution 不读取卡号/CVV 字段，不计算卡身份摘要，不将卡输入放入持久化 Context、数据库、日志或任务。通用 pay 不接收卡输入。Card 身份检查、记录引用与真实用卡材料均由 Card 拥有。

这精确调整了前序工具稿中“Web Processor 编排”和“任何 Execution 应用模型都不携带卡输入”的代码边界；Execution → Card 只依赖公开的 CardService 和领域参数，Card 不反向依赖 Execution，不共享 DataObject。前序 Card 加密、受控访问及真实材料边界继续保留。

| 请求字段 | 类型与约束 |
|---|---|
| clientId | 正十进制 Long 字符串，无前导零，与 Intent 归属一致 |
| intentId | 正十进制 Long 字符串，已创建的支付意图 |
| sourceSubmissionId | 正十进制 Long 字符串，与后续 pay 相同；换卡使用新提交 |
| paymentMethodCode | 1–64 位大写字母、数字或下划线，以大写字母开头；Card 验证具体方式 |
| card.pan | 12–19 位数字 |
| card.expiryMonth | 01–12 |
| card.expiryYear | 四位年份 |
| card.cardholderName | 非空，最多 128 位，无控制字符 |
| card.cvv | 3–4 位数字，只在本次处理使用，不参与卡身份比较 |

全部字段必填，ID 上限为 `9223372036854775807`。基础格式由 Body 注解和 `@Valid` 校验；接口拒绝未知字段。当前接口不需要额外幂等 Header、卡身份查询接口、摘要、keyRef、allowPreparation、会话 ID 或法律接受字段。期限由服务端确定，不接受客户端续期或 sourceExpiresAt；本切片的期限依据是 Intent、配置窗口及 Card 能力，不宣称核验了 Commerce 提交回执的独立期限。

HTTP 200、`Cache-Control: no-store`，返回 `paymentInstrumentToken` 和 UTC `expiresAt`。二者均来自实际新建或匹配的原工具记录，不回显卡信息和技术状态。

## 处理顺序

```text
Body 校验 → Converter → Processor 检查入口无事务 → 锁原 Intent
  → 查询 Intent，核对 Client
  → 按 Client + Intent + sourceSubmissionId 查询原工具
      已存在：比较方式/类型 → Card 核对原卡身份 → 返回原 Token、原期限
      不存在：检查 Intent 与来源提交资格 → 完整构造 Context
              → Processor 开启一次本地事务
                   → CardService 本地申请，取得引用与支持期限
                   → 绑定 Card 结果到待保存 Token
                   → Execution Service 保存工具绑定
                 一起提交或回滚
              → 从同一 Context 返回 Token、期限
```

- Context 只持有 PaymentIntent 和 PaymentInstrumentToken，Repository/Mapper 不接收 Context。
- 新申请要求 Intent 为 OPEN、尚未到期、没有活动或成功 Attempt；来源提交不能已用于 Attempt。
- 重复请求先比较原工具，再决定返回，不受 Intent/Token 当前到期、撤销或支付终态阻断。不重新准备材料，不创建替代 Token，不延长期限；Card 无法核对身份时返回不可用，不盲目返回工具。
- 同一工具不能更换支付方式或卡身份；CVV 不参与身份比较，也不写入任何表。新输入只被核对，不等于原工具材料被重新保存。
- Token 使用 32 字节 SecureRandom 后 Base64 URL 编码，不能由卡号或业务 ID 推导。工具内部 ID 由 Repository 用公共 IdGenerator 分配。
- 初始 expiresAt 为 `min(Intent.expiresAt, 当前时间 + 配置窗口)`，Card 可以进一步缩短，不能延长；统一为 UTC 微秒。窗口未配置为正值时仅拒绝新申请，原工具重放沿用原期限。
- 只写现有 `t_payment_instrument_token` 的 12 列，不改表或索引。插入 SQL 再检查 Intent 归属、OPEN/期限/Attempt 指针、Token 期限及来源未用于 Attempt，唯一索引防重；保存失败不组装成功结果。
- Processor 负责组合两个 Service，Service 不互相调用，也不独立提交。Card 或 Token 保存失败时整个本地事务回滚，本次候选 Context 丢弃；调用方使用原提交身份重试。
- 当前 Card 为纯本地合成替身，未写真实卡记录。未来 Card 保存使用同一数据源与事务管理器参与此事务；KMS 等外部准备由 Card Processor 在事务外完成，不放进 CardService。未接入的外部能力不预建空 Processor、清理任务或新会话表。

## 本地 Card Mock

真实 Card 尚未实现；默认 `payment.card.mock-enabled=false`，调用返回 SERVICE_UNAVAILABLE。只在本地合成演示时显式设置：

```text
PAYMENT_CARD_MOCK_ENABLED=true
PAYMENT_INSTRUMENT_PREPARATION_WINDOW=PT5M
```

`PT5M` 是示例配置，不是默认业务期限。Mock 只接受下面的公开合成卡和 CARD_TEST，不接受任意输入并伪造可用卡记录。它返回按原提交范围确定的 `mock-card:` 引用，不保存 PAN/CVV、不计算其摘要、不模拟真实材料缓存；真实加密、材料领取、清理及资金适配仍为 TODO。

```json
{
  "clientId": "101",
  "intentId": "601",
  "sourceSubmissionId": "401",
  "paymentMethodCode": "CARD_TEST",
  "card": {
    "pan": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2030",
    "cardholderName": "MOCK CARD",
    "cvv": "123"
  }
}
```

引用 ID 需替换为真实的本地合成 Intent/提交。取得 Token 后，调用当前 pay，使用相同 Client、Intent、sourceSubmissionId、paymentMethodCode；pay 的 `Idempotency-Key: submission:<sourceSubmissionId>` 保持不变。Risk/Channel 仍未接入，能申请合成工具不代表资金支付可完成。

## 代码导航

- [ApplyCardInstrumentProcessor](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/processor/ApplyCardInstrumentProcessor.java)：完整业务顺序及跨 Service 的单事务组合。
- [PaymentInstrumentController](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/ext/rest/instrument/PaymentInstrumentController.java)：请求转换、Processor、响应转换。
- [PaymentExecutionServiceImpl](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/impl/PaymentExecutionServiceImpl.java)：参与 Processor 事务，只保存 Execution 的工具绑定。
- [CardService](../../../payment-orchestration-service/paytech-payment-card/src/main/java/com/paytech/payment/card/service/CardService.java)：Card 模块公开合同。

本稿按本次入口范围取代前序准备工具的独立命令表、Hash、Binding、补偿清理、allowPreparation、sourceExpiresAt 和 `PrepareCardInstrument` 名称要求；不据此修改其他接口或宣称真实 Card 已通过验收。

2026-09-16 事务边界修订取代此前“事务外申请 Card、之后单独保存 Token”的顺序。当前按 Processor 组合本地 Service，共用一个事务；其余请求、响应、重放和敏感数据边界不变。

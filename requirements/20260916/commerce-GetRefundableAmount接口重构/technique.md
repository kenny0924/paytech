# 可退款金额查询技术说明

遵循[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)和[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。

## 调用与数据

`ClientRefundController.getRefundableAmount` → `GetRefundableAmountConverter.request` → `GetRefundableAmountProcessor.process` → `GetRefundableAmountConverter.response`。

1. HTTP 保留身份 Header 正数及订单号必填、长度约束；内部 `GetRefundableAmountRequest` 只接收 Client ID 和订单号，不增加 GET Body。
2. Processor 继承 Foundation `AbstractProcessor`，通过 `CommerceOrderRepository.findByOrderNo` 查询当前 Client 的订单；未命中即返回 `ORDER_NOT_FOUND`，不继续读取退款位置。
3. 通过 `OrderRefundPositionRepository.findByOrderId` 读取原表对象，确认位置存在且与订单币种一致，复用其 `validateAmounts()` 和 `refundableAmount()`。缺失位置不能被解释为零额，不复制一套金额校验器。
4. 计算结果直接传入 `Money.of(...).toAmountString()`，不新增文本往返解析或 try/catch。金额、asOf、version 来自同一位置记录；订单只提供身份和固定币种。
5. 内部 `GetRefundableAmountResponse` 与 API `response/GetRefundableAmountResponseBody` 均使用普通 class，Converter 保留既有版本数值转换及五个公开字段。

两次读取不依赖可变订单状态与退款位置的一致快照，允许观察相邻的已提交状态；金额组成来自同一行。不增加事务、锁、Context、空 Service 或外部步骤，不为响应重新加载数据库对象。写接口仍自行保护并发额度。

## 清理与兼容

删除只服务旧入口的 `OrderQueryService/Impl`、领域 `RefundableAmount`、`OrderWireConverter`、API `RefundModels` 容器以及 `OrderRefundableAmountView`。移除旧 `OrderRepository.refundableAmount` 和对应 Mapper 方法、结果映射及联表 SQL；其他旧查询链路不在本次范围。

公开 HTTP 路径、字段和金额单位保持，Java 响应类型与 Controller 构造器发生变化；旧类型和装配相关测试需后续适配。本次复用现有单表查询，无新增表、字段、索引或迁移 SQL，数据库和 PDManer 不涉及。

后续验证应覆盖：未知/跨 Client 订单、尚未支付的零额、预占与成功退款扣减、退款位置缺失或币种不一致、完整退款后的零额、无 Session 的订单，以及金额格式与 asOf/version 同行读取。本轮不执行测试或验证，当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refundable-amount-20260916)。

# 业务退款号查询技术说明

## 2026-09-21 金额故障分类修订

本库退款的币种或金额精度不合法，按[开发规范第 4 节](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#4-异常与枚举)属于系统错误，与公开退款号查询保持一致。Processor 仅在 `Money.of(...).toAmountString()` 处将 `InvalidParameterException` 转换为保留 Cause 的 `ErrorException`，对外返回 500 / INTERNAL_ERROR；HTTP 请求参数错误仍由原入口校验返回 400。没有重复解析金额、新增校验器或扩大异常捕获范围。

本条取代下方初始方案第 4 步的“不捕获包装异常”，其他查询职责保持；测试、失败复现和验证记录见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-by-client-id-tests-20260921)。

规则沿用[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)和 [Client API 设计第 11.3 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#113-查询-refund)。

## 调用链

`ClientRefundController.getRefundByClientRefundId` → `GetRefundConverter.requestByClientRefundId` → `GetRefundByClientRefundIdProcessor.process` → `GetRefundConverter.response`。

1. HTTP 入口保留 Client / Credential 正数及 clientRefundId 必填、长度和字符集约束，不增加 GET Body。内部输入为普通类 `GetRefundByClientRefundIdRequest`。
2. Processor 继承 `AbstractProcessor`，复用 `RefundRepository.findByClientRefundId`，已有 SQL 按 `client_id + client_refund_id + request_origin=CLIENT` 单表查询，直接返回 `RefundRequest`。未命中即返回 `REFUND_NOT_FOUND`。
3. 复用 `CommerceOrderRepository.findOrderNoByOrderId`，只读同一 Client / orderId 的公开订单号；保持原联表查询对不可见关联订单返回退款不存在的语义。
4. 复用退款对象的 `validateFacts()`，原 BigDecimal 直接传入 `Money.of(...).toAmountString()`，不重复解析或捕获包装异常。
5. 输出已有普通类 `GetRefundResponse`，经现有 Converter 输出 `GetRefundResponseBody`。两个详情接口本来就返回相同资源表示，因此不复制一套响应 DTO。

退款状态、金额、时间和版本来自同一行读取，第二条 SQL 只补充不可变订单身份；没有一致性快照要求，不增加事务或锁。无写入、恢复或外部步骤，不建立 Context 或空 Service。沿用现有字段白名单、显式 null 和主单位金额格式。

## 清理和兼容

- 删除失去生产调用方的 `RefundService.get`、其只读事务、`view/money` 私有方法，以及领域 `RefundModels.View`。
- 删除旧 API `RefundModels.RefundResponseBody` 和 Controller 的 `wire`；保留尚被可退金额入口使用的 `RefundableAmountResponseBody`。
- 提交 Handler 仍需要 `RefundRequestView` 和原查询，保留这些类型及 SQL，不借本次查询重构调整异步写流程。
- 不新增 SQL、索引、表、配置或其他系统改动。HTTP JSON 兼容；Java 返回类型和 Controller 构造器有变化，相关测试需后续适配。

没有运行测试、构建或验证；当前实现状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-by-client-refund-id-20260916)。

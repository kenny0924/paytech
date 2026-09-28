# 退款详情查询技术说明

## 权威与兼容

分层和对象遵循[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)与[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。业务字段沿用 [Client API 设计第 11.3 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#113-查询-refund)。本次不改变业务状态、数据所有权、HTTP 路径或 JSON 字段。

## 查询链路

`ClientRefundController.getRefund` → `GetRefundConverter` → `GetRefundProcessor.process` → Repository → Mapper XML。

1. 入口保留 Jakarta Validation 的 Client / Credential 正数以及退款号必填、最长 64 字符约束。Converter 构造普通类 `GetRefundRequest`，不让协议对象进入 Processor。
2. Processor 通过现有 `RefundRepository.findByRefundRequestNo(clientId, refundRequestNo)` 单表读取 `RefundRequest`；未命中返回 `REFUND_NOT_FOUND`，不继续查订单。
3. 新增 `CommerceOrderRepository.findOrderNoByOrderId(clientId, orderId)`，SQL 只选择 `order_no`。缺失或不属于同一 Client 时保持原 INNER JOIN 未命中的 `REFUND_NOT_FOUND` 语义。
4. 调用退款对象的 `validateFacts()` 检查已加载事实，将原 `BigDecimal` 直接传入公共 `Money` 输出金额，不再转成文本后重复解析。用户随后删除了金额转换的 try/catch，当前直接交由统一异常处理。
5. 从原退款及订单号构造 `GetRefundResponse`，Converter 输出普通类 `GetRefundResponseBody`。枚举转协议代码，版本沿用原 int 映射，可空字段仍明确输出 null。

退款状态、时间、金额和版本来自同一次退款读取；第二次读取只补充不可变的订单身份，不依赖另一次可变状态读取。因此不需要显式一致性事务或锁。不存在保存、重放或外部步骤，不新增 Context、Service、恢复任务或幂等逻辑。

## 改动边界

本入口不再使用 `RefundService.get`、`RefundModels.View` 或 `RefundRequestView`。按 Client 业务退款号查询在本轮结束时仍使用旧链路，随后由[后继重构](../commerce-GetRefundByClientRefundId接口重构/technique.md)迁移并清理旧 Service 查询及领域 View；提交 Handler 使用的持久化查询不在这两个详情入口的重构范围内。

数据库表、索引、PDManer 和其他服务不变，不涉及 SQL 迁移或 Gateway 配置。公开退款号输入不收窄，历史编号可继续查询。JSON 兼容，但 Java 返回类型和 Controller 构造器变化，既有测试及 Java 调用方需要按新类型适配。

## 验证边界

遵循用户要求，本轮不修改单元测试、不运行构建或验证。后续覆盖 Client 隔离、Operations 来源、未知/孤立退款、无 Session、各退款状态、金额精度和 nullable JSON，以及只读查询无写入或外部调用。不能引用此前测试证明本次代码已通过。

实现与交付状态统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-20260916)。

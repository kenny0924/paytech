# 订单退款列表技术说明

规则来源：[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[Java 工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。列表排序、字段及 Cursor 合同继续使用[Client API 设计第 11.4 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#114-order-refund-列表)。

## 调用链

`ClientRefundApi` → `ClientRefundController` → `ListOrderRefundsConverter` → `ListOrderRefundsProcessor` → 本模块 Repository/Mapper，结果经内部 Response 和 Converter 转成 `ListOrderRefundsResponseBody`。HTTP 参数仍为原 Path、Header、Query，不新增 GET Body。

- Processor 继承 Foundation AbstractProcessor，标准字段约束仍由 HTTP 入口负责。
- 先用 CommerceOrderRepository.findOrderIdByOrderNo(clientId, orderNo) 只取订单主键，建立可信归属和分页范围。
- 首屏不查游标。续页用 RefundRepository.findByRefundRequestNo 读取本表 RefundRequest，核对 Order 与 createTime；不加载关联订单或支付数据。
- 分页 SQL 只查询 t_refund_request，用可信 clientId + 已确认 orderId 限定范围；返回实际数据库对象，不使用联表 RefundRequestView。
- 多取一条仅用于判断还有数据，返回列表截取到 limit。所有返回行执行已有 RefundRequest.validateFacts 和公共 Money 精度校验，输出既有白名单字段；reasonSummary、内部 ID、channelRefundId 均不暴露。
- 金额直接使用原 BigDecimal 构造 Money，不转成文本后重复解析。仅将 Money 的 InvalidParameterException 转为系统错误，避免本库数据异常被公共映射器返回为客户端参数错误。
- Converter 将内部枚举映射为接口代码，将退款项转换为普通类 RefundInfo。列表字段、退款表示 version 及显式 null 保持，不借本次结构重构单独改变退款查询合同。

## 查询与事务

首屏两次单表读取，续页增加一次锚点读取。Order 归属和退款的 orderId/createTime/refundRequestNo 为该分页使用的不变事实，退款状态可以独立更新；每页允许观察相邻已提交时点，不需要为展示列表建立显式事务或跨页快照。没有写入或外部步骤，不创建空 Service、额外 Context、资源锁或恢复机制。

移除 RefundService.list 及其只读事务，删除不再使用的 domain Page 和 API RefundListResponseBody 容器；其他退款详情与 Handler 流程保持各自现状。RefundCursor 只整理注释、分支括号及输入异常范围，保留 r1 字节编码和原锚点语义。

没有 DDL、索引、PDManer、配置或其他系统变更。旧 Java 返回类型的调用方与测试需要适配新普通类；测试本轮不处理，未来恢复验证需覆盖分页边界与权限隔离。当前实现和实际验证范围见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-list-order-refunds-20260916)。

# 按公开退款号查询接口重构

用户指定继续按接口规范重构 `ClientRefundApi#getRefund`。本次只调整 Commerce 的查询分层、按需读取和对象形态，保留现行退款查询合同。

## 范围

- GET `/client-gateway-api/v1/refunds/{refundRequestNo}` 保留可信 Client、Credential Header 和原路径参数约束，不增加 GET Body。
- Controller / Converter 仅转换和委托；Processor 继承 Foundation `AbstractProcessor`，负责本地查询与业务校验。
- 按可信 Client 查询退款，再仅取关联订单的公开编号；不加载订单聚合、Checkout Session、支付或资金投影，不调用外部服务。
- 不创建显式事务或锁。内部 Request/Response 和独立 ResponseBody 使用普通 class，不增加没有职责的 Context 或 Service。
- 保留金额主单位字符串、状态、来源、时间、表示版本、显式 null，以及未知或跨 Client 返回 `REFUND_NOT_FOUND` 的合同。
- `getRefundByClientRefundId`、退款创建、列表和提交 Handler 不在本次迁移范围内。

## 验收目标

能查询当前 Client 的 Client / Operations 来源退款；其他 Client 和未知退款不能被读取。没有 Checkout Session 也能查询退款。退款金额按币种精度输出，不暴露内部主键、渠道身份或原因原文。查询不产生写入或推进退款。

用户此前要求暂不处理单元测试、不运行验证；本次不含构建、测试、数据库操作、提交或部署。交付状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-20260916)。

# 按 Client 业务退款号查询接口重构

> 后继修订：金额故障分类按[技术说明的 2026-09-21 修订](technique.md#2026-09-21-金额故障分类修订)处理，取代下文“不增加 try/catch”的对应限制；仍不重复解析金额或增加校验器。其余内容保留本次初始重构范围，测试及实际验证状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-by-client-id-tests-20260921)。

用户指定继续按接口规范重构 `ClientRefundApi#getRefundByClientRefundId`，沿用已确认的查询分层和普通 class 规则。

- 保留 GET `/client-gateway-api/v1/refunds?clientRefundId=...`、可信身份 Header、Query 约束及原 JSON 字段。
- Controller / Converter 只转换和委托，Processor 按需查询本库 Repository；不建立显式事务、锁或额外 Context。
- 仅匹配当前 Client 的 CLIENT 来源退款。未知、跨 Client 或所属订单不可见继续返回 `REFUND_NOT_FOUND`。
- 读取退款本表与关联订单号，不依赖 Checkout Session、支付或资金投影，不调用外部服务或推进退款。
- 与公开退款号查询共用详情响应。按用户最新要求，金额直接使用公共 Money，不重复文本解析、不增加 try/catch。
- 清理迁移后失去生产调用方的旧查询 Service 方法、View、响应 record 和 Controller 组装逻辑。

后续验收应覆盖 Client 命名空间隔离、同业务号不同 Client、未知退款、Operations 不匹配、终态/处理中、可空字段、金额格式及无 Session 查询。本轮按此前要求不处理测试、不运行构建或验证，也不提交或操作数据库。

# 可退款金额查询按接口规范重构

用户要求继续重构 `ClientRefundableAmountApi.getRefundableAmount`。本次只调整 Commerce 查询链路，沿用此前确认的 Processor 按需读取、Controller 仅适配、普通 class 对象和不增加查询事务的约定。

- 保持 GET 路径、Path/Header 参数及 JSON 字段 `orderNo/currency/refundableAmount/asOf/version`。
- 先按可信 Client 确认订单归属，未知与跨 Client 订单统一返回 `ORDER_NOT_FOUND`。
- 可退金额等于退款基数减去预占和成功退款；查询不预占，创建退款仍按写入合同校验。
- 只读取本地订单及退款位置，不依赖 Checkout Session、支付或资金投影，不调用外部服务。
- 清理该入口退出后失去消费者的旧 Service、模型、转换器及查询 SQL。

不修改 Gateway、其他系统、数据库结构或 PDManer。沿用用户要求，暂不处理测试，不运行构建或验证，不提交代码。业务合同见 [Client API 设计第 11.1 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#111-查询可退款金额)。

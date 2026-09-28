# Client Checkout 查询合并

> **2026-09-16 后续确认：** [响应结构修订](response-shape-amendment.md)取代下文平铺 Checkout 字段、外部版本及无数据库变更的描述：新增持久化 creationMode、checkout 归组，并移除订单查询两处 version。下文保留初次合并的授权记录。

2026-09-16 用户确认取消 ClientCheckoutApi#getCheckout，按前述方案由订单查询承接支付链接与准备状态。

- 删除 Client 独立 GET /orders/{orderNo}/checkout，不保留别名。
- 按 orderNo 或 clientOrderId 查询订单均返回 checkoutUrl、preparationStatus、amountStatus。
- 创建响应丢失后按原 clientOrderId 找回订单和原支付链接，不创建替代订单或新 Session。
- Customer 收银台加载、创建 Checkout、取消订单与其他系统业务合同不变。
- 不修改数据库模型、执行 SQL、配置 Gateway 或部署；按用户要求不运行测试或验证。

技术边界见 [technique.md](technique.md)，确认记录见 [delivery.md](delivery.md)。

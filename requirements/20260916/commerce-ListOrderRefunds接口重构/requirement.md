# Commerce 订单退款列表接口重构

用户指定按前序接口规范重构 `ClientRefundApi#listOrderRefunds`。本轮直接处理该查询入口和必要调用链，只修改 Commerce 应用及关联文档；不改变外部 JSON、排序、金额或权限合同，不包含测试、构建、数据库操作、提交或部署。

- Controller 只转换协议并委托 Processor；顶层 ResponseBody 和嵌套 RefundInfo 使用普通 class。
- 查询在 Processor 中逐项完成，按需读取本表数据库对象或单个身份值，不开显式事务、不加锁。
- 未知和跨 Client 的订单返回 ORDER_NOT_FOUND；无退款的有效订单返回空列表。
- Cursor 保留原 r1 编码及公开创建时间/退款号，仅允许当前 Client、当前 Order 的真实锚点。
- 默认 limit=20、范围 1–100；按 create_time、refund_request_no 倒序，多取一条判断下一页，游标取实际返回页的最后一条。
- 列表同时包含 Client 与 Operations 退款，支持没有 Checkout Session 的订单；不调用 Payment 或推进任务。

验收预期覆盖首屏、空页、末页、相同创建时间、非法/跨归属游标、未知订单、旧与新格式退款号及敏感字段不外露。按用户此前要求，测试与验证暂缓。当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-list-order-refunds-20260916)。

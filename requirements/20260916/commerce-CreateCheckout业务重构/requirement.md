# CreateCheckout 业务重构

用户确认：本次从 CreateCheckoutProcessor 移除 Key、租约和恢复阶段逻辑，只保留 clientOrderId 业务防重；Gateway 暂不修改。

- HTTP 保留 Idempotency-Key 的现有必填和格式校验，内部业务 Request 不携带 Key。
- 同 Client 的 clientOrderId 永久唯一，重复创建返回 CLIENT_ORDER_ID_ALREADY_EXISTS（409）。不同订单号不因相同 Key 冲突。
- Processor 负责金额、期限、业务订单号、CCT 资格预检、Context 和响应；Service 以 REQUIRED 事务原子保存订单、商品、退款仓位、资金投影和 Session。
- 事务内只操作数据库；不选 OE，不创建 Payment。失败不冻结创建响应，不做创建命令恢复。
- 创建国家必须成为订单事实，订单查询、Customer 读取及准备不能依赖新创建命令记录。
- 历史订单保持不变，旧创建快照仅作历史读取；Cancel、Refund、Customer 命令机制不在本次删除范围。

方案见 [technique.md](technique.md)，交付状态仅见 [当前实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-business-20260916)。

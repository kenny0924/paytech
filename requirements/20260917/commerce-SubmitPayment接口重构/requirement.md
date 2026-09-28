# Customer SubmitPayment 接口重构

用户要求按接口规范重构 `CustomerPaymentApi#submitPayment`，并明确要求移除仍残留的请求幂等逻辑。此次修订取代上一轮保留支付 Key、Hash 和原响应重放的说明。

- Controller/Converter 只做协议适配；RequestBody、ResponseBody、Context 和 DB 对象使用普通类。
- 支付接口不要求 Idempotency-Key，不计算卡指纹或请求 Hash，不认领命令、不保存或重放响应快照。
- 每次请求按当前状态校验并生成新的支付业务编号；submissionId 用于 Payment 来源绑定和查询，不再引用命令表。
- Service 原子保存订单版本与法律接受事实；事务内只操作本库，Payment 提交与公开查询在事务外。
- 复用同合同的三份法律接受；订单版本、状态及数据库唯一约束继续保护本地并发。Payment 负责 Intent 在途、成功及重复扣款保护。
- 查询接口脱离命令表；移除公开 retryIdempotencyKey，取消同键补卡/重放语义。
- 用户进一步确认删除 CheckoutPaymentQueryHandler 及其事件登记，不以无结果落地的后台查询充当业务补偿。

范围仅为 Commerce 及直接关联文档。准备接口仍使用原命令机制；不删除共享表或历史记录，不修改其他系统、Gateway、前端或自动准备交互。接受表历史命令列需要放宽可空，提供人工 SQL，不执行数据库变更或修改 PDManer。随删除功能移除专属测试，其余测试不调整，不执行构建或验证，不提交或部署。

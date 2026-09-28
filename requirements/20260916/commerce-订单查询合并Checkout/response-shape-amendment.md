# 订单查询响应与创建方式修订

日期：2026-09-16。用户已确认响应结构并要求实现；本修订精确取代同目录前序技术稿中的平铺 Checkout 字段、订单查询响应版本和“不涉及数据库变更”的边界。

## 已确认合同

两个订单 GET 使用同一 GetOrderResponseBody，路径、可信身份和查询参数不变：

- 删除根级 version 和 fundsAvailability.version。数据库 Order、Session、Payment/Refund/Funds 投影的版本及 CAS 保护保留。
- 将根级 checkoutStatus、checkoutUrl 改为 checkout.status、checkout.url，并在 checkout.expiresAt 返回会话期限。
- checkout 字段始终出现，可为 null；HOSTED_CHECKOUT 订单缺少 Session 仍是数据错误，不能伪装成可接受的 null。其他创建方式按实际关联决定是否存在 checkout，不从模式反推。
- 链接到期后为 null；完成支付但尚未到期时仍可展示结果。当前 Session 与 Order 使用同一期限，订单根级 expiresAt 继续表达订单期限。
- preparationStatus、amountStatus、paymentStatus、商品、金额、退款及资金摘要仍位于订单层；不通过字段重组改变业务状态或期限。
- 新增根级 creationMode，固定记录创建时的接入方式；当前值为 HOSTED_CHECKOUT，预留 DIRECT_PAY。API 以 String 返回稳定 Code，发布 OrderCreationMode 枚举定义，不接收 Client 输入或提供修改接口。
- Order 对应零个或一个 Payment Intent，重试使用同一 Intent 下的 Attempt；本修订不修改 Payment 的来源唯一约束。

[完整支付成功示例](order-paid-response.json)展示已支付、无退款、资金尚未可用的响应；示例标识和链接为占位值，不是真实订单。

## 实现边界

GetOrderProcessor 从 Order.creationMode 读取事实，GetOrderConverter 只进行 HTTP 字段转换，GetOrderResponse 不携带两处内部版本。Checkout 创建入口在上下文构建时显式赋值 HOSTED_CHECKOUT，经原本地事务保存。MyBatis 显式注册 Foundation CodeEnumTypeHandler，未知数据库 Code 不默认降级。

仅放宽订单查询对非 Hosted 模式缺少 Session 的表示；不新增 Direct Pay 创建/支付接口，不改其尚未设计的消费者资料、接受证据或准备流程。后续[CancelOrder 修订](../commerce-CancelOrder接口重构/technique.md)已单独调整取消对可空 Session 的处理；模式值及该兼容不表示 Direct Pay 创建或支付入口已可用。

## 数据库变更

`t_commerce_order` 在 client_order_id 后增加 creation_mode VARCHAR(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT'。现存订单来自已开放的 Hosted Checkout，迁移后使用此默认值；原金额、状态、版本、Payment 关联和 Session 坐标不修改。字段创建后不可变，无新增索引、CHECK 或物理外键。

- PDManer、生成器元数据和目标全量 Schema 同步维护。
- 人工增量：[db/creation-mode.sql](db/creation-mode.sql)。本文件只增加 creation_mode，不替代其他未执行的结构迁移。
- 发布依赖：先在获授权的 Commerce 数据库增加列，再运行新创建/查询代码；本任务没有授权应用 SQL。

## 交付与验证

用户确认本次直接调整既有实现，按此前要求暂不处理单元测试、不运行测试、构建或验证；旧 record 测试及新增字段的数据库场景留待后续。未提交、推送、配置 Gateway 或部署。代码、模型和数据库执行状态以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-order-response-shape-20260916)为准。

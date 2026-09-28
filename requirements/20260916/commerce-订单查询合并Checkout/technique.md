# 技术边界

> **2026-09-16 后续确认：** [响应结构修订](response-shape-amendment.md)取代下文平铺 Checkout 字段、外部版本及无数据库变更的描述：新增持久化 creationMode、checkout 归组，并移除订单查询两处 version。下文保留初次合并的授权记录。

Client 服务路径由 /commerce/client-gateway-api/v1 提供；OpenAPI 的 /api/v1 继续作为公共映射合同，本次不激活 Gateway。

两个订单 GET 共用 OrderResponseBody，在已有 checkoutStatus、amounts、expiresAt 之外补充：

| 字段 | 类型 | 语义 |
|---|---|---|
| checkoutUrl | string / null | Session 原 publicOrigin + /c/ + publicToken；now >= expiresAt 时为 null，终态到期前仍可展示只读结果 |
| preparationStatus | string | PREPARING 或金额仍为预估时 PENDING，否则 READY；不代表当前仍可支付 |
| amountStatus | string | 最终金额组为空时 ESTIMATED，完整时 FINAL；损坏的部分金额组拒绝返回 |

订单 SQL 在原有 Order + Session + Context 三表查询中读取已有 public_token/public_origin，不增加 JOIN、表或列。服务端校验归属并生成公开 URL，不单独向外暴露 Token 或 Origin 字段。旧 Checkout 查询 Service、Converter、Response、专用 Repository/Mapper 查询及投影删除；写入和到期 CAS 保留。

不更改订单/支付状态、版本或有效期；订单 GET 保持只读且无远端调用。Customer API 不属于取消范围。客户端应改调两个订单 GET，原 Java CheckoutResponseBody 删除，调用方需要同步编译适配。历史设计中的独立查询说明由本修订精确取代。

数据库变更：不涉及；Gateway 路由、其他服务及实际发布不在本次范围。既有测试仅同步已删除接口的引用和模型形状，不新增用例、不运行测试或构建。

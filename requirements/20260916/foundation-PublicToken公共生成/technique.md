# 技术边界

- Foundation `paytech-common-core` 提供静态 `PublicTokenGenerator.generate(String... mixingValues)` 和主体校验 `isValid(String)`。
  时间、CSPRNG、Core 编码与 CRC 均封装在内部；[工具合同](../../../paytech/foundation/paytech-common-core/PUBLIC_TOKEN.md)拥有准确格式和输入边界。
- Commerce 创建 Processor 直接调用工具并拼接 `CheckoutPublicToken.PREFIX`；删除原来的 Spring Generator 接口及实现。
  `CheckoutPublicToken` 仅保留业务前缀、parse 和委托校验，无第二份编码算法。
- 混入值从旧固定国家码/Client ID 字节位置变为可变文本数组的有边界摘要掩码，只作用于新的随机 Payload。
  不改变旧 Core 解码或 CRC，因此无数据回填或 Token 重建。时间采用生成时系统 UTC 秒，不代替订单的业务时间。
- 用户后续确认每次创建只生成一个 Token：事务前构建 Session 时直接生成，Context 不保存候选列表，Repository 单次插入；唯一冲突按数据库错误使五类创建事实整体回滚，不重试或重建 Session。旧快照候选字段仅用于历史读取，不参与新写入。不改变业务订单防重及外部依赖边界。
- 验证包括公共方法形态、当前时间、30/34 位格式、历史向量、非法输入、并发生成，以及 Commerce 契约/架构/真实 HTTP 参数校验。
  成功创建的数据库回归仍需前序 `consumer_country_code` 本机结构迁移；本需求不授权执行该 SQL。

现行 Checkout Wire 与数据库约束仍见[原设计第 6 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#6-checkout-public-token)。
不新增第三方依赖或配置；应用不自动创建或修改数据库。

## Commerce orderNo / refundRequestNo 生成格式

- `CreateCheckoutProcessor` 直接生成 `ord_ + PublicTokenGenerator.generate(clientIdText, clientOrderId)`，移除 CheckoutOrderNumberGenerator 接口和旧 Base64 实现。
- `CreateRefundProcessor` 直接生成 `rfd_ + PublicTokenGenerator.generate(clientIdText, orderNo, clientRefundId)`，替代 UUID 拼接；只在新退款分支生成，业务重放读取原编号。
- `chk_`、`ord_`、`rfd_` 保持各自业务意义，主体均为公共工具的 27 位 Core + 3 位校验码，总长度 34。每次生成一次，不增加候选、配置、Spring 组件或另一份编码算法。
- 创建 Checkout 响应的内部坐标校验同时接受新 Token 主体和旧 22 位 Base64 主体，因为该类型还供历史回执 Reader 使用。只读历史 CheckoutCreationSnapshot 继续按原快照格式解析，新创建不写该快照。
- 外部订单、退款查询继续接受既有不透明字符串；不以新 CRC 要求阻断旧编号。归属、权限、业务防重仍由已有业务事实决定，不能用编号格式或混入参数代替授权。
- `order_no`、`refund_request_no` 已为大小写敏感的 VARCHAR(64)，既有唯一索引继续保护生成冲突；无需 DDL、PDManer 或数据回填。OpenAPI 描述新生成格式，但保持原查询输入边界。

本增量只修改 Commerce 消费者及关联说明，Foundation 工具实现不变。按用户要求未执行测试、构建或验证，旧生成器及构造器相关测试适配待后续处理；当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-public-numbers-20260916)。

# Commerce Customer Checkout 实施说明

> 2026-09-19 时序修订：下文准备命令、Handler、准备时创建 Intent、未接入端口报不可用及准备前锁定 Consumer 的描述已被[准备与 Intent 拆分](../../../../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md)替代。当前创建完成 OE 等信息初始化、首次支付创建 Intent；独立 prepareCheckout 接口和 PREPARE 操作均删除，外部暂按用户授权固定 Mock；下文保留其他边界及前序实现证据。

> **2026-09-16 缓存职责后继：** [接入 Filter 精简](../../../../requirements/20260916/commerce-接入Filter精简/technique.md)删除同时覆盖 Client/Customer 的缓存 Filter。公开缓存头改由 Gateway 统一承担，以下原直连服务的 no-store 描述及测试记录为历史行为；本次未改或验证 Gateway。Customer 自有请求及幂等处理不在此次移除范围内。

本轮用户确认“全部 Customer 接口归 Commerce，其他系统 Mock +
TODO”；入口、卡瞬时转发及调用方向以[本轮修订](commerce-entry-amendment.md)为准，取代下述跨仓库文档相应旧段落。当前只修改
Commerce 仓库，公共文档调整作为交接项。

未被取代的需求与公开字段沿用 [Customer 合同](../../../../requirements/20260912/commerce-CustomerCheckout接口设计/customer-api-contract.md)
，Payment 单请求卡与法律确认沿用 [Card 内部合同](../../../../requirements/20260912/payment-自建CardVault/api-contract.md)
。本切片按用户“先完成 Commerce，外部接口 Mock +
TODO”的范围交付；实现、执行和验证状态统一见 [COM-CUSTOMER-01](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-customer-20260912)。

## 接口

[OpenAPI](../../api/customer-checkout.openapi.json) 与 `paytech-commerce-customer-api` 声明本期接口。实际直连路径加 `/commerce`，下面不重复该 Context Path。

| 方法   | 路径                                                                                      | 职责                                         |
|------|-----------------------------------------------------------------------------------------|--------------------------------------------|
| GET  | `/customer-gateway-api/v1/checkouts/{checkoutToken}`                                    | 加载和轮询；`includeConsumer=true` 才回填消费者原值      |
| PUT  | `/customer-gateway-api/v1/checkouts/{checkoutToken}/consumer`                           | 准备前完整替换姓名、Email、账单地址                       |
| POST | `/customer-gateway-api/v1/checkouts/{checkoutToken}/preparation`                        | 无 Body；受理原准备身份，PENDING 返回 202，READY 返回 200 |
| POST | `/customer-gateway-api/v1/checkouts/{checkoutToken}/payments`                           | 一次提交卡与协议确认，由 Commerce 编排                   |
| GET  | `/customer-gateway-api/v1/checkouts/{checkoutToken}/payment-submissions/{submissionId}` | 授权后查询原 Payment 结果及已有 3DS 动作                |

只有 Preparation 仍要求 `Idempotency-Key`；SaveConsumer 和 Payments 不要求或处理该 Header，每次请求按当前业务状态执行。Customer 不带 Order Version；版本与原期限仅在 Commerce → Payment 内部命令传递。旧四个反向
HTTP 接口均已删除。

所有响应采用 `no-store`，敏感前缀使用 Foundation metadata-only 日志。错误复用 Foundation Problem Writer，未另加 Advice、MDC 传播或服务专用 HTTP 客户端。

## 本地事实与事务

- GET、Save 和 SubmitPayment Processor 按公共规范复用 Foundation `AbstractProcessor`，入口拒绝调用方已有事务；其他现存入口暂保留 `AbstractGatewayProcessor`。Processor 负责验证、按需查询、上下文、事务外调用和响应。Customer 普通查询不创建事务；写入链路保留短事务、Order 锁与条件 SQL 保护。业务 Service 使用接口与实现分离；Repository 只操作 Commerce 库。
- Save 在事务外 Protect，本地只保存保护引用和批准维度；不计算请求 Hash，不读写命令表或保存响应快照。准备前每次请求均按当前状态完整替换资料，准备开始后拒绝保存，不返回历史成功回执。
- Save 的 HTTP Body 及其地址 Info、内部 Request/Response、Context 使用普通 class。规范化由内部 Request 完成，原 Token 与保存资格由 Processor 核对；直接读取 Session、CommerceOrder 和 ConsumerContext，不加载商品、合同或支付聚合。Processor 直接调用 `saveConsumerContext`，该 Service 自带 REQUIRED 事务，条件写入订单版本及 Consumer，失败全部回滚；Repository 只在 SQL 成功后同步原对象。订单条件 UPDATE 提供行锁和版本保护，不再为命令认领额外 SELECT FOR UPDATE。并发状态/版本变化返回冲突，客户端重新读取后重试。详见[2026-09-17 重构](../../../../requirements/20260917/commerce-SaveConsumer接口重构/technique.md)，本轮未修改测试或执行验证。
- 准备以原 `preparation_command_id` 固定时间和消费者资料。CCT 选择先写入受控命令结果及 Order 的 OE 身份，再调用 Risk/Tax；选择后不重选。完整 Context、法律卖方、最终金额和合同原子提交后才创建 Intent。
- `SELECT_CHECKOUT_ENTITY` 和 `START_CHECKOUT_INTENT` 是 Commerce 的内部幂等操作，均按原准备 ID 定位。前者保留不含 URL/Token 的选择响应，预算 32 KiB，BIGINT 使用字符串；后者在首发前保存原创建键及开始时间。它们不增加业务表、不增加浏览器操作，也不维护一份可变 JSON 阶段快照。
- Finance 结果必须匹配原 Order/OE/国家/币种/净额，行明细按 `client_item_id` 完整对齐，汇总税和最终金额恒等。最终税事实留在 Finance Snapshot，不修改原商品行。
- 外部 Intent 响应未知时保留原任务并使用原键恢复。取消或到期时，未首发可本地收敛；已有首发标记必须取得 Payment 原身份的终止证明，不能以暂未找到资源作为成功。外部创建成功但本地绑定失败时，原创建仍可恢复。
- SubmitPayment 不再使用 Key、卡指纹、Hash、命令认领或响应快照；Processor 构建完整 Context 后直接调用 Service，由其 REQUIRED 事务保存首次法律接受与订单版本，提交后再调用 Payment。支付查询 Handler 及登记已删除，公开 GET 查询保留。每次请求生成新的来源提交编号，查询按原订单/Intent/编号直接访问 Payment。同一合同复用原接受事实，重复扣款和在途支付保护归 Payment。详见[2026-09-17 修订](../../../../requirements/20260917/commerce-SubmitPayment接口重构/technique.md)。
- GetPayment 使用 request 包中的普通类输入及 CustomerPaymentQueryContext，Session、Order、支付投影各读一次；事务外读取 Payment，复用 Context 构造响应。返回前只按原期限做内存判断，不重查数据库或比较版本；有效期内终态订单仍可查询。详见[查询重构](../../../../requirements/20260917/commerce-GetPayment接口重构/technique.md)。
- GET 通过 `GetCheckoutConverter` 将原 Path/Query 组合为内部 `GetCheckoutRequest`，不增加 GET Body。`CheckoutQueryContext` 使用普通 class，直接持有 Session、CommerceOrder、ConsumerContext、支付投影、商品和有限法律 View，封装已有事实上的就绪、操作资格及版本比较。内部 `GetCheckoutResponse` 及其金额、商品结果也使用普通 class；Converter 再投影为独立 `response/GetCheckoutResponseBody` 及嵌套 `XxxInfo`。金额使用公共 Money 按订单币种输出，不重复解析数据库金额；消费者详情不复用保存请求模型。公开 JSON 字段、路径和查询参数保持不变。
- GET 的消费者回填、Payment 查询和操作资格由 Processor 编排；原文件链接封装在 `integration/customer/CheckoutDocumentLinkClient`，准备响应复用同一实现。移除承担外部访问的 `CheckoutViewService`，不把远端查询隐藏在本地 Service 中。该变化不接入真实下游，原 Owner 端口与测试隔离边界继续有效。
- GET 先按原 Token 读取 Session 和 CommerceOrder，由订单对象完成到期校验后，直接从所属 Repository 读取消费者、法律展示、支付投影和商品，不再经过 CustomerCheckoutService 或装配 OrderRecord。`CheckoutLegalView` 仅组合上下文、法律事实和消费者合同三表中的国家、语言、卖方及文件引用，不读取定价、Risk 和税快照。冻结后使用原计税国家，准备前使用订单国家，不再读取 Create Checkout 命令快照。`PaymentStatus` 负责摘要自身的状态、动作和观察时刻校验；Processor 只编排查询及依赖不可用降级。外部只读结果取得后，仅用 `CheckoutVersionView` 和支付版本重验原 Token 绑定与变化；版本不一致直接返回原可重试冲突，由调用方重新查询，不自动重读，不用混合状态返回支付操作。详见[本次重构](../../../../requirements/20260916/commerce-GetCustomerCheckout接口重构/technique.md)，本轮未执行构建或验证。
- Repository 不再提供全量 `load`。`CheckoutAccessContext` 只包含旧入口仍需要的订单与 Session；Save 与 SubmitPayment 直接读取所属表对象，后者不再使用 `CheckoutPaymentContext`、`CheckoutContractView` 或 `OrderRecord`；`PrepareCheckoutContext` 只加载准备响应需要的金额、卖方和文件链接，不读取商品、消费者详情或 Payment 展示概要。查询按当前用例的数据集合组合，合同关系 JOIN 保持三张表以内。
- 准备 Handler 恢复和外部操作前预检不创建读事务；已绑定 Intent 的任务直接完成，已冻结阶段不重新读取商品或创建快照。选择、冻结、绑定与终止各自只在原本地写事务内读取并锁定当前阶段需要的事实，去除 `canProceed` 后重复的全量读取。GET 不产生接受、证据或支付操作，Payment 返回 SUCCEEDED 也不使 GET 写入 Commerce PAID；真实支付成功事实的接入仍由后续 Payment 集成切片处理。

同步用例以短数据库事务受理，在提交后执行外部调用；补偿以数据库 Claim/Lease 恢复准备；原支付查询由公开 GET 提供，不再登记后台查询任务。此切片不新增 Redis
依赖。Order 行锁、唯一键、版本校验和外部原键幂等承担各自的并发保护，数据库事务不跨任何外部调用。

## 外部边界与 TODO

| Owner 端口                | 后续真实接入职责                                                      |
|-------------------------|---------------------------------------------------------------|
| ConsumerProtectionPort  | Vault 稳定 Email Protect、独立 Profile Protect/Reveal；按真实用途与保留策略接入 |
| CheckoutCommercialPort  | CCT 准确候选、Source、Scope、合同、Term Version 与法律事实                   |
| CheckoutRiskPort        | 当前原订单/OE 的真实允许评估；不得默认 ALLOW                                   |
| CheckoutTaxPort         | Finance 原幂等 Tax Snapshot 及逐行事实                                |
| CheckoutDocumentPort    | Platform 不可变合同生成与受控原文件链接                                      |
| CheckoutCardPaymentPort | 当前卡提交、Intent 业务防重、按来源提交编号查询当前结果与受限 3DS 动作 |
| CheckoutPaymentPort     | 原准备身份的唯一 Intent 创建、关闭迟到创建及终止证明、无副作用支付概要                       |

这些是本期 Commerce 的业务端口，不是声称已发布的远程 Wire。真实接入继续使用 Owner 轻量 API JAR、`@HttpExchange`、负载均衡及公共
Request ID/Problem 组件，不新增 base-url/Feign。Payment 卡模块、NI、Gateway、Checkout Web、Risk 服务均不在此代码切片中；Card
输入只在 Commerce 当前请求中瞬时转发。

主应用的未接入端口明确报依赖不可用；状态型 Mock 只存在于 test 模块。默认运行不能得到合成 READY。显式本地联调可运行：

```sh
./tools/run-customer-checkout-mock.sh --server.port=18112
```

该入口使用本机 `127.0.0.1:13306/mor_commerce` 和内存中的合成外部 Owner，不建表、迁移或初始化业务数据；仅用于合成资料。Mock 文件 URL 不是实际文件，Mock 远端状态随进程结束丢弃。不能用这个入口证明真实 Vault、跨进程远端恢复、真实文件或支付已完成。

真实 HTTP 与独立 Handler 验证命令：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw clean verify
```

## 数据变更

支付去幂等需先人工应用[接受历史命令列可空调整](../../../../requirements/20260917/commerce-SubmitPayment接口重构/db/ddl.sql)。代码不再读写该列，历史值保留；未执行 SQL，也未同步 PDManer，新库执行旧基线后同样需要此增量。

[人工增量 SQL](ddl.sql) 面向已存在的本地旧库：三张表增加 8 列、放宽 5 列可空约束，不重写历史数据。完整新库 Schema 仍由 PDManer 导出。`consumer_reference` 在没有 Client 输入来源时保持 NULL；模型保留既有表、字段与索引身份，不创建新的 Acceptance/Assignment/阶段表。具体授权、备份及执行证据见状态台账。

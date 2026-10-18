# 技术设计

## 创建并初始化

`CreateCheckoutProcessor` 校验字段、金额与 Client 业务订单唯一性，生成原 Order、商品和 Session，再在事务外取得 CCT 商业事实、Risk 资格引用、Finance 税结果及 Platform 合同文件。`CheckoutInitializationContext` 只校验/组装结果，不访问外部或数据库；`CheckoutCreationService` 的 REQUIRED 本地事务原子保存订单、商品、退款仓位、资金投影、Session、Order Context、Order Legal、Consumer Contract。失败全部回滚，外部已生成文件/税引用的保留由各 Owner 接入时落实，不自动生成替代 Order。

返回 `READY / FINAL / OPEN`，保持原 checkoutUrl 和公开金额格式。没有 Consumer、法律接受或 Payment 投影。GET 不写入，不创建 Intent；删除 POST `/preparation`、对应 API/Controller 方法、Processor、Request/Response、Context 和 Converter；不再有独立准备阶段或 PREPARE 操作。

## 首次支付

`SubmitCustomerPaymentProcessor` 查询并校验原 Session、Order、Consumer、Contract 与可空 Payment 投影。`CheckoutSubmissionService.saveSubmissionContext` 用订单 CAS 原子保存首次三份法律接受及网络证据。接受事实存在后不再修改 Consumer；它同时说明 Intent 创建可能已经发出，不能以本地投影为空断言 Payment 无资源。

无投影时，以 `sourceType=COMMERCE_ORDER + sourceId=orderId` 及原 Client/OE/金额/币种/行业/MCC/定价版本/期限调用 `CheckoutPaymentPort.createIntent`。Payment 必须在该永久业务身份下只创建一个 Intent，重复调用返回原资源。Commerce 校验归属和金额后，`CheckoutSubmissionService.bindPayment` 在同一个 REQUIRED 本地事务保存唯一投影并 CAS 验证取消/期限/版本，才调用卡支付。任一写入失败整体回滚，数据库冲突返回 409，不把任意重复键当成功。已有投影时直接复用，不再创建或重复绑定。

创建 Intent 失败或响应丢失，保留已提交的接受事实，尚不调用卡支付；后续请求按同一 Order 身份取得原 Intent。卡支付提交响应未知则返回 202 与 submissionId，客户端查询原提交，不自动再次扣款。法律接受不代表支付成功，Commerce PAID 仍由后续真实 Payment 事实驱动。

## 取消

无 Payment 投影、无接受事实且无历史 preparation_command_id 时，原子取消本地订单/可选 Session。否则先持久化终止意图，在事务外按 Client + COMMERCE_ORDER + orderId 请求 Payment 取消。取消链路不得调用 createIntent，不创建或补绑定支付投影。

Payment 明确终止，或者明确返回该 Intent 不存在的业务 `NOT_FOUND`，Commerce 均可在本地短事务取消 Order / 实际 Session。超时、不可用或 PENDING 返回 202，保留原终止意图；已支付走撤销/退款拒绝分支，其他错误原样处理，不能将普通路由 404 解释为 Intent 不存在。本地保存继续校验订单版本、未支付状态和已读取投影的版本；无投影时仍要求写入时没有投影，本地直接取消额外保留无接受和无历史准备标识的条件。

此段按 2026-09-21 用户纠正，取代原“先恢复创建结果再终止”的取消流程。当前 Payment 的正式终止合同仅支持 Intent ID；按来源订单定位的取消能力是待接 Owner 合同，本轮只调整 Commerce 本地端口和 Mock，不宣称真实接口已支持。

“不存在”只代表 Payment 此次处理时的事实，不能承诺此前超时的创建请求永远不会完成。Commerce 的终止标记阻止新支付；已在途的创建即使随后返回，本地关联与支付资格 CAS 仍必须一起回滚，不能调用卡支付。可能晚到的未支付远端 Intent 保留由 Payment 按原期限处理，取消不为清理该可能性去创建 Intent。

## 暂缓集成

本轮经用户明确授权，`CustomerCheckoutIntegrationConfiguration` 提供固定 Client Mock，`FixedPaymentTerminationClient` 固定终止结果；不是按测试环境切换的业务分支。固定 OE 为 AE001、资格评估为合成引用、税和舍入为零、文件链接为 `.invalid`、支付返回 PROCESSING。Consumer 保护/回填为固定合成值，不是可用的个人信息保存能力。它们不证明真实合同、风险通过、税务、文件、Vault 或扣款已完成。

后续通过 Owner API JAR、HttpExchange、服务发现/负载均衡替换端口，验证 Payment 按 sourceType/sourceId 唯一创建、终止时阻止后续 Attempt。定价真实支付方式维度仍待 Owner 接入，不在本轮改 Payment、Risk 或 Gateway。旧 CCT 基础预检适配器保留，但创建现在调用完整商业解析端口，不再重复调用预检。

## 数据与迁移

无新增业务字段或新的阶段/提交表。新库接受表的历史 command_idempotency_id 可空，沿用[已确认的去幂等增量](../../20260917/commerce-SubmitPayment接口重构/db/ddl.sql)。本地缺失四表的唯一执行来源为[本次补表 SQL](../../../paytech/acquiring/docs/requirements/20260919-checkout-preparation/local-test-tables.sql)，不修改已有表或数据。测试完整初始化 SQL 同步包含四表，仍需人工执行。

历史订单、旧命令表和旧补偿事件不重写；旧未初始化订单不会由 GET 补建，不保留 preparation 兼容入口。旧 CHECKOUT_PREPARATION Handler 已删除；存在存量任务的其他环境启用前必须先处理这些任务和未准备订单，不能直接套用本地新写结论。

## 验证

真实随机端口 HTTP → 正式 Controller/Processor/Service/Mapper → 本地 MySQL。用例 DTO 和故障控制仅在 test 中，类 BeforeEach 配置，公共 AfterEach 清理本例数据与外部替身记录。覆盖创建初始化原子性、旧准备路径返回 404、Consumer 未保存也 READY、首建/复用 Intent、响应丢失、取消交错、创建/支付超时、错归属结果、业务重复与事务边界。不将固定 Client 测试表述为真实外部 HTTP 或跨服务联调。

## 2026-09-20 锁和本地原子性修订

首次支付使用 Foundation `AbstractLockProcessor`，逻辑 Key 为 `order:{orderId}`，TRY_LOCK 等待 0、Watchdog 自动续租。锁前只按 Token 查询不可变订单主键，锁内再按需读取当前事实；锁覆盖本次同步编排和事务外 Payment 调用。相同订单竞争返回 409，不先保存接受或调用外部；不同订单独立处理。既有 Cancel/Save Consumer 继续由 CAS 与原 Intent 约束保护，本次未批量迁移其他入口。

旧实现先独立提交 Intent 关联，再检查订单资格，本修订取消这个部分提交状态。绑定与 CAS 原子回滚后，已提交法律接受和原订单业务身份仍然存在，下一次支付可从 Payment 找回同一 Intent。取消入口按上方 2026-09-21 修订直接请求终止，不再创建或绑定 Intent。锁不是 HTTP 请求幂等，也不替代 Payment 的 Intent 唯一键、在途尝试与重复扣款保护。

正式应用按需引入 Foundation Redis Starter 并启用 coordination，自动测试复用仓库本地 16379 Redis，随机 Namespace 隔离。未接入其他业务系统、未修改表结构，实际验证及本地运行状态见当前台账。

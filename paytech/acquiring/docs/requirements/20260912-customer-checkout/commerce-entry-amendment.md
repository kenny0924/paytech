# Customer 入口收敛到 Commerce

> **2026-09-17 SubmitPayment 后继（按用户纠正去幂等）：** 当前提交和查询以[本次修订](../../../../requirements/20260917/commerce-SubmitPayment接口重构/technique.md)及 [OpenAPI](../../api/customer-checkout.openapi.json)为准。Payments 不再要求 Idempotency-Key，不计算卡指纹/Hash，不读写命令或响应快照，也不提供原键重放/补卡。每次 POST 生成新的业务 submissionId；GET 使用订单、Intent 和该编号查询 Payment；CheckoutPaymentQueryHandler 及其登记已按用户要求删除，Processor 直接调用本地 Service。普通类模型与 Context、Foundation AbstractProcessor、REQUIRED 本地 Service 和事务外 Payment 调用保留。接受历史命令列需要先人工应用[可空 SQL](../../../../requirements/20260917/commerce-SubmitPayment接口重构/db/ddl.sql)，本次未执行。Payment 对同 Intent 在途与重复扣款的保护仍需真实适配；新接口没有恢复自动扣款的机制。
>
> 下文保留 2026-09-12 的入口收敛记录。其中提交分层、Key/HMAC、命令回执、allowExecution、原键恢复、后台查询任务和对应测试场景均被上述后继取代；不能据历史测试描述声明当前实现已验证。入口统一归 Commerce、卡数据不持久化和事务内禁止外部调用等边界继续有效。


2026-09-12 用户确认：全部 Customer 接口归 Commerce，先改 Commerce，其他系统暂时 Mock + TODO。本轮只有 Commerce 仓库变更；未修改 Gateway、Payment、Risk、前端或数据库结构，未部署。

## 入口与职责

Commerce 提供 Checkout 加载、资料保存、准备、支付提交、原支付提交查询，共五个公开操作，见 [OpenAPI](../../api/customer-checkout.openapi.json)。删除上一版 Payment 专用访问校验、法律确认、原确认查询及 readiness 四个 HTTP 操作。协议接受仍保存在 Commerce，订单/协议资格校验在本地执行，事务提交后向 Payment 传递真实接受坐标、Order Version、原期限和当前执行资格。

调用方向是 Checkout Web → Commerce → Payment Card。Commerce 公共 `POST /payments` 一次接收卡与协议确认；Card 处理、存储、Intent/Attempt、MID、NI 和资金状态继续归 Payment。Payment 专用内部端口不接收 Checkout Token，也不要求调用 Commerce。

`CustomerPaymentController` 只将路径参数、幂等键和原 RequestBody 封装为 `SubmitCustomerPaymentRequest` 或 `GetCustomerPaymentRequest` 后转发。两个 Processor 均直接继承 `AbstractGatewayProcessor.process(request)`，不声明或重载 `process`；基础校验、当前请求上下文构建、业务编排和响应组装均在模板阶段内完成。提交 Processor 返回包装 `PaymentSubmissionResponseBody` 的 HTTP 响应，查询 Processor 返回该响应体；两者复用无状态的支付响应 Converter。Controller 不解析标识、转换卡信息或解释支付状态。

原始卡输入现在会瞬时经过 Commerce。这一窄变更取代旧文档“Commerce 不接收任何原始卡输入”的描述；卡输入不得进入 Commerce 数据库、缓存、日志、幂等快照、补偿事件或结果回执。CVV 全链路不持久化、不计算摘要。Card 类型和顶层输入覆写 `toString()` 避免意外日志输出；共享 Foundation metadata-only 日志继续覆盖整个 Customer 前缀。

## 提交与事务

1. Processor 检查基础字段、真实 Token、准备状态和当前合同，先查原幂等命令；已支付订单仍允许定位原命令，新键仍需通过新支付门禁。
2. 在事务外调用 Payment Card 的 `fingerprint`。输入只有受信 Client/Session/Intent/合同和 CardIdentity，没有 CVV。Payment 负责规范化与作用域 HMAC；Commerce 只对含该安全身份的投影计算本地命令 Hash，不对 PAN 计算裸摘要。
3. `CheckoutSubmissionService.accept` 自身提供 REQUIRED `TransactionTemplate`。Order 行锁下重验当前资格、认领原键、原子保存或复用恰好三份接受、同 Session 网络证据、安全受理回执和查询补偿事件。`CheckoutAcceptanceService.accept` 同样自带 REQUIRED 并参与这笔事务。
4. 本地提交后，Processor 重新读取当前资格并调用 Payment 专用 Card 提交端口。接口携带 `allowExecution`：关闭/已支付时仅允许重放原结果；Payment 必须在自己的 Intent 锁下验证原身份、当前终止/资金状态、期限及唯一活动支付。外层不能用数据库事务包住 Processor。
5. 支付当前响应不写入 Commerce 的命令回执。200 表示可返回的当前结果，202 表示已受理且仍在处理或结果未知，不表示已扣款。Payment 的 `SUCCEEDED` 也不直接把 Commerce Order 改为 PAID，后续仍接入真实 PaymentSucceeded 事实。

`SUBMIT_CHECKOUT_PAYMENT` 使用现有 `t_command_idempotency`。公开 `submissionId` 是 Commerce 命令 ID，Payment 应将其作为来源提交身份去重，与 Payment 自己的物理主键区分。回执只冻结原 Order/Session/Intent、合同、三份接受及证据 ID、接受时间和原期限。没有新表、列、索引或 SQL 执行动作；旧 `CONFIRM_CHECKOUT_PAYMENT` 常量只用于识别可能残留的本地历史数据，不再开放写入口。

## 查询与恢复

- GET 先验证当前 Token 和原提交归属，再查询 Payment；不加载消费者或商品展示数据、不打开读事务。跨订单返回 404，原公开访问过期返回 410。
- `CheckoutPaymentQueryHandler` 在本地事务提交后立即由 Registrar 唤醒，事件只有 `OrderNo + submissionId`，Payload 为空。Handler 从原回执恢复参数，在事务外只查 Payment，不补造同意、不保存或重发卡、不调用资金执行接口。独立 Handler 测试从 Mapper 安排状态。
- Payment 已存在原提交时，Commerce 查询任务可完成，Payment 自己负责后续资金恢复；未找到时保留有限重试。查询的 null 不能证明在途请求已经取消，不伪造 FAILED 或 AWAITING_CARD。
- 首次提交响应丢失后，原键同参数重试仍使用同一个来源身份；同键换卡拒绝。CVV 改变不改变身份。缺少瞬时卡输入时后台无法发起首笔资金请求，需用户带原键重新提交；只有 Payment 提供安全恢复依据时才返回 AWAITING_CARD 及原键。
- 本轮 3DS 端口只支持受控 `THREE_DS_CHALLENGE` + HTTPS 跳转 URL；响应仅在当前请求中转发，不落本地库。真实 NI SDK/frictionless/challenge 合同仍待 Payment 接入验证，不能声称已有 NI 3DS 联调。

## Mock 与后续接口交接

生产配置缺少真实 Adapter 时明确不可用。`CustomerCardPaymentMock` 仅存在于 test 模块，供自动测试和显式本地联调入口组装；它保留最小 Payment 状态、作用域 HMAC 和唯一活动执行约束，且逐次断言调用发生在事务外。它不把原请求或卡输入存入模拟器状态，也没有替换业务方法。

| 后续 Owner | 必须完成的真实能力 |
|---|---|
| Payment Card | 专用内部 HTTP 契约；作用域 HMAC、规范化及密钥轮换下原键稳定性；原提交幂等与输入缺失恢复；PAN 加密存储及 CVV 瞬时使用 |
| Payment | 原 Intent 绑定验证、来源提交 ID 唯一性、取消/过期栅栏、唯一活动支付、Risk/路由/NI、3DS 当前动作及只读查询；PaymentSucceeded MQ |
| Gateway | 当前没有公共路由变更；上线前确认消费者 IP 受信转交合同。直连本地测试使用实际连接地址，不信任浏览器自报转发 Header |
| Checkout Web | 接口全部调用 Commerce；原键重试、202/查询、原 3DS 动作；不从 SDK 结束或接受回执推导支付成功 |

外部适配使用 Owner 轻量 `*-api`、公共 `@HttpExchange`、负载均衡和 Request ID/Problem 组件，不引入 Feign 或服务专用 base-url。

公共文档 Owner 后续需同步 `APPLICATION_ARCHITECTURE.md`、原 Customer/Card 需求与数据稿、全局需求/时序目录及 `CURRENT_IMPLEMENTATION.md` 中的入口归属、卡瞬时边界和 COM-I10 调用方向。当前用户将修改范围限定为 Commerce，本文件记录准确取代范围，不批量改写其他仓库或共享文件。

## 验证

自动测试覆盖统一 HTTP 提交/查询、同键换卡/换 CVV、成功后重放、跨订单/到期、严格 JSON、3DS 临时材料不落库、响应丢失与并发唯一执行，并为查询 Handler 提供独立恢复测试。完整执行结果以本轮实际验证记录为准；测试使用真实本机 MySQL 和测试侧 Payment Mock，不代表真实 Payment、NI、Gateway 或前端已经联调。

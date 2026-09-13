# 工具 Token 修订研发检查

本页是本次修订的代码导航与验收映射。业务合同由[已确认修订](./instrument-token-amendment.md)唯一维护；实际执行、数据库及验证结果只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。此页不表示数据库业务用例已经通过。

## 代码导航

| 用例 / 边界 | 入口 |
|---|---|
| PI-I00 卡身份查询 | [PaymentInstrumentApi](../../../payment-orchestration-service/paytech-payment-api/src/main/java/com/paytech/payment/api/instrument/PaymentInstrumentApi.java)、[ResolveCardIdentityProcessor](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/processor/ResolveCardIdentityProcessor.java) |
| PI-I01 原交易工具准备 | [PrepareCardInstrumentProcessor](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/processor/PrepareCardInstrumentProcessor.java) |
| EX-I05 统一 pay | [PaymentExecutionApi](../../../payment-orchestration-service/paytech-payment-api/src/main/java/com/paytech/payment/api/execution/PaymentExecutionApi.java)、[PayProcessor](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/processor/PayProcessor.java)、[PayAcceptanceProcessor](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/processor/acceptance/PayAcceptanceProcessor.java) |
| Token 范围、期限、撤销和安全结束 | [PaymentInstrumentService](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/PaymentInstrumentService.java)、[实现](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/impl/PaymentInstrumentServiceImpl.java) |
| 方式抽象 / Card 代码流程 | [PaymentCommand](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/flow/PaymentCommand.java)、[CardPaymentFlow](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/flow/CardPaymentFlow.java) |
| T3 发送与原动作恢复 | [PaymentActionProcessor](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/service/processor/PaymentActionProcessor.java) |
| 清理恢复 | [PaymentInstrumentCleanupHandler](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/ext/job/PaymentInstrumentCleanupHandler.java) |
| Card 受控输入与 Owner 端口 | [CardInstrumentInputs](../../../payment-orchestration-service/paytech-payment-card/src/main/java/com/paytech/payment/card/domain/CardInstrumentInputs.java)、[ExecutionPorts](../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/integration/execution/ExecutionPorts.java) |

支付方式使用共同的基本提交命令和内部 Token ID；Card Flow 自行访问 Card Owner。普通 Channel 不接收用卡材料。未来方式可注册自己的 Flow 和受控 Owner 能力，不增加空 Wallet/Crypto 实现。

Token 清理登记通过 Execution 拥有的本地端口交给 Web 补偿机制，和 Token 创建/撤销处于同一数据库事务。真实 Card 材料准备、领取、发送和清理均在事务外。PI-I01 锁代理使用显式 `intentId` 参数，方法内重验它与输入 Scope 一致；项目资源锁不支持多层属性表达式。

`payment.execution.instrument-preparation-window` 必须显式配置，缺省 `PT0S` 禁用准备。实现的技术上限为 24 小时，清理重排预算固定覆盖该上限，配置缩短或禁用时不会缩短历史任务预算。实际期限仍取 Intent、原提交、当前窗口及 Card Owner 的最小值；该上限不是允许缓存 CVV 24 小时的规则。真实 Card 提供更短材料期限与提前销毁控制。

## 验收映射

专项源码：[PaymentInstrumentAutoTest](../../../payment-orchestration-service/paytech-payment-test/src/test/java/com/paytech/payment/auto/execution/PaymentInstrumentAutoTest.java)。以下是待运行/补齐的场景映射，是否已运行以台账为准。

| 合同编号 | 已编写的检查 | 后续组合或 Owner 证明 |
|---|---|---|
| IT-V01 | 同来源准备重放、CVV 变化、并发重放、固定期限 | 首次并发响应丢失的完整组合 |
| IT-V02/03 | 无 Token 前换卡拒绝、旧 keyRef 在轮换后重放、改原期限冲突 | Commerce 真正的本地接受/Hash 竞争另阶段接入 |
| IT-V04 | 跨 Client/Intent 使用同 Token 拒绝、未知方式不降级 | 正式调用方鉴权仍为 Owner TODO |
| IT-V05 | Token/Attempt 唯一约束；既有并发提交、Redis Lease 失效反例已适配 | 非空旧协议兼容迁移不属于本次空表方案 |
| IT-V06 | T1 后材料写入失败，原 Token 重试补齐；材料丢失原 Attempt 补输入 | 真实 Card 暂存故障、进程间材料生命周期待专项验证 |
| IT-V07 | Token 到期未受理、等待输入到期、认证 UNKNOWN、短 Token 下挑战完成 | 真实认证能力与 CVV 使用时点由渠道适配器证明 |
| IT-V08 | 领取后跨期限不发送；既有 T3 前后 JVM 崩溃恢复测试已适配 | 完整新 Schema 回归尚需实际运行 |
| IT-V09 | 关闭原子撤销、清理失败、重复到期/撤销清理 | 真实材料介质晚到写、主从/备份/销毁证明未实现 |
| IT-V10 | 到期后的原 Capture、Refund、查询、UNKNOWN 保留 | Void 和晚到定价的专项到期组合继续补齐 |
| IT-V11 | 精确 Flow 注册、缺失/重复拒绝、普通 Channel 不接 Card 材料 | Wallet/Crypto 真实能力另阶段设计 |
| IT-V12 | Owner 调用事务外断言、Hash 不含 CVV、任务 Payload 空、HTTP 日志不含合成卡 | 真实卡数据泄漏和销毁验证不由 Mock 替代 |
| IT-V13 | 两个工具 HTTP、pay 和查询；生产未接入返回 503/no-store | Commerce 公开接口和实际跨系统链路未改造 |

## 数据库前置

- 已有旧切片且所有相关表为空：审阅[工具 Token 空旧表迁移](./db/instrument-token-empty-migration.sql)。
- 完全空库：审阅[工具 Token 空库创建](./db/instrument-token-create-empty-schema.sql)。这不是对已有库的升级脚本。
- 两者互斥，均需手工执行授权。应用与测试不会创建、迁移或初始化 Schema；不能用旧基线 SQL 初始化新代码。
- 有旧业务、命令或未决任务时禁止使用此迁移，必须另行设计兼容读写和在途排空。

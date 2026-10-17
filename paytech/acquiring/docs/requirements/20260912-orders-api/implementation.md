# Orders 接口本地实现

> **2026-09-16 后继：** 本文保留原切片记录。取消请求幂等和旧 Processor/事务组织已由[本轮用户纠正](../../../../requirements/20260916/commerce-CancelOrder接口重构/technique.md)取代：取消不再保存 Key/Hash/Lease/恢复回执，按订单事实处理；Service 维护本地短事务，Processor 查询与 Payment 调用均在事务外。最新查询结构及验证状态只读[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

用户本轮明确要求只修改 Commerce，完成 Orders 接口，并纳入可退款金额查询；外部服务暂用 Mock + TODO。沿用已有 v1 合同，本轮开发与相关本地验证直接推进，不改网关、其他服务、PDManer、数据库结构，不提交或部署。

- GET /orders/{orderNo}、GET /orders?clientOrderId=：在同一个只读快照内组合本库 Order、Session、Items、Payment/Refund/Funds 投影。未准备时从原创建命令的校验快照读取消费者国家；金额未知保留 null，未发生支付返回 NOT_STARTED。
- GET /orders/{orderNo}/refundable-amount：按 Client 隔离读取退款位置，返回 base - reserved - refunded；损坏或缺失的必需事实返回错误，不伪造零额。
- POST /orders/{orderNo}/cancel：使用 Foundation 本库幂等 Claim/Lease/CAS，冻结取消目标和稳定终态。短事务按 Order 行串行核对并写入 termination_requested_at；外部终止位于事务外；最终同事务关闭 Order/Session 并冻结回执。未开始准备且无 Context/Payment/已选 OE 的基础单可本地取消；准备中而 Intent 未明时保持 202；已存在 Intent 通过 Payment 端口终止；成功支付返回 409。
- 同键不同目标返回 409；同键处理中保留原命令；不同取消键共享按 Order 派生的 Payment 终止键。数据库行锁/CAS 只保护短事务，不依赖跨 HTTP 排他锁或进程内互斥保证正确性；重复外部调用由稳定终止键消重。不会增加无必要的 Redis 协调依赖。
- Payment 端口默认报告依赖不可用，取消编排将其保留为 202 待定结果。模拟实现、状态和请求记录只在 `paytech-commerce-test/src/test`，场景测试显式导入测试配置；业务应用没有 `local-mock` Profile 分支。TODO 接入真实 Payment Owner 契约和终止事件；当前 202 使用相同取消命令重试恢复，尚无后台 Payment 事件消费者。此边界的修正和验证见 [测试代码隔离说明](../20260912-refunds-api/test-isolation.md)。

验收覆盖基础/准备后查询、Client 隔离、Query/Body 边界、金额/投影一致性、取消终态与幂等、待定/未知/异常恢复、同 Order 并发和本地事务回滚。测试仅复用已准备的本地 MySQL，精确清理自身合成记录。

## 后续接入约束

- Payment 准备入口接入时，必须在相同 Order 锁/CAS 边界核对 `termination_requested_at`；已请求终止不得创建新 Intent。当前基础创建不产生 Payment 单据。
- TODO(PAY-I12)：替换本地端口为真实 Owner API，经项目 HTTP Group、服务发现、共享 Request ID/Problem 支持调用；不得以投影缺失推断不存在 Payment。
- TODO(PAY-I12)：接入 Payment 确定终止结果事件和可靠后台唤醒。当前显式 POST 原键重试负责恢复，GET 没有写副作用。
- 其他取消入口也应使用按 Order 派生的终止键和本地资源锁顺序。当前 `CLIENT_REQUESTED` 仅记录 Client 取消的状态原因，不新增历史工作流。

本期沿用已有表和人工准备的本地数据库，不执行 DDL，也不改 PDManer。测试合成支付/资金事实不代表 Payment 或 Finance 事件链已实现。

## 取消编排规范对齐（2026-09-12）

`CancelOrderProcessor` 继承 `AbstractGatewayProcessor<CancelOrderCommand, OrderCancellation>`，由模板的 `process` 入口调用 `validateBasic` 扩展点完成基础字段验证，再进入 `processRequest` 执行请求业务验证、不可变上下文构建、本地事务、事务外下游操作和响应构建。业务预检返回后续实际使用的请求摘要，不在单例 Bean 或线程变量中暂存请求状态。预检确认 Client 归属、必需 Session 和幂等 Key 冲突；订单当前状态在写事务中重验，已有命令仍重放原回执。`OrderCancellationContext` 只保存各阶段复用的输入、命令身份、租约 Owner 和时间。

`OrderCancellationService` 负责取消条件、终止意图和 Order/Session 状态迁移；`OrderCancellationIdempotencyService` 负责既有 Hash、Claim/Lease、恢复阶段和回执协议。Processor 先锁订单再认领命令，业务变更与命令记录一起提交。Payment 返回后先校验响应归属，再用独立短事务重锁订单、核对当前支付事实并提交结果；业务失败在回执提交后映射为 HTTP 错误。

沿用原有 API、数据库字段、Hash 输入、三秒/十五秒租约、稳定 Payment 终止键及失败恢复语义。本轮没有接入新的 Payment Owner 契约；真实接入仍遵循上方 TODO。

新增七个 HTTP 场景和一个架构边界检查，覆盖提交后事务外调用、调用期间支付成功、失去租约后的 Order/Session/回执原子回滚及原键恢复、四类损坏 Payment 响应。事务观察和故障安排全部位于 `src/test`。

- `./mvnw -q clean verify`：294 个测试全部通过，0 失败、0 错误、0 跳过；覆盖率门禁 1921/2094 行（91.74%）。
- `npm --prefix requirements-site test`：136 个检查全部通过；`git diff --check` 通过。
- 普通及可执行 Commerce Web JAR 检查未发现 Commerce 测试包、Mock、Simulator 或 Stub 类。
- 本地验证证据：`/Users/kenny/.codex/verification/commerce-cancel-refactor-20260912T094509Z`。本轮仅修改 Commerce，尚未提交、推送或部署。

后续修正恢复了 Gateway 模板继承，并补充架构检查约束共享 `process` 入口；修正后定向执行 `CommerceArchitectureAutoTest`、`ClientOrderHttpAutoTest`、`ClientOrderPaymentHttpAutoTest`，65 个测试全部通过，文档检查 136 项通过。此次定向验证没有重新执行全量覆盖率统计，上方全量记录对应此前的六阶段重构。

## 本地验证记录（2026-09-12）

- Commerce Reactor：`./mvnw -q clean verify` 退出码 0；239 个测试，0 失败、0 错误、0 跳过。
- Orders 的 Repository、Service、Controller、Processor 已加入原有 JaCoCo 门禁；1564/1719 行，90.98%，满足 90% 阈值。
- 工作区要求的 `npm --prefix requirements-site test`：136 个检查全部通过。
- `git diff --check` 通过。此次没有执行 DDL、提交、推送或部署；只修改 Commerce 仓库。
- 验证日志和汇总保存在本机 `/Users/kenny/.codex/verification/commerce-orders-20260912T064155Z`；它是本次执行证据，不代表跨服务或生产支付链路验证。

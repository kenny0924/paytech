# 交付记录

## 用户范围

- 2026-09-16：用户询问 `ClientOrderApi#cancelOrder` 的用途，在说明现有行为与 PAY-I12 尚未接入后，明确要求“重构一下”。本次按已经批准的开发规范执行现有接口重构，不重新设计取消业务。
- 延续用户“单元测试先不用管，不用验证”：不修改测试，不运行测试、构建、格式化或静态验证。
- 没有 SQL 执行、Git 提交、推送、部署、配置中心或其他系统修改授权；必要的模型和人工迁移文件随代码准备。

## 本轮纠正

用户指出 `OrderCancellationIdempotencyService#validateRequest` 不应保留，并要求按 processor-conventions.md 重做。以本轮要求覆盖先前“保留请求幂等恢复”的错误范围：删除取消 Hash/Claim/Lease/命令快照，改用原订单事实；查询验证位于事务外，Service 独立维护短事务，数据库 CAS 保护竞争。取消内部 version 也不再对外返回。共享命令表、历史行及其他操作不删除。

## 本轮追加要求

用户要求取消接口不用 OrderRecord，按需查询真实表对象，并明确考虑 Hosted Checkout 与 Direct Pay 的会话差异。取消链路改为 CommerceOrder、CheckoutSession、OrderPaymentProjection；Direct Pay 不再强制 Session，若实际关联会话则一起关闭。原取消时间归属订单，补充订单 cancelled_at、目标 Schema、PDManer/生成器与人工增量 SQL，未执行数据库变更。

## 交付材料

- 用户继续要求 SQL 更新后直接修改原数据库对象；订单与会话由 record 改为可修改 class，Repository 更新改为 void，SQL 成功后同步原实例，取消 Context 保留同一引用；关联 getter 与 Mapper 属性映射一并适配。此轮不增加字段或执行 SQL，测试仍按用户要求暂不修改、不验证。
- [范围](requirement.md)、[结构与边界](technique.md)。
- 源码入口：`commerce-service` 的 `ClientOrderApi`、`CancelOrderConverter`、`CancelOrderProcessor`、`OrderCancellationContext`、`OrderCancellationService` 以及 Order/Session 单表持久化。
- 当前实现及未验证事项统一见 [状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-cancel-order-refactor-20260916)。

## 未执行事项

测试源码仍有旧 `OrderModels`/record 类型及取消 Key/租约/回执断言，后续验证前需要迁移；本次未宣称可编译或测试通过。真实 Payment 终止及后台恢复能力仍按原 TODO 管理。新增列及历史取消时间的精确回填仅准备 [SQL 文件](db/cancelled-at.sql)，没有执行。CommerceOrder 构造器及本轮 Processor/Context 依赖变化对应的测试也待后续迁移。

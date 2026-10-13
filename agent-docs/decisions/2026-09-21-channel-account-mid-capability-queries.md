# Channel 账号、MID 能力与主链路查询范围决定

- Decision status: Confirmed；业务选择与实施细则见需求及实施合同，非空业务库迁移独立处理。
- Decision date: 2026-09-21
- 实施、验证与迁移状态：[当前台账](../CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。
- 详细需求唯一入口：[渠道账号与 MID 能力查询](../../requirements/20260921/payment-渠道账号与MID能力查询/requirement.md)。

## 1. 决定

按本轮逐表讨论，Channel 配置组采用 Channel、Account、MID、Capability 主表、Capability Detail 五表目标；删除 adapter_code，Account 增加 OE/名称/备注及受保护配置引用方向，能力明细保存 value_type，保留 country_code，scheme_code 解释为支付体系，MCC 作为主表精确适用条件。本期移除 Health State。

主链路查询先收敛为 queryMidCandidates 和 getChannelExecutionConfig，分别提供有效候选能力与冻结三元组下的执行配置校验/读取。最终选路属于 Routing，冻结身份和支付状态属于 Execution；两个查询不承担协议调用或交易持久化。

完整字段、接口参数方向与 SQL 清单只在需求正文维护；本决定不另建一份字段权威。

2026-09-21 实施澄清：用户确认 country_code 使用冻结的消费者国家；账号 JSON 包括渠道 API 凭据使用 Vault；原交易/恢复允许 SUSPENDED/CLOSED，任何 DRAFT 拒绝，新支付三者必须 ACTIVE。用户已授权源码、模型和规范测试，并单独授权初始化专用 mor_payment_channel_test；未授权共享业务库迁移。

两个公开 Processor、逐代码匹配、REPEATABLE_READ 只读快照及事务外 Reveal 的细则统一见[实施合同](../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)。

## 2. 取代范围

本决定精确取代 [Payment 数据库规范](../database/PAYMENT_ORCHESTRATION_DATABASE.md)第 3 节及[前序五表方案](../plans/2026-08-25-payment-provider-account-mid-simplification-design.md)中受影响的 Channel/Account/Capability/Health 目标；工件及数据库实际状态只见当前台账。

对 [Vault 安全边界](../VAULT_SERVICE_ARCHITECTURE.md)和[数据库规范](../DATABASE_DESIGN_GUIDE.md)中的渠道 Secret 限制，仅为 Payment 的 CHANNEL_ACCOUNT_CONFIG_V1 增加专项例外：REFERENCE Token、CHANNEL_EXECUTION Purpose，Caller 为 payment-orchestration-service（2026-10-13 起另有 platform-support-service，仅限 [Platform 读取决定](./2026-10-13-platform-channel-account-reveal.md)的执行用途）。账号 Schema 与策略片段由需求持有。卡数据、登录密码、KMS/Vault 根密钥、其他系统 Secret 的限制不变；Vault 不解析 Payment 业务含义，不增加业务表，也不因此成为通用 Secret Manager。

不取代未涉及的 MID 字段、Execution 冻结坐标、Routing 最终选择、Card 保护边界、Callback/Dispute 数据所有权，以及[通用执行器](../../requirements/20260914/platform-support-外部API配置与执行/generic-execution-amendment.md)的无执行持久化/无自动重试合同。

删除健康能力需要后续核对 Routing 中复制的健康字段，但本轮未逐字段批准那些表的删除。schema 字段总量和运行状态继续只由当前台账维护。

## 3. 后续事项

共享业务库的非空迁移、真实环境策略和消费者接入，以及未来钱包的 MID/MCC 适用性仍须独立推进，详见[决策与后续事项](../../requirements/20260921/payment-渠道账号与MID能力查询/requirement.md#open-items)。

账号 JSON 例外只来自此次用户明确选择，不能推广成所有 Secret 均可存 Vault。真实策略部署、共享业务库 SQL、提交和服务部署没有包含在研发授权中。

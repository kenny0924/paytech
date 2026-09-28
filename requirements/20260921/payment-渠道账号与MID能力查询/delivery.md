# 渠道账号与 MID 能力查询确认记录

## 1. 本轮授权与交付范围

- 数据讨论期间，用户明确要求先讨论，不修改文件；这轮之前的字段结论不能被当作已落库。
- 用户逐表确认渠道、账号、MID、能力主表与明细，决定本期移除健康状态表。
- 用户明确主链路讨论只限五张配置表支撑的“候选 MID、能力与账号执行配置的业务查询”，不同时展开支付、授权、退款标准执行 API。
- 2026-09-21 用户要求：“帮我把前面聊的sql和接口改动等整理成一个需求文档”。本轮据此登记文档及必要索引，不延伸至源码、正式模型、SQL 执行或部署。
- 文档整理归档已有讨论，不倒退重跑已经确认的字段评审。接口职责已经讨论；详细请求响应及待定事项不冒充完成评审。
- 随后用户要求开始实现，并明确遵守开发规范和单元测试规范，授权五表模型与两个查询的实现。
- 实施澄清确认消费者国家、账号 JSON 含渠道凭据存 Vault，以及原交易允许 SUSPENDED/CLOSED、DRAFT 拒绝。合同细化见[实施合同](./implementation-contract.md)。
- 用户另行明确授权在本机 `127.0.0.1:13306` 初始化 `mor_payment_channel_test`、受限测试账号和五表并验证；不包括 `mor_payment` 或其他现有业务库迁移。

## 2. 确认结论

| 主题 | 结论 |
|---|---|
| Channel | 删除 adapter_code，其余身份/状态字段沿用 |
| Account | 增加 OE、account_name、remark；删除 account_code、external_account_id、integration_config_key |
| 账号保护 | account_vault_token 引用 CHANNEL_ACCOUNT_CONFIG_V1；用户明确选择 Vault 托管该账号 JSON，包括渠道 API 凭据 |
| Account 与 MID | 同 OE/Channel 可多账号、多 MID；每个 MID 当前绑定一个账号；凭据先按账号组织 |
| MID | 暂保留已列字段及 Channel 内外部 MID 唯一性；不恢复 MID Credential 表 |
| 能力拆分与名称 | 主表 t_mid_capability；明细 t_mid_capability_detail；不使用 t_mid_capability_scope 作为最终表名 |
| 明细类型 | 用户再次明确要求直接存储类型，最终保留 value_type；BOOLEAN / DECIMAL / CODE |
| 国家 | country_code 为冻结的消费者国家 |
| MCC | 主表的业务适用范围，精确四位值，不使用 *；多条配置以真实渠道能力为前提 |
| 支付体系 | 继续使用 schemeCode/scheme_code，含义为支付体系；撤回 cardSchemeCode 改名建议 |
| 健康状态 | 本期移除 t_mid_health_state，不引入健康统计、评分与自动熔断 |
| 查询入口 | queryMidCandidates 与 getChannelExecutionConfig；执行配置查询包含按操作的配置校验，原交易沿冻结账号 |

决定收敛与后续事项只在[需求 §6](./requirement.md#open-items)维护。匹配细则属于实施合同，用户逐项确认的业务选择在上方单独记录。

## 3. 交付物与检查

| 工件 | 内容 |
|---|---|
| [requirement.md](./requirement.md) | 五表完整字段、关系、两个接口、SQL 清单、未决项与验收标准 |
| [technique.md](./technique.md) | 现状差异、模块边界、非空迁移要求及消费者影响 |
| [范围决定](../../../agent-docs/decisions/2026-09-21-channel-account-mid-capability-queries.md) | 与旧 Payment/Channel 文档的取代边界 |
| [implementation-contract.md](./implementation-contract.md) | 公开入口、匹配、用途与状态、Vault 合同、事务及消费者边界 |
| [channel-account-config.schema.json](./channel-account-config.schema.json) | Payment 拥有的账号配置结构 |
| [vault-policy-fragment.json](./vault-policy-fragment.json) | 供环境维护者合并审查的窄策略片段 |

文档归档阶段的静态检查与随后实施的编译、真实数据库测试、HTTP Client 测试、架构门禁及覆盖率分别记录在[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)，不把历史文档检查当作运行验收。

规范自查：两个 class 请求通过 Foundation Processor 触发 Bean Validation；ChannelExecutionContext/MidCapabilityContext 与单表 Repository 均归 Channel；多表输出使用 View，不暴露持久化对象；只读快照结束后才 Reveal。没有本地业务写入，因此写锁、原子写入、CAS、命令重放和提交后事件不适用。测试采用正式应用启动、独立测试模块、真实本地数据库和外部 HTTP DTO Mock，不添加生产测试分支。共享库迁移、运营维护、Routing 消费者与真实渠道接入不在本次授权内。

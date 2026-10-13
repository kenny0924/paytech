# Channel 查询实施合同

本文件细化[需求正文](./requirement.md)，记录 2026-09-21 实施期间的用户确认与实现选择。运行、验证和 SQL 执行状态统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

## 1. 已收敛决定

- `country_code` 为 Commerce 冻结到 Payment Intent 的**消费者国家**；不表示发卡国家。
- 用户明确选择将账号 JSON（包括渠道 API 凭据）存入 Vault。此为 `CHANNEL_ACCOUNT_CONFIG_V1` 的专项例外，取代旧规范对此类渠道账号材料的 Secrets Manager-only 限制；不扩大卡数据、登录密码、Vault/KMS 根密钥或任意 Payload 的范围。
- 原交易后续操作及原操作恢复允许 Channel、Account、MID 的 SUSPENDED/CLOSED 状态；任何 DRAFT 都拒绝。新支付仍要求三者 ACTIVE。
- 两个接口为 Channel 模块的公开 Processor `process` 入口，不新增 HTTP/Wire、Gateway 注册或运营 CRUD。

## 2. 公开入口

| 业务接口 | Java 入口 | 结果 |
|---|---|---|
| queryMidCandidates | `QueryMidCandidatesProcessor.process(QueryMidCandidatesRequest)` | `List<MidCandidateView>` |
| getChannelExecutionConfig | `GetChannelExecutionConfigProcessor.process(GetChannelExecutionConfigRequest)` | `ChannelExecutionConfigView` |

候选输入为 `entityNo + criteria`。`MidCriteria` 包含精确的 `paymentMethodCode`、消费者 `countryCode`、`currency`、`mccCode`，以及可空 `schemeCode`；不允许调用方用 `*` 扩大检索范围。没有 scheme 只匹配配置 `*`。结果只含三元组和已解析能力，完全不含 Vault 引用或秘密。

执行输入为 `entityNo + channelId + channelAccountId + midId + operation + purpose`；新支付还需 `criteria + amount`。金额是交易币种主单位 BigDecimal，最多 16 位整数和 8 位小数，必须大于零。

| purpose | operation | 配置状态与能力检查 |
|---|---|---|
| NEW_PAYMENT | PAY / AUTHORIZE / AUTHENTICATE | 三者 ACTIVE，账号等于 MID 当前绑定；重新匹配对应 ACTION 能力且必须明确 true，检查配置的金额上下限 |
| ORIGINAL_TRANSACTION | CAPTURE / VOID / REFUND / QUERY | 允许 ACTIVE / SUSPENDED / CLOSED；只按冻结身份与归属读取，不重新套用新交易准入条件 |
| RECOVERY | 上述已定义操作 | 只恢复调用方已有业务操作；状态同原交易，不按当前能力重新选路 |

原交易/恢复的合法性、原始业务操作存在性及冻结坐标的来源由可信调用方负责。Channel 不跨模块读 Attempt，不接受浏览器直接传入“恢复用途”来获取秘密。该接口不是业务授权入口，也不会执行渠道请求。读取成功只证明本地配置与 Vault 读取有效；远端已撤销/过期凭据由后续真实协议调用报告，不能据此承诺远端认证成功。

## 3. 能力匹配

1. 先限定当前 OE 和 Channel/Account/MID 关联，三者 ACTIVE 才进入新交易候选。
2. 完整条件组同时匹配：支付方式、MCC 精确，scheme/country/currency 各允许精确或 `*`；DISABLED 组不参与解析。
3. 按能力代码分别比较精确度，精确度为三个可通配维度中精确值的数量；只有最高精确度参与决胜。
4. 最高精确度只允许一条明细，即使两条值相同也报配置冲突。一个更精确的唯一配置可以覆盖低精确度配置；某代码在精确组缺失时继续使用其匹配通配组。
5. BOOLEAN false 是明确否定，会覆盖低精确度 true。缺失代码不补默认值；Routing 自己检查所需动作。没有任何有效能力的 MID 不返回。
6. 明细必须匹配已注册目录类型，且恰好一个值列非空；Boolean 仅 0/1，CODE 按目录允许值校验，Decimal 使用精确数值。
7. 金额边界不得负数，MIN 不得大于 MAX。不同 MCC/币种配置不能拼接成不存在的条件组合。

开放能力目录由 `payment.channel.capability-definitions` 维护代码、值类型及 CODE 允许值。代码本身不是 Java enum；只有值类型和状态是闭合枚举。初始目录为 ACTION.PAY/AUTHORIZE/AUTHENTICATE/CAPTURE/VOID/REFUND、AMOUNT.MIN/MAX、SECURITY.3DS_MODE。历史 ACTION.SALE、INTEGER 等不得自动改写；新增代码须同时明确消费者及目录定义。

## 4. Vault 账号配置

- 业务库只保存 `account_vault_token`，物理列允许 NULL 以支持 DRAFT；执行读取时要求有效的 43 字符 Base64URL Token。
- Data Type：`CHANNEL_ACCOUNT_CONFIG_V1`，Token Mode：`REFERENCE`；读取 Purpose：`CHANNEL_EXECUTION`，Caller：`payment-orchestration-service`。2026-10-13 起 Platform 执行标准 API 时也以 `platform-support-service` 读取，见[决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)；Payment 后续需新增“只校验不 Reveal”的查询，本期未改。
- 结构见 [JSON Schema](./channel-account-config.schema.json)，完整 UTF-8 Payload 最大 32 KiB（沿用 Vault 上限）。数字 Schema 版本固定为 1；这是结构版本，不是配置发布机制。
- `channelCode`、`channelAccountId`、`entityNo` 必须与本地冻结账号一致；用于拒绝错绑 Token，不代替 Vault 的 Caller/Purpose 授权。
- `parameters`、`credentials` 均为有界字符串键值对象，最多各 64 项，键 1..64 字符，值 1..8192 字符且不全为空白。渠道接入方定义具体参数名称；不假设 externalMidCode 等于 outletId，也不假设 channelCode 等于 integrationCode。
- 严格拒绝重复键、额外顶层字段、隐式数字转字符串与不符归属的值。
- 还原结果只进入本次受控执行所需的 `ChannelExecutionConfigView`，不进入候选、数据库、普通 Context、日志、缓存、MQ 或 BCR。对象不生成打印全字段的 toString，解析失败只输出固定错误文字。
- [Vault policy 片段](./vault-policy-fragment.json)是供环境维护者合并审查的配置材料，不是完整策略替换文件。Protect 属于未来受控账号维护入口，本期只实现 Reveal，不新增管理 API 或部署策略。

## 5. 一致性与错误

两个 Processor 使用 Foundation 公共入口校验，拒绝调用方已有数据库事务。查询内部通过 REPEATABLE_READ 的短只读 TransactionTemplate 读取同一次本地配置快照，避免账号换绑和能力主从更新导致混合配置。所有 Repository 均为单表、只读；无写锁、无 Redis 配置缓存、无执行流水。

执行配置在数据库事务结束后才访问 Vault。该快照是本次读取的配置，不是跨网络执行锁；下一次调用读取最新提交。原交易始终使用冻结账号；当前 MID 换绑不自动改写或否定原账号身份，仍核验 Channel/OE。

- 参数或用途组合非法：公共 `InvalidParameterException`。
- 指定记录不存在、跨 OE/Channel、当前状态不允许或能力拒绝：公共 `BizCheckFailException`。
- 配置歧义、非法值类型、损坏关联、Vault Payload 错绑/结构错误：公共系统异常，失败关闭。
- 数据库与 Vault 访问失败原样通过相应基础设施异常边界向上传播，不改为空候选、不自动重试、不替换账号。

## 6. SQL 与消费者边界

[正式 PDManer](../../../payment-orchestration-service/docs/database/payment-orchestration.pdma.json)与[完整 Schema](../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql)保持一致。完整 Schema 仅用于空库，禁止直接对非空业务库使用；非空数据问题继续见[迁移影响](./technique.md#3-sql与历史迁移)。本期未获授权迁移共享业务数据库。

[测试 DDL](../../../payment-orchestration-service/paytech-payment-test/src/test/resources/init/ddl.sql)仅创建隔离的 `mor_payment_channel_test`、本库受限账号和五张配置表；不含 DROP/TRUNCATE，不删除或重置已有账号。测试仅在空基线后接管本例 Fixture，统一 AfterEach 清理自身数据。

当前任务交付 Channel 的公开查询能力。Routing 选路策略及其可信 scheme/MCC 来源、真实渠道协议动作和运营写接口不属于本次两个查询的实现；现有 RouteService 的占位实现不能因此被宣称已接入真实主链路。后续调用方通过上述入口接入，不穿透 Mapper/Repository/DataObject。

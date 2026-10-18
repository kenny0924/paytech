# Payment 渠道账号、MID 能力与主链路查询需求

登记日期：2026-09-21

Owner：Payment / Channel

授权范围：五表模型、两个内部查询及规范测试；实现、验证和数据库执行状态只在下方当前台账维护。

本需求收敛为五张配置表和两个模块内部业务查询入口。相对前序模型，新增能力明细表、调整 Channel/Account/Capability，移除健康状态表；MID 主表字段沿用。物理工件与环境状态分别核验，不因完整 Schema 更新就视为旧库已经迁移。

- 本文集中保存已讨论的字段、关系、接口职责、SQL 变更清单及待定项。
- [技术与迁移影响](./technique.md)记录现状证据、迁移风险和后续实施边界。
- [确认记录](./delivery.md)记录用户决定及逐步扩展的实施授权。
- [实施合同](./implementation-contract.md)定义请求、匹配算法、状态矩阵、Vault JSON 与公开入口。
- [范围决定](../../../agent-docs/decisions/2026-09-21-channel-account-mid-capability-queries.md)说明对前序设计的精确取代范围。
- 实现、验证与数据库迁移状态以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)为准。

## 1. 背景与范围

Channel 维护渠道、技术账号、MID 及其静态能力。Routing 需要取得候选 MID 和有效能力；渠道执行逻辑需要按已经冻结的渠道坐标读取账号执行配置。

本期先明确这两类主链路查询，不按每张表机械提供 CRUD，也不在候选查询中决定最终路由。

本期包含：

1. 渠道与账号字段精简，账号增加 OE 归属、展示名称、备注及 Vault 账号配置引用。
2. MID 与账号、Channel、OE 的关系。
3. MID 能力拆成主表与明细表；主表维护适用条件，明细保存能力代码、值类型和值。
4. `queryMidCandidates` 与 `getChannelExecutionConfig` 两个业务查询入口。
5. SQL/模型变化清单、历史兼容要求与验收场景。

本期不包含：

- Operations 配置管理接口和页面研发。
- 支付、授权、捕获、撤销、退款等 Channel 标准执行接口的详细合同。
- Callback、Dispute、交易引用等其他业务表的重构。
- MID 健康统计、评分、自动熔断及其持久化。
- 通用 API 配置执行器的重新设计、执行流水、自动重试或发布版本机制。
- 共享业务数据库迁移、真实 Vault 策略部署、服务部署或提交。
- Routing 策略与既有占位选路的替换；本期交付供后续消费者接入的 Channel 查询能力。

[Platform 外部 API 配置与执行](../../20260914/platform-support-外部API配置与执行/requirement.md)拥有标准 API、渠道流程、字段映射、公共脚本和 `executeStatus + outputs` 的现行合同（2026-10-13 起取代原[通用执行修订](../../20260914/platform-support-外部API配置与执行/generic-execution-amendment.md)的 `execute(integrationCode, apiCode, context)` 单次调用形式）。本需求的两个查询不直接复用该执行结果类型，也不把地图、邮件等非支付接入写入 Payment Channel 表。

## 2. 已确定的业务关系

```text
t_payment_channel
    1 ── N t_channel_account（归属一个 OE）
                1 ── N t_channel_mid（当前绑定一个账号）
                            1 ── N t_mid_capability
                                        1 ── N t_mid_capability_detail
```

- 一个 Channel 可以有多个 Account；一个 Account 属于一个 Channel、一个 OE。
- 同一 Channel、同一 OE 可以有多个 Account，不增加 `(channel_id, entity_no)` 唯一约束。
- 一个 Account 可以服务多个 MID；一个 MID 当前只绑定一个 Account。
- MID、当前账号的 Channel 和 OE 必须一致。OE 权威为 CCT 的不可变 `entity_no`，不根据编号推断路由资格。
- MID 暂保留自己的 `channel_id` 和 `entity_no`，不因账号新增 OE 就删除这两个字段。
- 凭据首先按 Account 组织：共享凭据的 MID 可共用账号；需要独立凭据时使用独立账号。本期不恢复 MID Credential 表；账号共享材料与 MID 独有材料并存的真实接入需求另行细化。
- Attempt 冻结 Channel、Account、MID 三元组。原交易后续操作沿用该三元组，不根据 MID 当前绑定改写历史账号。

### 2.1 支付方式与支付体系

继续使用 `schemeCode` / `scheme_code`，中文语义为“支付体系代码”，不改名为 `cardSchemeCode`。

| 维度 | 含义 |
|---|---|
| Channel | 实际对接的机构或渠道 |
| paymentMethodCode | 精确支付产品/方式目录值，不把笼统 CARD/WALLET 分类自动当成已注册产品代码 |
| schemeCode | 本次支付使用的体系或网络；卡组织是其中一种 |

钱包通过自有体系处理且确需按此维度匹配能力时，可以注册对应体系代码；卡钱包仍可能使用底层卡网络。目录成员需按实际接入合同注册，不能把钱包品牌、接入机构和卡组织随意混用。

请求没有该维度或尚未知晓时不伪造代码，只能匹配配置中的 `*`，不能同时命中任意具体体系。`*` 是配置通配符，不是调用方的交易事实。

### 2.2 MCC 与国家

- `mcc_code` 表示该组能力允许承接的业务 MCC，保留在能力主表。
- 一个 MID 可以有多条不同 MCC 的配置，但必须符合渠道实际开通范围；系统支持多条不等于任何渠道 MID 都支持动态 MCC。
- 不新增 MID–MCC 关系表，也不把 MCC 保存为普通能力明细值。
- 本期 MCC 使用四位明确值，不允许 `*`；不能为了选中 MID 而改写上游业务 MCC。
- 开户固定/默认 MCC、可处理的业务 MCC 范围与交易实际使用 MCC 是不同事实。本期不额外增加固定 MCC 字段。
- `country_code` 为消费者国家，取 Commerce 冻结到 Payment Intent 的事实；用户已明确选择该口径，不使用发卡国家替代。

## 3. 五张表的目标字段

以下为五表字段合同；Vault 账号配置结构见[实施合同](./implementation-contract.md#4-vault-账号配置)。所有时间使用 UTC；主键使用项目统一 ID 生成方式。关联为逻辑关系，不创建物理 `FOREIGN KEY` 或 `CHECK`，保留主键、唯一索引和必要的 `NOT NULL`。

### 3.1 t_payment_channel：渠道

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| channel_id | BIGINT | 是 | 渠道主键 |
| channel_code | VARCHAR(64) | 是 | 稳定渠道代码，创建后不变 |
| channel_name | VARCHAR(255) | 是 | 渠道名称 |
| channel_status | VARCHAR(64) | 是 | DRAFT / ACTIVE / SUSPENDED / CLOSED |
| create_time | DATETIME(6) | 是 | 创建时间 |
| update_time | DATETIME(6) | 是 | 最后修改时间 |

唯一约束：`(channel_code)`。CLOSED 沿用终态语义。

删除 `adapter_code`。这不等于删除代码注册的系统函数，也不自动建立 `channelCode = integrationCode` 的隐式映射；支付业务选择通用 API 的映射合同仍需由对应接入设计明确。

### 3.2 t_channel_account：渠道技术账号

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| channel_account_id | BIGINT | 是 | 技术账号主键 |
| channel_id | BIGINT | 是 | 所属 Channel |
| entity_no | CHAR(5) | 是 | 所属 OE，引用 CCT 不可变编号 |
| account_name | VARCHAR(128) | 是 | 可编辑展示名称，不要求唯一 |
| account_vault_token | VARCHAR(64)，ascii_bin | 否；执行读取时必须有效 | CHANNEL_ACCOUNT_CONFIG_V1 的 Vault REFERENCE Token |
| account_status | VARCHAR(64) | 是 | DRAFT / ACTIVE / SUSPENDED / CLOSED |
| remark | VARCHAR(512) | 否 | 备注，不放入秘密或真实凭据 |
| create_time | DATETIME(6) | 是 | 创建时间 |
| update_time | DATETIME(6) | 是 | 最后修改时间 |

删除 `account_code`、`external_account_id`、`integration_config_key`。渠道要求的外部账号标识按实际账号配置 Schema 表达，不再作为统一账号表的固定字段。未来若确有按该标识反向查找的消费者，再明确专门查询合同。

账号信息使用受控结构化 JSON；业务库只保存 Vault Token。草稿可为空；执行读取必须具有有效引用，并按冻结身份校验还原结果。具体字段、大小与读取权限见[JSON Schema](./channel-account-config.schema.json)和[实施合同](./implementation-contract.md#4-vault-账号配置)。

**专项决定：** 用户在实施澄清中明确要求账号 JSON（含渠道 API 凭据）使用 Vault，形成 CHANNEL_ACCOUNT_CONFIG_V1 的窄例外，见[范围决定](../../../agent-docs/decisions/2026-09-21-channel-account-mid-capability-queries.md)。不扩大卡数据、根密钥或其他系统 Secret 的托管边界；策略材料不等于已经部署。

账号新增 OE 后，当前 MID 的 OE 必须与账号一致。账号 OE 建议创建后不可变；账号跨 OE 历史数据的处理见迁移要求，不能通过修改 OE 解释历史交易。

### 3.3 t_channel_mid：渠道商户号

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| mid_id | BIGINT | 是 | MID 主键 |
| channel_id | BIGINT | 是 | 所属 Channel |
| channel_account_id | BIGINT | 是 | 当前执行账号 |
| external_mid_code | VARCHAR(128) | 是 | 渠道侧 MID 代码 |
| entity_no | CHAR(5) | 是 | 所属 OE，创建后不变 |
| mid_status | VARCHAR(64) | 是 | DRAFT / ACTIVE / SUSPENDED / CLOSED |
| activated_at | DATETIME(6) | 否 | 首次激活时间 |
| closed_at | DATETIME(6) | 否 | 关闭时间，未关闭为空 |
| create_time | DATETIME(6) | 是 | 创建时间 |
| update_time | DATETIME(6) | 是 | 最后修改时间 |

唯一约束沿用 `(channel_id, external_mid_code)`；账号及 OE 维度的既有访问索引继续作为物理设计输入。

本轮没有确认删除 `activated_at`、`closed_at` 或 MID 的 OE 冗余字段。MID 账号调整仍需受控；已冻结的执行账号不随当前绑定变化。外部 MID 不必然等于渠道 HTTP 请求里的 Outlet/Store 标识，执行参数按渠道合同解析。

### 3.4 t_mid_capability：MID 能力配置主表

一条记录代表某个 MID 在完整条件组合下的一组能力配置。

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| mid_capability_id | BIGINT | 是 | 能力配置主键 |
| mid_id | BIGINT | 是 | 所属 MID |
| payment_method_code | VARCHAR(64) | 是 | 精确支付方式目录值，不允许 `*` |
| scheme_code | VARCHAR(64) | 是 | 支付体系目录值或 `*` |
| country_code | CHAR(2) | 是 | 消费者国家条件或 `*` |
| currency | CHAR(3) | 是 | 交易币种或 `*` |
| mcc_code | VARCHAR(4) | 是 | 获准业务 MCC，四位数字，不允许 `*` |
| capability_status | VARCHAR(64) | 是 | ENABLED / DISABLED，整组是否参与匹配 |
| create_time | DATETIME(6) | 是 | 创建时间 |
| update_time | DATETIME(6) | 是 | 最后修改时间 |

唯一约束：

```text
(mid_id, payment_method_code, scheme_code, country_code, currency, mcc_code)
```

完整组合防止把“某 MCC 只支持 AED、另一 MCC 只支持 USD”错误组合成两个 MCC 均支持所有币种。

### 3.5 t_mid_capability_detail：MID 能力配置明细

一条记录代表某组配置下的一项能力及其值。**明细直接保存 `value_type`；此前仅从目录推导、不落类型字段的建议已撤回。**

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| mid_capability_detail_id | BIGINT | 是 | 明细主键 |
| mid_capability_id | BIGINT | 是 | 所属能力配置 |
| capability_code | VARCHAR(128) | 是 | 能力目录代码 |
| value_type | VARCHAR(64) | 是 | 当前代码的值类型：BOOLEAN / DECIMAL / CODE |
| boolean_value | TINYINT(1) | 条件必填 | BOOLEAN 时填写，值为 0/1 |
| decimal_value | DECIMAL(24,8) | 条件必填 | DECIMAL 时填写，使用精确数值 |
| code_value | VARCHAR(128) | 条件必填 | CODE 时填写，允许值由能力定义规定 |
| create_time | DATETIME(6) | 是 | 创建时间 |
| update_time | DATETIME(6) | 是 | 最后修改时间 |

唯一约束：`(mid_capability_id, capability_code)`。

- 三个值字段必须且只能有一个非空，并与 `value_type` 一致。
- 对系统已注册的能力，保存类型还必须与能力定义一致；不能同一个代码在不同配置中随意切换类型。
- `boolean_value=0` 表示明确不支持，不等于缺少配置或整组 DISABLED。
- 明细不单独启停；整组启停由主表控制。必要能力没有有效配置时，不能默认支持。
- 暂不保留 `integer_value` / INTEGER；有旧整数能力时必须先明确消费者及迁移规则，不能丢弃或自动改变类型。

示例（目录代码示意，不是初始化数据）：

| capability_code | value_type | boolean_value | decimal_value | code_value |
|---|---|---:|---:|---|
| ACTION.AUTHORIZE | BOOLEAN | 1 | NULL | NULL |
| ACTION.REFUND | BOOLEAN | 0 | NULL | NULL |
| AMOUNT.MAX | DECIMAL | NULL | 5000.00 | NULL |

### 3.6 本期移除 t_mid_health_state

本期目标不保留该表，也不以另一张表或缓存方案恢复健康评分、技术成功率、熔断等能力。静态状态与能力继续参与候选筛选。

这只是目标删除决定，不能直接对现有数据库执行 DROP；历史数据及 Routing 健康字段消费者须按[迁移影响](./technique.md#3-sql与历史迁移)核对。

## 4. 主链路 API

### 4.1 总体边界

| 接口 | 主要消费者 | 职责 |
|---|---|---|
| queryMidCandidates | Routing | 返回指定 OE 与交易维度下的候选 MID 及有效能力 |
| getChannelExecutionConfig | 渠道执行逻辑 / Card 受控适配 | 按冻结三元组校验本次操作的配置可用性，并读取执行配置 |

两个接口是 Channel 提供的内部业务入口。同一 Payment 进程优先本地公开接口，不为模块隔离新增微服务、HTTP 路由或 Gateway 注册。参数和结果由 Channel 定义，不暴露 DataObject、Repository 或表级 CRUD。

具体 Java 入口、字段必填矩阵、错误与限额见[实施合同](./implementation-contract.md#2-公开入口)。以下是内部业务合同，不新增 HTTP Wire Contract。接口只读配置，不选择最终路由、不调用外部渠道、不写 Attempt/交易记录；数据库事务内也不得调用 Vault、Secrets Manager 或其他外部服务。

### 4.2 queryMidCandidates

**用途：** 回答“该 OE 在这次交易条件下有哪些可评估的 MID，各自有什么有效能力”。

输入（交易维度封装在 `criteria`）：

| 参数 | 语义与约束 |
|---|---|
| entityNo | 交易已确定的 OE；必须来自可信业务上下文，不允许跨 OE 搜索替代主体 |
| paymentMethodCode | 精确支付方式目录代码 |
| schemeCode | 支付体系；适用且已知时提供；不传只匹配配置 `*` |
| countryCode | 必填，冻结的消费者国家，两位大写代码 |
| currency | 本次交易币种 |
| mccCode | 上游确定的业务 MCC，不根据候选 MID 反推或改写 |

返回候选集合，每项包含 `channelId`、`channelAccountId`、`midId`、`capabilities`。能力结果使用具名 `CapabilityView` 列表，包含代码、值类型和对应值。

处理规则：

1. 检查当前 Channel、Account、MID 的状态及 Channel/OE 一致性；新交易候选要求满足启用条件。
2. 根据完整交易维度匹配 ENABLED 的能力主表，并读取明细。
3. 明确处理具体条件与 `*` 的优先级；每个生效能力必须能唯一确定。歧义按配置错误处理，不能随机选一条。逐代码最高精确度、回退与冲突算法见[实施合同 §3](./implementation-contract.md#3-能力匹配)。
4. 没有候选返回空集合；配置错误、依赖故障不伪装成正常空集合。
5. 不返回账号秘密或原始受保护 JSON。

Routing 按所需动作、金额上下限、路由策略和优先级评估这些结果并最终选路。该查询不需要为了重复判断金额再机械接收一份金额参数；具体所需能力和交易金额由 Routing 掌握。

### 4.3 getChannelExecutionConfig

**用途：** 回答“已选定的 Channel、Account、MID，本次操作可否使用，以及执行要读取哪些配置”。

输入（交易维度封装在 `criteria`）：

| 参数 | 语义与约束 |
|---|---|
| entityNo | 校验业务归属 |
| channelId | 已冻结 Channel |
| channelAccountId | 已冻结 Account |
| midId | 已冻结 MID |
| operation / purpose | 区分操作和 NEW_PAYMENT / ORIGINAL_TRANSACTION / RECOVERY，组合见实施合同 |
| criteria / amount | 新支付必填；完整维度与主单位精确金额。原交易与恢复不重新套用当前新支付能力 |

返回 `ChannelExecutionConfigView`：`channelCode`、`externalMidCode`、本次受控执行所需的 `accountConfiguration`。该结果含还原的账号参数和凭据，只允许可信执行边界短时使用，不进入普通 Context、候选列表、HTTP 响应、日志、缓存或 MQ。

处理规则：

1. 执行前重新检查配置和本次操作所需条件，不能只复用更早的候选查询结论。
2. 原交易使用冻结账号；不能根据 MID 当前的 `channel_account_id` 替换历史账号。当前绑定改变本身也不能被机械解释为原交易的冻结三元组非法，需核验其可信来源和归属。
3. 新支付与捕获、撤销、退款、查单、恢复的校验规则须区分。原交易/恢复允许 SUSPENDED/CLOSED，DRAFT 拒绝；新支付三者都必须 ACTIVE。Vault 拒绝或失效引用仍失败；真实渠道侧凭据有效性由后续协议调用确认。
4. 不存在、归属不符、配置冲突或本次操作不允许时明确失败，不自动寻找替代账号或 MID。
5. 每次读取采用一致的配置视图；配置更新可影响后续读取，不新增版本发布机制。执行前检查不等于持有跨网络调用的配置锁，本地使用短 REPEATABLE_READ 只读事务，事务结束后才调用 Vault；不缓存查询结果。

### 4.4 调用关系

```text
Routing → queryMidCandidates → 能力/金额/策略评估 → 选择三元组
Execution → 冻结三元组
渠道执行边界 → getChannelExecutionConfig → 校验与配置读取 → 按独立执行合同调用渠道
```

暂不单独提供“查询渠道详情”“查询账号配置”“查询 MID 能力”“校验 MID 可执行”等主链路入口；这两类业务查询已经覆盖当前消费者。运营查询和管理接口另行规划。

## 5. SQL 变更清单

本节记录相对前序模型的结构差异。[正式 Schema](../../../paytech/payment/docs/database/sql/payment-schema.sql)是空库结构入口；非空业务库须另行盘点并准备增量 SQL，禁止以完整 Schema 代替迁移。专用测试库入口见[实施合同 §6](./implementation-contract.md#6-sql-与消费者边界)。

| 对象 | 计划变化 |
|---|---|
| t_payment_channel | 删除 adapter_code，同步表注释及关联代码目录引用 |
| t_channel_account | 新增 entity_no、account_name、remark；新增受保护配置引用方向 account_vault_token（合同见 Q-02）；删除 account_code、external_account_id、integration_config_key |
| 账号索引 | 随删除字段移除对应唯一索引；不新增 Channel+OE 唯一约束；按候选查询审查访问索引，既有其他索引不因本稿自动删除 |
| t_channel_mid | 本轮字段与唯一约束暂沿用；新增账号 OE 后补齐应用层一致性校验 |
| t_mid_capability | 原逐能力行改为条件组合主表；移出 capability_code、value_type、boolean_value、decimal_value、code_value，移除 integer_value；保留状态并改为整组语义 |
| t_mid_capability 唯一键 | 改为六列条件组合，不再包含 capability_code |
| t_mid_capability_detail | 新增本稿九个字段，以及 (mid_capability_id, capability_code) 唯一键 |
| t_mid_health_state | 从本期目标移除；关联消费者和历史数据先核对，再准备实际删除步骤 |

业务初始化 DML、Route/Permission/Console Resource 注册 DML 本轮均不新增。历史数据转换需求不等于已经存在获批迁移 DML。

非空库必须单独处理：账号跨 OE、旧账号定位字段承载的数据、旧 INTEGER 能力、旧 MCC 通配符、同一条件下不同能力的混合启停状态、主从 ID 映射，以及历史 Attempt 冻结账号的兼容。不得通过清空表、随意取第一行或重写历史三元组规避这些问题。

## 6. 决策收敛与后续事项

<a id="open-items"></a>

| 编号 | 当前决定 / 待定问题 | 边界 |
|---|---|---|
| Q-01 | 已确认：消费者国家 | 来源为 Commerce 冻结的 Payment Intent 事实；Routing 消费者接入另行实施 |
| Q-02 | 已确认：账号 JSON 含渠道 API 凭据使用 Vault；Owner Schema、REFERENCE Token、Purpose 与窄策略见实施合同 | 不扩大其他 Secret 边界；真实环境策略部署单独处理 |
| Q-03 | 实施合同已细化：完整组匹配、逐代码最高精确度唯一胜出、false 覆盖、缺失回退 | 旧状态不能无损自动合并 |
| Q-04 | 状态矩阵已确认；用途、输入、错误与事务边界见实施合同 | 原交易沿冻结账号；调用方负责已有操作合法性 |
| Q-05 | 待定：共享业务库的非空迁移映射和恢复方案 | 仅获准初始化专用 mor_payment_channel_test；业务库未迁移 |
| Q-06 | 未来钱包接入的商户标识、是否存在 MCC、具体支付体系代码 | 当前五表只提供扩展方向，不以通用 scheme_code 宣称所有钱包产品已适配 |

## 7. 验收标准

以下为验收标准；实际覆盖范围与检查结果只见当前台账，标准本身不证明部署完成。

| 编号 | 验收要求 |
|---|---|
| AC-01 | 同 Channel/OE 可有多个 Account；当前 Account/MID 的 Channel、OE 一致；不跨 OE 选路 |
| AC-02 | adapter_code、三个旧账号固定字段不再被新模型/新查询依赖；账号受保护配置按 Q-02 的最终合同处理 |
| AC-03 | 条件主表与能力明细唯一性成立，value_type 持久化，恰好一个值字段合法，布尔 false 不等于缺失 |
| AC-04 | country_code 保留；scheme_code 表示支付体系；缺少请求体系只匹配 *；MCC 精确匹配且无 * |
| AC-05 | 候选查询输出完整冻结三元组和有效能力；无候选与配置错误可区分；不返回秘密或最终选路决定 |
| AC-06 | 执行配置查询按操作重新校验；原交易沿冻结账号；当前 MID 改绑不导致静默换账号 |
| AC-07 | 停用后的新支付与原交易后续处理遵循明确矩阵；不存在用缺失条件绕过能力校验的路径 |
| AC-08 | 两个查询不发起外部渠道操作、不写交易记录；受保护材料读取不在数据库事务内 |
| AC-09 | 新模型/查询不创建健康状态表、自动熔断或替代统计方案；候选按静态能力返回，Routing 消费者适配独立交付 |
| AC-10 | 启用非空业务库前完成迁移核验，不丢弃 INTEGER、MCC 通配符或混合状态数据，不改写冻结交易身份 |

实现与验证证据见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。专用测试库通过不等于共享业务库迁移、Routing 消费者接入或真实渠道上线。

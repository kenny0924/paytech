# 渠道账号与 MID 查询：技术差异及迁移影响

本文补充[需求正文](./requirement.md)，不重复维护字段与接口定义。用户已授权模型、源码和规范测试，另行明确只允许初始化专用测试库。细化合同见[实施合同](./implementation-contract.md)。

## 1. 现状与目标

| 证据 | 前序差异 / 消费者边界 | 目标入口 |
|---|---|---|
| [当前完整 Schema](../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql) | 前序含 adapter_code、旧账号字段、单表能力和健康表；本需求的五表取代这些定义 | [需求 §3](./requirement.md#3-五张表的目标字段) |
| [Payment 数据库规范](../../../agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md) | 前序五表是 Channel/Account/MID/Capability/Health，与本轮五表组成不同 | [范围决定](../../../agent-docs/decisions/2026-09-21-channel-account-mid-capability-queries.md) |
| [RouteContext](../../../payment-orchestration-service/paytech-payment-routing/src/main/java/com/paytech/payment/routing/domain/RouteContext.java) | 包含 Client、OE、Money、paymentMethodCode、captureMode，尚无独立 schemeCode、countryCode、mccCode | 调用候选查询前需收敛可信输入来源，不能猜默认值 |
| [RouteService](../../../payment-orchestration-service/paytech-payment-routing/src/main/java/com/paytech/payment/routing/service/RouteService.java) | 提供本地 route 能力，结果为三元组 | 保留 Routing 最终选择职责，不把策略选择搬入 Channel |
| [Execution 路由修订](../../20260913/payment-Execution设计/execution-field-simplification-amendment.md#9-受理前路由) | 受理前选路，Attempt 冻结三元组；原交易不按当前账号重路由 | 两个查询接入此协作边界 |
| [Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md#32-明确禁止进入-vault-的数据) | 原规范不允许 Channel Secret；当前用户确认账号 JSON 的窄例外 | [实施合同 §4](./implementation-contract.md#4-vault-账号配置)，不推导其他 Secret 权限 |

这些是仓库工件证据，不代表已连接数据库或验证线上行为。实际实施状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

## 2. 模块与事务边界

- 五张配置表均由 Channel 持有。Routing 和 Execution 不直接读取 Channel Mapper/Repository 或跨模块 JOIN。
- Channel 对外提供候选/执行配置领域结果；Routing 负责所需动作、金额与策略筛选，Execution 负责冻结选择、支付业务状态和资金事实。
- 同进程依赖通过 Owner 公开 Service/Processor，遵守单向无环边界。两个公开 Processor 的类名及结果见[实施合同](./implementation-contract.md#2-公开入口)；Context 和五表持久化保持在 Channel 内。
- 本地查询可以在短只读事务中获得一致数据；任何 Vault、Secrets Manager、外部渠道调用必须在数据库事务外。两个主链路查询不新增写表、幂等流水或调用流水。
- 账号配置引用、获取秘密的权限和实际执行位置分开定义。通用查询不返回秘密，Card 明文处理继续遵守 Card 受控边界。
- 一个配置查询结果不能被解释为无限期执行许可。调用方应在实际执行前完成对应检查，每次查询读取当前已提交快照，不引入查询缓存；不通过长事务包住外部调用。

## 3. SQL与历史迁移

SQL 对象清单集中在[需求 §5](./requirement.md#5-sql-变更清单)。完整 Schema 和专用测试 DDL 的路径见[实施合同 §6](./implementation-contract.md#6-sql-与消费者边界)；不提供未经目标环境盘点的非空库迁移脚本。

### 3.1 空库与非空库分开

- 空库：以唯一正式 [PDManer](../../../payment-orchestration-service/docs/database/payment-orchestration.pdma.json)及一致的完整 Schema、注释和索引为准；测试 DDL 仅投影本次五表到专用测试库。
- 非空库：先只读盘点，设计逐条转换及核验，再生成需求目录下的唯一增量执行文件。明确目标库、前置版本、备份、顺序、是否可重跑及恢复限制。
- 不创建物理 FK/CHECK；应用负责关联、枚举、类型与金额约束，数据库保留 PK/UK/NOT NULL。

### 3.2 必须先处理的数据问题

| 问题 | 处理要求 |
|---|---|
| 一个旧 Account 下有多个 OE 的 MID | 不能随意取一个 OE 回填；需明确账号拆分与历史三元组的兼容方式 |
| 账号无 MID，无法推导 OE | 从可信开户/业务资料明确归属，不编造默认 OE |
| 旧 account_code/external_account_id/integration_config_key 仍被消费者使用 | 先梳理用途和配置迁移映射，验证新读取路径，再删除列和相关唯一键 |
| 旧能力 ID 与新主表 ID 语义不同 | 按六维条件分组建立新主表，建立旧行→新明细→新主表映射；不能仅改表名就完成拆分 |
| 同一条件下部分能力 ENABLED、部分 DISABLED | 新模型只有主表状态，无法机械无损合并；须评审业务意图，不将 DISABLED 自动改成 boolean false |
| 旧 INTEGER / integer_value | 逐项确认实际消费者、边界和类型转换；未确认不能删除或无条件转 DECIMAL |
| 旧 mcc_code='*' | 获得实际获准 MCC 集合后再转换，不能用任意默认 MCC 或空值替代 |
| 旧 scheme 仅为卡组织 | 核对目录及消费者，扩大说明不等于自动注册所有钱包品牌；不全局改写其他模块的卡组织字段 |
| 删除健康表 | 核对 Writer、Reader、任务、缓存和模型关系；停用依赖后再评审数据保留与 DROP |
| Routing 候选/决策复制健康字段 | [数据库规范](../../../agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md)仍含健康快照列；本轮只确认不使用健康能力，未逐字段批准这些表的迁移，需另列改动而非静默删列或伪造健康值 |

只备份结构不足以恢复删除的数据。实际删列/删表与回填的恢复方案必须基于目标环境数据设计，不能声称 DDL 反向执行即可完整回滚。

### 3.3 索引与字符规则

- 删除账号三项旧字段时检查 `uk_channel_account_code`、`uk_channel_account_external`、`uk_channel_account_config` 的消费者并同步移除。
- 不自动删除 `uk_channel_account_owner` 或其他既有访问索引；是否冗余由最终查询、并发检查与模型决定。
- 能力主表 UK 为六维组合，明细 UK 为主表 ID + 能力代码。新索引名称按模型规范确定，不增加多套等价索引。
- `entity_no` 沿用 CHAR(5) 与明确大小写语义；受保护 Token 按其最终合同使用区分大小写的存储/比较。其他 Code 统一由目录校验，完整字符集、排序规则、默认值及注释在物理设计时核对。
- 同一组条件的保存与明细替换须原子；本期尚未设计 Operations 写接口，不能据此添加一组管理 API。

## 4. 交付与后续接入

本期包含五表模型、查询 Processor、真实 Mapper 与 Vault Client 读取、独立规范测试。测试从正式 PaymentApplication 和公开入口启动；外部 Vault 仅在测试模块通过正式 HTTP DTO 配置响应，生产无测试分支。

后续接入 Routing/真实执行前，必须补齐可信 scheme/MCC 来源、路由策略、目标环境非空迁移和 Vault 策略配置；保持最终路由和资金状态的 Owner。实际完成与尚未接入的范围只在当前台账记录。

本轮不新增 HTTP API、Gateway Route、Permission、Console Resource、业务初始化数据或页面。因此注册 DML 为 0；这不免除后续账号/能力旧数据迁移设计。

## 5. 与通用配置执行器的衔接

Channel 查询提供支付业务身份、能力和获准执行配置。通用执行器读取 API/脚本/模型并完成单次协议调用，不接管 MID 选路、冻结账号解释、退款资格或业务恢复。

`channel_code` 与 `integrationCode` 的映射、账号参数与通用受控连接解析器的装配，以及系统函数如何获取必要值，属于接入合同的后续细化。删除 adapter_code/integration_config_key 不允许实现者自行假设同名映射，也不证明这些消费者已迁移。

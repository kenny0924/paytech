# Payment 模块划分

> **工具表后继目标：** 用户已确认删除旧 Instrument、由 Attempt 直接关联 Execution 所属的交易级 Token；详细字段、接口及 Card 受控适配职责见[修订评审稿](../payment-Execution设计/instrument-token-amendment.md)。下列表清单是前一版映射，不表示新表已落模或旧表已删除。

> 命名后继：现行模型及表归属使用[全系统 Channel 命名决定](../../../agent-docs/decisions/2026-09-13-payment-channel-terminology.md)。下文按新名称同步，四模块职责与表 Owner 保持；此前评审和确认历史见 delivery.md。

状态：Execution、Routing、Channel、Card 四业务模块划分已确认，用户进一步要求落地工程拆分，见[本次范围决定](../../../agent-docs/decisions/2026-09-13-payment-module-scaffolding.md)。本轮在原 api/web/test 之外创建四个普通业务 JAR，由 Web 组合根装配，共七个 Maven 子模块；具体业务接口与首个落地用例继续细化。新增或修改 HTTP 接口、表、字段、索引、DML 和页面均为 0。父工程 packaging=pom，不计入七个子模块。

## 1. 模块划分

以下均为 `paytech-payment-` 前缀下的模块：

| 模块 | 负责的业务或工程能力 |
|---|---|
| execution | 支付 Intent、Submission、Attempt、Action 与消费者动作；支付侧退款、Void、重复成功处理、状态与成功定价事实。拥有支付状态推进和金额不变量 |
| routing | 路由策略与版本、候选评估、选择结果和选择证据；在已冻结 OE 内选择 Channel/Account/MID |
| channel | Channel/Account/MID 配置、静态能力与动态健康度；非卡明文渠道协议适配、回调验签与归一化、渠道交互记录和渠道侧争议事实 |
| card | 卡采集处理、PAN 加密存储、内部 Card Token、受控用卡、卡访问审计及接触卡明文的渠道执行片段 |
| api | 供其他服务消费的内部 HTTP 契约；不承载共享内部领域模型或持久化对象 |
| web | 唯一 Spring Boot 入口、Controller/MQ/Job 协议入口、跨模块 Processor、配置与服务级技术表接入 |
| test | 真实服务链路、状态与并发测试、模块架构检查及外部系统 Simulator |

采用四块的理由：Execution 的支付生命周期、Routing 的策略与解释证据、Channel 的渠道配置/协议、Card 的受保护用卡各有明确 Owner。相比把 Routing 也塞进 Execution，独立路由模块能让策略配置、候选筛选和支付状态推进分别演进；相比单独拆 Refund、Callback、3DS 或每个 Channel，当前四块足以表达已批准能力并控制协作成本。

Refund、Void、Customer Action/3DS 状态留在 Execution；NI、Geidea 先按 Channel 模块内部适配包组织，其中接触原始卡的片段必须位于 Card。后续是否新增 JAR 由真实依赖与业务边界证明，不按渠道数量机械拆分。

### 命名与术语边界

- 业务能力 JAR 命名为 `paytech-payment-channel`，Java 业务根包采用 `com.paytech.payment.channel`；具体 Channel 的适配代码按工程规范位于其中的 `integration` 边界，卡明文片段继续由 Card 封装。
- Channel 表示支付渠道及其能力，包含渠道资料、Account/MID、静态能力、健康状态、协议、通信和回调；自有实体和字段统一为 Channel，NI、Geidea 等外部协议原字段在适配边界保留。Payment Channel 与 Platform 的访问 Channel 是不同业务概念。
- 不额外创建名为 Channel 的业务实体、渠道类型枚举或持久化表。文档涉及模块时写 Payment Channel/支付渠道模块，避免与 Platform 的访问 Channel（OPERATIONS、CLIENT、PARTNER）混淆；支付方式仍使用既有 Payment Method 术语。

## 2. 当前差异与系统影响

| 事项 | 现有证据 | 本方案补充 |
|---|---|---|
| 应用模块 | 改前仅 api/web/test；[父 POM](../../../payment-orchestration-service/pom.xml)为工程入口，状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-business-modules) | 创建四业务 JAR 和包边界，接入 Web；工程骨架不表示业务实现 |
| Payment 数据资产 | [现有完整 Schema](../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql) | 为每张现有表指定建议代码 Owner，结构不变 |
| Card | [已确认需求](../../20260912/payment-自建CardVault/requirement.md)确认独立模块；[数据稿](../../20260912/payment-自建CardVault/data-design.md)仍有待评审内容 | 保留已确认边界，候选表只映射模块，不通过本建议批准字段、恢复合同或技术表迁移 |

| 系统/仓库 | 本轮影响 | 后续范围 |
|---|---|---|
| 工作区需求文档 | 新增本评审稿及检索入口 | 确认后的模块设计由此维护，公共规则仍链接各主题 Owner |
| payment-orchestration-service | 只读核对工程和数据资产 | 首个已确认用例实施时建立所需 JAR、业务接口、Owner 持久化与架构门禁 |
| Commerce、Risk、CCT、Finance、Foundation、Gateway、前端 | 本轮无修改 | 既有支付/Card 专项按其自身确认点推进，模块拆分不批准其未实现合同或接入 |

本轮没有新的外部业务能力或全局时序变更，因此不新增需求站业务编号，不修改公共需求目录或全局时序。

## 3. 表的唯一归属建议

以下是对当前 Schema 的代码 Owner 映射，不表示表已被应用或持久化代码已存在。表字段、状态及历史迁移仍由既有业务权威负责，不因表名含 `channel` 就归入 Channel 模块。

| Owner | 现有表 |
|---|---|
| execution | `t_payment_intent`、`t_payment_attempt`、`t_payment_pricing_fact`、`t_payment_instrument`、`t_payment_action`、`t_payment_intent_binding`、`t_channel_transaction_ref`、`t_duplicate_payment_case`、`t_payment_state_transition`、`t_payment_refund_position`、`t_channel_refund` |
| routing | `t_routing_policy`、`t_routing_policy_version`、`t_routing_policy_mid`、`t_routing_decision`、`t_route_candidate_eval` |
| channel | `t_payment_channel`、`t_channel_account`、`t_channel_mid`、`t_mid_capability`、`t_mid_health_state`、`t_channel_interaction`、`t_channel_callback`、`t_channel_callback_item`、`t_channel_event_exception`、`t_channel_dispute_notice`、`t_channel_dispute_event`、`t_channel_dispute_action` |
| web 服务级基础设施 | `t_command_idempotency`、`t_compensation_event` |

Card 专项候选表单独映射，不计入上面的现有表清单：

| Owner | 候选表 | 原因 |
|---|---|---|
| execution | `t_payment_submission` | 一次 Pay 的受理与恢复身份属于支付生命周期 |
| card | `t_payment_card`、`t_card_access_event` | 卡库及受保护值访问证据属于 Card |

其中 Channel Transaction Ref 表达支付 Action 的资金结果与退款目标导航，归 Execution；Channel Interaction 表达对渠道的一次通信，既服务支付动作也服务渠道举证动作，归 Channel。Channel 维护的争议状态只表示渠道收到和返回的事实，业务争议决定仍由 Risk 拥有。

每个 Owner 独占自己表的 DataObject、Mapper、Repository 和 SQL；其他模块通过公开业务能力访问，不共享 DataObject，不跨模块 JOIN。服务级技术表保持服务内唯一；本方案不批准 Payment 接入 Foundation 命令幂等的新消费者迁移，该事项继续由 Card 专项评审。

## 4. 依赖与协作

默认 Maven 依赖为 `test → web → api/execution/routing/channel/card`。Execution 切片的 test 另显式声明直接消费的 api/execution，用于公开合同测试及完整 JaCoCo 聚合；该测试依赖不改变任何生产模块依赖。四个业务模块默认互不依赖，均不反向依赖 web/test，也不借 api 共享内部业务对象。使用同一个进程、数据库及事务管理器。

跨模块用例由 web Processor 调用 Owner 发布的窄业务 Service 接口；参数/结果由 Owner 定义，使用 Domain/Application Command/Result，不暴露持久化对象或任意 CRUD。稳定能力以后确需直接复用时，再按项目 §3.4 的门禁批准单向无环依赖。

路由协作示例：

1. Execution 提供可信的 Intent/Attempt 约束；Channel 提供候选 MID、Account、能力和健康度业务查询结果。
2. Web 将所需领域输入交给 Routing。Routing 拥有过滤、排序、策略匹配与选择理由，不把算法放在 Web 或 Channel。
3. Routing 冻结选择证据，Execution 冻结相同 Channel/Account/MID 并建立 Action；需要原子保存时，由 Web 的同一本地短事务调用两个 Owner 的写能力。

渠道执行和回调协作：

- Execution 判定允许执行的动作与发送状态；Channel 提供已冻结账户对应的非敏感执行上下文。原始卡输入从受控 Web 入口交给 Card，PAN 解密及向 NI 传卡留在 Card 内，不进入普通 Channel 模块适配器或 Execution。
- Card 对外返回受限结果；由 Web 调用 Execution 写回支付事实、调用 Channel 写入经过裁剪的通信结果。Card 不直接写 Execution 的 Action 或 Channel 的 Interaction 表。
- 回调协议入口在 Web；Channel 验签、去重、归一化并保存信封，Execution 通过公开查询/命令接口完成原交易定位和状态推进，Channel 保存最终处理结果。Web 负责两者的协作，Channel 不直接更新支付表。
- Channel/KMS/Commerce/Risk 等外部调用全部位于数据库事务外。结果写回通过新的本地短事务执行；同事务参与的模块不能提前提交或使用 REQUIRES_NEW 拆开原子范围。

详细发送/恢复阶段及原值权限继续遵循 Card 专项和 Payment 业务合同。该协作示例不新增 Detokenize API，不允许仅凭 Token 任意用卡，不将模块边界当作运行隔离证明。

## 5. 后续落地与验证建议

本次按用户要求先完成七模块工程骨架：Parent 管理四业务模块版本；Web 直接依赖 api 与四业务 JAR，Test 只依赖 Web；四业务 POM 用 Enforcer 禁止依赖本服务 api/web/test/其他业务模块。每个新模块仅有 POM 和根包说明，不创建占位业务类型或额外运行配置。架构测试验证模块/包/入口和现有表 Owner 边界，实际构建产物还需检查四 JAR 进入 Web 可执行制品。

本地开发验证运行完整 Payment reactor，复用原应用真实 HTTP 回归；本次不增加数据库、中间件或渠道测试前置。后续业务用例仍需按下列规则补齐真实合同、状态/事务与 Card 安全场景。Card 未确认字段/恢复细节仍需按原专项评审。

未来实际建立或调整模块时，验收至少包括：

- 每张表的唯一 Owner 与现有模型/Schema 对照，无遗漏或重复。
- Maven 依赖图无环；架构检查能阻止跨模块 Impl、内部 Service、DataObject、Repository、Mapper 与 SQL 越权访问。
- 从真实服务/业务入口验证一次路由与 Action 冻结的一致性、跨模块事务回滚和回调重放；NI 使用测试模块内的 Simulator。
- Card 用卡权限、受限输出和普通模块无原始卡数据；服务级幂等/补偿表不复制。
- 依照本次实际实现范围运行构建、测试，分别记录业务验证、数据库状态及部署证据。

# 目标架构 V2 技术方案（讨论稿）

- 状态：阶段 1 讨论稿。本文是画板内容的文字摘要，权威细节以画板与[决定草案](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)为准；实现与部署状态只见 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
- 画板：[assets/architecture-design.pen](./assets/architecture-design.pen)（6 个 frame）。时序：[acquiring-sequence.md](./acquiring-sequence.md)。术语：[glossary.md](./glossary.md)。

一句话规模总结：本方案涉及 24 个逻辑服务（其中 7 个为新增边界：计费、数据服务、争议处理、消费者、工单与审批、业务事件目录、合规与案件拆分），初期约 13 个部署单元，6 个可选模块，33 条跨系统决策；不新增接口、表或页面，不执行 SQL。

## 1. 分层与依赖规则（D08）

| 层 | 判定标准 | 服务 | 依赖 |
|---|---|---|---|
| 接入层 | 逻辑接入通道，不拥有事实 | Nginx / Gateway、Checkout Web（无 BFF，D04）、Client / Partner / Operations 三个独立 BFF、Client API 与渠道回调两个机器入口 | 只做协议、身份校验、限流、路由、请求幂等 |
| 流程层 | 围绕一笔交易的生命周期 | 收单（Acquiring）、支付执行（Payment）、出款编排、争议处理、外汇交易 | 可调领域层；按四类写操作与下游终态裁决（D17 / D18） |
| 领域层 | 非交易编排的领域事实：主数据、规则、资金账 | 商务（Commercial）、客户主档、消费者、卡 · PCI、风控决策、合规与案件、渠道连接、计费、账户与余额、会计、清算与对账、税务与发票、汇率、数据服务 | 不得调流程层，只经事件感知交易 |
| 平台支撑 | 横切能力，不拥有业务事实 | Platform、工单与审批引擎、业务事件目录、Vault、数据与分析（可选）、公共组件 | — |

## 2. 关键边界

- **收单（D31 / D33）**：`acquiring-service` 内含 transaction / checkout / command / client-api 四模块共用一库；Checkout Session 为交易的可选子聚合，Direct Pay 不经 Session。支付执行独立服务与部署，Acquiring → Payment → Channel 三层保持。
- **渠道（D06）**：渠道连接是支付渠道领域唯一 Owner；Platform 集成仅做通用出站执行与凭据读取；支付执行不直连渠道、不持凭据。非支付外部方由各自 Owner 经 Platform 集成接入。
- **资金（D05 / D16）**：计费是唯一经济事实来源；账户是可用额度实时权威，会计是法定报表权威，两者只消费计费明细，按经济事实 ID 幂等，周期核对、差异挂起。三服务独立库，一致性用 Saga / TCC 式多段本地事务 + 补偿 + 核对，不用 XA。
- **读模型（D25）**：数据服务建在近实时 OLAP 数仓上，CDC 同步各服务白名单表 DML（不经 MQ），每实例一套；Client / Partner 只走预定义查询模板 + 服务端租户过滤；详情与操作前校验实时读 Owner；不触发 Webhook（D07）。
- **身份（D26 / D29）**：服务间用 Platform 签发的内部签名 Token；消费者不设账号，买家门户用收单签发的订单级 Token。
- **合规传播（D27）**：结算限制变更 → 出款拒绝、账户停止在途转可用，新收单不受影响。
- **法律主体（D28）**：所有法律主体含平台自身法人都是客户主档的 Party，会计只引用。
- **消费者（D14 / D30）**：交易侧存不可变快照；消费者服务持可变档案并作为删除编排 Owner。

## 3. 一致性模式（D17 / D18 / D24）

四类跨服务写操作：A 查询后冻结（版本校验）；B 可逆跨服务命令（锁定、占用；五步：写意图 → 提交后调用 → 回写 → 补偿重试 → 终止规则，取消是下游命令并由下游终态裁决）；C 不可逆外部命令（渠道、银行；只有确认、恢复查询与人工终止）；D 事件消费（幂等，更正用新事件）。

事件：按聚合键分区有序，事件携带聚合版本号，消费者按版本判定乱序与重放；每个事件在业务事件目录登记 Schema、版本、聚合键、生产者与消费者。

## 4. 实例 × 模块 × 品牌（D03 / D20 / D21 / D22）

- 实例是数据隔离边界（独立库 / MQ / Redis / Vault / KMS）；运营方决定运营后台账号归属；品牌 = 展示配置 + 法律引用，入口域名定新会话品牌、交易创建时冻结；租户只能用被分配的品牌。
- 基座必装；可选模块：卡收单、出款、外汇、争议、数据与分析、（未来）订阅 / 分期 / 分账。税务与发票属基座。装配规则：基座不依赖模块并定义缺席行为与最小登记；模块只依赖基座，模块间依赖显式成图；模块自带服务与库、事件、Console 页与资源目录、路由与 API Scope、配置 Schema、SQL。模块三态：启用 / 停用（只读）/ 卸载，有在途禁止卸载。
- 多实例运维平面：按实例清单组合部署、版本与兼容预检、配置与品牌下发、模块授权、脱敏遥测；不持任何实例数据库凭据，Schema 变更由实例内迁移执行器执行（D11 / D22）。
- 实例内地理分站为正交的部署形态，三项前置设计未完成前不允许分站（D23）。

## 5. 部署单元建议（D09）

逻辑服务 = api + core 模块 + 独立库，不含启动类；`*-app` 模块按下表组合启动，各模块注册自己的 DataSource / 事务管理器 / Mapper 范围；服务间只经 `*-api` 调用，进程内绑本地 Bean、跨进程绑 HTTP，配置切换；ArchUnit 强制包边界。拆分触发：独立扩缩容、故障隔离、安全隔离、发布节奏。

| 部署单元 | 逻辑服务 | 说明 |
|---|---|---|
| client-contract | 商务 · 客户主档 | 低频主数据 |
| acquiring | 收单 · 消费者 | 独立于 payment（D33） |
| payment | 支付执行 · 渠道连接 | 独立；PCI 范围止于此与卡域 |
| card（条件成立时） | 卡 · PCI | D10 三条件成立才独立 |
| risk | 风控决策 · 合规与案件 | 实时 SLO 与批处理冲突时拆风控决策 |
| finance | 计费 · 账户 · 会计 · 清算与对账 · 税务 | 各自持库（D16） |
| payout / dispute / fx | 可选模块 | 装卸即部署单元 |
| data-service | 数据服务 | 每实例一套数仓，始终独立 |
| platform | Platform · 工单与审批 · 事件目录 | 控制面 |
| vault | Vault | 独立安全边界 |
| gateway / 3 × bff / web | 入口 | 运营 BFF 永不共用 |

## 6. 新旧映射与迁移路径（D15 / D19）

| V2 服务 | 承接现有 | 方式 |
|---|---|---|
| 收单 | Commerce（Order / Checkout / Consumer Context / 接受记录 / 退款请求 / Cancel · Reversal） | 保留 · 演进 |
| 支付执行 | Payment Execution（Intent / Attempt / Action / 路由 / Instrument Token） | 保留 · 演进；按核对结果 Action 矩阵、恢复、回调接近新建 |
| 争议处理 | Risk Dispute + Payment Channel Dispute | 拆出 |
| 出款编排 | Finance Manual Payout | 拆出 |
| 商务 | CCT Agreement / Pricing Plan / Price / Component / Band / Profile | 拆出，扩展产品目录与报价 |
| 客户主档 | CCT Party / Client / Partner / OE / Scope / Affiliation | 保留 · 演进 |
| 消费者 | Commerce Consumer Context + Vault Token | 拆出 |
| 卡 · PCI | Payment Card 模块 | 升级隔离（D10） |
| 风控决策 / 合规与案件 | Risk 实时部分 / Case 部分 | 拆出 |
| 渠道连接 | Payment Channel / Account / MID / Capability + Platform Integration 调用 | 拆出 |
| 计费 / 账户 / 会计 / 清算与对账 / 税务 | Finance 各部分 | 拆出，独立库 |
| 汇率 / 数据服务 / 工单与审批 / 事件目录 | 无 | 新建 |
| Platform / Vault / Gateway · BFF · Web | 现有 | 保留 · 演进 |

迁移路径：阶段 0 立契约（事件目录、业务模式属性、原则门禁）→ 阶段 1 拆 Finance 与 Risk → 阶段 2 拆支付执行 → 阶段 3 补公共能力 → 阶段 4 PSP 资金线。每个边界切换前需运行证据（D15）；写入方切换以"新建归新 Owner、存量归原 Owner 到终态"为粒度，回退 = 停止新建、不搬数据（D19）。

## 7. NFR 目标（D32）

单实例收单峰值 100–500 TPS；门户列表新鲜度 ≤ 10 秒；支付同步路径 P95 ≤ 2 秒（不含渠道）；RTO 1 小时 / RPO 接近 0；事件至少一次、分区有序；收单热路径可用性 99.9%。

## 8. 对现行规范的影响

- D11 要求修订 `DEVELOPMENT_CONVENTIONS.md` 与 `DATABASE_DESIGN_GUIDE.md` 中"人工执行 SQL"的执行方式表述（评审原则不变）。
- D26 替代 Vault / 内部调用的 `X-Caller-Service` + 静态白名单。
- D09 的构建期组合与 `JAVA_PROJECT_STRUCTURE.md` 的模块规则需要在"阶段 0"对齐。
- 以上修订均未执行；现行文档继续描述运行系统。

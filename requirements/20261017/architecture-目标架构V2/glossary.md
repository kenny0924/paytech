# 目标架构 V2 术语表

依据决策 D01、D31（见[决定草案](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)）。中文名用于画板与讨论，英文名用于服务、模块、代码与接口命名。

## 1. 参与方

| 中文 | 英文 | 定义 | 不是 |
|---|---|---|---|
| Client | Client | 平台客户，`INDIVIDUAL` / `ORGANIZATION` 两类（沿用现行定义，不改为商户） | Merchant |
| 消费者 | Consumer | 在收银台或 Client 系统发起支付的最终付款人 | Customer（Platform 人类身份 Realm） |
| 合作伙伴 | Local Partner | 提供 OE 主体并获得独立配置分润的外部合作方 | Client |
| 签约主体 | Operating Entity (OE) | 与支付渠道签约、持有 MID 的法律卖方；`entity_no` 为国家码 + 序号 | Finance Entity |
| 平台法人 | Platform Legal Party | 收服务费、持平台账簿的法人；是客户主档的 Party（D28） | 单独的 Finance Entity 主档 |
| 运营方 | Operator | 拥有并运营某个实例的公司（自营或代运营伙伴） | 租户 |
| 品牌 | Brand | 实例内的白标展示配置 + 法律引用（D21） | 租户、实例 |
| 实例 | Instance | 一套完整部署，独立数据库 / MQ / Redis / Vault / KMS，是数据隔离边界 | 地理站点 |

## 2. 流程层服务（围绕一笔交易的生命周期）

| 中文 | 英文 / 服务名 | 范围 | 不含 |
|---|---|---|---|
| 收单 | Acquiring / `acquiring-service` | Client 委托平台收的每一笔款：交易与冻结上下文、Checkout Session（可选子聚合）、消费者快照与法律证据、取消 / 退款请求 / 冲正命令；两种受理模式 Hosted Checkout 与 Direct Pay；内含 transaction / checkout / command / client-api 四模块共用一库 | 支付执行、渠道、价格、余额 |
| 支付执行 | Payment / `payment-service` | Intent / Attempt / Action、路由决策、回调处理、Void、渠道退款、Instrument Token；唯一接收原始卡数据的公共入口；独立部署（D33） | 渠道适配（归渠道连接） |
| 出款编排 | Payout | 出款单生命周期：申请、双人审批、Execution、提交、凭证、确认借记；可选模块 | 余额锁定与扣减（归账户） |
| 争议处理 | Dispute | 拒付 / 争议完整生命周期与资金影响指令；可选模块 | 保证金消费（归账户） |
| 外汇交易 | FX Deal | 兑换单生命周期；可选模块，未决 | 汇率（归汇率服务） |

## 3. 领域层服务（非交易编排的领域事实）

| 中文 | 英文 / 服务名 | 范围 |
|---|---|---|
| 商务 | Commercial / `commercial-service` | 产品目录、价目、报价、合同；业务模式 MoR / PSP 在合同层定义 |
| 客户主档 | Client Master | Party、Client、合作伙伴、OE 与 Scope、Affiliation；所有法律主体的唯一权威 |
| 消费者 | Consumer | 付款人可变档案、联系方式 Token、删除编排 Owner（D30） |
| 卡 · PCI | Card | 受控卡数据域：存储、平台卡令牌、CVV 瞬时处理；独立服务条件见 D10 |
| 风控决策 | Risk Decision | 实时决策引擎：规则、模型、名单、限额、结账点与出款检查点评估 |
| 合规与案件 | Compliance & Case | KYB / KYC 审核决定、Case、AML / 制裁、结算限制、最低保证金要求、监管报送 |
| 渠道连接 | Channel Connector | 支付渠道（收单机构、APM、钱包）的接入、账号、MID、能力、适配、验签、映射、恢复查询、健康度；不含非支付外部方（D06） |
| 计费 | Billing / Fee Engine | 唯一经济事实来源：按合同价格与交易事实生成不可变计费明细；各产品场景由模块注册 |
| 账户与余额 | Account | 可用额度实时权威：余额、冻结、在途、锁定、保证金、追偿；命令终态裁决（D17） |
| 会计 | Ledger | 法定报表权威：复式记账与多法人账簿 |
| 清算与对账 | Settlement & Reconciliation | 渠道 / 银行对账、差错、清算批次、结算单、实际成本确认 |
| 税务与发票 | Tax & Invoice | 税计算、代扣代缴（MoR）、收据 / 贷项单、发票；基座必装（D03） |
| 汇率 | FX Rate | 汇率源、点差、报价；外汇敞口与平盘未决 |
| 数据服务 | Data Service | 近实时 OLAP 数仓上的查询读模型，CDC 同步各服务白名单表 DML；替代交易中心（D25） |

## 4. 支撑与入口

| 中文 | 英文 | 范围 |
|---|---|---|
| Platform | Platform | 身份与权限、租户、API 凭证、公共控制面、通知与 Webhook、外部集成、文件；Webhook 唯一触发源（D07） |
| 工单与审批引擎 | Workflow & Approval | Maker-Checker、多级审批、工单；只记录"人已同意"（D13） |
| 业务事件目录 | Event Catalog | 事件定义、Schema 版本、聚合键与版本字段、生产 / 消费登记（D24） |
| Vault | Vault | 非卡敏感值 Protect / Reveal、令牌化、KMS；仅私网内部调用 |
| 多实例运维平面 | Multi-instance Operations Plane | 统一部署、升级、配置下发、模块授权、脱敏遥测；不持任何实例数据库凭据（D22） |
| 网关 | API Gateway | 入口、认证、限流、路由、Request ID、`Idempotency-Key` 请求幂等 |
| BFF | Backend for Frontend | Client / Partner / Operations 三个独立 BFF；不设 Checkout BFF（D04） |

## 5. 关键概念

| 术语 | 定义 |
|---|---|
| 业务模式 | 合同属性，取值 MoR / PSP；在支付准备时冻结到交易；驱动卖方记录、税务责任与资金归属账簿（D02） |
| 经济事实 | 计费生成的不可变、带版本、带经济事实 ID 的明细；账户与会计只消费它（D16） |
| 四类跨服务写操作 | A 查询后冻结 / B 可逆跨服务命令 / C 不可逆外部命令 / D 事件消费（D18） |
| 五步模式 | 写意图 → 提交后调用 → 回写结果 → 补偿重试 → 终止规则；仅适用于 B 类（D17 / D18） |
| 部署单元 | 由 `*-app` 模块组合若干逻辑服务启动的进程；逻辑服务各自持库，仅经 `*-api` 互调（D09） |
| 模块三态 | 启用 / 停用（只读）/ 卸载；有在途实体禁止卸载（D20） |
| Hosted Checkout / Direct Pay | 收单的两种受理模式：经 Checkout Session 由消费者完成，或由 Client 经 API 直接提交并以 Client 承诺作为接受模式 |
| Order | 不作为服务名；"订单"仅指收单交易；出款 = Payout，兑换 = FX Deal，退款 = Refund Request（D31） |

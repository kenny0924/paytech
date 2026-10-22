# counterparty（交易相对方）的职责与设计

> 状态：**已确认（2026-10-19）**，决定记录 [2026-10-19 counterparty 重新定位](../../agent-docs/decisions/2026-10-19-counterparty-repositioning.md)；Q1–Q3、Q5、Q6 均按建议（第 8 节）。首个切片"改名"已实施并部署 mor-test（2026-10-20，main `c661e9fc`），见 [counterparty-consumer服务改名](../../requirements/20261019/counterparty-consumer服务改名/delivery.md)。同日用户确认：① 不建平台级 Consumer，只管 **Client 命名空间下**的相对方；② Customer 增加可变档案；③ D30 删除编排（请求表、逐 Owner 任务表、擦除事件）**不在 MVP 实施**，归入 AGENTS.md 要求的数据合规专项；④ MVP 的删除只是现有 `DELETE /customers` 顺带擦除档案；⑤ 系统**重新定位并改名**：`consumer` → `counterparty`（交易相对方），为将来给 Client 下的收款人代发（如主播分成）预留位置。依据现状调查（`paytech/consumer`、`paytech/acquiring`、`paytech/payout` 源码与模型）、[目标架构 V2 决定](../../agent-docs/decisions/2026-10-17-target-architecture-v2.md) D29 / D30 / D42、[V2 词汇表](../../requirements/20261017/architecture-目标架构V2/glossary.md)、[收单端到端时序](../../requirements/20261017/architecture-目标架构V2/acquiring-sequence.md) Stage 2、[第 03 步任务卡](../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/03-acquiring-consumer.md)。讨论定稿后登记决定记录。配图 `counterparty.pen`（四张画板：职责与设计、数据库表、场景例子、未来扩展），导出图在 `assets/`。

## 1. 一句话

**counterparty 是"Client 命名空间内、不与平台签约的交易相对方主档"。** 现在它管付款的一方（Customer）：身份、当前档案，以及付款人资料进出 Vault 的规范化与保护；将来扩展收款的一方（Payee）。它不保存订单时点快照（acquiring）、不保存卡（card）、不管收款账户（payout）、不做审核决定（compliance）、不做风控名单（risk-decision），不存任何个人原值（只存 Vault Token 与批准的派生维度）。

## 2. 定位与命名（2026-10-19 确认）

### 2.1 和 client-master 的边界

| | client-master | counterparty |
|---|---|---|
| 和平台的关系 | 与平台**签约**（Client、Local Partner、OE，统称 Party） | **不与平台签约**，属于某个 Client 的命名空间 |
| 在交易里的角色 | 商务主体 | 付款方（买家）、收款方（主播 / 创作者等） |
| 形态 | 法人为主 | 个人为主，也可能是公司（如主播背后的 MCN） |

### 2.2 命名

| 名字 | 含义 |
|---|---|
| 服务 `counterparty-service`、库 `mor_counterparty`、目录 `paytech/counterparty` | 交易相对方主档；替换现名 `consumer-service` / `mor_consumer` / `paytech/consumer` |
| **Customer**（角色） | 付款角色；`t_customer` 与 Client API `/customers` 保持不变（与 Stripe 等业界习惯一致） |
| **Payee**（角色，未来） | 收款角色；将来新增 `t_payee`（见第 10 节） |
| Consumer（业务概念） | Hosted Checkout 上付钱的人（含访客），不再作为服务名 |

选择理由：付款方与收款方都是交易的"相对方"，个人与公司都能装下；业界有同义先例（Modern Treasury 的 Counterparty 指"你付钱给他或他付钱给你的一方"，下挂外部账户）。避开已占用的词：Party（client-master）、Customer（Platform Realm 名）、Account（account 服务）。ledger 的异常码 `COUNTERPARTY_MISMATCH` 是会计含义的枚举值，不冲突。

### 2.3 改名的影响（单独登记需求实施）

调用方只有 acquiring，Gateway 尚无 Route，现在改成本最低。按 D42 改名口径（Nacos 不保留旧名别名，同分支改完全部调用方）：模块目录与 Maven 坐标、应用名与发现名、库名与账号（`mor_consumer` → `mor_counterparty`）、arch-rules 表归属、acquiring 的 client 与配置、本地环境与 mor-test 配置；文档同步 V2 词汇表、目标架构决定 D30（"消费者服务" → "交易相对方服务"）、CURRENT_IMPLEMENTATION。

## 3. 现状与目标的差距

| 能力 | 现在（2026-10-19） | 目标（MVP） |
|---|---|---|
| 服务名 | `consumer-service` / `mor_consumer` | `counterparty-service` / `mor_counterparty` |
| Customer 身份 | `t_customer`：按 `(client_id, active_reference)` 建或取；删除不可撤销 | 不变 |
| 已存卡失效 | 删除同事务登记 `STORED_CARD_INVALIDATION`，事务外调 card | 不变 |
| 存卡资格 | 组合 Customer 状态与 client-master 开关，失败关闭 | 不变 |
| Customer 档案（回头客预填） | **没有**：每次结账重填；资料只在 acquiring `t_consumer_context` | `t_customer_profile`：当前档案的 Vault Token 与派生维度 |
| 付款人资料保护（Vault Protect / Reveal） | **没有真实实现**：acquiring 的 `ConsumerProtectionPort` 是固定 Mock（`mock-email-token`，`TODO(Vault)`） | counterparty 提供 protect / reveal 内部接口，独占 Vault 调用（Q1） |
| 删除时的档案 | 无档案可删 | `DELETE /customers` 同事务把档案置 ERASED 并清空 Token 引用 |
| 删除编排（D30） | 没有 | **MVP 不做**，归数据合规专项（见第 9 节） |
| Client API 外部可达 | **不可达**：Gateway 没有该服务的 Target / Route（CURRENT_IMPLEMENTATION） | 建 Route（Q3） |
| 文档与命名 | README 写已存卡在 payment-service，实际配置已是 `card-service`；类名仍叫 `PaymentStoredCardClient` | 随改名一并修正 |

## 4. 职责边界（提案）

### 4.1 counterparty 拥有什么

1. **Customer 身份**：Client 命名空间内、由 Client 服务端传入不透明 `consumerReference` 确定的已登录买家；`customer_id` / `customer_no` 全局稳定（S6）。访客没有 Customer。
2. **Customer 当前档案**：Email / Phone 的稳定 Vault Value Token、姓名 + 完整账单地址的 Profile Token、批准的派生维度（邮箱域名、电话国家码、账单国家）。可变、带版本，只代表"最近一次填写"，不是交易证据。
3. **相对方资料保护**：规范化、调 Vault Protect 取 Token、按 Token Reveal 或返回脱敏值。acquiring 不再直连 Vault（Q1），个人资料原值只进 counterparty（Q6）。
4. **存卡资格**（已有）：只组合判断，不复制 client-master 的开关。
5. **删除编排 Owner 的身份**（D30）保留，但 MVP 不实施编排本身。
6. **将来**：Payee 身份、KYC 级资料 Token、税务身份、验证状态投影（第 10 节）。

### 4.2 counterparty 不拥有什么

| 不拥有 | 归属 | 说明 |
|---|---|---|
| 订单时点的消费者快照、法律接受、网络证据 | acquiring | 交易时点不可变事实；档案变了也不回写订单 |
| 已存卡、卡令牌 | card | counterparty 只转发 Client 的列出 / 删除，并在删除时触发失效 |
| 收款账户（银行、钱包） | payout | 将来 Payee 的收款账户扩展 payout 的 `t_beneficiary_account` |
| KYC 审核决定、制裁筛查、Case | compliance | 将来只存结果投影 |
| 名单、频次计数 | risk-decision | counterparty 只提供稳定 ID 与 Email / Phone Token |
| 收据、发票、代扣税 | tax | |
| 营销同意 | Client 自己 | MoR 平台不做营销 |
| 与平台签约的主体 | client-master | Party 不进 counterparty |
| 跨 Client 的同一个人 | 不建 | 同一个人在两个 Client 下是两条记录 |
| 原值与真正的销毁 | Vault / 数据合规专项 | AGENTS.md：MVP 不做自动销毁；Vault 不建业务表 |

### 4.3 设计原则

- **只存 Token**：任何姓名、Email、Phone、地址、证件原值都不落 `mor_counterparty`；日志、MQ、补偿载荷也不带。
- **档案不是证据**：acquiring 在结账时把 Token 复制进自己的 `t_consumer_context`；之后档案更新或擦除，都不改已有订单。
- **事务公理**：Vault 调用、card 调用都在本地事务之外；本地写只在短事务里。
- **角色各自独立**：Customer 与将来的 Payee 各有身份表，同一个 Client 用户可以同时是两者，只做关联、不合并。

## 5. 两条主要流程

### 5.1 结账时保存与预填档案（Q6 已定：原值只进 counterparty）

浏览器侧路径不变（`/customer-gateway-api/v1/checkouts/{checkoutToken}…`），只是 Gateway 把资料提交路由到 counterparty。内部接口名为草案，在切片 2 的阶段 3 定稿。

```
浏览器 ──GET checkout──> acquiring
  acquiring：订单已有快照 → 带 Token 调 counterparty 取脱敏值（只回脱敏值）
            订单有 customer_id → 调 counterparty 取该 Customer 档案脱敏值 + profile_version
  浏览器：显示"使用上次资料"或重新填写（Q2：只给脱敏值）

浏览器 ──PUT …/checkouts/{token}/consumer（原值 或 useSavedVersion）──> Gateway ──> counterparty
  [事务外] counterparty 调 acquiring 内部接口按 Checkout Token 取 {orderId, clientId, customerId?, 是否可编辑}
  [事务外] 规范化 → Vault Protect（Email / Phone 稳定 Token、Profile 新 Token）；useSavedVersion 则复用档案 Token
  [短事务] 有 customerId：upsert t_customer_profile（profile_version+1）
  [事务外] 带 Token 与派生维度调 acquiring 内部接口写订单快照
           acquiring 本地事务：再判可编辑（未接受法律条款）→ 写 t_consumer_context，订单 version+1
  返回 COMPLETE（不回显原值）
```

要点：
- acquiring 从此不接触原值，也没有 Vault 凭据。
- 访客不带 `customerId`，counterparty 只做保护，不落任何行。
- acquiring 写快照被拒（如已接受法律条款）时档案已更新为"最近一次填写"，可以接受（档案不是证据）；Vault 里多出的 Token 无引用，不影响业务。
- `useSavedVersion` 版本不一致返回冲突，浏览器重新拉取。

### 5.2 删除 Customer（现有接口顺带擦除档案）

```
Client ──DELETE /customers/{customerNo}──> counterparty
  [本地事务] t_customer 置 DELETED、清空 active_reference
            t_customer_profile 置 ERASED，清空三个 Token 与派生维度（只断引用，Vault 原值不销毁）
            登记 STORED_CARD_INVALIDATION
  [提交后] 补偿任务事务外调 card 失效全部已存卡
```

acquiring 订单快照里的 Token 不动（订单证据按法律保留）；Vault 原值不销毁。

## 6. 表模型（提案）：`mor_counterparty` 3 张

| 表 | 状态 | 用途 |
|---|---|---|
| `t_customer` | 已有，不改（随库改名） | Customer 身份 |
| `t_customer_profile` | 新 | Customer 当前档案，1:1 |
| `t_compensation_event` | 已有，不改 | 已存卡失效 |

### 6.1 `t_customer_profile`

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| `customer_id` | BIGINT | N | 主键，等于 `t_customer.customer_id` |
| `client_id` | BIGINT | N | 冗余，便于按 Client 校验隔离 |
| `profile_status` | VARCHAR(16) | N | `ACTIVE` / `ERASED`；ERASED 不可恢复 |
| `email_vault_token` | VARCHAR(128) | Y | 稳定 Email Value Token；ACTIVE 时必填，ERASED 时清空 |
| `phone_vault_token` | VARCHAR(128) | Y | 稳定 Phone Value Token；可选 |
| `consumer_profile_vault_token` | VARCHAR(128) | Y | 姓名 + 完整账单地址；ACTIVE 时必填 |
| `email_domain` | VARCHAR(255) | Y | 派生维度；ERASED 时清空 |
| `phone_country_code` | VARCHAR(8) | Y | 派生维度 |
| `billing_country_code` | CHAR(2) | Y | 派生维度 |
| `last_order_id` | BIGINT | Y | 最近一次更新档案的 acquiring 订单软引用 |
| `profile_version` | BIGINT | N | 档案内容版本，预填与 `useSavedVersion` 比较用 |
| `version` | BIGINT | N | 乐观锁 |
| `create_time` / `update_time` | DATETIME(6) | N | UTC |

字段与 acquiring `t_consumer_context` 的 Token / 派生维度同名同型，复制时不转换。

### 6.2 不变量（应用层）

- `t_customer_profile` 只为存在且 ACTIVE 的 Customer 写入；ERASED 后拒绝任何写入。
- ACTIVE 档案的 `email_vault_token` 与 `consumer_profile_vault_token` 非空；ERASED 档案三个 Token 与派生维度全为空。
- Customer 置 DELETED 与档案置 ERASED 在同一事务。

### 6.3 关系

```
t_customer 1 ──── 0..1 t_customer_profile
（acquiring.t_commerce_order.customer_id、card.t_payment_stored_card.customer_id 为跨库软引用，不建关系）
```

## 7. 合同变化（提案）

### 7.1 内部接口

路径前缀随改名从 `/consumer/v1` 改为 `/counterparty/v1`。新增接口名为草案，切片 2 阶段 3 定稿。

| 接口 | 方向 | 说明 |
|---|---|---|
| `PUT /customer-gateway-api/v1/checkouts/{checkoutToken}/consumer` | 浏览器 → Gateway → counterparty | 路径不变，路由从 acquiring 改到 counterparty；原值只在这里进入 |
| counterparty 取脱敏值（按 Token 或按 customerId） | acquiring → counterparty | 新。结账页展示订单资料与回头客档案，只回脱敏值与 `profile_version` |
| `POST /counterparty/v1/consumer-details/reveal` | 受控调用方 → counterparty | 新。按 Token 回填原值，调用方与用途白名单 + 审计；MVP 调用方待定（收据、AVS 等随各自需求接入） |
| acquiring 按 Checkout Token 取订单上下文 | counterparty → acquiring | 新（acquiring 侧）。返回 orderId、clientId、customerId、是否可编辑 |
| acquiring 写订单消费者快照（只收 Token） | counterparty → acquiring | 新（acquiring 侧）。替换现 `SaveConsumerProcessor` 的原值入口与 `ConsumerProtectionPort` Mock |
| `resolve` / `GET customers/{id}` / `card-saving-eligibility` | acquiring → counterparty | 已有，语义不变，仅路径前缀改名 |

### 7.2 Client API

已有 6 个路径不变（`/client-gateway-api/v1/customers…`）；`DELETE /customers/{customerNo}` 的语义增加"档案随之擦除"。需要 Gateway Route 指向 `counterparty-service` 才能对外可达（Q3）。

### 7.3 事件

无新增。counterparty 仍不消费 MQ。

## 8. 已定问题（2026-10-19，均按建议）

| # | 问题 | 结论 |
|---|---|---|
| Q1 | Vault Protect / Reveal 由 counterparty 独占，还是 acquiring 直连 Vault | counterparty 独占：规范化与 Token 稳定性只有一个 Owner，也是档案写入的天然入口 |
| Q2 | 预填给浏览器原值还是脱敏值 | 只给脱敏值 + "使用上次资料"：持有 Checkout Token 的人看不到完整资料；要改就整份重填 |
| Q3 | Client Customer API 现在是否开 Gateway Route | 开：否则 Customer 管理没有外部入口 |
| Q5 | Client API 能否直接写档案（不经结账） | MVP 不开放：档案只来自 Hosted Checkout 中消费者本人填写 |
| Q6 | 原值是否只进 counterparty（个人资料边界） | 是：浏览器资料提交经 Gateway 直达 counterparty，acquiring 只见 Token；原值读出（结账回显、将来 Payment 的 AVS / 3DS、收据 Email、Operations 查人）都只经 counterparty 按用途授权与审计。否则独立部署只有成本没有收益 |

Q4 命名：服务叫 counterparty，Customer 作为付款角色名保留。

## 9. 暂不实施：D30 删除编排（归数据合规专项）

2026-10-19 讨论过一版"请求表 + 逐 Owner 任务表 + `ConsumerErasureRequested` / `ConsumerErasureReported` 事件 + 两张消息表"的方案，用户确认从 MVP 拿掉，原因：

- 日常业务不读它；MVP 的"浅执行"（Customer 删除、已存卡失效、档案断引用）现有 DELETE 加一行就能完成，编排表只是在记录这三件事的进度。
- AGENTS.md 要求处理真实受监管数据前先完成数据合规专项（数据分级、留存、Legal Hold、Vault 生命周期、受控销毁、审计），删除编排是那个专项的核心，现在建表大概率要推倒重来。

代价：专项完成前没有"请求 → 各 Owner 回报"的证据链，Operations 没有代客户发起删除的入口，访客无法提删除请求；按 AGENTS.md，这期间本就不能处理真实受监管数据。D30 中本服务是编排 Owner 的结论不变（事件名随改名再定）。

## 10. 未来扩展：代发收款人（Payee，不在 MVP）

场景：Client 下有收款人（如直播平台的主播），平台从 Client 余额代发分成。

### 10.1 counterparty 内会新增

| 新增 | 内容 | 和 Customer 的区别 |
|---|---|---|
| `t_payee` | Client 命名空间内的收款人：`payee_reference`（Client 传入）、状态（待验证 / 可收款 / 暂停 / 关闭）、可选关联同一用户的 `customer_id` | 长期关系，有暂停与恢复；不是删了就完 |
| 收款人身份资料 | 法定姓名、出生日期、国籍、居住地址、证件，全部 Vault Token | KYC 级，比买家档案敏感；改动需重新验证 |
| 税务身份 | 税务居民国、税号 Token、申报表类型 | 买家没有 |
| 验证状态投影 | compliance 审核结果、制裁筛查结果的只读副本 | 决定能否付款 |
| 收款人协议接受 | 收款人同意平台作为代付方的条款版本 | 按人，不按订单 |

### 10.2 归其他系统

| 能力 | 归属 | 说明 |
|---|---|---|
| 收款账户（银行、钱包、卡） | payout | 现有 `t_beneficiary_account`（Token + 盲索引 + 末四位 + 复核状态）的收款方类型从 CLIENT / LOCAL_PARTNER 扩展 PAYEE；盲索引可发现多个收款人共用账户 |
| 批量代发 | payout | 现有"单张 + 双人审批 + 人工提交"不适合 API 大批量，需批次与逐笔明细 |
| 资金来源 | account | 从 Client AVAILABLE 直接扣；建议不给收款人开余额（避免储值 / 电子钱包牌照问题） |
| KYC、制裁筛查、Case | compliance | |
| 出款检查点 | risk-decision | 收款人维度：频次、共用账户、新收款人首笔大额、自付自收 |
| 代扣税与年度申报 | tax | |
| 代发手续费、汇兑费 | billing | 新事实类型 |
| 出款通道 | channel-connector | 银行转账、本地钱包等 |

### 10.3 决定规模的业务问题

1. 法律上谁在付钱：Client 付、平台代付（常见，义务较轻），还是平台直接付？
2. 收款人 KYC 由平台做，还是依赖 Client、平台只做制裁筛查？取决于牌照与法域。
3. 收款人要不要余额（钱包），还是只做直接代发？
4. 收款账户怎么录入：Client 服务端经 API 推送，还是平台托管录入页？后者需要收款人登录或一次性链接，碰到 D29"不引入消费者账号"的边界。

### 10.4 对 MVP 的约束

现有 3 张表不带"只能是买家"的假设，将来加 `t_payee` 及其档案是纯新增。现在就定一条：同一 Client 下的 `payee_reference` 与 `consumerReference` 允许指向同一用户（用于风控识别自付自收），两种角色各自独立、不合并成一张表。

## 11. 首个切片的建议范围

1. 改名：`consumer` → `counterparty`（代码、库、配置、文档；README 与 `PaymentStoredCardClient` 一并修正）。
2. 档案与保护：`t_customer_profile`；资料提交入口经 Gateway 改到 counterparty（Q6）；脱敏值与 reveal 接口；acquiring 改为只收 Token 并删除 `ConsumerProtectionPort` Mock（Vault 以现有 API 为准）；DELETE 顺带擦除档案。
3. 顺带：Gateway Route。

每一项分开登记需求、分阶段确认。

# consumer 重新定位为 counterparty（交易相对方）决定

- Decision status: Confirmed
- Implementation status: 切片 1 改名 Implemented（main `c661e9fc`，2026-10-20，本地提交未推送）；切片 2 档案与保护、切片 3 Gateway Route 未开始
- Deployment status: 切片 1 已部署 mor-test（2026-10-20，counterparty-service / `mor_counterparty`），见 [发布记录](../../requirements/20261019/counterparty-consumer服务改名/release/release.md)
- Decision date: 2026-10-19
- 用户决定：2026-10-19 讨论中逐项确认——不建平台级 Consumer，只管 Client 命名空间下的相对方；Customer 增加可变档案；D30 删除编排从 MVP 拿掉；为将来代发收款人重新定位并改名为 counterparty（"end-user 不好"，"可以，counterparty 吧"）；其余待定项 Q1–Q3、Q5、Q6 按建议（"可以"）。
- 详细设计：[counterparty 的职责与设计](../../design-discuss/counterparty/design.md)，配套图 [design.pen](../../design-discuss/counterparty/design.pen)（四张画板：职责与设计、数据库表、场景例子、未来扩展：代发收款人）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

V2 第 03 步把 Commerce 拆为 acquiring 与 consumer。consumer 只原样搬走了 Customer 代码：`mor_consumer` 两张表（`t_customer`、`t_compensation_event`），没有档案、没有真实的 Vault 保护（acquiring 的 `ConsumerProtectionPort` 是固定 Mock），没有 Gateway Route，与[目标架构 V2](./2026-10-17-target-architecture-v2.md) 词汇表"付款人可变档案、联系方式 Token、删除编排 Owner（D30）"差距大；且浏览器提交的个人资料原值先经过 acquiring，独立部署没有带来个人资料隔离。用户同时提出将来要给 Client 下的收款人代发（如直播主播分成）。

## 2. 决定

1. **定位与改名。** 服务定位为"Client 命名空间内、不与平台签约的交易相对方主档"，改名 `counterparty`：`counterparty-service`、`mor_counterparty`、`paytech/counterparty`，内部合同前缀 `/counterparty/v1`。与平台签约的主体（Party）仍归 client-master。按 [D42](./2026-10-17-target-architecture-v2.md) 改名口径，Nacos 不保留旧名别名，同分支改完全部调用方。
2. **角色名。** Customer = 付款角色，`t_customer` 与 Client API `/client-gateway-api/v1/customers…` 不变；Payee = 收款角色（未来）；Consumer 退为业务概念（Hosted Checkout 上付钱的人，含访客），不再作为服务名。
3. **只管 Client 命名空间。** 不建跨 Client 的平台级 Consumer；同一个人在两个 Client 下是两条记录。访客不建 Customer。
4. **Customer 档案。** 新增 `t_customer_profile`（与 Customer 1:1）：Email / Phone 稳定 Vault Value Token、姓名 + 完整账单地址 Profile Token、批准的派生维度、`profile_version`。只代表"最近一次填写"，不是交易证据；acquiring 订单快照复制 Token，之后档案变更或擦除不回写订单。`mor_counterparty` 为 3 张表：`t_customer`、`t_customer_profile`、`t_compensation_event`。
5. **个人资料边界（Q1、Q6）。** 个人资料原值只进 counterparty：Hosted Checkout 的资料提交经 Gateway 直达 counterparty，counterparty 规范化并调 Vault Protect 后，带 Token 调 acquiring 内部接口写订单快照（订单可编辑性仍由 acquiring 判断）；acquiring 不持有原值、不直连 Vault。原值读出（结账回显，将来 Payment 的 AVS / 3DS、收据 Email、Operations 查人）只经 counterparty，按调用方与用途授权并审计。Vault 凭据只在 counterparty。
6. **预填只给脱敏值（Q2）。** 回头客预填只向浏览器返回脱敏值与"使用上次资料"；使用时 counterparty 直接复用已有 Token，不重复 Protect；要修改则整份重填。
7. **删除（D30 收窄）。** MVP 的删除只是现有 `DELETE /customers/{customerNo}`：同一事务置 Customer DELETED、档案 ERASED 并清空 Token 引用、登记 `STORED_CARD_INVALIDATION`；不销毁 Vault 原值，不改订单快照。D30 的编排（请求表、逐 Owner 任务表、擦除请求 / 回报事件、消息表）不在 MVP 实施，归入 AGENTS.md 要求的数据合规专项；本服务是删除编排 Owner 的结论不变。
8. **外部入口（Q3、Q5）。** 为 Client Customer API 建 Gateway Route 指向本服务；MVP 不开放 Client API 直接写档案，档案只来自 Hosted Checkout 中消费者本人填写。
9. **未来收款人。** Payee 的身份、KYC 级资料 Token、税务身份、验证状态投影、收款人协议接受归本服务；收款账户归 payout（`t_beneficiary_account` 扩收款方类型）、KYC 与制裁筛查决定归 compliance、出款检查点归 risk-decision、代扣税归 tax、代发费归 billing、资金从 Client 余额扣（account，不给收款人开余额）。同一 Client 下 `payee_reference` 与 `consumerReference` 允许指向同一用户，两种角色只关联、不合并。现在不预建任何收款人表。

## 3. 不做与后置

D30 删除编排与数据导出请求、访客删除请求（数据合规专项）；Client API 写档案；跨 Client 识别；Payee 全部能力（另行立项，先回答设计稿第 10.3 节四个业务问题）。

## 4. 实施切片

1. **改名**：consumer → counterparty（代码、Maven、应用与发现名、库与账号、arch-rules、acquiring / card 调用方与常量、本地环境与 mor-test 配置及数据迁移、文档与 PDManer 生成器）；同步修正 README 与 `PaymentStoredCardClient` 命名。
2. **档案与保护**：`t_customer_profile`、protect / reveal / profile 内部接口、资料提交入口改到 counterparty（Gateway Route）、acquiring 改为只收 Token、DELETE 顺带擦除档案。
3. **Gateway Route**：Client Customer API 指向本服务（可与切片 2 合并）。

阶段划分按[需求交付流程](../REQUIREMENT_DELIVERY_WORKFLOW.md)逐需求约定。

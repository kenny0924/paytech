# commercial 六层产品定价模型（含商业关系模式与多币种）

> 状态：阶段 1 评审稿（2026-10-19 登记）。决定记录 [2026-10-19 commercial 六层产品定价模型](../../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md)（Confirmed）；详细设计 [design-discuss/commercial/design.md](../../../design-discuss/commercial/design.md)、[database.md](../../../design-discuss/commercial/database.md)、画板 [commercial.pen](../../../design-discuss/commercial/commercial.pen)（七张）。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景

V2 拆分后 commercial（`mor_commercial`）只有只读的两段式 Checkout 解析，零写入代码；`term_type` 写死三类，没有产品目录与能力开通，`business_mode` 没有字段；billing 首切片依赖的价格命中接口未实现（上游缺口 X3）。2026-10-19 用户确认采用六层产品定价模型，并先后确认：IC++ 本期只留字段、月量阶梯规则归商务 / 用量归 billing、多币种方案 B（Price 加 `currency`）、画像表改名 `t_pricing_profile`、商业关系模式（Direct Merchant / ISV / PayFac）用 `fund_mode`、`partner_role`、`pricing_authority` 三个属性表达。

## 2. 目标

1. 商务成为"卖什么、按什么条件卖、跟谁签了什么"的唯一权威：产品目录、合同（含业务模式、资金模式、定价权）、开通项、商业条款版本、定价画像。
2. 运营可在控制台建合同、签署登记、订阅产品与开通项、配置并排期激活价格版本。
3. 提供 `POST /commercial-prices/v1/resolutions` 与 `GET /commercial-prices/v1/{priceId}`，补齐 billing 的 X3。
4. Checkout 解析响应补 `businessMode`、`fundMode`、`agreementProducts`、开通项与 D02 四项派生，供收单冻结。

## 3. 本期范围（首切片）

| 编号 | 范围 |
|---|---|
| S1 | 模型与 SQL：20 张表（6 新增）、既有表字段改动、画像表改名、Price `currency`、合同三属性；PDManer、完整 Schema、测试 DDL、本机测试库变更（已执行）|
| S2 | 运营写入：合同创建与签署登记、产品订阅与开通项（含约束、保证金条款）、定价画像、Plan / Version / Price / Component / Band 配置、SCHEDULED 排期与激活校验；Processor 锁单资源；审批经 workflow-approval |
| S3 | 价格命中两接口（按 stage3 合同，Band 整表返回）；`GET /commercial/v1/rate-cards` 只读接口供 Client 门户 |
| S4 | 解析响应补字段；risk-decision 资格门禁替换 503 占位；收单冻结属于 acquiring 的配合改动，本需求只定义合同 |
| S5 | 现状偏差收口：Plan `status`、BCR `record_type` 注释、`CCT_` 错误码前缀、trigger / calculation_base / 日历目录 |

取值约束：本期 `pricing_model` 只接受 BLENDED，`band_basis` 只接受 TRANSACTION_AMOUNT，`fund_mode` 只接受 SETTLE_THROUGH_PLATFORM，`pricing_authority` 只接受 PLATFORM；字段进模型，取值放开随后续切片。

## 4. 不做项

IC++ 计算链；月量阶梯与回溯返利；报价（Quote）；标准价目模板管理；门户价目展示页面（只做读接口）；Partner 门户写入；Direct Merchant 的应收与开票链、ISV buy rate 分润计算（模型字段已留，billing / account 侧另行立项）；分账 / 子商户模块；定价 · 成本 · 路由毛利快照（渠道连接与 billing 侧另行立项）；BCR 共享白名单拆分；client-master 的 `partner_role` 与推荐关系表（另行登记 client-master 需求）。

## 5. 验收标准（草案）

- AC-01 六层模型表与字段与 database.md 一致，PDManer 门禁通过，Schema SQL 可在空库执行。
- AC-02 运营可完成"建合同 → 订阅产品 → 开通 Visa（AED / USD）→ 建 Plan / Version / Price → 排期激活"，激活前校验画像 ACTIVE、OE 有效、每开通项 × 币种至少一条 Price、Band 连续。
- AC-03 激活后的版本不可修改；换版只新建；历史版本按 ID 回读不受换版影响。
- AC-04 价格命中接口按"币种精确 → 画像具体度 → priority_no"命中，事实缺失 422 `PRICING_FACTS_INCOMPLETE`、无命中 422 `PRICE_NOT_FOUND`、歧义 409。
- AC-05 Checkout 解析按 `contextAsOfTime` 选版本，未订阅产品 / 未开通支付方式 / 币种不在约束内 → 落到下一候选或失败关闭。
- AC-06 全部写入经 BCR 留档；审批"人已同意" ≠ 可执行。
- AC-07 commercial auto 测试与模型门禁全绿；本机 local-environment 全链路冒烟通过。

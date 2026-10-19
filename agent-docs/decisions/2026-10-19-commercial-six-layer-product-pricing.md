# commercial 六层产品定价模型决定

- Decision status: Confirmed
- Implementation status: Not started（讨论稿定稿；PDManer 模型、SQL、写入能力与价格命中接口均未实现；需求目录待按 `requirements/README.md` 登记）
- Deployment status: Not deployed
- Decision date: 2026-10-19
- 用户决定：commercial（商务 · 售卖）采用六层产品定价模型；2026-10-19 讨论中用户先确认六层模型、IC++ 本期只留字段、月量阶梯"规则归商务 / 用量归 billing"，随后对其余待定项全部"按建议来"。
- 详细设计：[commercial 的职责与设计](../../design-discuss/commercial/design.md)，配套图 [design.pen](../../design-discuss/commercial/design.pen)（三张画板：职责与设计、数据库表与枚举、六层模型例子）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

V2 里没有 CCT 服务：原 client-contract-service 在阶段 4 第 04 步拆为 client-master 与 commercial，Agreement 四表、Payment Method Profile 两表与 Commercial Term 五表归 `mor_commercial`。拆分后商务只有只读的两段式 Checkout 解析，零写入代码；`term_type` 写死三类，没有产品目录与能力开通，`business_mode`（[D02](./2026-10-17-target-architecture-v2.md)）没有字段，billing 首切片依赖的价格命中接口（[stage3 合同](../../requirements/20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)、[billing 讨论稿](../../design-discuss/billing/design.md)缺口 X3）未实现。收单、出款、外汇、争议、平台类收费结构差异大，需要一个能按目录扩展而不改骨架的模型。

## 2. 决定

1. **定位。** commercial 是"卖什么、按什么条件卖、跟谁签了什么"的唯一权威：产品目录、合同（含业务模式）、商业条款版本、定价画像；只提供交易前解析与交易后命中两个只读接口。不算钱（billing）、不管主体（client-master）、不算税（tax）、不持交易状态（acquiring）、不做审批裁决（workflow-approval）。
2. **六层模型。** L0 产品（= 可售模块）→ L1 能力开通（合同在精确 OE 下开通的支付方式 / 出款通道 / FX 等，附 T+N 与保证金条款）→ L2 计费事件（= `component.trigger_code` 值域）→ L3 定价画像（现 Profile 加 `profile_kind`，出款 / FX / 争议复用维度机制）→ L4 价格与组件（精确 OE × 画像，组件按事件各算后相加，`refund_behavior`、`band_basis`）→ L5 条款附属（`pricing_model`、`fee_tax_treatment`）。上层约束下层：没订阅的产品没有开通项，没开通的能力没有价格，没有价格的事件不收费。
3. **业务模式挂合同级。** `t_agreement.business_mode` 取 MOR / PSP，一份合同一个模式，并存签两份。解析响应补 `businessMode`、`agreementProducts`、开通项，并按模式给出 D02 四项派生（卖方 Party、收单 OE、税务责任方、资金归属账簿主体）；收单只冻结不推导。
4. **出款、FX、争议、平台类条款归商务**，价格与收单同样挂精确 OE（MoR 下钱从 OE 出）；模块只向计费事件目录登记 `term_type`。
5. **IC++ 本期不做。** Version 留 `pricing_model`，只允许 BLENDED；IC++ 需 billing 的"估算 → 实际 → 差异"链与对账逐笔成本映射，随 PSP 资金线另行立项。
6. **周期阶梯：规则在商务，用量在 billing。** 组件 `band_basis` 取 TRANSACTION_AMOUNT / PERIOD_VOLUME，`band_mode` 增 RETROACTIVE；命中接口整表返回 Band，billing 按自己的周期用量选档；商务永不读 billing。首切片只做单笔金额阶梯。
7. **换版不加字段。** 使用已有 `effective_from` / `effective_to` 与 SCHEDULED 状态排期；ACTIVE 版本永不修改；解析按 `contextAsOfTime` 选版本，历史交易按冻结版本回读，换版、到期、画像退役不改变已冻结交易。限时价用 SCHEDULED 版本加 `effective_to` 表达，不做覆盖层。
8. **标准价目只是模板**，建 Plan 时复制成自己的 Version，不做引用覆盖。报价（Quote）后置。
9. **签署沿用 `agreement_party.acceptance_method`**：线上接受与线下上传已签文件两条路径产生同一份不可变签署证据。
10. **写入与审批。** 运营经 operations-console-bff 写入；Processor 用 `AbstractLockProcessor` 锁一个资源（合同或 Plan），事务内只写本库 + BCR；workflow-approval 只记"人已同意"，激活前重新校验画像仍 ACTIVE、OE 仍有效、覆盖完整、Band 连续；提交后发布 `AgreementStatusChanged`、`CommercialTermVersionActivated`（只带 ID 与版本）。
11. **失败关闭。** 无命中、事实缺失、父链冲突一律失败，不默认通过、不默认零费率；错误码按 stage3 合同。

## 3. 模型改动（提案已定，实现切片内进 PDManer）

新增 `t_product`、`t_product_capability`、`t_product_fee_event`、`t_agreement_product`、`t_agreement_capability`、`t_agreement_capability_limit`；既有表加 `business_mode`、`profile_kind`、`pricing_model`、`fee_tax_treatment`、`refund_behavior`、`band_basis`；结算四字段从 Price 挪到开通项；`term_type`、Plan `status`、`calculation_base`、`dimension_code`、BCR `record_type` 收口为闭合值。逐表字段见设计稿第 5 节与画板二。

## 4. 不做与后置

IC++ 计算链、月量阶梯与回溯返利、报价、标准价目模板管理、门户价目展示、Partner 合同签署页面、分账 / 多收款方、订阅 / 分期的周期调度。

## 5. 首切片范围

六层目录表与字段改动的模型与 SQL；合同创建与签署登记、产品订阅与开通项、Plan / Version / Price / Component / Band 配置与 SCHEDULED 排期激活；`POST /commercial-prices/v1/resolutions` 与 `GET /commercial-prices/v1/{priceId}`；解析响应补字段与收单冻结；risk-decision 门禁替换 503 占位；第 9 节现状偏差收口。阶段划分按[需求交付流程](../REQUIREMENT_DELIVERY_WORKFLOW.md)另行约定。

# commercial：商业关系快照与真实解析接入 · 技术方案

> 状态：**阶段 2—3 未开始**。第 1 节为阶段 1 调研记录的现状证据（只读核对，main `32dbdd18`）。

## 1. 现状证据（阶段 1）

- acquiring 商务 Mock：`paytech/acquiring/paytech-acquiring-app/src/main/java/com/paytech/acquiring/configuration/CustomerCheckoutIntegrationConfiguration.java:35-47`（固定 AE001 / 105 / 104）；`acquiring/pom.xml:37-38` 无 commercial-api 依赖；端口模型 `paytech-acquiring-core/.../domain/customer/CustomerCheckoutModels.java:48-112`（`InitializationRequest`、`CommercialFacts`）；冻结在建单事务内（`CreateCheckoutProcessor.java:88-156`），落 `t_commerce_order`、`t_order_context`、`t_order_legal`（`acquiring/docs/database/sql/acquiring-schema.sql:7-90`）；Intent 在首次提交时创建（`SubmitCustomerPaymentProcessor.java:107-113`）。
- commercial 解析：`POST /checkout-context/v1/resolutions`（`CheckoutContextApi.java:25`），只允许 `acquiring-service`；流程 `CheckoutResolutionProcessor`（client-master 候选 → 本库快照 → 资格 → verify）；资格客户端 `CheckoutQualificationClient`（`requireClientEligible` / `isCandidateEligible`）当前装配 `UnavailableCheckoutQualificationClient`（恒 OWNER_UNAVAILABLE）；解析只检查 CURRENCY / CONSUMER_COUNTRY（`CheckoutSubscriptionContext.java:34,103-104`），`paymentMethodCode` 省略时 `capabilities` 为空。
- compliance：`t_qualification_decision`（`compliance-schema.sql:272`）、`t_role_qualification`（`:294`）已建；API 只有入驻审核、材料要求、结算控制合同，无资格查询。
- client-master：`t_party`、`t_local_partner`、`t_operating_entity`、`t_entity_source_relation`（`client-master-schema.sql:7,147,161,340`）；OE 来源 PLATFORM / CLIENT / PARTNER；无收单资质、无 Partner 角色 / 关联关系、无推荐关系表。
- Finance 账簿按 OE（`entity_no`）与 Finance Entity（`design-discuss/finance-account-ledger-v3/design.md` 3.1）。
- mor-test 带条件的 CARD 开通项合同：CL-F1105E、DEMO-CL-001 / 002、DEMO2-CL-101 / 103 / 901（见 `requirements/20261020/commercial-开通与定价模型调整/verification/stage7-acceptance.md`）；acquiring 建单只收 USD。

## 2. 差异与影响（阶段 2）

待阶段 1 确认后填写。

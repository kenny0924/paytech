# 任务卡 D · Checkout 解析扩展、风控门禁、错误码改名

> 依赖 A 合入；与 B / C 并行但只读它们的表。执行者 Opus 5.5 agent。范围：**只在 `paytech/commercial`**（2026-10-19 用户委托夜间自动推进后调整：不碰 `paytech/acquiring`，避免与其他会话冲突）。解析响应只做向后兼容的加字段；acquiring 侧消费与 Order Context 冻结另行登记。接口合同：[technique.md §2.3、§2.5、§2.10](../technique.md)。

## 交付

1. `ResolveCheckoutContextRequestBody` 增 `paymentMethodCode`、`currency`（可选）；`ResolveCheckoutContextResponseBody.clientTerms` 增 `businessMode`、`fundMode`、`pricingAuthority`、`referralPartnerId`、`agreementProducts[]`、`capability{}`；新增 `derived{sellerPartyId, acquiringEntityNo, taxResponsibleParty, fundsBookOwner}`。
2. `CheckoutQueryServiceImpl` / `CheckoutResolutionContext`：订阅含 ACQUIRING；给出支付方式与币种时命中 ENABLED 且区间内、约束满足的开通项；版本按 §2.3 选取（复用 C 线的 Repository 查询，若 C 未合入则本卡先实现并在报告中注明）；派生四项按 MOR / PSP 规则；`fund_mode ≠ SETTLE_THROUGH_PLATFORM` 的候选本期落空并记日志。
3. 风控门禁：`CheckoutQualificationClient` 从 `UnavailableCheckoutQualificationClient`（503 占位）改为真实调用 risk-decision 的 Checkout 资格接口（先读 `paytech/risk-decision/paytech-risk-decision-api` 找现有合同；若没有可用接口，保留 503 占位、在报告中写明缺口，不伪造）；不可用一律 503 `OWNER_UNAVAILABLE`。
4. S5 错误码改名（`CCT_CONTEXT_*` → `COMMERCIAL_CONTEXT_*`）**推迟**到后续切片，本卡不做；在报告里登记为遗留。
5. 测试：§2.11 解析矩阵（未订阅、未开通、币种不在约束、SUSPENDED、区间外、SCHEDULED 已到时未切 ACTIVE）；既有 Checkout 测试加固件改造后全绿。

## 不做

acquiring 的任何改动（另行登记）、B / C / E 范围。

# I1 线补充到 technique.md 的两节（待并入）

> technique.md 在主工作区尚未纳入版本库，I1 分支不提交同名文件以免合并冲突；以下两节由合并者并入 technique.md 第 2 节末尾。

## F9 结论（BFF 侧）

- **现状**：Foundation `HttpServiceClientInterceptor` 先按严格 Problem 合同校验 4xx 响应（`type/title/detail/code/requestId/timestamp`、`X-Request-Id`），再把公共类别转为 `BizCheckFailException`、`InvalidParameterException`、`ConcurrentRequestException`、`FailException(NOT_FOUND)`，已登记合同码转为 `FailException(合同码)`。异常消息固定为公共默认说明（设计意图"远端 detail 不外显"），但原始 `RestClientResponseException` 作为 Cause 保留，其响应体就是已校验的 Problem。因此 **Owner detail 在异常对象上可得**，无需改 Foundation。
- **实现**：`operations-console-bff` 新增 `CommercialOwnerProblemDetail`，只包装 `CommercialClientConfiguration` 创建的 commercial 代理（AgreementApi、PricingProfileApi、CommercialTermApi、CommercialTermVersionPriceApi、RateCardApi、CommercialPriceApi）。精确类型为上述公共类别或 `FailException` 本身、且 Cause 为 `application/problem+json` 的 4xx 时，用 Owner `detail` 重建同类型异常（错误码、状态与 Cause 不变），MVC 边界据此输出 Owner 的中文原因。字段校验（`HttpRequestValidationException` / `RequestBodyValidationException`）、5xx、超时与协议错误不变；detail 为空白或超过 1000 字时保留公共说明。其他 BFF 区域（Platform、Risk、Finance、Vault 等）不受影响。
- **commercial 侧前提**：commercial 业务异常消息按项目规范是可安全外显的提示（DEVELOPMENT_CONVENTIONS §4）。H 线无需为激活校验、签署、订阅等拒绝注册专有错误码（方案 B 不需要）。
- **价格命中**：`CommercialPriceApi` 用独立代理工厂登记 `CommercialPriceErrorCode`，浏览器得到 422 `PRICING_FACTS_INCOMPLETE` / `PRICE_NOT_FOUND` / `INVALID_PRICING_FACT`、409 `PRICE_AMBIGUOUS` 与 Owner 原因（`OperationsErrorStatusResolver` 增加状态映射）。
- **Web**：`ApiError` 增加 `detail` 字段；`commercialErrorMessage` 对 400 `BUSINESS_CHECK_FAILED` 优先显示 Owner 原因（如"业务校验未通过：激活校验：开通项 AE001/APPLE_PAY/AED 无价格"），只有公共默认说明时回退到按场景的提示；试算页按错误码给出说明并附 commercial 原因。
- **验证**：BFF auto 测试覆盖 400 / 404 / 409 透传、超长 detail 不透传、422 字段校验不透传、价格命中四个错误码透传。

## Client 选择器可复用的查询（I2 实现）

- **没有现成的"运营按名称 / 编号搜索 Client"接口**。client-master 对运营只暴露入驻审核相关的 `ClientApplicationAdminApi`（按 caseId 读申请）与 `ClientClassificationAdminApi`；`ClientWorkspaceApi.summaries`（`GET /client-workspace/v1/client-summaries?clientId=…`，返回 clientNo、displayName、clientType、clientStatus、country、approvedAt）按 ID 批量读，但合同限定调用方为 client-console-bff 并要求 CUSTOMER 会话头。
- **运营控制台已在用的列表**：compliance `ClientReviewAdminApi.listCases`（`GET /client-onboarding-admin/v1/cases?page&size&status&qualificationType&applicationId`，Operations BFF 已接入），每条含 `clientId`、`caseNo`、`applicationId`、状态与时间，不含 Client 名称。
- **Party / OE**：`ClientMasterCheckoutApi.partyProfiles`（`GET /client-master/v1/party-profiles?ids=…`）按 ID 批量读 Party 资料；`checkout-candidates` 是按 Client 解析 Checkout 候选 OE 的私网接口，不是 OE 列表。client-master 没有 OE 列表接口，mor-test 也还没有 OE 数据。
- **建议**：I2 的 Client 选择器先用审核任务列表（`status=APPROVED`）取 clientId 候选，名称经 client-master 新增运营可调用的按 ID 摘要接口（或放开 `client-summaries` 给 operations-console-bff，并去掉 CUSTOMER 会话头要求）补齐；按名称 / 编号搜索、Party 与 OE 选择需要 client-master 另行提供运营查询接口，属于 client-master 的独立需求。

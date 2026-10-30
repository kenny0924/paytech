# tax-api 已就绪（W1，2026-10-29）

> **变更 3（2026-10-29 晚；已编译，tax 测试 61/61 通过）**
> 1. **`TaxCalculationLineRequestBody.categoryCode` 改为自由文本**：只校验非空与长度 ≤ 64（去掉 @Pattern），可含空格与非 ASCII。不在 t_tax_category 或已停用 → 200 + `BLOCKED`、`UNKNOWN_TAX_CATEGORY`，路径 `lines[<lineNo>].categoryCode`，每个未知行一条；**不再返回 400**（S-13）。超过 64 字符为 422 VALIDATION_ERROR。
> 2. 本机初始化数据的 AE001 演示 Party 改为 **880000000702**（与 acquiring MOCK 商业配置一致）；脚本变量 `@ae001_party_id` 保留，阶段 6 替换为 mor-test 实际值。
> 3. 变更 2 的各项（taxSubjectPartyId 可空 → NOT_MANAGED / TAX_RESPONSIBLE_UNRESOLVED，出现在响应 `notManagedReason`；行 `description`；`registrations[].managedBy`；路径 `lines[n]` 的 n 为 1 起的 lineNo）早已在代码中，未变。
>
> **变更 2（2026-10-29 晚，主会话批准；已编译，tax 测试 59/59 通过）**
> 1. `CalculateTaxRequestBody.taxSubjectPartyId` **改为可空**（去掉 @NotBlank）。不传时结果恒为 `NOT_MANAGED`、`taxAmountSource=DECLARED`（税额 = `declaredTaxAmount`，缺省 0），计算照常落库（`tax_subject_party_id` 为空），没有组件与证据。用于 CLIENT 来源 OE（没有商业关系快照，责任方在 Client 一侧）。
> 2. 新枚举 `TaxNotManagedReason{SUBJECT_NOT_REGISTERED, TAX_RESPONSIBLE_UNRESOLVED}`；`TaxCalculationResponseBody` 新增 `notManagedReason`（只在 NOT_MANAGED 非空：未传责任方为 TAX_RESPONSIBLE_UNRESOLVED，责任方没有任何准入为 SUBJECT_NOT_REGISTERED）。没有放进 `TaxBlockReason`：NOT_MANAGED 不阻断支付，与 BLOCKED 原因分开。`taxSubjectPartyId` 响应字段此时为 null。
> 3. NOT_MANAGED（两种原因）都**不保存证据**，响应 `evidence[]` 为空列表；同身份重放不比较证据。
> 4. `TaxCalculationLineRequestBody` 新增可选 `description`（≤255，非敏感商品说明）→ 存 `t_tax_calculation_line.description` → `TaxCalculationLineResponseBody.description`（计算接口与订单查询都返回；不参与计算，参与同身份内容比较）。acquiring 可传订单行 `itemName`。
> 5. `TaxRegistrationResponseBody` 新增 `managedBy`（ManagedBy Code，取计算组件引用的准入 `t_tax_market.managed_by`；同一注册多个准入取最新修订；没有引用为空）。
> 6. **BLOCKED 原因路径 `lines[n]` 中的 n 是该行的 `lineNo`（从 1 开始）**，不再是数组下标；例如第一行税类未知为 `lines[1].categoryCode`。
>
> **变更 1（只澄清语义，Java 签名与字段不变）**
> - 2026-10-29 晚：错误状态澄清——字段注解不符为 **422 VALIDATION_ERROR**（平台公共映射），金额精度 / 行合计 / 行号或证据类型重复为 400 INVALID_REQUEST。
> - 2026-10-29 晚：NOT_MANAGED 的行 `taxAmount` / `grossAmount` 为 null（只有头部给自报税额）；INCLUSIVE 且 BLOCKED / NOT_MANAGED 时头与行的 `netAmount` 为 null、`grossAmount` 为行金额合计（acquiring 只发 EXCLUSIVE，不受影响）。
>
> 状态：**已冻结**。`paytech/tax/paytech-tax-api` 在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax` 中编译通过（`./mvnw -o -pl tax/paytech-tax-api -am compile`）。后续若必须修改，会在本文件顶部追加变更记录。

## 1. 模块与依赖

- Maven：`com.paytech:paytech-tax-api:${project.version}`（reactor 内 `-pl <你的模块> -am` 可直接解析，**不要 install**）。
- 依赖：`spring-web`、`jakarta.validation-api`、`paytech-common-core`、Lombok（provided）。
- 服务名 / HTTP Service Group：`tax-service`；本机端口 `18116`（`http://127.0.0.1:18116`），**无 context-path**，路径即下表绝对路径。
- 错误：RFC 9457 Problem；调用方用 `HttpServiceClientFactory` 时显式注册 `TaxErrorCode`。

## 2. 接口（包 `com.paytech.tax.api`）

| 接口 | Method + Path | 方法签名 | 返回 |
|---|---|---|---|
| `TaxCalculationApi` | `POST /tax/v1/calculations` | `TaxCalculationResponseBody calculateTax(@Valid @RequestBody CalculateTaxRequestBody body)` | 200（READY / BLOCKED / NOT_MANAGED 都是 200；同身份同内容返回原计算）；422 字段注解；400 金额精度 / 行合计 / 重复；409 `TAX_CALCULATION_CONFLICT` |
| `TaxCalculationApi` | `GET /tax/v1/calculations/{calculationId}` | `TaxCalculationResponseBody getTaxCalculation(String calculationId)` | 200；404 `TAX_CALCULATION_NOT_FOUND` |
| `TaxOrderQueryApi` | `GET /tax/v1/orders/{orderNo}` | `TaxOrderResponseBody getOrderTax(String orderNo)` | 200；404 `TAX_ORDER_NOT_FOUND` |

计算身份 = `sourceSystem + sourceObjectType + sourceObjectId + sourceRevision + taxScopeKey`。金额与 ID 为十进制字符串（响应按币种精度补齐），时间 `Instant`（UTC ISO-8601），法定日期 `yyyy-MM-dd` 字符串，枚举字段为 String Code。

## 3. 请求（包 `com.paytech.tax.api.request`）

`CalculateTaxRequestBody`

| 字段 | 必填 | 约束 / 说明 |
|---|---|---|
| sourceSystem | 是 | `[a-z][a-z0-9-]{0,31}`，acquiring 传 `acquiring` |
| sourceObjectType | 是 | 只能 `ORDER` |
| sourceObjectId | 是 | 订单 ID，正十进制 |
| sourceObjectNo | 是 | 订单号 |
| sourceRevision | 是 | Integer ≥ 1；建单 1，每次重算 +1 |
| taxScopeKey | 是 | 只能 `SALE` |
| stage | 是 | `PROVISIONAL`（建单，用 DECLARED_COUNTRY）/ `FINAL`（资料提交后，用 BILLING_COUNTRY） |
| supplyKind | 是 | 只能 `SALE` |
| businessMode | 是 | commercial BusinessMode Code，如 `MOR` / `PSP`（只冻结，不决定税负） |
| sellerPartyId | 否 | 正十进制 |
| taxSubjectPartyId | 否 | 商业关系快照 `TAX_RESPONSIBLE` 的 partyId；tax 无该主体 market → NOT_MANAGED(SUBJECT_NOT_REGISTERED)；不传 → NOT_MANAGED(TAX_RESPONSIBLE_UNRESOLVED) |
| buyerClientId | 是 | Client ID |
| entityNo | 是 | `[A-Z]{2}[0-9]{3}` |
| currency | 是 | ISO 4217 |
| priceMode | 是 | `EXCLUSIVE` / `INCLUSIVE`（acquiring 恒 EXCLUSIVE） |
| taxPointDate | 是 | `yyyy-MM-dd` |
| evidence[] | 是（可空列表） | `TaxEvidenceRequestBody{type, countryCode, subdivisionCode?}`；type ∈ `DECLARED_COUNTRY`/`BILLING_COUNTRY`，同类型至多一条；缺本阶段所需证据 → BLOCKED(MISSING_EVIDENCE) |
| lines[] | 是，1–200 | `TaxCalculationLineRequestBody{lineRef, lineNo, categoryCode, description?, quantity, unitPrice, discountAmount, lineAmount}`；lineRef = clientItemId；lineNo 从 1；**lineAmount = quantity × unitPrice − discountAmount**（acquiring 的 lineNetAmount），否则 400 |
| declaredTaxAmount | 否 | 只在 NOT_MANAGED 时作为税额，缺省 0 |

## 4. 响应（包 `com.paytech.tax.api.response`）

`TaxCalculationResponseBody`：`calculationId, sourceSystem, sourceObjectType, sourceObjectId, sourceObjectNo, sourceRevision(Integer), taxScopeKey, stage, status, taxAmountSource, supplyKind, businessMode, sellerPartyId, taxSubjectPartyId, buyerClientId, entityNo, currency, priceMode, taxPointDate, taxPointBasis, netAmount, taxAmount, roundingAmount, grossAmount, expiresAt(Instant), calculatedAt(Instant), blockReasons[], evidence[], lines[], components[]`

- BLOCKED：`taxAmount / roundingAmount / grossAmount` 与行 `taxAmount / grossAmount` 为 **null**；`netAmount` 仍有值。
- NOT_MANAGED：`taxAmount` = 自报值（缺省 0.00），`roundingAmount` = 0，无组件，`components` 空列表。
- `expiresAt`：只有 FINAL + READY 非空（计算时间 + 地区包 `quoteValidityHours`，缺省 24h）。
- `roundingAmount` 本期恒 0（逐行取整，尾差在行上）。
- `TaxBlockReasonResponseBody{code, path}`：code ∈ `TaxBlockReason`。
- `TaxEvidenceResponseBody{type, countryCode, subdivisionCode, placeOfSupplyBasis(Boolean)}`。
- `TaxCalculationLineResponseBody{lineId, lineRef, lineNo, categoryCode, quantity, unitPrice, discountAmount, netAmount, taxAmount, grossAmount, roundingDelta, components[]}`。
- `TaxComponentResponseBody{componentId, componentCode, jurisdictionCode, taxType, treatment, reasonCode, liableRole, marketId, registrationId, policyId, ruleCode, taxBase, rate, taxAmount, calculationMethod, roundingMethod, roundingScale(Integer)}`（行内明细）。
- `TaxComponentSummaryResponseBody{jurisdictionCode, taxType, registrationId, taxSubjectPartyId, taxAmount}`：顶层 `components[]`，按辖区 + 注册 + 纳税主体汇总、只含税额 > 0 的组件，**可原样作为 `OrderPaymentSucceeded.taxComponents[]`**（technique 11.1 的 `{jurisdictionCode, registrationId, taxSubjectPartyId, taxAmount}`，多一个 `taxType`，事件可不带）。

`TaxOrderResponseBody`：`orderNo, calculations[]（TaxCalculationResponseBody，按 sourceRevision 升序，最后一条为当前报价）, transactions[], registrations[], policies[]`

- `TaxTransactionResponseBody{transactionId, calculationId, sourceSystem, sourceFactType, sourceFactId, taxScopeKey, origin, transactionKind, taxSubjectPartyId, currency, occurredAt, taxPointDate, taxPointBasis, registeredAt, reportingStatus, reportingBlockReason, reportingEntries[], documents[]}`；`reportingStatus=PENDING` 时 `reportingBlockReason=FX_RATE_MISSING` 且 `reportingEntries` 为空。
- `TaxReportingEntryResponseBody{entryId, componentId, registrationId, periodId, periodStart, periodEnd, filingDueDate, entryRole, reportingRuleCode, sourceCurrency, sourceTaxBase, sourceTaxAmount, reportingCurrency, fxRate, fxRateDate, reportingTaxBase, reportingTaxAmount, taxPointDate}`。
- `TaxDocumentResponseBody{documentId, documentKind, issuanceMode, issuerPartyId, registrationId, seriesCode, documentNumber, issuedAt, currency, templateCode, issueStatus, submissionStatus, documentFileAddress, documentDigest}`；`documentFileAddress` 形如 `/files/v1/{fileId}`，`issueStatus=ISSUED` 时非空。
- `TaxRegistrationResponseBody{registrationId, subjectPartyId, jurisdictionCode, authorityCode, taxType, schemeCode, publicRegistrationNo, reportingCurrency, legalTimezone, filingFrequency, status}`。
- `TaxPolicyResponseBody{policyId, packageCode, revision(Integer), jurisdictionCode, taxType, schemeCode, effectiveFrom, effectiveTo, status}`。

## 5. 枚举（包 `com.paytech.tax.api.enums`，均实现 `CodeEnum`，Code = 常量名）

| 枚举 | 值 |
|---|---|
| `TaxCalculationStatus` | READY, BLOCKED, NOT_MANAGED |
| `TaxCalculationStage` | PROVISIONAL, FINAL |
| `TaxAmountSource` | COMPUTED, DECLARED |
| `TaxTreatment` | TAXABLE, ZERO_RATED, EXEMPT, OUT_OF_SCOPE, REVERSE_CHARGE, NOT_COLLECTED |
| `TaxBlockReason` | MARKET_NOT_ENABLED, NO_RULE, AMBIGUOUS_RULE, UNKNOWN_TAX_CATEGORY, MISSING_EVIDENCE, POLICY_INVALID |
| `ManagedBy` | SUBJECT_HOLDER, PLATFORM_OPERATIONS |
| `TaxReportingStatus` | PENDING, READY |
| `TaxDocumentKind` | RECEIPT |
| `IssuanceMode` | SYSTEM, EXTERNAL |
| `TaxDocumentIssueStatus` | RESERVED, ISSUED, VOID |
| `TaxPriceMode` | EXCLUSIVE, INCLUSIVE（technique 10.4 外新增：请求 priceMode 的 Code） |
| `TaxEvidenceType` | DECLARED_COUNTRY, BILLING_COUNTRY（technique 10.4 外新增：evidence.type 的 Code） |
| `TaxErrorCode`（CodeMessageEnum） | TAX_CALCULATION_CONFLICT（409）, TAX_CALCULATION_NOT_FOUND（404）, TAX_ORDER_NOT_FOUND（404） |

## 6. 与 technique 10.1 的差异

1. 请求行字段 `netAmount` 改名 `lineAmount`：含税价模式下该值不是净额。acquiring 传 `lineNetAmount`。
2. 响应顶层 `components[]` 是按辖区 + 注册 + 纳税主体的**汇总**（无 componentId / treatment / rate），逐组件明细在 `lines[].components[]`；目的是让 acquiring 直接写 `components_json` 与事件 `taxComponents[]`。
3. 新增 `TaxPriceMode`、`TaxEvidenceType`、`TaxErrorCode` 三个枚举；订单查询响应增加 `registrations[]`、`policies[]` 供页面"计算依据"使用。

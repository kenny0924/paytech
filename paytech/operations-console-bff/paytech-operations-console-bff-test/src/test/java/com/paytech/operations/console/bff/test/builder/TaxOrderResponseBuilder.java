package com.paytech.operations.console.bff.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.tax.api.enums.IssuanceMode;
import com.paytech.tax.api.enums.ManagedBy;
import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxNotManagedReason;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.api.response.TaxBlockReasonResponseBody;
import com.paytech.tax.api.response.TaxCalculationLineResponseBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentResponseBody;
import com.paytech.tax.api.response.TaxDocumentResponseBody;
import com.paytech.tax.api.response.TaxEvidenceResponseBody;
import com.paytech.tax.api.response.TaxOrderResponseBody;
import com.paytech.tax.api.response.TaxPolicyResponseBody;
import com.paytech.tax.api.response.TaxRegistrationResponseBody;
import com.paytech.tax.api.response.TaxReportingEntryResponseBody;
import com.paytech.tax.api.response.TaxTransactionResponseBody;

/**
 * tax 订单查询响应（GET /tax/v1/orders/{orderNo}）的测试 DTO，按原型 16—18 的三类订单给出合法默认值
 * <li>registered：MoR，r1 建单暂估 AE 5.00，r2 资料提交 SA 15.00，已登记、已开票、底稿缺 SAR 汇率。</li>
 * <li>blocked：MoR，r1 暂估 AE 5.00，r2 科威特账单国家被阻断（市场未开通 + 第 2 行税类未登记）。</li>
 * <li>notManaged：PSP，Client 自报税额 7.00，无组件、无正式交易。</li>
 * <li>只构造数据，不访问外部资源；测试可在返回对象上覆盖字段构造负例。</li>
 */
public final class TaxOrderResponseBuilder {
    public static final String ORDER_NO = "ORD-20261029-000184";
    public static final String CALCULATION_R1 = "912000184001";
    public static final String CALCULATION_R2 = "912000184002";
    public static final String DOCUMENT_ID = "913000031001";
    public static final String DOCUMENT_NUMBER = "SA-RCPT-2026-000031";
    public static final String FILE_ID = "914000031001";
    public static final String REGISTRATION_ID = "915000000001";
    public static final String POLICY_ID = "916000000001";
    public static final String CLIENT_ID = "80001";
    public static final String SUBJECT_PARTY_ID = "880000000002";
    public static final String ENTITY_NO = "AE001";
    public static final String USD = "USD";
    private static final Instant R1_AT = Instant.parse("2026-10-29T06:31:00Z");
    private static final Instant R2_AT = Instant.parse("2026-10-29T06:42:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-10-29T06:43:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-29T06:43:09Z");

    /** 工具类不实例化。 */
    private TaxOrderResponseBuilder() {
    }

    /** 原型 16：已登记、已开票、底稿缺法定汇率。 */
    public static TaxOrderResponseBody registered() {
        var order = order(List.of(provisional(), finalSaudi()));
        order.setTransactions(new ArrayList<>(List.of(transaction())));
        return order;
    }

    /** 最终报价已就绪、尚未支付。 */
    public static TaxOrderResponseBody quoted() {
        return order(List.of(provisional(), finalSaudi()));
    }

    /** 只有建单暂估。 */
    public static TaxOrderResponseBody estimated() {
        return order(List.of(provisional()));
    }

    /** 原型 17：最终报价被阻断。 */
    public static TaxOrderResponseBody blocked() {
        var blocked = calculation(CALCULATION_R2, 2, TaxCalculationStage.FINAL, TaxCalculationStatus.BLOCKED, R2_AT);
        blocked.setEvidence(List.of(evidence(TaxEvidenceType.DECLARED_COUNTRY, "AE", false),
            evidence(TaxEvidenceType.BILLING_COUNTRY, "KW", true)));
        blocked.setTaxAmount(null);
        blocked.setGrossAmount(null);
        blocked.setRoundingAmount(null);
        blocked.setLines(List.of(line(1, "nimbus-pro-annual", "SAAS_AUTOMATED", "80.00", null, List.of()),
            line(2, "game-key", "GAME_KEY", "20.00", null, List.of())));
        blocked.setBlockReasons(List.of(blockReason(TaxBlockReason.MARKET_NOT_ENABLED, "market.KW"),
            blockReason(TaxBlockReason.UNKNOWN_TAX_CATEGORY, "lines[2].categoryCode")));
        var order = order(List.of(provisional(), blocked));
        order.setRegistrations(new ArrayList<>());
        order.setPolicies(new ArrayList<>());
        return order;
    }

    /** 原型 18：PSP 订单，税额为 Client 自报值。 */
    public static TaxOrderResponseBody notManaged() {
        var declared = calculation(CALCULATION_R1, 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.NOT_MANAGED,
            R1_AT);
        declared.setBusinessMode("PSP");
        declared.setTaxAmountSource(TaxAmountSource.DECLARED.getCode());
        declared.setTaxSubjectPartyId("880000000017");
        declared.setTaxAmount("7.00");
        declared.setGrossAmount("107.00");
        declared.setLines(List.of(line(1, "lumen-pack", "GAME_KEY", "100.00", null, List.of())));
        declared.setComponents(List.of());
        declared.setEvidence(List.of());
        declared.setNotManagedReason(TaxNotManagedReason.SUBJECT_NOT_REGISTERED.getCode());
        var order = order(List.of(declared));
        order.setRegistrations(new ArrayList<>());
        order.setPolicies(new ArrayList<>());
        return order;
    }

    /** CLIENT 来源 OE：责任方未确定，tax 不保存证据，纳税主体为空。 */
    public static TaxOrderResponseBody notManagedUnresolved() {
        var order = notManaged();
        var calc = order.getCalculations().getFirst();
        calc.setTaxSubjectPartyId(null);
        calc.setNotManagedReason(TaxNotManagedReason.TAX_RESPONSIBLE_UNRESOLVED.getCode());
        return order;
    }

    /** 订单外壳：注册与地区包为 SA 演示数据，交易为空。 */
    static TaxOrderResponseBody order(List<TaxCalculationResponseBody> calculations) {
        var order = new TaxOrderResponseBody();
        order.setOrderNo(ORDER_NO);
        order.setCalculations(new ArrayList<>(calculations));
        order.setTransactions(new ArrayList<>());
        order.setRegistrations(new ArrayList<>(List.of(registration())));
        order.setPolicies(new ArrayList<>(List.of(policy())));
        return order;
    }

    /** r1：建单按 Client 声明国家 AE 暂估 5%。 */
    static TaxCalculationResponseBody provisional() {
        var calc = calculation(CALCULATION_R1, 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.READY, R1_AT);
        calc.setEvidence(List.of(evidence(TaxEvidenceType.DECLARED_COUNTRY, "AE", true)));
        calc.setTaxAmount("5.00");
        calc.setGrossAmount("105.00");
        calc.setLines(List.of(line(1, "nimbus-pro-annual", "SAAS_AUTOMATED", "80.00", "4.00",
                List.of(component("AE", "0.05", "80.00", "4.00", "AE_REMOTE_B2C_STANDARD"))),
            line(2, "extra-seat", "SAAS_AUTOMATED", "20.00", "1.00",
                List.of(component("AE", "0.05", "20.00", "1.00", "AE_REMOTE_B2C_STANDARD")))));
        return calc;
    }

    /** r2：账单国家 SA，按沙特 15%，逐行 12.00 + 3.00。 */
    static TaxCalculationResponseBody finalSaudi() {
        var calc = calculation(CALCULATION_R2, 2, TaxCalculationStage.FINAL, TaxCalculationStatus.READY, R2_AT);
        calc.setEvidence(List.of(evidence(TaxEvidenceType.DECLARED_COUNTRY, "AE", false),
            evidence(TaxEvidenceType.BILLING_COUNTRY, "SA", true)));
        calc.setTaxAmount("15.00");
        calc.setGrossAmount("115.00");
        calc.setExpiresAt(R2_AT.plusSeconds(86400));
        calc.setLines(List.of(line(1, "nimbus-pro-annual", "SAAS_AUTOMATED", "80.00", "12.00",
                List.of(component("SA", "0.15", "80.00", "12.00", "SA_REMOTE_B2C_STANDARD"))),
            line(2, "extra-seat", "SAAS_AUTOMATED", "20.00", "3.00",
                List.of(component("SA", "0.15", "20.00", "3.00", "SA_REMOTE_B2C_STANDARD")))));
        return calc;
    }

    /** 计算头的合法默认值：MoR、AE001、USD、未税价 100.00。 */
    static TaxCalculationResponseBody calculation(String id, int revision, TaxCalculationStage stage,
        TaxCalculationStatus status, Instant calculatedAt) {
        var calc = new TaxCalculationResponseBody();
        calc.setCalculationId(id);
        calc.setSourceSystem("acquiring");
        calc.setSourceObjectType("ORDER");
        calc.setSourceObjectId("912000184");
        calc.setSourceObjectNo(ORDER_NO);
        calc.setSourceRevision(revision);
        calc.setTaxScopeKey("SALE");
        calc.setStage(stage.getCode());
        calc.setStatus(status.getCode());
        calc.setTaxAmountSource(TaxAmountSource.COMPUTED.getCode());
        calc.setSupplyKind("SALE");
        calc.setBusinessMode("MOR");
        calc.setTaxSubjectPartyId(SUBJECT_PARTY_ID);
        calc.setBuyerClientId(CLIENT_ID);
        calc.setEntityNo(ENTITY_NO);
        calc.setCurrency(USD);
        calc.setPriceMode(TaxPriceMode.EXCLUSIVE.getCode());
        calc.setTaxPointDate("2026-10-29");
        calc.setNetAmount("100.00");
        calc.setRoundingAmount("0.00");
        calc.setCalculatedAt(calculatedAt);
        calc.setBlockReasons(List.of());
        calc.setComponents(List.of());
        return calc;
    }

    /** 一条证据。 */
    static TaxEvidenceResponseBody evidence(TaxEvidenceType type, String country, boolean basis) {
        var evidence = new TaxEvidenceResponseBody();
        evidence.setType(type.getCode());
        evidence.setCountryCode(country);
        evidence.setPlaceOfSupplyBasis(basis);
        return evidence;
    }

    /** 一行明细。 */
    static TaxCalculationLineResponseBody line(int lineNo, String lineRef, String category, String net, String tax,
        List<TaxComponentResponseBody> components) {
        var line = new TaxCalculationLineResponseBody();
        line.setLineId("9170000" + lineNo);
        line.setLineRef(lineRef);
        line.setLineNo(lineNo);
        line.setCategoryCode(category);
        line.setDescription("nimbus-pro-annual".equals(lineRef) ? "Nimbus Notes Pro · Annual" : null);
        line.setQuantity("1");
        line.setUnitPrice(net);
        line.setDiscountAmount("0.00");
        line.setNetAmount(net);
        line.setTaxAmount(tax);
        line.setGrossAmount(tax == null ? null : new BigDecimal(net).add(new BigDecimal(tax)).toPlainString());
        line.setRoundingDelta(tax == null ? null : "0.00");
        line.setComponents(components);
        return line;
    }

    /** 一个 VAT 组件；SA 组件引用演示注册与地区包。 */
    static TaxComponentResponseBody component(String jurisdiction, String rate, String base, String tax, String rule) {
        var component = new TaxComponentResponseBody();
        component.setComponentId("918" + jurisdiction + base.replace(".", ""));
        component.setComponentCode("VAT_STANDARD");
        component.setJurisdictionCode(jurisdiction);
        component.setTaxType("VAT");
        component.setTreatment(TaxTreatment.TAXABLE.getCode());
        component.setReasonCode(rule);
        component.setLiableRole("SELLER");
        component.setMarketId("919000000001");
        component.setRegistrationId("SA".equals(jurisdiction) ? REGISTRATION_ID : null);
        component.setPolicyId("SA".equals(jurisdiction) ? POLICY_ID : null);
        component.setRuleCode(rule);
        component.setTaxBase(base);
        component.setRate(rate);
        component.setTaxAmount(tax);
        component.setCalculationMethod("LINE");
        component.setRoundingMethod("HALF_UP");
        component.setRoundingScale(2);
        return component;
    }

    /** 一条阻断原因。 */
    public static TaxBlockReasonResponseBody blockReason(TaxBlockReason code, String path) {
        var reason = new TaxBlockReasonResponseBody();
        reason.setCode(code.getCode());
        reason.setPath(path);
        return reason;
    }

    /** 支付成功后的正式交易：底稿缺 SAR 汇率，已开具税票。 */
    static TaxTransactionResponseBody transaction() {
        var transaction = new TaxTransactionResponseBody();
        transaction.setTransactionId("920000184001");
        transaction.setCalculationId(CALCULATION_R2);
        transaction.setSourceSystem("acquiring");
        transaction.setSourceFactType("ORDER_PAYMENT_SUCCEEDED");
        transaction.setSourceFactId("912000184");
        transaction.setTaxScopeKey("SALE");
        transaction.setOrigin("AUTO");
        transaction.setTransactionKind("ORIGINAL");
        transaction.setTaxSubjectPartyId(SUBJECT_PARTY_ID);
        transaction.setCurrency(USD);
        transaction.setOccurredAt(PAID_AT);
        transaction.setTaxPointDate("2026-10-29");
        transaction.setTaxPointBasis("PAYMENT");
        transaction.setRegisteredAt(REGISTERED_AT);
        transaction.setReportingStatus(TaxReportingStatus.PENDING.getCode());
        transaction.setReportingBlockReason("FX_RATE_MISSING");
        transaction.setReportingEntries(new ArrayList<>());
        transaction.setDocuments(new ArrayList<>(List.of(document())));
        return transaction;
    }

    /** 已开具的简化税票。 */
    public static TaxDocumentResponseBody document() {
        var document = new TaxDocumentResponseBody();
        document.setDocumentId(DOCUMENT_ID);
        document.setDocumentKind(TaxDocumentKind.RECEIPT.getCode());
        document.setIssuanceMode(IssuanceMode.SYSTEM.getCode());
        document.setIssuerPartyId(SUBJECT_PARTY_ID);
        document.setRegistrationId(REGISTRATION_ID);
        document.setSeriesCode("SA-RCPT-2026");
        document.setDocumentNumber(DOCUMENT_NUMBER);
        document.setIssuedAt(REGISTERED_AT);
        document.setCurrency(USD);
        document.setTemplateCode("SA_SIMPLIFIED_RECEIPT");
        document.setIssueStatus(TaxDocumentIssueStatus.ISSUED.getCode());
        document.setDocumentFileAddress("/files/v1/" + FILE_ID);
        document.setDocumentDigest("a".repeat(64));
        return document;
    }

    /** 已就绪的底稿行（申报币种 SAR）。 */
    public static TaxReportingEntryResponseBody reportingEntry() {
        var entry = new TaxReportingEntryResponseBody();
        entry.setEntryId("921000184001");
        entry.setRegistrationId(REGISTRATION_ID);
        entry.setPeriodId("922000202610");
        entry.setPeriodStart("2026-10-01");
        entry.setPeriodEnd("2026-10-31");
        entry.setFilingDueDate("2026-11-30");
        entry.setEntryRole("OUTPUT");
        entry.setSourceCurrency(USD);
        entry.setSourceTaxBase("100.00");
        entry.setSourceTaxAmount("15.00");
        entry.setReportingCurrency("SAR");
        entry.setFxRate("3.75");
        entry.setFxRateDate("2026-10-29");
        entry.setReportingTaxBase("375.00");
        entry.setReportingTaxAmount("56.25");
        entry.setTaxPointDate("2026-10-29");
        return entry;
    }

    /** SA 非居民注册：SAR 月报。 */
    static TaxRegistrationResponseBody registration() {
        var registration = new TaxRegistrationResponseBody();
        registration.setRegistrationId(REGISTRATION_ID);
        registration.setSubjectPartyId(SUBJECT_PARTY_ID);
        registration.setJurisdictionCode("SA");
        registration.setAuthorityCode("ZATCA");
        registration.setTaxType("VAT");
        registration.setSchemeCode("NON_RESIDENT");
        registration.setPublicRegistrationNo("300000000000003");
        registration.setReportingCurrency("SAR");
        registration.setLegalTimezone("Asia/Riyadh");
        registration.setFilingFrequency("MONTHLY");
        registration.setStatus("ACTIVE");
        registration.setManagedBy(ManagedBy.PLATFORM_OPERATIONS.getCode());
        return registration;
    }

    /** SA 演示地区包 r1。 */
    static TaxPolicyResponseBody policy() {
        var policy = new TaxPolicyResponseBody();
        policy.setPolicyId(POLICY_ID);
        policy.setPackageCode("DEMO_SA_SAAS");
        policy.setRevision(1);
        policy.setJurisdictionCode("SA");
        policy.setTaxType("VAT");
        policy.setSchemeCode("NON_RESIDENT");
        policy.setEffectiveFrom("2026-01-01");
        policy.setStatus("ACTIVE");
        return policy;
    }
}

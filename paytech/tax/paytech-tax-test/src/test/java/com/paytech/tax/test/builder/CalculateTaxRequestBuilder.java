package com.paytech.tax.test.builder;

import java.util.ArrayList;
import java.util.List;

import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.request.TaxCalculationLineRequestBody;
import com.paytech.tax.api.request.TaxEvidenceRequestBody;

/**
 * 计算请求构造器
 * <li>template()：acquiring 订单 FINAL 报价，测试纳税主体、MOR、USD、未税价，一行 SAAS_AUTOMATED 100.00，Client 声明 SA、账单国家 SA。</li>
 * <li>build() 不修复负例：用例可显式置空或给出非法值。</li>
 */
public final class CalculateTaxRequestBuilder {
    private final CalculateTaxRequestBody body = new CalculateTaxRequestBody();

    private final List<TaxEvidenceRequestBody> evidence = new ArrayList<>();

    private final List<TaxCalculationLineRequestBody> lines = new ArrayList<>();

    /** 只能从模板开始。 */
    private CalculateTaxRequestBuilder() {
    }

    /** 默认 FINAL、SA、100.00 USD 未税价。 */
    public static CalculateTaxRequestBuilder template() {
        CalculateTaxRequestBuilder builder = new CalculateTaxRequestBuilder();
        CalculateTaxRequestBody body = builder.body;
        body.setSourceSystem("acquiring");
        body.setSourceObjectType("ORDER");
        body.setSourceObjectId(Long.toString(TaxTestConstants.ORDER_ID));
        body.setSourceObjectNo(TaxTestConstants.ORDER_NO);
        body.setSourceRevision(2);
        body.setTaxScopeKey("SALE");
        body.setStage(TaxCalculationStage.FINAL.getCode());
        body.setSupplyKind("SALE");
        body.setBusinessMode("MOR");
        body.setSellerPartyId(Long.toString(TaxTestConstants.SUBJECT_PARTY_ID));
        body.setTaxSubjectPartyId(Long.toString(TaxTestConstants.SUBJECT_PARTY_ID));
        body.setBuyerClientId(Long.toString(TaxTestConstants.BUYER_CLIENT_ID));
        body.setEntityNo(TaxTestConstants.ENTITY_NO);
        body.setCurrency(TaxTestConstants.USD);
        body.setPriceMode(TaxPriceMode.EXCLUSIVE.getCode());
        body.setTaxPointDate(TaxTestConstants.TAX_POINT_DATE.toString());
        body.setEvidence(builder.evidence);
        body.setLines(builder.lines);
        return builder.evidence(TaxEvidenceType.DECLARED_COUNTRY, "SA").evidence(TaxEvidenceType.BILLING_COUNTRY, "SA")
            .line(1, "1", "100.00", "0");
    }

    /** 订单 ID 与订单号。 */
    public CalculateTaxRequestBuilder order(long orderId, String orderNo) {
        body.setSourceObjectId(Long.toString(orderId));
        body.setSourceObjectNo(orderNo);
        return this;
    }

    /** 准备版本。 */
    public CalculateTaxRequestBuilder revision(Integer revision) {
        body.setSourceRevision(revision);
        return this;
    }

    /** 计算阶段。 */
    public CalculateTaxRequestBuilder stage(TaxCalculationStage stage) {
        body.setStage(stage.getCode());
        return this;
    }

    /** 价格口径。 */
    public CalculateTaxRequestBuilder priceMode(TaxPriceMode priceMode) {
        body.setPriceMode(priceMode.getCode());
        return this;
    }

    /** 税务责任方；null 表示调用方未确定责任方。 */
    public CalculateTaxRequestBuilder taxSubject(Long partyId) {
        body.setTaxSubjectPartyId(partyId == null ? null : partyId.toString());
        return this;
    }

    /** 自报税额。 */
    public CalculateTaxRequestBuilder declaredTaxAmount(String amount) {
        body.setDeclaredTaxAmount(amount);
        return this;
    }

    /** 清空证据。 */
    public CalculateTaxRequestBuilder clearEvidence() {
        evidence.clear();
        return this;
    }

    /** 追加或替换一条国家证据。 */
    public CalculateTaxRequestBuilder evidence(TaxEvidenceType type, String country) {
        evidence.removeIf(item -> item.getType().equals(type.getCode()));
        TaxEvidenceRequestBody item = new TaxEvidenceRequestBody();
        item.setType(type.getCode());
        item.setCountryCode(country);
        evidence.add(item);
        return this;
    }

    /** 两类证据都设为同一国家。 */
    public CalculateTaxRequestBuilder country(String country) {
        return evidence(TaxEvidenceType.DECLARED_COUNTRY, country).evidence(TaxEvidenceType.BILLING_COUNTRY, country);
    }

    /** 清空商品行。 */
    public CalculateTaxRequestBuilder clearLines() {
        lines.clear();
        return this;
    }

    /**
     * 追加一行 SAAS_AUTOMATED；行金额 = 数量 × 单价 − 折扣
     *
     * @param lineNo 行号
     * @param quantity 数量
     * @param unitPrice 单价
     * @param discount 整行折扣
     * @return 本构造器
     */
    public CalculateTaxRequestBuilder line(int lineNo, String quantity, String unitPrice, String discount) {
        java.math.BigDecimal amount = new java.math.BigDecimal(quantity).multiply(new java.math.BigDecimal(unitPrice))
            .subtract(new java.math.BigDecimal(discount));
        return line(lineNo, TaxTestConstants.SAAS_AUTOMATED, quantity, unitPrice, discount, amount.toPlainString());
    }

    /** 追加一行，字段全部由用例给出（可构造负例）。 */
    public CalculateTaxRequestBuilder line(int lineNo, String category, String quantity, String unitPrice, String discount,
        String lineAmount) {
        TaxCalculationLineRequestBody line = new TaxCalculationLineRequestBody();
        line.setLineRef("item-" + lineNo);
        line.setLineNo(lineNo);
        line.setCategoryCode(category);
        line.setDescription("Item " + lineNo);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setDiscountAmount(discount);
        line.setLineAmount(lineAmount);
        lines.add(line);
        return this;
    }

    /** 构造请求。 */
    public CalculateTaxRequestBody build() {
        return body;
    }
}

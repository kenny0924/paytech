package com.paytech.acquiring.test.builder.external;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxNotManagedReason;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.response.TaxBlockReasonResponseBody;
import com.paytech.tax.api.response.TaxCalculationLineResponseBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentSummaryResponseBody;

/**
 * tax 计算响应 DTO：按本次请求回显身份与商品行，按场景配置的状态、单一税率与原因给出金额
 * <li>READY：逐行 税额 = 行金额 × 税率（两位 HALF_UP），汇总为一个组件；NOT_MANAGED：头税额 0、行税额与含税额为 null、无组件，未传
 * 责任方时原因为 TAX_RESPONSIBLE_UNRESOLVED；BLOCKED：税额与总额为 null。与 tax-api-ready 变更 2 的合同一致。</li>
 * <li>不模拟 tax 的规则匹配；用例期望值由用例按业务样例直接给出，不调用本构造器反推。</li>
 */
public final class TaxCalculationResponseBuilder {
    /** 默认辖区（沙特，S-06 演示）。 */
    public static final String JURISDICTION = "SA";

    /** 默认注册 ID。 */
    public static final String REGISTRATION_ID = "9200001";

    /** 默认税种。 */
    public static final String TAX_TYPE = "VAT";

    private TaxCalculationStatus status;
    private BigDecimal rate = BigDecimal.ZERO;
    private TaxBlockReason blockReason;
    private Instant expiresAt;
    private String jurisdiction = JURISDICTION;

    private TaxCalculationResponseBuilder(TaxCalculationStatus status) {
        this.status = status;
    }

    /** 已按规则计算：单一税率，组件归属请求的纳税主体。 */
    public static TaxCalculationResponseBuilder ready(String rate) {
        var builder = new TaxCalculationResponseBuilder(TaxCalculationStatus.READY);
        builder.rate = new BigDecimal(rate);
        return builder;
    }

    /** 不由平台计税：税额为自报值 0、无组件。 */
    public static TaxCalculationResponseBuilder notManaged() {
        return new TaxCalculationResponseBuilder(TaxCalculationStatus.NOT_MANAGED);
    }

    /** 无法正确计税：给出一条原因，金额为空。 */
    public static TaxCalculationResponseBuilder blocked(TaxBlockReason reason) {
        var builder = new TaxCalculationResponseBuilder(TaxCalculationStatus.BLOCKED);
        builder.blockReason = reason;
        return builder;
    }

    /** 报价有效截止时刻（只有 FINAL + READY 才由 tax 给出）。 */
    public TaxCalculationResponseBuilder expiresAt(Instant value) {
        expiresAt = value;
        return this;
    }

    /** 组件辖区。 */
    public TaxCalculationResponseBuilder jurisdiction(String value) {
        jurisdiction = value;
        return this;
    }

    /** 按请求构造新的响应对象。 */
    public TaxCalculationResponseBody build(CalculateTaxRequestBody request, long calculationId) {
        var body = new TaxCalculationResponseBody();
        body.setCalculationId(Long.toString(calculationId));
        body.setSourceSystem(request.getSourceSystem());
        body.setSourceObjectType(request.getSourceObjectType());
        body.setSourceObjectId(request.getSourceObjectId());
        body.setSourceObjectNo(request.getSourceObjectNo());
        body.setSourceRevision(request.getSourceRevision());
        body.setTaxScopeKey(request.getTaxScopeKey());
        body.setStage(request.getStage());
        body.setStatus(status.getCode());
        body.setTaxAmountSource(status == TaxCalculationStatus.NOT_MANAGED ? TaxAmountSource.DECLARED.getCode()
            : TaxAmountSource.COMPUTED.getCode());
        body.setSupplyKind(request.getSupplyKind());
        body.setBusinessMode(request.getBusinessMode());
        body.setSellerPartyId(request.getSellerPartyId());
        body.setTaxSubjectPartyId(request.getTaxSubjectPartyId());
        body.setNotManagedReason(status != TaxCalculationStatus.NOT_MANAGED ? null
            : request.getTaxSubjectPartyId() == null ? TaxNotManagedReason.TAX_RESPONSIBLE_UNRESOLVED.getCode()
                : TaxNotManagedReason.SUBJECT_NOT_REGISTERED.getCode());
        body.setBuyerClientId(request.getBuyerClientId());
        body.setEntityNo(request.getEntityNo());
        body.setCurrency(request.getCurrency());
        body.setPriceMode(request.getPriceMode());
        body.setTaxPointDate(request.getTaxPointDate());
        body.setExpiresAt(expiresAt);
        body.setCalculatedAt(Instant.parse("2026-10-29T00:00:00Z"));
        body.setEvidence(List.of());
        fillAmounts(body, request);
        return body;
    }

    /** 逐行与汇总金额、阻断原因与组件。 */
    private void fillAmounts(TaxCalculationResponseBody body, CalculateTaxRequestBody request) {
        boolean blocked = status == TaxCalculationStatus.BLOCKED;
        boolean lineAmounts = status == TaxCalculationStatus.READY;
        var lines = new ArrayList<TaxCalculationLineResponseBody>();
        var net = BigDecimal.ZERO;
        var tax = BigDecimal.ZERO;
        for (var requestLine : request.getLines()) {
            var lineNet = new BigDecimal(requestLine.getLineAmount());
            var lineTax = status == TaxCalculationStatus.READY ? lineNet.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
            var line = new TaxCalculationLineResponseBody();
            line.setLineRef(requestLine.getLineRef());
            line.setLineNo(requestLine.getLineNo());
            line.setNetAmount(money(lineNet));
            line.setTaxAmount(lineAmounts ? money(lineTax) : null);
            line.setGrossAmount(lineAmounts ? money(lineNet.add(lineTax)) : null);
            line.setDescription(requestLine.getDescription());
            line.setComponents(List.of());
            lines.add(line);
            net = net.add(lineNet);
            tax = tax.add(lineTax);
        }
        body.setLines(lines);
        body.setNetAmount(money(net));
        body.setTaxAmount(blocked ? null : money(tax));
        body.setRoundingAmount(blocked ? null : "0.00");
        body.setGrossAmount(blocked ? null : money(net.add(tax)));
        body.setBlockReasons(blocked ? List.of(reason()) : List.of());
        body.setComponents(status == TaxCalculationStatus.READY && tax.signum() > 0
            ? List.of(component(request, tax)) : List.of());
    }

    /** 一条阻断原因。 */
    private TaxBlockReasonResponseBody reason() {
        var reason = new TaxBlockReasonResponseBody();
        reason.setCode(blockReason.getCode());
        reason.setPath("lines[0]");
        return reason;
    }

    /** 汇总组件：本辖区、默认注册、请求的纳税主体。 */
    private TaxComponentSummaryResponseBody component(CalculateTaxRequestBody request, BigDecimal tax) {
        var component = new TaxComponentSummaryResponseBody();
        component.setJurisdictionCode(jurisdiction);
        component.setTaxType(TAX_TYPE);
        component.setRegistrationId(REGISTRATION_ID);
        component.setTaxSubjectPartyId(request.getTaxSubjectPartyId());
        component.setTaxAmount(money(tax));
        return component;
    }

    /** USD 两位小数字符串。 */
    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}

package com.paytech.acquiring.test.builder.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;

/**
 * 订单税务计算行的前置事实：单行订单（clientItemId=line-1）按场景给出 revision、阶段、状态与金额
 * <li>金额与 JSON 由用例的业务样例直接给出，不调用生产规则计算。</li>
 */
public final class OrderTaxCalculationBuilder {
    /** 前置订单唯一商品行的 Client Item ID。 */
    public static final String LINE_ID = "line-1";

    private OrderTaxCalculationBuilder() {
    }

    /** 建单暂估（revision 1，READY）：按 Client 声明国家与税率给出的税额。 */
    public static OrderTaxCalculation provisional(long id, long orderId, long calculationId, String country,
        String net, String tax, Instant now) {
        return ready(id, orderId, 1, calculationId, TaxCalculationStage.PROVISIONAL, country, net, tax, null, now);
    }

    /** 资料提交后的最终计算（READY，带报价有效期）。 */
    public static OrderTaxCalculation finalReady(long id, long orderId, int revision, long calculationId,
        String country, String net, String tax, Instant expiresAt, Instant now) {
        return ready(id, orderId, revision, calculationId, TaxCalculationStage.FINAL, country, net, tax, expiresAt,
            now);
    }

    /** 资料提交后被阻断的最终计算：金额与明细为空。 */
    public static OrderTaxCalculation finalBlocked(long id, long orderId, int revision, long calculationId,
        String country, TaxBlockReason reason, Instant now) {
        return OrderTaxCalculation.builder().orderTaxCalculationId(id).orderId(orderId).revision(revision)
            .taxCalculationId(calculationId).calculationStage(TaxCalculationStage.FINAL)
            .taxStatus(TaxCalculationStatus.BLOCKED).basisCountryCode(country).blockReasonCode(reason.getCode())
            .createTime(now).build();
    }

    /** 不由平台计税的计算（CLIENT 来源未传责任方）：税额 0、无组件。 */
    public static OrderTaxCalculation notManaged(long id, long orderId, int revision, long calculationId,
        TaxCalculationStage stage, String country, String net, Instant now) {
        return OrderTaxCalculation.builder().orderTaxCalculationId(id).orderId(orderId).revision(revision)
            .taxCalculationId(calculationId).calculationStage(stage).taxStatus(TaxCalculationStatus.NOT_MANAGED)
            .basisCountryCode(country).taxAmount(BigDecimal.ZERO).roundingAmount(BigDecimal.ZERO)
            .totalAmount(new BigDecimal(net))
            .linesJson("[{\"clientItemId\":\"" + LINE_ID + "\",\"netAmount\":\"" + net
                + "\",\"taxAmount\":\"0.00\",\"totalAmount\":\"" + net + "\"}]")
            .componentsJson("[]").createTime(now).build();
    }

    /** READY 计算：单行、单一组件。 */
    private static OrderTaxCalculation ready(long id, long orderId, int revision, long calculationId,
        TaxCalculationStage stage, String country, String net, String tax, Instant expiresAt, Instant now) {
        var total = new BigDecimal(net).add(new BigDecimal(tax)).toPlainString();
        return OrderTaxCalculation.builder().orderTaxCalculationId(id).orderId(orderId).revision(revision)
            .taxCalculationId(calculationId).calculationStage(stage).taxStatus(TaxCalculationStatus.READY)
            .basisCountryCode(country).taxAmount(new BigDecimal(tax)).roundingAmount(BigDecimal.ZERO)
            .totalAmount(new BigDecimal(total)).expiresAt(expiresAt)
            .linesJson("[{\"clientItemId\":\"" + LINE_ID + "\",\"netAmount\":\"" + net + "\",\"taxAmount\":\"" + tax
                + "\",\"totalAmount\":\"" + total + "\"}]")
            .componentsJson("[{\"jurisdictionCode\":\"" + country + "\",\"taxType\":\"VAT\",\"registrationId\":\"9200001\","
                + "\"taxSubjectPartyId\":\"601\",\"taxAmount\":\"" + tax + "\"}]")
            .createTime(now).build();
    }
}

package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder.taxComponent;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_REFERENCE_TYPE;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLEARING_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_CONTEXT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_VERSION;
import static com.paytech.billing.test.builder.PaymentFactConstants.OTHER_TAX_JURISDICTION;
import static com.paytech.billing.test.builder.PaymentFactConstants.OTHER_TAX_REGISTRATION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.OTHER_TAX_SUBJECT_PARTY_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_INTENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_CALCULATION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_JURISDICTION;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_REGISTRATION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_SCOPE_SALE;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_SUBJECT_PARTY_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_TYPE_VAT;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.core.enums.OrderTaxStatus;
import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * OrderPaymentSucceeded 携带 tax 计算时的 PAYMENT 事实（技术方案 11.1、11.2，AC-09、AC-12）
 * <li>READY：税额为正的组件各一行 SELLER_TAX（渠道清算账户 → TAX_AUTHORITY 纳税主体），规则族 TAX_RULE、版本为计算 ID，本金减去税额，
 * 引用 TAX_CALCULATION 替代 TAX_SNAPSHOT；零税额组件不生成行。</li>
 * <li>NOT_MANAGED 与无税务字段的存量载荷与原行为相同；事件内税额不一致、BLOCKED、缺注册、孤立税务字段在读取上游前进入死信；
 * 本金不为正与纳税主体不唯一在组装时业务拒绝进入死信。</li>
 * <li>默认上游：Client 手续费 2.9%、最低 3.00，渠道成本 0.50，订单 100.00 USD。</li>
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededTaxTest extends AbstractOrderPaymentSucceededTest {
    /** 清算账户标识（渠道付方 / 收方）。 */
    private static final String CLEARING = Long.toString(CLEARING_ACCOUNT_ID);

    /** 纳税主体标识（SELLER_TAX 收方）。 */
    private static final String TAX_SUBJECT = Long.toString(TAX_SUBJECT_PARTY_ID);

    /** 单组件 5.00：手续费 3.00、税 5.00、本金 92.00、渠道成本 0.50；税行证据与 TAX_CALCULATION 引用完整，发布的事实带税行。 */
    @Test
    void test_singleComponentSellerTax() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY, "5.00",
            taxComponent(TAX_JURISDICTION, TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "5.00")).build());

        long factId = fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION)
            .header(CLIENT_ID, ENTITY_NO, USD, "100.00", null, null)
            .refs(taxCalculationRefs(), CHANNEL_REFERENCE_TYPE, CHANNEL_TRANSACTION_REF).lineCount(4)
            .line(1, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "3.00")
            .line(2, FactComponentType.SELLER_TAX, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.TAX_AUTHORITY,
                TAX_SUBJECT, "5.00")
            .evidence(2, "5.00", null, "5.00", SourceRuleType.TAX_RULE, TAX_CALCULATION_ID, null, null)
            .evidenceField(2, "taxCalculationId", Long.toString(TAX_CALCULATION_ID))
            .evidenceField(2, "taxScopeKey", TAX_SCOPE_SALE).evidenceField(2, "jurisdictionCode", TAX_JURISDICTION)
            .evidenceField(2, "taxType", TAX_TYPE_VAT)
            .evidenceField(2, "registrationId", Long.toString(TAX_REGISTRATION_ID))
            .evidenceField(2, "taxSubjectPartyId", TAX_SUBJECT)
            .line(3, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "92.00")
            .evidence(3, "100.00", null, "92.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, null)
            .line(4, FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                CLEARING, "0.50").factId();

        BillingFactPayload published = events.awaitEnvelope(factId).envelope(factId, 1, "main").payloadShape().payload();
        BillingFactLinePayload taxLine = published.getLines().get(1);
        assertAll("BillingFactRecorded 税行", () -> assertEquals(4, published.getLines().size()),
            () -> assertEquals(FactComponentType.SELLER_TAX.getCode(), taxLine.getComponentType()),
            () -> assertEquals(FactPartyRole.TAX_AUTHORITY.getCode(), taxLine.getPayeeRole()),
            () -> assertEquals(TAX_SUBJECT, taxLine.getPayeeId()));
    }

    /** 同一纳税主体两个组件 3.00 + 2.00 与一个零税额组件：两行 SELLER_TAX、零税额不生成行，本金 92.00。 */
    @Test
    void test_twoComponentsSellerTax() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY, "5.00",
            taxComponent(TAX_JURISDICTION, TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "3.00"),
            taxComponent(OTHER_TAX_JURISDICTION, OTHER_TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "2.00"),
            taxComponent(OTHER_TAX_JURISDICTION, null, TAX_SUBJECT_PARTY_ID, "0.00")).build());

        fact(PAYMENT_ATTEMPT_ID).refs(taxCalculationRefs(), CHANNEL_REFERENCE_TYPE, CHANNEL_TRANSACTION_REF)
            .lineCount(5)
            .line(2, FactComponentType.SELLER_TAX, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.TAX_AUTHORITY,
                TAX_SUBJECT, "3.00")
            .evidenceField(2, "jurisdictionCode", TAX_JURISDICTION)
            .evidenceField(2, "registrationId", Long.toString(TAX_REGISTRATION_ID))
            .line(3, FactComponentType.SELLER_TAX, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.TAX_AUTHORITY,
                TAX_SUBJECT, "2.00")
            .evidence(3, "2.00", null, "2.00", SourceRuleType.TAX_RULE, TAX_CALCULATION_ID, null, null)
            .evidenceField(3, "jurisdictionCode", OTHER_TAX_JURISDICTION)
            .evidenceField(3, "registrationId", Long.toString(OTHER_TAX_REGISTRATION_ID))
            .line(4, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "92.00");
    }

    /** NOT_MANAGED：自报税额不生成税行，本金 97.00 与无税订单相同，引用 TAX_CALCULATION。 */
    @Test
    void test_notManagedNoTaxLines() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.NOT_MANAGED, "5.00")
            .build());

        fact(PAYMENT_ATTEMPT_ID).refs(taxCalculationRefs(), CHANNEL_REFERENCE_TYPE, CHANNEL_TRANSACTION_REF)
            .lineCount(3)
            .line(2, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "97.00");
    }

    /** 存量载荷既无 tax 计算也无税务快照：八类引用、三行、本金 97.00。 */
    @Test
    void test_legacyPayloadWithoutTaxFields() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().remove("financeTaxSnapshotId").build());

        Map<FactRefType, String> refs = taxCalculationRefs();
        refs.remove(FactRefType.TAX_CALCULATION);
        fact(PAYMENT_ATTEMPT_ID).refs(refs, CHANNEL_REFERENCE_TYPE, CHANNEL_TRANSACTION_REF).lineCount(3)
            .line(2, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, CLEARING, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "97.00");
    }

    /** 手续费 3.00 与税额 97.00 用尽毛额：本金不为正，业务拒绝进入死信，不登记事实。 */
    @Test
    void test_taxLeavesNoPrincipalRejected() {
        mockPlatformDefaults();

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY, "97.00",
            taxComponent(TAX_JURISDICTION, TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "97.00")).build());

        assertNoFact();
    }

    /** 两个组件的纳税主体不同：ledger 无法确定唯一 TAX_PAYABLE 收方，登记不变量拒绝进入死信。 */
    @Test
    void test_taxSubjectsDifferRejected() {
        mockPlatformDefaults();

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY, "5.00",
            taxComponent(TAX_JURISDICTION, TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "3.00"),
            taxComponent(OTHER_TAX_JURISDICTION, OTHER_TAX_REGISTRATION_ID, OTHER_TAX_SUBJECT_PARTY_ID, "2.00")).build());

        assertNoFact();
    }

    /** 税额合计与组件之和不一致：读取上游前拒绝。 */
    @Test
    void test_taxAmountMismatchRejected() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY,
            "5.00", taxComponent(TAX_JURISDICTION, TAX_REGISTRATION_ID, TAX_SUBJECT_PARTY_ID, "4.00")).build());
    }

    /** 已支付订单的计算为 BLOCKED：读取上游前拒绝。 */
    @Test
    void test_blockedTaxRejected() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template()
            .taxCalculation(OrderTaxStatus.BLOCKED, "0.00").build());
    }

    /** 征税组件缺税务注册：读取上游前拒绝。 */
    @Test
    void test_taxRegistrationMissingRejected() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().taxCalculation(OrderTaxStatus.READY,
            "5.00", taxComponent(TAX_JURISDICTION, null, TAX_SUBJECT_PARTY_ID, "5.00")).build());
    }

    /** 没有 taxCalculationId 却给出 taxStatus：载荷不合法，读取上游前拒绝。 */
    @Test
    void test_taxFieldsWithoutCalculationRejected() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template()
            .set("taxStatus", OrderTaxStatus.READY.getCode()).build());
    }

    /** 进入死信、不登记事实、不读取任何上游。 */
    private void assertRejectedBeforeUpstream(DomainEvent event) {
        deadLettered(event);

        assertNoFact();
        commercialPrices.assertNoCalls();
        channelCosts.assertNoCalls();
        clearingAccounts.assertNoCalls();
        financeEntities.assertNoCalls();
    }

    /** 八类必需引用加 TAX_CALCULATION 的期望标识（可修改的副本）。 */
    private static Map<FactRefType, String> taxCalculationRefs() {
        Map<FactRefType, String> refs = new EnumMap<>(FactRefType.class);
        refs.put(FactRefType.TAX_CALCULATION, Long.toString(TAX_CALCULATION_ID));
        refs.put(FactRefType.ORDER, Long.toString(ORDER_ID));
        refs.put(FactRefType.ORDER_CONTEXT, Long.toString(ORDER_CONTEXT_ID));
        refs.put(FactRefType.PAYMENT_INTENT, Long.toString(PAYMENT_INTENT_ID));
        refs.put(FactRefType.PAYMENT_ATTEMPT, Long.toString(PAYMENT_ATTEMPT_ID));
        refs.put(FactRefType.PAYMENT_CHANNEL_ACCOUNT, Long.toString(CHANNEL_ACCOUNT_ID));
        refs.put(FactRefType.MID, Long.toString(MID_ID));
        refs.put(FactRefType.CLEARING_ACCOUNT, CLEARING);
        refs.put(FactRefType.CHANNEL_TRANSACTION_REF, Long.toString(CHANNEL_TRANSACTION_REF_ID));
        return refs;
    }
}

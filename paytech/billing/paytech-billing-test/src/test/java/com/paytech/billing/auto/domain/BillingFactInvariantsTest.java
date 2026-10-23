package com.paytech.billing.auto.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.core.enums.FactTargetBucket;
import com.paytech.billing.domainservice.validator.fact.BillingFactInvariants;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 人工登记与自动 PAYMENT 计费事实的目录约束与登记不变量（纯 Java 公共静态合同）
 * <li>入口测试覆盖 HTTP 与 MQ 合同；本类聚焦组合规则，便于定位：类型目录、引用集合、组件与方向、付方 / 收方、目标余额桶、合作方二元组、
 * 自动 PAYMENT 的行目录与金额恒等式。</li>
 *
 * @see BillingFactInvariants#checkManualRegistration(BillingFactPayload)
 * @see BillingFactInvariants#checkAutoPayment(BillingFactPayload)
 */
class BillingFactInvariantsTest {
    private static final long CLIENT_ID = 31001L;

    private static final String ENTITY_NO = "AE001";

    private static final long PARTNER_ID = 3001L;

    private static final long FINANCE_ENTITY_ID = 5601L;

    private static final String CHANNEL_ID = "7001";

    private static final String CLEARING_ACCOUNT_ID = "610001";

    /** 合法的人工调整（含合作方行与目标余额桶）与合法的成本调整都通过。 */
    @Test
    void test_validFactsAccepted() {
        BillingFactPayload manual = manual(PARTNER_ID, FINANCE_ENTITY_ID,
            line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), FactLineDirection.ADJUSTMENT, FactTargetBucket.PENDING),
            line(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.LOCAL_PARTNER,
                Long.toString(PARTNER_ID), FactLineDirection.CHARGE, null),
            line(FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, Long.toString(PARTNER_ID),
                FactPartyRole.FINANCE_ENTITY, Long.toString(FINANCE_ENTITY_ID), FactLineDirection.CHARGE, null));
        manual.getRefs().add(ref(FactRefType.TICKET, "OPS-1"));

        assertDoesNotThrow(() -> BillingFactInvariants.checkManualRegistration(manual));
        assertDoesNotThrow(() -> BillingFactInvariants.checkManualRegistration(cost(costLine(FactPartyRole.CHANNEL,
            CHANNEL_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO))));
    }

    /** 自动事实类型不能人工登记。 */
    @Test
    void test_nonManualTypeRejected() {
        BillingFactPayload fact = manual(null, null, clientPrincipal());
        fact.setFactType(BillingFactType.REFUND.getCode());
        assertThrows(InvalidParameterException.class, () -> BillingFactInvariants.checkManualRegistration(fact));
    }

    /** 人工调整：Client 必填、不得有来源事实、只允许 TICKET（自动专用引用 CHANNEL_TRANSACTION_REF、RECON_RESOLUTION 拒绝）、组件限六类。 */
    @Test
    void test_manualAdjustmentCatalog() {
        BillingFactPayload noClient = manual(null, null, clientPrincipal());
        noClient.setClientId(null);
        assertRejected(noClient, "人工调整必须指定 Client");
        BillingFactPayload withParent = manual(null, null, clientPrincipal());
        withParent.setParentFactId(1L);
        assertRejected(withParent, "人工调整不得指定来源事实");
        BillingFactPayload withClearing = manual(null, null, clientPrincipal());
        withClearing.getRefs().add(ref(FactRefType.CLEARING_ACCOUNT, "1"));
        assertThrows(InvalidParameterException.class, () -> BillingFactInvariants.checkManualRegistration(withClearing));
        for (FactRefType automaticOnly : List.of(FactRefType.CHANNEL_TRANSACTION_REF, FactRefType.RECON_RESOLUTION)) {
            BillingFactPayload withAutomaticRef = manual(null, null, clientPrincipal());
            withAutomaticRef.getRefs().add(ref(automaticOnly, "1"));
            assertThrows(InvalidParameterException.class,
                () -> BillingFactInvariants.checkManualRegistration(withAutomaticRef));
        }
        assertLineInvalid(manual(null, null, line(FactComponentType.FX_FEE, FactPartyRole.CLIENT, Long.toString(CLIENT_ID),
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactLineDirection.CHARGE, null)));
    }

    /** 目标余额桶只用于收方为 CLIENT / LOCAL_PARTNER 的人工调整行。 */
    @Test
    void test_targetBucketOnlyForOwnerPayee() {
        assertLineInvalid(manual(null, null, line(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT,
            Long.toString(CLIENT_ID), FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactLineDirection.CHARGE,
            FactTargetBucket.AVAILABLE)));
        BillingFactPayload withBucket = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
            CHANNEL_ID));
        withBucket.getLines().getFirst().setTargetBucket(FactTargetBucket.AVAILABLE.getCode());
        assertLineInvalid(withBucket);
    }

    /** 成本调整：来源事实与 CLEARING_ACCOUNT 必填，自动专用引用拒绝，组件与方向、付方 / 收方受限。 */
    @Test
    void test_costAdjustmentCatalog() {
        BillingFactPayload noParent = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
            CHANNEL_ID));
        noParent.setParentFactId(null);
        assertRejected(noParent, "成本调整必须指定来源支付事实");
        BillingFactPayload noClearing = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
            CHANNEL_ID));
        noClearing.getRefs().clear();
        assertRejected(noClearing, "成本调整必须引用 CLEARING_ACCOUNT");
        BillingFactPayload withOrder = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
            CHANNEL_ID));
        withOrder.getRefs().add(ref(FactRefType.ORDER, "1"));
        assertThrows(InvalidParameterException.class, () -> BillingFactInvariants.checkManualRegistration(withOrder));
        for (FactRefType automaticOnly : List.of(FactRefType.CHANNEL_TRANSACTION_REF, FactRefType.RECON_RESOLUTION)) {
            BillingFactPayload withAutomaticRef = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.CHANNEL, CHANNEL_ID));
            withAutomaticRef.getRefs().add(ref(automaticOnly, "1"));
            assertThrows(InvalidParameterException.class,
                () -> BillingFactInvariants.checkManualRegistration(withAutomaticRef));
        }
        BillingFactPayload charge = cost(costLine(FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
            CHANNEL_ID));
        charge.getLines().getFirst().setDirection(FactLineDirection.CHARGE.getCode());
        assertLineInvalid(charge);
        assertLineInvalid(cost(costLine(FactPartyRole.CLIENT, Long.toString(CLIENT_ID), FactPartyRole.CHANNEL, CHANNEL_ID)));
    }

    /** 金额为正、付方 ≠ 收方、头部主体角色的标识与头部一致。 */
    @Test
    void test_lineAmountAndParties() {
        BillingFactPayload zero = manual(null, null, clientPrincipal());
        zero.getLines().getFirst().setPostedAmount(BigDecimal.ZERO);
        assertLineInvalid(zero);
        assertLineInvalid(manual(null, null, line(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT, Long.toString(CLIENT_ID),
            FactPartyRole.CLIENT, Long.toString(CLIENT_ID), FactLineDirection.CHARGE, null)));
        assertLineInvalid(manual(null, null, line(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT, "999",
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactLineDirection.CHARGE, null)));
        assertLineInvalid(manual(null, null, line(FactComponentType.SELLER_TAX, FactPartyRole.CLIENT, Long.toString(CLIENT_ID),
            FactPartyRole.TAX_AUTHORITY, "TAX", FactLineDirection.CHARGE, null)));
    }

    /** 合作方二元组同有同无；无二元组不能登记合作方组件；平台服务费收方必须是 Finance Entity。 */
    @Test
    void test_partnerConsistency() {
        assertRejected(manual(PARTNER_ID, null, clientPrincipal()), "合作方二元组不完整");
        assertLineInvalid(manual(null, null, line(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactPartyRole.LOCAL_PARTNER, Long.toString(PARTNER_ID), FactLineDirection.CHARGE, null)));
        assertLineInvalid(manual(PARTNER_ID, FINANCE_ENTITY_ID, line(FactComponentType.PLATFORM_SERVICE_CHARGE,
            FactPartyRole.LOCAL_PARTNER, Long.toString(PARTNER_ID), FactPartyRole.CLIENT, Long.toString(CLIENT_ID),
            FactLineDirection.CHARGE, null)));
    }

    /** 校验明细行以 FACT_LINE_INVALID 拒绝。 */
    private static void assertLineInvalid(BillingFactPayload fact) {
        FailException error = assertThrows(FailException.class, () -> BillingFactInvariants.checkManualRegistration(fact));
        assertEquals(BillingFactErrorCode.FACT_LINE_INVALID.getCode(), error.getCode());
    }

    /** 校验被业务拒绝且消息含给定片段。 */
    private static void assertRejected(BillingFactPayload fact, String fragment) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class,
            () -> BillingFactInvariants.checkManualRegistration(fact));
        assertTrue(error.getMessage().contains(fragment), "拒绝消息应含 [" + fragment + "]: " + error.getMessage());
    }

    /** 人工调整头与给定明细行；引用为空。 */
    private static BillingFactPayload manual(Long localPartnerId, Long financeEntityId, BillingFactLinePayload... lines) {
        BillingFactPayload fact = new BillingFactPayload();
        fact.setFactId(1L);
        fact.setFactType(BillingFactType.MANUAL_ADJUSTMENT.getCode());
        fact.setSourceEventKey("ADJ-1");
        fact.setClientId(CLIENT_ID);
        fact.setEntityNo(ENTITY_NO);
        fact.setLocalPartnerId(localPartnerId);
        fact.setFinanceEntityId(financeEntityId);
        fact.setRefs(new ArrayList<>());
        fact.setLines(new ArrayList<>(List.of(lines)));
        return fact;
    }

    /** 成本调整头（来源事实与 CLEARING_ACCOUNT 已给出）与一行。 */
    private static BillingFactPayload cost(BillingFactLinePayload line) {
        BillingFactPayload fact = manual(null, null, line);
        fact.setFactType(BillingFactType.COST_ADJUSTMENT.getCode());
        fact.setParentFactId(2L);
        fact.getRefs().add(ref(FactRefType.CLEARING_ACCOUNT, "610001"));
        return fact;
    }

    /** OE 付 Client 本金 1。 */
    private static BillingFactLinePayload clientPrincipal() {
        return line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
            Long.toString(CLIENT_ID), FactLineDirection.CHARGE, null);
    }

    /** 渠道成本调整行。 */
    private static BillingFactLinePayload costLine(FactPartyRole payer, String payerId, FactPartyRole payee,
        String payeeId) {
        return line(FactComponentType.CHANNEL_COST, payer, payerId, payee, payeeId, FactLineDirection.ADJUSTMENT, null);
    }

    /** 金额为 1 的明细行。 */
    private static BillingFactLinePayload line(FactComponentType component, FactPartyRole payer, String payerId,
        FactPartyRole payee, String payeeId, FactLineDirection direction, FactTargetBucket bucket) {
        BillingFactLinePayload line = new BillingFactLinePayload();
        line.setLineNo(1);
        line.setComponentType(component.getCode());
        line.setPayerRole(payer.getCode());
        line.setPayerId(payerId);
        line.setPayeeRole(payee.getCode());
        line.setPayeeId(payeeId);
        line.setDirection(direction.getCode());
        line.setPostedAmount(BigDecimal.ONE);
        line.setTargetBucket(bucket == null ? null : bucket.getCode());
        return line;
    }

    /** 外部引用。 */
    private static BillingFactRefPayload ref(FactRefType refType, String refId) {
        BillingFactRefPayload ref = new BillingFactRefPayload();
        ref.setRefType(refType.getCode());
        ref.setRefId(refId);
        return ref;
    }

    /** 合法的自动 PAYMENT 事实（含合作方两行与渠道成本）通过。 */
    @Test
    void test_autoPaymentAccepted() {
        assertDoesNotThrow(() -> BillingFactInvariants.checkAutoPayment(payment(true)));
        assertDoesNotThrow(() -> BillingFactInvariants.checkAutoPayment(payment(false)));
    }

    /** 头部必填、毛额为正与合作方二元组。 */
    @Test
    void test_autoPaymentHeader() {
        assertPaymentRejected(fact -> fact.setBalanceAvailableTime(null), "事实头不完整");
        assertPaymentRejected(fact -> fact.setGrossAmount(BigDecimal.ZERO), "事实头不完整");
        assertPaymentRejected(fact -> fact.setFinanceEntityId(null), "合作方二元组不完整");
    }

    /** 引用必须覆盖八类且不含 TICKET。 */
    @Test
    void test_autoPaymentRefs() {
        assertPaymentRejected(fact -> fact.getRefs().removeIf(
            ref -> FactRefType.CHANNEL_TRANSACTION_REF.getCode().equals(ref.getRefType())), "引用集合不完整");
        assertPaymentRejected(fact -> fact.getRefs().add(ref(FactRefType.TICKET, "OPS-1")), "引用集合不完整");
    }

    /** 行目录：角色、方向、目标余额桶、渠道标识等于清算账户、合作方行只在有合作方时出现。 */
    @Test
    void test_autoPaymentLineCatalog() {
        assertPaymentRejected(fact -> fact.getLines().getFirst().setPayerRole(FactPartyRole.OPERATING_ENTITY.getCode()),
            "不符合行目录");
        assertPaymentRejected(fact -> fact.getLines().getFirst().setDirection(FactLineDirection.ADJUSTMENT.getCode()),
            "不符合行目录");
        assertPaymentRejected(fact -> fact.getLines().getFirst().setTargetBucket(FactTargetBucket.PENDING.getCode()),
            "不符合行目录");
        assertPaymentRejected(fact -> fact.getLines().getFirst().setPayerId(CHANNEL_ID), "不符合行目录");
        assertPaymentRejected(fact -> fact.getLines().add(paymentLine(FactComponentType.SELLER_TAX,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.TAX_AUTHORITY, CHANNEL_ID, "1")), "不符合行目录");
        BillingFactPayload withoutPartner = payment(false);
        withoutPartner.getLines().add(paymentLine(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.LOCAL_PARTNER, Long.toString(PARTNER_ID), "1"));
        assertThrows(BizCheckFailException.class, () -> BillingFactInvariants.checkAutoPayment(withoutPartner));
    }

    /** 本金恰一行，本金与手续费之和等于毛额。 */
    @Test
    void test_autoPaymentTotals() {
        assertPaymentRejected(fact -> fact.getLines().get(1).setPostedAmount(new BigDecimal("89")), "本金必须恰一行");
        assertPaymentRejected(fact -> fact.getLines().add(paymentLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.CHANNEL, CLEARING_ACCOUNT_ID, FactPartyRole.CLIENT, Long.toString(CLIENT_ID), "1")),
            "本金必须恰一行");
    }

    /** 修改一份合法 PAYMENT 事实后被业务拒绝，消息含给定片段。 */
    private static void assertPaymentRejected(Consumer<BillingFactPayload> change, String fragment) {
        BillingFactPayload fact = payment(true);
        change.accept(fact);
        BizCheckFailException error = assertThrows(BizCheckFailException.class,
            () -> BillingFactInvariants.checkAutoPayment(fact));
        assertTrue(error.getMessage().contains(fragment), "拒绝消息应含 [" + fragment + "]: " + error.getMessage());
    }

    /** 毛额 100：手续费 10、本金 90，可选合作方分润 8 与服务费 2，渠道成本 0.5；八类引用齐全。 */
    private static BillingFactPayload payment(boolean partner) {
        BillingFactPayload fact = manual(partner ? PARTNER_ID : null, partner ? FINANCE_ENTITY_ID : null,
            paymentLine(FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, CLEARING_ACCOUNT_ID,
                FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "10"),
            paymentLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, CLEARING_ACCOUNT_ID,
                FactPartyRole.CLIENT, Long.toString(CLIENT_ID), "90"),
            paymentLine(FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                CLEARING_ACCOUNT_ID, "0.5"));
        fact.setFactType(BillingFactType.PAYMENT.getCode());
        fact.setSourceEventKey("PAYMENT_ATTEMPT:1");
        fact.setCurrency("USD");
        fact.setGrossAmount(new BigDecimal("100"));
        fact.setBusinessTime(Instant.parse("2026-10-22T10:00:00Z"));
        fact.setBalanceAvailableTime(Instant.parse("2026-10-23T23:59:59Z"));
        if (partner) {
            fact.getLines().add(paymentLine(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.LOCAL_PARTNER, Long.toString(PARTNER_ID), "8"));
            fact.getLines().add(paymentLine(FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, FactPartyRole.FINANCE_ENTITY, Long.toString(FINANCE_ENTITY_ID), "2"));
        }
        for (FactRefType refType : List.of(FactRefType.ORDER, FactRefType.ORDER_CONTEXT, FactRefType.PAYMENT_INTENT,
            FactRefType.PAYMENT_ATTEMPT, FactRefType.PAYMENT_CHANNEL_ACCOUNT, FactRefType.MID,
            FactRefType.CHANNEL_TRANSACTION_REF)) {
            fact.getRefs().add(ref(refType, "9" + fact.getRefs().size()));
        }
        fact.getRefs().add(ref(FactRefType.CLEARING_ACCOUNT, CLEARING_ACCOUNT_ID));
        return fact;
    }

    /** 金额为给定值的 CHARGE 行。 */
    private static BillingFactLinePayload paymentLine(FactComponentType component, FactPartyRole payer, String payerId,
        FactPartyRole payee, String payeeId, String amount) {
        BillingFactLinePayload line = line(component, payer, payerId, payee, payeeId, FactLineDirection.CHARGE, null);
        line.setPostedAmount(new BigDecimal(amount));
        return line;
    }
}

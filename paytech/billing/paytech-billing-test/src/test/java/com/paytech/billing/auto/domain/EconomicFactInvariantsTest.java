package com.paytech.billing.auto.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.domainservice.validator.fact.EconomicFactInvariants;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 经济事实登记不变量（纯 Java 公共静态合同）：Partner 三元组全有或全无、Partner 明细行与三元组一致、平台服务费归属等于 Finance Entity
 * <li>由原 TransactionPostingRejectionTest 的四个 Partner 不变量用例迁入（任务卡 07 第 3.2 项）；第 11a 步登记入口接入后改由入口测试覆盖。</li>
 *
 * @see EconomicFactInvariants#checkPartnerConsistency(EconomicFactPayload)
 */
class EconomicFactInvariantsTest {
    private static final long FACT_ID = 31001L;

    private static final long PARTNER_ID = 3001L;

    private static final long PARTNER_AGREEMENT_OE_ID = 3101L;

    private static final long FINANCE_ENTITY_ID = 5601L;

    /** 无 Partner 与完整 Partner 两种合法组合均通过。 */
    @Test
    void test_consistentFactsAccepted() {
        assertDoesNotThrow(() -> EconomicFactInvariants.checkPartnerConsistency(
            fact(null, null, null, line(AmountSource.CLIENT_PRINCIPAL, null), line(AmountSource.CLIENT_FEE, null))));
        assertDoesNotThrow(() -> EconomicFactInvariants.checkPartnerConsistency(
            fact(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID, line(AmountSource.CLIENT_PRINCIPAL, null),
                line(AmountSource.PARTNER_SHARE, String.valueOf(PARTNER_ID)),
                line(AmountSource.PLATFORM_SERVICE_CHARGE, String.valueOf(FINANCE_ENTITY_ID)))));
    }

    /** Partner 三元组不完整。 */
    @Test
    void test_incompletePartnerTripleRejected() {
        assertRejected(fact(PARTNER_ID, null, null, line(AmountSource.CLIENT_PRINCIPAL, null),
            line(AmountSource.PARTNER_SHARE, String.valueOf(PARTNER_ID))), "Partner 三元组不完整");
    }

    /** 无 Partner 却存在分润明细。 */
    @Test
    void test_partnerComponentWithoutPartnerRejected() {
        assertRejected(fact(null, null, null, line(AmountSource.CLIENT_PRINCIPAL, null),
            line(AmountSource.PARTNER_SHARE, String.valueOf(PARTNER_ID))), "Partner 组件不一致",
            AmountSource.PARTNER_SHARE.getCode());
    }

    /** 有 Partner 但缺少平台服务费明细。 */
    @Test
    void test_partnerWithoutServiceChargeRejected() {
        assertRejected(fact(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID,
            line(AmountSource.CLIENT_PRINCIPAL, null), line(AmountSource.PARTNER_SHARE, String.valueOf(PARTNER_ID))),
            "Partner 组件不一致", AmountSource.PLATFORM_SERVICE_CHARGE.getCode());
    }

    /** 平台服务费归属与头 Finance Entity 不一致。 */
    @Test
    void test_serviceChargePartyMismatchRejected() {
        assertRejected(fact(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID,
            line(AmountSource.CLIENT_PRINCIPAL, null), line(AmountSource.PARTNER_SHARE, String.valueOf(PARTNER_ID)),
            line(AmountSource.PLATFORM_SERVICE_CHARGE, String.valueOf(FINANCE_ENTITY_ID + 1))), "平台服务费归属");
    }

    /** 校验被拒：消息含经济事实 ID 与全部给定片段。 */
    private static void assertRejected(EconomicFactPayload fact, String... fragments) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class,
            () -> EconomicFactInvariants.checkPartnerConsistency(fact));
        String message = error.getMessage();
        assertTrue(message.contains(String.valueOf(FACT_ID)), "拒绝消息应含经济事实 ID: " + message);
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
        }
    }

    /** PAYMENT 经济事实头与给定明细行。 */
    private static EconomicFactPayload fact(Long localPartnerId, Long partnerAgreementOeId, Long financeEntityId,
        EconomicFactLinePayload... lines) {
        EconomicFactPayload fact = new EconomicFactPayload();
        fact.setFactId(FACT_ID);
        fact.setFactType(EconomicsType.PAYMENT.getCode());
        fact.setLocalPartnerId(localPartnerId);
        fact.setPartnerAgreementOeId(partnerAgreementOeId);
        fact.setFinanceEntityId(financeEntityId);
        fact.setLines(new ArrayList<>(List.of(lines)));
        return fact;
    }

    /** 指定组件类型与归属方的明细行，金额固定为 1。 */
    private static EconomicFactLinePayload line(AmountSource type, String partyId) {
        EconomicFactLinePayload line = new EconomicFactLinePayload();
        line.setComponentType(type.getCode());
        line.setPartyId(partyId);
        line.setPostedAmount(BigDecimal.ONE);
        return line;
    }
}

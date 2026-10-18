package com.paytech.arch.auto;

import com.paytech.arch.rule.CardDataIsolationRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R11：卡域表与卡域加密包只允许 card-core / card-app 访问。
 *
 * @see com.paytech.arch.rule.CardDataIsolationRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class CardDataIsolationRuleAutoTest {
    private static final String CRYPTO_PACKAGE = Fixtures.classes("r11.cardcore.crypto");

    @Test
    void test_normalFlow() {
        ServiceArchitectureScope card = Fixtures.scope("card")
            .module(Fixtures.path("r11/pass/card/paytech-card-core"), Fixtures.classes("r11.cardcore"))
            .build();
        ServiceArchitectureScope payment = Fixtures.scope("payment")
            .module(Fixtures.path("r11/pass/payment/paytech-payment-core"), Fixtures.classes("r11.paymentcore.clean"))
            .build();

        ViolationChecker.of(new CardDataIsolationRule(CRYPTO_PACKAGE).check(card)).none();
        ViolationChecker.of(new CardDataIsolationRule(CRYPTO_PACKAGE).check(payment)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope payment = Fixtures.scope("payment")
            .module(Fixtures.path("r11/violation/payment/paytech-payment-core"), Fixtures.classes("r11.paymentcore"))
            .module(Fixtures.path("r11/violation/payment/paytech-payment-test"), Fixtures.classes("r11.paymenttest"))
            .build();

        ViolationChecker.of(new CardDataIsolationRule(CRYPTO_PACKAGE).check(payment))
            .allFrom("R11")
            .contains("PaymentAttemptMapper.xml:2", "t_payment_card_credential")
            .contains("PaymentAttemptMapper.xml:3", "t_payment_stored_card")
            .contains("TestCardMapper.xml:1", "t_payment_card_future_table")
            .contains("CardProbe.java:3", "import 了卡域加密包")
            .contains("CardProbe.java:4", "import 了卡域加密包")
            .containsMessage("PaymentCardReader", "CardCipher");
    }
}

package com.paytech.arch.auto;

import com.paytech.arch.rule.RuleViolation;
import com.paytech.arch.support.KnownDeviations;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 既有偏差清单：区分未登记违规与过期登记，清单格式非法时失败。
 *
 * @see com.paytech.arch.support.KnownDeviations#loadDefault()
 * @author Claude
 */
class KnownDeviationsAutoTest {

    private static final RuleViolation REGISTERED = new RuleViolation("R2", "com.paytech:paytech-x-api", "dep a");

    private static final RuleViolation OTHER = new RuleViolation("R2", "com.paytech:paytech-y-api", "dep b");

    private final KnownDeviations deviations = KnownDeviations.parse("""
        deviations:
          - rule: R2
            subject: com.paytech:paytech-x-api
            message: dep a
        """);

    @Test
    void test_normalFlow() {
        Assertions.assertEquals(List.of(OTHER), deviations.unregistered(List.of(REGISTERED, OTHER)));
        Assertions.assertEquals(List.of(), deviations.missing(List.of(REGISTERED), entry -> true));
    }

    @Test
    void test_staleEntry() {
        Assertions.assertEquals(List.of(REGISTERED), deviations.missing(List.of(), entry -> true));
        Assertions.assertEquals(List.of(), deviations.missing(List.of(), entry -> false));
    }

    @Test
    void test_invalidParameter() {
        Assertions.assertThrows(IllegalStateException.class, () -> KnownDeviations.parse("""
            deviations:
              - rule: R2
                subject: com.paytech:paytech-x-api
            """));
        Assertions.assertThrows(IllegalStateException.class, () -> KnownDeviations.parse("""
            deviations:
              - {rule: R2, subject: s, message: m}
              - {rule: R2, subject: s, message: m}
            """));
    }

    @Test
    void test_defaultResource() {
        Assertions.assertTrue(KnownDeviations.loadDefault().entries().stream()
            .allMatch(entry -> entry.ruleId().matches("R([1-9]|1[0-2])")));
    }
}

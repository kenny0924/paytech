package com.paytech.risk.auto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.service.request.RiskOperationRequestContext;
import com.paytech.risk.service.request.impl.RiskOperationRequestContextParserImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Operations 风控配置入口元数据的纯协议测试。 */
class RiskOperationRequestContextParserAutoTest {

    private final RiskOperationRequestContextParserImpl parser = new RiskOperationRequestContextParserImpl();

    @Test
    void test_parsePreservesValidCanonicalActorAndSession() {
        RiskOperationRequestContext context = parser.parse("9223372036854775807", "session id:ABC_123");

        assertEquals(Long.MAX_VALUE, context.actorAccountId());
        assertEquals("session id:ABC_123", context.sessionId());
        assertEquals(42L, parser.parsePositiveId("releaseId", "42"));
    }

    @Test
    void test_actorAndPathIdRejectNonCanonicalOrOverflowValuesWithoutTrimming() {
        for (String value : List.of("", "0", "00", "01", "+1", "-1", " 1", "1 ", "١",
            "9223372036854775808", "10000000000000000000")) {
            assertThrows(InvalidParameterException.class, () -> parser.parsePositiveId("releaseId", value), value);
        }
        assertThrows(InvalidParameterException.class, () -> parser.parsePositiveId("releaseId", null));
        assertThrows(InvalidParameterException.class, () -> parser.parse(null, "session"));
    }

    @Test
    void test_sessionAcceptsOnlyPrintableAsciiWithoutBoundarySpaces() {
        assertEquals("! internal spaces are retained !", parser.parse("1", "! internal spaces are retained !").sessionId());

        for (String value : List.of("", " leading", "trailing ", "line\nbreak", "tab\tvalue", "delete\u007f",
            "café", "🔒", "a".repeat(129))) {
            assertThrows(InvalidParameterException.class, () -> parser.parse("1", value), value);
        }
        assertThrows(InvalidParameterException.class, () -> parser.parse("1", null));
    }

    @Test
    void test_idempotencyKeyIsOpaqueAsciiAndLengthBounded() {
        assertEquals("Aa0._:-z", parser.parseIdempotencyKey("Aa0._:-z"));
        assertEquals("a".repeat(128), parser.parseIdempotencyKey("a".repeat(128)));

        for (String value : List.of("a".repeat(7), "a".repeat(129), "with space", "with/slash", "幂等键")) {
            assertThrows(InvalidParameterException.class, () -> parser.parseIdempotencyKey(value), value);
        }
        assertThrows(InvalidParameterException.class, () -> parser.parseIdempotencyKey(null));
    }

    @Test
    void test_upperCodeParsingIsExactAndOptionalNullStaysNull() {
        assertEquals("CHECKOUT_PREPARE", parser.parseRequiredUpperCode("pointCode", "CHECKOUT_PREPARE"));
        assertEquals("A" + "1".repeat(63),
            parser.parseOptionalUpperCode("status", "A" + "1".repeat(63)));
        assertNull(parser.parseOptionalUpperCode("status", null));

        for (String value : List.of("", "checkout", "1POINT", "_POINT", "POINT-DASH", " POINT", "POINT ",
            "A".repeat(65), "İ")) {
            assertThrows(InvalidParameterException.class,
                () -> parser.parseRequiredUpperCode("pointCode", value), value);
            assertThrows(InvalidParameterException.class,
                () -> parser.parseOptionalUpperCode("pointCode", value), value);
        }
        assertThrows(InvalidParameterException.class,
            () -> parser.parseRequiredUpperCode("pointCode", null));
    }
}

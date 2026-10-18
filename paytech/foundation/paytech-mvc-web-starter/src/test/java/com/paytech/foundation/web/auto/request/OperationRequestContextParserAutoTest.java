package com.paytech.foundation.web.auto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.request.OperationRequestContext;
import com.paytech.foundation.web.request.OperationRequestContextParser;
import com.paytech.foundation.web.request.OperationRequestContextParserImpl;

/**
 * Operations 入口元数据解析：与 Risk 原 {@code RiskOperationRequestContextParserImpl} 行为等价，错误统一为
 * {@code INVALID_REQUEST}。
 *
 * @see OperationRequestContextParser#parse(String, String)
 * @see OperationRequestContextParser#parsePositiveId(String, String)
 * @see OperationRequestContextParser#parseRequiredUpperCode(String, String)
 * @see OperationRequestContextParser#parseOptionalUpperCode(String, String)
 */
class OperationRequestContextParserAutoTest {

    private final OperationRequestContextParser parser = new OperationRequestContextParserImpl();

    @Test
    void test_normalFlow() {
        OperationRequestContext context = parser.parse("70001", "session-abc.123 ~ok");

        assertEquals(70001L, context.getActorAccountId());
        assertEquals("session-abc.123 ~ok", context.getSessionId());
        assertEquals(Long.MAX_VALUE, parser.parsePositiveId("ruleId", "9223372036854775807"));
        assertEquals("RULE_SET_1", parser.parseRequiredUpperCode("code", "RULE_SET_1"));
        assertNull(parser.parseOptionalUpperCode("code", null));
        assertEquals("A", parser.parseOptionalUpperCode("code", "A"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "01", "+1", "1.0", " 1", "9223372036854775808", "12345678901234567890", "١"})
    void test_invalidParameter_actorAccountId(String actorAccountId) {
        assertInvalid("actorAccountId 格式无效", () -> parser.parse(actorAccountId, "session"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " session", "session ", "tab\tsession", "会话", "line\nbreak"})
    void test_invalidParameter_sessionId(String sessionId) {
        assertInvalid("sessionId 格式无效", () -> parser.parse("1", sessionId));
    }

    @Test
    void test_edgeCase_sessionIdLength() {
        assertEquals(128, parser.parse("1", "s".repeat(128)).getSessionId().length());
        assertInvalid("sessionId 格式无效", () -> parser.parse("1", "s".repeat(129)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "rule", "1RULE", "_RULE", "RULE-SET", "RULE SET"})
    void test_invalidParameter_upperCode(String code) {
        assertInvalid("flowCode 格式无效", () -> parser.parseRequiredUpperCode("flowCode", code));
    }

    @Test
    void test_edgeCase_upperCodeLengthAndOptional() {
        assertEquals(64, parser.parseRequiredUpperCode("code", "A".repeat(64)).length());
        assertInvalid("code 格式无效", () -> parser.parseRequiredUpperCode("code", "A".repeat(65)));
        assertInvalid("code 格式无效", () -> parser.parseOptionalUpperCode("code", "lower"));
    }

    @Test
    void test_invalidParameter_unsafeParameterNameNotEchoed() {
        assertInvalid("parameter 格式无效", () -> parser.parsePositiveId("<script>", "x"));
        assertInvalid("parameter 格式无效", () -> parser.parsePositiveId(null, "x"));
    }

    private static void assertInvalid(String message, org.junit.jupiter.api.function.Executable executable) {
        InvalidParameterException exception = assertThrows(InvalidParameterException.class, executable);
        assertEquals(message, exception.getMessage());
        assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), exception.getCode());
    }
}

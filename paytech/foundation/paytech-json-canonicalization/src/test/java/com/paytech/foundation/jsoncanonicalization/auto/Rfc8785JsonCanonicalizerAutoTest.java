package com.paytech.foundation.jsoncanonicalization.auto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

class Rfc8785JsonCanonicalizerAutoTest {

    private static final String SAFE_MESSAGE = "JSON canonicalization failed";

    @Test
    void test_canonicalizeObjectSortsPropertiesAndPreservesArrays() {
        byte[] actual = Rfc8785JsonCanonicalizer.canonicalizeObject("""
            { "z": 0, "nested": { "b": true, "a": null }, "array": [3, 2, 1] }
            """.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(
            "{\"array\":[3,2,1],\"nested\":{\"a\":null,\"b\":true},\"z\":0}"
                .getBytes(StandardCharsets.UTF_8),
            actual
        );
    }

    @Test
    void test_canonicalizeObjectUsesUtf16PropertyOrder() {
        assertCanonical(
            "{\"\\uE000\":\"private-use\",\"\\uD800\\uDC00\":\"supplementary\"}",
            "{\"𐀀\":\"supplementary\",\"\":\"private-use\"}"
        );
    }

    @Test
    void test_canonicalizeObjectPreservesUnicodeNormalizationAndValidReplacementCharacter() {
        assertCanonical("{\"value\":\"é\"}", "{\"value\":\"é\"}");
        assertCanonical("{\"value\":\"e\\u0301\"}", "{\"value\":\"é\"}");
        assertCanonical("{\"value\":\"�\"}", "{\"value\":\"�\"}");

        byte[] nfc = canonical("{\"value\":\"é\"}");
        byte[] nfd = canonical("{\"value\":\"e\\u0301\"}");
        assertFalse(Arrays.equals(nfc, nfd));
    }

    @Test
    void test_rfcAppendixBZeroIsInsideTheObjectNumberProfile() {
        assertCanonical("{\"n\":0}", "{\"n\":0}");
    }

    @ParameterizedTest(name = "RFC Appendix B token {0} is outside the V1 Number Profile")
    @ValueSource(strings = {
        "5e-324",
        "-5e-324",
        "1.7976931348623157e+308",
        "-1.7976931348623157e+308",
        "9007199254740992",
        "-9007199254740992",
        "295147905179352830000",
        "9.999999999999997e+22",
        "1e+23",
        "1.0000000000000001e+23",
        "999999999999999700000",
        "999999999999999900000",
        "1e+21",
        "9.999999999999997e-7",
        "0.000001",
        "333333333.3333332",
        "333333333.33333325",
        "333333333.3333333",
        "333333333.3333334",
        "333333333.33333343",
        "-0.0000033333333333333333",
        "1424953923781206.2"
    })
    void test_rejectsRfcAppendixBNumbersOutsideTheObjectNumberProfile(String numberToken) {
        assertRejected("{\"canary-property\":\"canary-value\",\"n\":" + numberToken + "}");
    }

    @ParameterizedTest(name = "canonical non-negative integer {0}")
    @ValueSource(strings = {
        "0",
        "1",
        "2",
        "9",
        "10",
        "99",
        "100",
        "999",
        "1000",
        "1024",
        "1048576",
        "1000000",
        "1000000000000",
        "1000000000000000",
        "4294967296",
        "281474976710656",
        "4503599627370496",
        "9007199254740990",
        "9007199254740991"
    })
    void test_canonicalizeObjectPreservesCanonicalNonNegativeIntegerForms(String numberToken) {
        String json = "{\"n\":" + numberToken + "}";
        assertCanonical(json, json);
    }

    @Test
    void test_rejectsNullAndEmptyInput() {
        assertRejected((byte[]) null);
        assertRejected(new byte[0]);
    }

    @Test
    void test_rejectsUtf8BomAndMalformedUtf8() {
        assertRejected(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'});
        assertRejected(new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xC3, 0x28, '"', '}'});
    }

    @ParameterizedTest(name = "non-object Root {0}")
    @ValueSource(strings = {"[]", "\"value\"", "1", "true", "null"})
    void test_rejectsNonObjectRoots(String json) {
        assertRejected(json);
    }

    @ParameterizedTest(name = "duplicate property input {0}")
    @ValueSource(strings = {
        "{\"a\":1,\"a\":2}",
        "{\"a\":1,\"\\u0061\":2}",
        "{\"nested\":{\"a\":1,\"a\":2}}"
    })
    void test_rejectsDuplicateDecodedPropertyNamesAtEveryObjectDepth(String json) {
        assertRejected(json);
    }

    @Test
    void test_duplicateDecodedPropertyNameKeepsSafeOuterMessageAndOriginalCause() {
        JsonCanonicalizationException exception = assertRejected(
            "{\"canary-secret\":0,\"canary-secret\":1}"
        );

        assertFalse(exception.getMessage().contains("canary-secret"));
        assertNotNull(exception.getCause());
    }

    @ParameterizedTest(name = "unpaired surrogate input {0}")
    @ValueSource(strings = {
        "{\"value\":\"\\uD800\"}",
        "{\"\\uDC00\":\"value\"}"
    })
    void test_rejectsUnpairedSurrogatesInNamesAndValues(String json) {
        assertRejected(json);
    }

    @ParameterizedTest(name = "Number token {0} is outside the V1 profile")
    @ValueSource(strings = {
        "-1",
        "1.0",
        "1e0",
        "01",
        "9007199254740992",
        "-0.0",
        "-0e2",
        "-1e-324",
        "1e-320",
        "NaN",
        "Infinity",
        "-Infinity"
    })
    void test_rejectsNumbersOutsideTheObjectNumberProfile(String numberToken) {
        assertRejected("{\"canary-property\":\"canary-value\",\"n\":" + numberToken + "}");
    }

    @Test
    void test_rejectsNegativeZeroSourceTokenRequiredByRfcErrata7920() {
        assertRejected("{\"canary-property\":\"canary-value\",\"n\":-0}");
    }

    @ParameterizedTest(name = "non-standard JSON input {index}")
    @ValueSource(strings = {
        "{\"canary-property\":\"canary-value\"/*comment*/}",
        "{'canary-property':'canary-value'}",
        "{canaryProperty:\"canary-value\"}",
        "{\"canary-property\":\"canary-value\",}",
        "{\"canary-property\":\"canary-value\"} {\"second\":0}"
    })
    void test_rejectsNonStandardJsonAndMultipleRoots(String json) {
        assertRejected(json);
    }

    private static byte[] canonical(String json) {
        return Rfc8785JsonCanonicalizer.canonicalizeObject(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCanonical(String input, String expected) {
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), canonical(input));
    }

    private static JsonCanonicalizationException assertRejected(String json) {
        return assertRejected(json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonCanonicalizationException assertRejected(byte[] jsonUtf8) {
        JsonCanonicalizationException exception = assertThrows(
            JsonCanonicalizationException.class,
            () -> Rfc8785JsonCanonicalizer.canonicalizeObject(jsonUtf8)
        );

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertFalse(exception.getMessage().contains("canary-property"));
        assertFalse(exception.getMessage().contains("canary-value"));
        return exception;
    }
}

package com.paytech.foundation.jsoncanonicalization.auto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

class Rfc8785JsonCanonicalizerBoundaryAutoTest {

    private static final String RFC_SECTION_322_STRING_SOURCE =
        "\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\\"\\/";
    private static final String SAFE_MESSAGE = "JSON canonicalization failed";
    private static final int PARALLEL_CALL_TIMEOUT_SECONDS = 30;
    private static final int EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 10;

    @Test
    void test_documentByteLimitAcceptsExactLimitAndRejectsLimitPlusOne() {
        byte[] accepted = ("{}" + " ".repeat(1_048_574)).getBytes(StandardCharsets.UTF_8);
        byte[] rejected = ("{}" + " ".repeat(1_048_575)).getBytes(StandardCharsets.UTF_8);

        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), canonical(accepted));
        assertRejected(rejected, "{}");
    }

    @Test
    void test_tokenCountLimitAcceptsExactLimitAndRejectsLimitPlusOne() {
        String accepted = zeroArrayObject(99_995);
        String rejected = zeroArrayObject(99_996);

        assertArrayEquals(accepted.getBytes(StandardCharsets.UTF_8), canonical(accepted));
        assertRejected(rejected, "values");
    }

    @Test
    void test_nestingDepthLimitAcceptsExactLimitAndRejectsLimitPlusOne() {
        String accepted = nestedArrayObject(63);
        String rejected = nestedArrayObject(64);

        assertArrayEquals(accepted.getBytes(StandardCharsets.UTF_8), canonical(accepted));
        assertRejected(rejected, "value");
    }

    @Test
    void test_asciiStringLengthAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "a".repeat(262_144);

        assertStringBoundary(accepted, accepted + "a", "a".repeat(32));
    }

    @Test
    void test_bmpStringLengthAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "é".repeat(262_144);

        assertStringBoundary(accepted, accepted + "é", "é".repeat(16));
    }

    @Test
    void test_supplementaryStringLengthAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "😀".repeat(131_072);

        assertStringBoundary(accepted, accepted + "a", "😀".repeat(8));
    }

    @Test
    void test_asciiPropertyNameAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "a".repeat(512);

        assertPropertyNameBoundary(accepted, accepted + "a", accepted, "a".repeat(32));
    }

    @Test
    void test_bmpPropertyNameAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "é".repeat(512);

        assertPropertyNameBoundary(accepted, accepted + "é", accepted, "é".repeat(16));
    }

    @Test
    void test_supplementaryPropertyNameAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "😀".repeat(256);

        assertPropertyNameBoundary(accepted, accepted + "a", accepted, "😀".repeat(8));
    }

    @Test
    void test_escapedPropertyNameAcceptsExactUtf16LimitAndRejectsLimitPlusOne() {
        String accepted = "\\u0061".repeat(512);
        String rejected = accepted + "\\u0061";
        String decoded = "a".repeat(512);

        assertPropertyNameBoundary(accepted, rejected, decoded, "\\u0061");
    }

    @Test
    void test_rawAndEscapedExactLimitPropertyNamesCanonicalizeToIdenticalBytes() {
        String raw = objectWithPropertyName("a".repeat(512));
        String escaped = objectWithPropertyName("\\u0061".repeat(512));

        assertArrayEquals(canonical(raw), canonical(escaped));
    }

    @Test
    void test_numberLengthAcceptsSixteenDigitsAndRejectsSeventeenDigits() {
        assertArrayEquals(
            "{\"value\":9007199254740991}".getBytes(StandardCharsets.UTF_8),
            canonical("{\"value\":9007199254740991}")
        );
        assertRejected("{\"value\":10000000000000000}", "10000000000000000");
    }

    @Test
    void test_rfcSection322AcceptedProfileSubsetPreservesPrimitiveSemantics() {
        String source = "{\"z\":{\"b\":false,\"a\":true},"
            + "\"string\":\"" + RFC_SECTION_322_STRING_SOURCE + "\","
            + "\"literals\":[null,true,false]}";
        String expected = "{\"literals\":[null,true,false],"
            + "\"string\":\"€$\\u000f\\nA'B\\\""
            + "\\\\\\\\"
            + "\\\"/\","
            + "\"z\":{\"a\":true,\"b\":false}}";

        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), canonical(source));
    }

    @Test
    void test_unmodifiedRfcSection322ExampleIsRejectedByNarrowerNumberProfile() {
        String source = "{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,"
            + "0.000000000000000000000000001],"
            + "\"string\":\"" + RFC_SECTION_322_STRING_SOURCE + "\","
            + "\"literals\":[null,true,false]}";

        assertRejected(source, "numbers");
    }

    @Test
    void test_rfcSection323PropertyOrderUsesRawUtf16CodeUnits() {
        String source = "{\"\\u20ac\":\"Euro Sign\",\"\\r\":\"Carriage Return\","
            + "\"\\ufb33\":\"Hebrew Letter Dalet With Dagesh\",\"1\":\"One\","
            + "\"\\ud83d\\ude00\":\"Emoji: Grinning Face\",\"\\u0080\":\"Control\","
            + "\"\\u00f6\":\"Latin Small Letter O With Diaeresis\"}";
        String expected = "{\"\\r\":\"Carriage Return\",\"1\":\"One\","
            + "\"\u0080\":\"Control\",\"ö\":\"Latin Small Letter O With Diaeresis\","
            + "\"€\":\"Euro Sign\",\"😀\":\"Emoji: Grinning Face\","
            + "\"דּ\":\"Hebrew Letter Dalet With Dagesh\"}";

        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), canonical(source));
    }

    @Test
    void test_whitespaceAndEscapedPropertyRepresentationsProduceIdenticalBytes() {
        String compact = "{\"a\":\"é\",\"b\":0}";
        String spacedAndEscaped = " { \"\\u0062\" : 0, \"\\u0061\" : \"\\u00e9\" } ";

        assertArrayEquals(canonical(compact), canonical(spacedAndEscaped));
    }

    @Test
    void test_differentPropertyInsertionOrdersProduceIdenticalBytes() {
        String first = "{\"z\":0,\"nested\":{\"b\":false,\"a\":true},\"a\":null}";
        String second = "{\"a\":null,\"nested\":{\"a\":true,\"b\":false},\"z\":0}";

        assertArrayEquals(canonical(first), canonical(second));
    }

    @Test
    void test_canonicalizationDoesNotModifyInputArray() {
        byte[] input = " { \"b\" : 0, \"a\" : true } ".getBytes(StandardCharsets.UTF_8);
        byte[] before = input.clone();

        canonical(input);

        assertArrayEquals(before, input);
    }

    @Test
    void test_mutatingOneReturnDoesNotAffectIndependentReturnsOrLaterCalls() {
        byte[] expected = "{\"a\":true,\"b\":0}".getBytes(StandardCharsets.UTF_8);
        byte[] previouslyReturned = canonical("{\"b\":0,\"a\":true}");
        byte[] mutated = canonical("{\"a\":true,\"b\":0}");

        assertNotSame(previouslyReturned, mutated);
        mutated[0] = '[';
        byte[] laterReturn = canonical("{\"b\":0,\"a\":true}");

        assertArrayEquals(expected, previouslyReturned);
        assertArrayEquals(expected, laterReturn);
        assertFalse(java.util.Arrays.equals(mutated, previouslyReturned));
    }

    @Test
    void test_256ParallelInvocationsWithDifferentInsertionOrdersProduceFrozenBytes() throws Exception {
        byte[] expected = ("{\"a\":null,\"array\":[3,2,1],"
            + "\"nested\":{\"a\":true,\"b\":false},\"z\":0}")
            .getBytes(StandardCharsets.UTF_8);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        AssertionError interruptionFailure = null;
        try {
            List<java.util.concurrent.Callable<byte[]>> calls = new ArrayList<>(256);
            for (int index = 0; index < 256; index++) {
                String source = concurrentSource(index);
                calls.add(() -> canonical(source));
            }

            List<Future<byte[]>> results = executor.invokeAll(
                calls,
                PARALLEL_CALL_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
            assertEquals(256, results.size());
            for (int index = 0; index < results.size(); index++) {
                int taskIndex = index;
                Future<byte[]> result = results.get(index);
                assertTrue(result.isDone(), () -> "Canonicalization task did not complete: " + taskIndex);
                assertFalse(result.isCancelled(), () -> "Canonicalization task timed out: " + taskIndex);
                assertArrayEquals(expected, result.get());
            }
        } catch (InterruptedException exception) {
            interruptionFailure = new AssertionError(
                "Interrupted while waiting for parallel canonicalization tasks",
                exception
            );
        } finally {
            try {
                shutdownNowAndAssertTermination(executor);
            } catch (AssertionError cleanupFailure) {
                if (interruptionFailure == null) {
                    throw cleanupFailure;
                }
                interruptionFailure.addSuppressed(cleanupFailure);
            }
        }
        if (interruptionFailure != null) {
            Thread.currentThread().interrupt();
            throw interruptionFailure;
        }
    }

    private static void shutdownNowAndAssertTermination(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertTrue(
                executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Canonicalization executor did not terminate after shutdownNow"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                "Interrupted while waiting for canonicalization executor termination",
                exception
            );
        }
    }

    private static byte[] canonical(String json) {
        return canonical(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] canonical(byte[] jsonUtf8) {
        return Rfc8785JsonCanonicalizer.canonicalizeObject(jsonUtf8);
    }

    private static void assertStringBoundary(String accepted, String rejected, String canary) {
        String acceptedJson = "{\"value\":\"" + accepted + "\"}";
        String rejectedJson = "{\"value\":\"" + rejected + "\"}";

        assertArrayEquals(acceptedJson.getBytes(StandardCharsets.UTF_8), canonical(acceptedJson));
        assertRejected(rejectedJson, canary);
    }

    private static void assertPropertyNameBoundary(
        String accepted,
        String rejected,
        String expectedName,
        String canary
    ) {
        String expected = objectWithPropertyName(expectedName);

        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), canonical(objectWithPropertyName(accepted)));
        assertRejected(objectWithPropertyName(rejected), canary);
    }

    private static String objectWithPropertyName(String propertyName) {
        return "{\"" + propertyName + "\":0}";
    }

    private static String zeroArrayObject(int valueCount) {
        StringBuilder json = new StringBuilder(2 * valueCount + 12);
        json.append("{\"values\":[");
        for (int index = 0; index < valueCount; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('0');
        }
        return json.append("]}").toString();
    }

    private static String nestedArrayObject(int arrayDepth) {
        return "{\"value\":" + "[".repeat(arrayDepth) + "0" + "]".repeat(arrayDepth) + "}";
    }

    private static String concurrentSource(int index) {
        return switch (index % 4) {
            case 0 -> "{\"z\":0,\"nested\":{\"b\":false,\"a\":true},"
                + "\"array\":[3,2,1],\"a\":null}";
            case 1 -> "{\"a\":null,\"array\":[3,2,1],"
                + "\"nested\":{\"a\":true,\"b\":false},\"z\":0}";
            case 2 -> "{\"nested\":{\"a\":true,\"b\":false},\"z\":0,"
                + "\"a\":null,\"array\":[3,2,1]}";
            default -> "{\"array\":[3,2,1],\"a\":null,\"z\":0,"
                + "\"nested\":{\"b\":false,\"a\":true}}";
        };
    }

    private static JsonCanonicalizationException assertRejected(String json, String canary) {
        return assertRejected(json.getBytes(StandardCharsets.UTF_8), canary);
    }

    private static JsonCanonicalizationException assertRejected(byte[] jsonUtf8, String canary) {
        JsonCanonicalizationException exception = assertThrows(
            JsonCanonicalizationException.class,
            () -> canonical(jsonUtf8)
        );

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertFalse(exception.getMessage().contains(canary));
        return exception;
    }
}

package com.paytech.foundation.httpproblem.auto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiProblemProfileAutoTest {

    @Test
    void test_acceptsRequestIdsAtBothLengthBoundaries() {
        assertTrue(ApiProblemProfile.isValidRequestId("a"));
        assertTrue(ApiProblemProfile.isValidRequestId("A" + "a".repeat(63)));
    }

    @Test
    void test_rejectsRequestIdsOutsideTheProfileBoundaries() {
        assertFalse(ApiProblemProfile.isValidRequestId(null));
        assertFalse(ApiProblemProfile.isValidRequestId(""));
        assertFalse(ApiProblemProfile.isValidRequestId("-request"));
        assertFalse(ApiProblemProfile.isValidRequestId("request value"));
        assertFalse(ApiProblemProfile.isValidRequestId("a".repeat(65)));
    }

    @Test
    void test_formatsFixedUtcMicrosecondTimestampVector() {
        assertEquals(
            "2026-09-06T10:20:30.123456Z",
            ApiProblemProfile.formatTimestamp(Instant.parse("2026-09-06T10:20:30.123456789Z"))
        );
        assertTrue(ApiProblemProfile.isCanonicalTimestamp("2026-09-06T10:20:30.123456Z"));
    }

    @Test
    void test_rejectsNonCanonicalTimestampVectors() {
        assertFalse(ApiProblemProfile.isCanonicalTimestamp("2026-09-06T10:20:30Z"));
        assertFalse(ApiProblemProfile.isCanonicalTimestamp("2026-09-06T10:20:30.123456+00:00"));
        assertFalse(ApiProblemProfile.isCanonicalTimestamp("2026-09-06T10:20:30.12345Z"));
        assertFalse(ApiProblemProfile.isCanonicalTimestamp("not-a-timestamp"));
    }

    @Test
    void test_validatesRequiredProfileMembersAndRequestIdHeaderParity() {
        ApiProblemResponse response = new ApiProblemResponse(
            "about:blank",
            "Bad Request",
            "The request is invalid.",
            "INVALID_REQUEST",
            "request-04",
            "2026-09-06T10:20:30.123456Z",
            null
        );

        assertDoesNotThrow(() -> ApiProblemProfile.validate(response, "Bad Request", "request-04"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ApiProblemProfile.validate(response, "Conflict", "request-04")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ApiProblemProfile.validate(response, "Bad Request", "other-request")
        );
    }
}

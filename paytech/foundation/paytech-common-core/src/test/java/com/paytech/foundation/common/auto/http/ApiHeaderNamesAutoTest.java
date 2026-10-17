package com.paytech.foundation.common.auto.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.paytech.foundation.common.http.ApiHeaderNames;
import org.junit.jupiter.api.Test;

class ApiHeaderNamesAutoTest {

    @Test
    void test_exposesStableHttpHeaderNames() {
        assertEquals("X-Request-Id", ApiHeaderNames.REQUEST_ID);
        assertEquals("X-Gateway-Client-Id", ApiHeaderNames.GATEWAY_CLIENT_ID);
        assertEquals("X-Gateway-Credential-Id", ApiHeaderNames.GATEWAY_CREDENTIAL_ID);
        assertEquals("Idempotency-Key", ApiHeaderNames.IDEMPOTENCY_KEY);
    }
}

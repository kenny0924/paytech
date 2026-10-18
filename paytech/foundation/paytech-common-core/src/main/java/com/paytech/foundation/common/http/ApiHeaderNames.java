package com.paytech.foundation.common.http;

/** 稳定且框架无关的公共 HTTP Header 名称。 */
public final class ApiHeaderNames {

    public static final String REQUEST_ID = "X-Request-Id";

    public static final String GATEWAY_CLIENT_ID = "X-Gateway-Client-Id";

    public static final String GATEWAY_CREDENTIAL_ID = "X-Gateway-Credential-Id";

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private ApiHeaderNames() {
    }
}

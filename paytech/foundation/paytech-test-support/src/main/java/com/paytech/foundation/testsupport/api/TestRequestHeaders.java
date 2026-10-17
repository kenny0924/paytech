package com.paytech.foundation.testsupport.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;

/** 测试入口请求头工厂；每次独立构造，调用方可覆盖或删除，不校验负例值。 */
public final class TestRequestHeaders {
    /** 工具类不实例化。 */
    private TestRequestHeaders() {
    }

    /**
     * 模拟 Client Gateway 转发给业务服务的 JSON 请求，不代表验证 Gateway 认证。
     *
     * @param requestId 请求关联标识，null 时省略
     * @param clientId 可信 Client 身份原值，null 时省略，非法值保留给负例
     * @param credentialId 可信 Credential 身份原值，null 时省略
     * @return 本次独享且可修改的 Header，不自动添加 Idempotency-Key
     */
    public static HttpHeaders client(String requestId, String clientId, String credentialId) {
        var headers = json(requestId);
        if (clientId != null) {
            headers.set(ApiHeaderNames.GATEWAY_CLIENT_ID, clientId);
        }
        if (credentialId != null) {
            headers.set(ApiHeaderNames.GATEWAY_CREDENTIAL_ID, credentialId);
        }
        return headers;
    }

    /** Customer JSON 请求；访问凭据和网络信息由具体接口按合同补充。 */
    public static HttpHeaders customer(String requestId) {
        return json(requestId);
    }

    /** 内部 JSON 请求；具体调用方认证由接口提供，不携带 Gateway 身份。 */
    public static HttpHeaders internal(String requestId) {
        return json(requestId);
    }

    /** 构造公共 JSON 默认值，之后显式删除 Content-Type 或 Request ID 仍然有效。 */
    public static HttpHeaders json(String requestId) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestId != null) {
            headers.set(ApiHeaderNames.REQUEST_ID, requestId);
        }
        return headers;
    }
}

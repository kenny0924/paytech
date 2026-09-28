package com.paytech.foundation.httpclient.core.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;

/** 共享同步出站边界：传播当前 Request ID，并按接口配置限制响应大小。 */
public final class HttpServiceExchangeInterceptor implements ClientHttpRequestInterceptor {

    private final int maximumResponseBytes;

    public HttpServiceExchangeInterceptor(int maximumResponseBytes) {
        this.maximumResponseBytes = maximumResponseBytes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        String requestId = MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
        if (ApiProblemProfile.isValidRequestId(requestId)) {
            request.getHeaders().set(ApiProblemProfile.REQUEST_ID_HEADER, requestId);
        }
        ClientHttpResponse response = execution.execute(request, body);
        if (maximumResponseBytes == 0) {
            return response;
        }
        try (response) {
            byte[] bytes = response.getBody().readNBytes(maximumResponseBytes + 1);
            if (bytes.length > maximumResponseBytes) {
                throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
            }
            return new BufferedResponse(response.getStatusCode(), response.getStatusText(), response.getHeaders(), bytes);
        }
    }

    private record BufferedResponse(HttpStatusCode status, String statusText, HttpHeaders headers, byte[] body)
        implements ClientHttpResponse {
        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }
        @Override
        public String getStatusText() {
            return statusText;
        }
        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }
        @Override
        public void close() {
            // 网络响应已在读取有界正文后关闭。
        }
    }
}

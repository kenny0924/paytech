package com.paytech.platform.support.referencedata.client.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 限制单个响应体的字节数（1 MiB）：Foundation 只在严格 JSON 模式下限制响应大小，本客户端使用宽松 JSON，因此在此单独设上限。
 * <li>超过上限抛 UPSTREAM_INVALID_RESPONSE，该种类本次刷新失败。</li>
 */
public final class BoundedResponseInterceptor implements ClientHttpRequestInterceptor {

    /** 响应体上限（字节）。 */
    public static final int MAXIMUM_RESPONSE_BYTES = 1024 * 1024;

    /** 读取有界正文后关闭网络响应，返回内存副本。 */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        try (ClientHttpResponse response = execution.execute(request, body)) {
            byte[] bytes = response.getBody().readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
            if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE,
                    "Platform 参考数据响应超过 " + MAXIMUM_RESPONSE_BYTES + " 字节");
            }
            return new BufferedResponse(response.getStatusCode(), response.getStatusText(), response.getHeaders(), bytes);
        }
    }

    /** 已读入内存的响应。 */
    private record BufferedResponse(HttpStatusCode status, String statusText, HttpHeaders headers, byte[] body)
        implements ClientHttpResponse {

        /** 原状态码。 */
        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        /** 原状态文本。 */
        @Override
        public String getStatusText() {
            return statusText;
        }

        /** 原响应头。 */
        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        /** 内存正文。 */
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        /** 网络响应已在读取后关闭。 */
        @Override
        public void close() {
            // 无需释放资源。
        }
    }
}

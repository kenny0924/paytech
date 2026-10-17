package com.paytech.vault.client.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import lombok.AllArgsConstructor;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 在 RestClient Observation 可见前完成有界读取、闭合协议校验与错误脱敏。 */
@AllArgsConstructor
public final class BoundedResponseBody implements ClientHttpRequestInterceptor {
    private final VaultRequestHeaders requestHeaders;
    private static final int MAXIMUM_BYTES = 262144;
    private static final boolean LEGACY_ERROR_RESPONSE_ENABLED_FOR_VAULT_TARGET = true;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        requestHeaders.enforce(request);
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        int status = 0;
        byte[] bytes = new byte[MAXIMUM_BYTES + 1];
        InputStream responseBody = null;
        boolean successfulBufferOwnsBytes = false;
        try {
            ClientHttpResponse response = execution.execute(request, body);
            status = response.getStatusCode().value();
            responseBody = response.getBody();
            int length = readBounded(responseBody, bytes);
            if (length > MAXIMUM_BYTES) throw VaultResponseErrorHandler.invalidResponse(requestId);
            JsonNode parsed = parse(bytes, length);
            if (status != 200) {
                throw VaultResponseErrorHandler.failure(status, parsed, response.getHeaders(), requestId,
                        LEGACY_ERROR_RESPONSE_ENABLED_FOR_VAULT_TARGET);
            }
            validateSuccess(request, response, parsed);
            var buffered = new BufferedResponse(response.getStatusCode(), response.getHeaders(), bytes, length);
            successfulBufferOwnsBytes = true;
            return buffered;
        } catch (IOException ignored) {
            if (status == 0 || status == 200) throw VaultResponseErrorHandler.unavailable(requestId);
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        } catch (RuntimeException failure) {
            // 本层拥有专门的零泄漏协议契约，不能让低层异常进入 Observation。
            if (failure instanceof com.paytech.foundation.common.error.ErrorException
                    || failure instanceof com.paytech.foundation.common.error.FailException) throw failure;
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        } finally {
            if (!successfulBufferOwnsBytes) Arrays.fill(bytes, (byte) 0);
            closeWithoutDraining(responseBody);
        }
    }

    private static int readBounded(InputStream source, byte[] destination) throws IOException {
        int offset = 0;
        while (offset < destination.length) {
            int count = source.read(destination, offset, destination.length - offset);
            if (count < 0) return offset;
            if (count > 0) {
                offset += count;
                continue;
            }
            int value = source.read();
            if (value < 0) return offset;
            destination[offset++] = (byte) value;
        }
        return offset;
    }

    private static void closeWithoutDraining(InputStream responseBody) {
        if (responseBody == null) return;
        try {
            responseBody.close();
        } catch (IOException | RuntimeException ignored) {
            // 直接关闭 JDK Body；不能调用会尝试排空任意剩余响应的 ClientHttpResponse.close()。
        }
    }

    private static JsonNode parse(byte[] bytes, int length) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length)).toString();
            return JSON.readTree(text);
        } catch (RuntimeException | CharacterCodingException ignored) {
            return null;
        }
    }

    private static void validateSuccess(HttpRequest request, ClientHttpResponse response, JsonNode parsed) {
        String path = request.getURI().getPath();
        if (path.startsWith(VaultRequestHeaders.ADMIN_PATH_PREFIX)) {
            validateAdminSuccess(request, response, parsed, path);
            return;
        }
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        String field = path.equals("/vault/v1/records/protect") ? "token" : "value";
        if (!hasExactJsonContentType(response.getHeaders()) || parsed == null || !parsed.isObject()
                || !Set.copyOf(parsed.propertyNames()).equals(Set.of(field)) || !parsed.get(field).isString()) {
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        }
        try {
            if (field.equals("token")) VaultValidation.token(parsed.get(field).stringValue());
            else VaultValidation.value(parsed.get(field).stringValue());
        } catch (RuntimeException ignored) {
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        }
    }

    /**
     * 管理接口成功响应：JSON 对象；人工加/解密额外校验 Token 或原值，其余字段由 Jackson 按类型绑定。
     */
    private static void validateAdminSuccess(HttpRequest request, ClientHttpResponse response, JsonNode parsed,
            String path) {
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        if (!hasExactJsonContentType(response.getHeaders()) || parsed == null || !parsed.isObject()) {
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        }
        try {
            if (path.equals("/vault/v1/admin/manual/protect")) {
                require(Set.copyOf(parsed.propertyNames()).equals(Set.of("token")) && parsed.get("token").isString());
                VaultValidation.token(parsed.get("token").stringValue());
            } else if (path.equals("/vault/v1/admin/manual/reveal")) {
                require(Set.copyOf(parsed.propertyNames()).equals(Set.of("dataTypeCode", "value"))
                        && parsed.get("dataTypeCode").isString() && parsed.get("value").isString());
                VaultValidation.code(parsed.get("dataTypeCode").stringValue());
                VaultValidation.value(parsed.get("value").stringValue());
            }
        } catch (RuntimeException ignored) {
            throw VaultResponseErrorHandler.invalidResponse(requestId);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("invalid Vault admin response");
    }

    private static boolean hasExactJsonContentType(HttpHeaders headers) {
        List<String> values = headers.get(HttpHeaders.CONTENT_TYPE);
        if (values == null || values.size() != 1) return false;
        try {
            MediaType type = MediaType.parseMediaType(values.getFirst());
            return MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(type.getType() + "/" + type.getSubtype())
                    && (type.getCharset() == null || StandardCharsets.UTF_8.equals(type.getCharset()));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private record BufferedResponse(HttpStatusCode status, HttpHeaders headers, byte[] bytes, int length)
            implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() { return status; }
        @Override public String getStatusText() { return ""; }
        @Override public HttpHeaders getHeaders() { return headers; }
        @Override public InputStream getBody() { return new ByteArrayInputStream(bytes, 0, length); }
        @Override public void close() { Arrays.fill(bytes, (byte) 0); }
        @Override public String toString() { return "BufferedResponse[redacted]"; }
    }
}

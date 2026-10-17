package com.paytech.gateway.idempotency;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.idempotency.IdempotencyRecordStore.StoredResponse;
import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 重放已记录的响应：状态码、记录的响应头与响应体原样写出；Problem 体只把 requestId 改为本次值。
 * X-Request-Id、Cache-Control 等由终端响应头策略在提交前统一重建。
 */
@Component
public class IdempotencyReplayWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyReplayWriter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REQUEST_ID_MEMBER = "requestId";

    public Mono<Void> write(ServerWebExchange exchange, StoredResponse stored, String routeCode) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatusCode.valueOf(stored.status()));
        HttpHeaders headers = response.getHeaders();
        for (Map.Entry<String, List<String>> header : stored.headers().entrySet()) {
            headers.put(header.getKey(), header.getValue());
        }
        byte[] body = replayBody(exchange, headers, stored.body(), routeCode);
        headers.setContentLength(body.length);
        Mono<DataBuffer> buffer = Mono.fromSupplier(() -> response.bufferFactory().wrap(body))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        return response.writeWith(buffer);
    }

    private byte[] replayBody(ServerWebExchange exchange, HttpHeaders headers, byte[] body, String routeCode) {
        if (!isProblem(headers)) {
            return body;
        }
        Object requestId = exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID);
        try {
            JsonNode node = JSON.readTree(body);
            if (!(node instanceof ObjectNode problem) || !(requestId instanceof String currentRequestId)) {
                throw new IllegalStateException("重放 Problem 不是 JSON 对象或缺少当前 Request ID");
            }
            problem.put(REQUEST_ID_MEMBER, currentRequestId);
            return JSON.writeValueAsBytes(problem);
        } catch (JacksonException | IllegalStateException exception) {
            LOGGER.warn("请求幂等重放 Problem 无法改写 requestId，按原样写出: routeCode={}, failure={}", routeCode,
                exception.getClass().getSimpleName());
            return body;
        }
    }

    private boolean isProblem(HttpHeaders headers) {
        try {
            MediaType contentType = headers.getContentType();
            return contentType != null && contentType.equalsTypeAndSubtype(MediaType.APPLICATION_PROBLEM_JSON);
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }
}

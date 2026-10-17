package com.paytech.risk.test.base;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 字典维护接口测试的公共入口调用
 * <li>每次请求携带独立 Request ID 与 Gateway 转发的 Operations 可信身份，经真实 HTTP 进入字典生产链。</li>
 * <li>请求 DTO 先按宿主 JSON 配置序列化为原文（保留 null 键），原始字符串负例原样发送。</li>
 *
 * @author Kenny
 */
public abstract class AbstractDecisionDictionaryApiTest extends MockTestBase {
    /** 默认可信操作者。 */
    protected static final long ACTOR = 70001L;

    /** 默认会话标识。 */
    protected static final String SESSION = "risk-test-session";

    protected static final String REASONS = "/decision-dictionaries/v1/rejection-reasons";

    protected static final String TAGS = "/decision-dictionaries/v1/subject-tags";

    /** 由响应结果、ObjectMapper 和期望 Request ID 构造业务 Checker。 */
    @FunctionalInterface
    protected interface CheckerFactory<C> {
        C create(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId);
    }

    /** 以默认操作者发送 GET。 */
    protected <C> C get(String uri, CheckerFactory<C> checker) {
        return exchange(HttpMethod.GET, uri, headers(ACTOR), null, checker);
    }

    /** 以给定操作者发送带 JSON Body 的写请求。 */
    protected <C> C send(HttpMethod method, String uri, long actor, Object body, CheckerFactory<C> checker) {
        return exchange(method, uri, headers(actor), body, checker);
    }

    /** 发送一次请求；DTO 序列化为 JSON 原文，字符串 Body 原样发送。 */
    protected <C> C exchange(HttpMethod method, String uri, HttpHeaders headers, Object body,
        CheckerFactory<C> checker) {
        Object payload = body == null || body instanceof String ? body : objectMapper.writeValueAsString(body);
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return checker.create(http.exchange(method, uri, headers, payload), objectMapper, requestId);
    }

    /** 将请求 DTO 转为可修改的 JSON 树，用于构造缺键、多键或错误类型的报文。 */
    protected ObjectNode tree(Object dto) {
        return objectMapper.valueToTree(dto);
    }

    /** 两个请求在同一闸门后同时发出，返回各自结果（顺序与提交顺序一致）。 */
    protected <C> List<C> concurrently(Supplier<C> first, Supplier<C> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<C>> calls = new ArrayList<>();
            for (Supplier<C> call : List.of(first, second)) {
                calls.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return call.get();
                }));
            }
            start.countDown();
            List<C> results = new ArrayList<>();
            for (Future<C> call : calls) {
                results.add(call.get(15, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    /** Gateway 转发的 Operations 可信身份及独立 Request ID。 */
    protected static HttpHeaders headers(long actor) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-dictionary-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(actor));
        headers.set("X-Gateway-Session-Id", SESSION);
        return headers;
    }
}

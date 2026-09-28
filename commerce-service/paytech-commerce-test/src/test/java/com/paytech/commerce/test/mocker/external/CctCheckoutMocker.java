package com.paytech.commerce.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.client.contract.api.checkout.CheckoutEligibilityModels.PrecheckResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 Client、国家和币种配置真实 CCT 响应 DTO；未配置请求绝不默认成功。 */
@TestComponent
public final class CctCheckoutMocker implements Clear {
    private final Map<String, Reply> replies = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<String> unexpectedCalls = new ArrayList<>();
    private Consumer<Call> observation = call -> {
    };

    /** 配置当前场景的资格结果，不复制 CCT 的业务模型和数据库。 */
    public synchronized void mockPrecheck(long clientId, String country, String currency, PrecheckResponseBody body) {
        replies.put(key(clientId, country, currency), new Reply(200, body, null, false));
    }

    /** 使用公共错误响应 DTO 配置 HTTP 失败，与 eligible=false 的业务结果分开。 */
    public synchronized void mockHttpFailure(long clientId, int status, String code) {
        var problem = new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(),
            "测试依赖故障", code, "cct-checkout-test", "2026-09-08T00:00:00.000000Z", null);
        replies.put(key(clientId, "US", "USD"), new Reply(status, problem, null, false));
    }

    /** 畸形响应经过真实 HTTP 解码，不能直接让生产 Client 抛异常。 */
    public synchronized void mockMalformedResponse(long clientId, String body) {
        replies.put(key(clientId, "US", "USD"), new Reply(200, null, body, false));
    }

    /** 本例故意断开 HTTP 连接，验证真实传输错误。 */
    public synchronized void mockDisconnect(long clientId) {
        replies.put(key(clientId, "US", "USD"), new Reply(200, null, null, true));
    }

    /** 仅在测试 HTTP 线程观察时序或协调并发，不注入生产链。 */
    public synchronized void observe(Consumer<Call> observation) {
        this.observation = observation;
    }

    /** 匹配后在锁外执行观察，允许两个真实 HTTP 请求在测试屏障相遇。 */
    public Reply answer(Call call) {
        Reply reply;
        Consumer<Call> observer;
        synchronized (this) {
            calls.add(call);
            reply = replies.get(key(call.getClientId(), call.getCountry(), call.getCurrency()));
            observer = observation;
            if (reply == null) {
                unexpectedCalls
                    .add("未配置 CCT 预检：" + call.getClientId() + "/" + call.getCountry() + "/" + call.getCurrency());
                throw new AssertionError("CCT 预检响应未配置");
            }
        }
        observer.accept(call);
        return reply;
    }

    /** HTTP 框架尚未进入业务匹配或观察失败也必须进入 teardown 检查。 */
    public synchronized void recordUnexpected(String reason) {
        unexpectedCalls.add(reason);
    }

    /** 只读检查完整次数、身份、请求维度及公共 Header 传播。 */
    public synchronized void assertCalls(long clientId, int count, String requestId) {
        var matching = calls.stream().filter(call -> call.getClientId() == clientId).toList();
        assertEquals(count, matching.size(), "CCT 预检次数");
        for (var call : matching) {
            assertAll(() -> assertEquals("US", call.getCountry()), () -> assertEquals("USD", call.getCurrency()),
                () -> assertEquals("commerce-service", call.getCallerService()),
                () -> assertEquals(requestId, call.getRequestId()));
        }
    }

    /** 提前拒绝的场景不允许任何下游请求。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用 CCT");
    }

    /** 业务即使捕获远程错误，也不能掩盖未配置请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpectedCalls.isEmpty(), () -> "CCT 意外调用：" + unexpectedCalls);
    }

    /** 确认上例清理没有遗漏响应或调用记录。 */
    public synchronized void assertEmpty() {
        assertAll(() -> assertTrue(replies.isEmpty()), () -> assertTrue(calls.isEmpty()),
            () -> assertTrue(unexpectedCalls.isEmpty()));
    }

    /** 全局 AfterEach 统一清理本例配置、观察和调用证据。 */
    @Override
    public synchronized void clear() {
        replies.clear();
        calls.clear();
        unexpectedCalls.clear();
        observation = call -> {
        };
    }

    /** 操作固定为预检，匹配包含它所需的全部业务维度。 */
    private String key(long clientId, String country, String currency) {
        return clientId + "/" + country + "/" + currency;
    }

    /** 仅记录安全的接口维度和追踪 Header。 */
    @Getter
    @AllArgsConstructor
    public static final class Call {
        private final long clientId;
        private final String country;
        private final String currency;
        private final String callerService;
        private final String requestId;
    }

    /** 测试专用 HTTP 回应，body 可以是对方 DTO 或 Problem。 */
    @Getter
    @AllArgsConstructor
    public static final class Reply {
        private final int status;
        private final Object body;
        private final String rawBody;
        private final boolean disconnect;
    }
}

package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.cloud.client.discovery.simple.InstanceProperties;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.configuration.CommercialClientConfiguration;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * commercial 结账上下文解析的响应 DTO：按 Client / 国家 / 币种配置常驻响应，或按调用顺序配置一组响应（DTO、Problem、断连、延迟），并
 * 记录每次请求及其 X-Caller-Service Header
 * <li>顺序响应耗尽且没有常驻响应、或没有任何配置时记为意外调用；请求记录只含合成测试值。</li>
 * <li>观察回调在 Mock HTTP 线程、本次响应返回前执行，用于检查调用时点的本地状态或制造并发。</li>
 */
@TestComponent
public class CheckoutCommercialMocker implements Clear {
    /** Problem 响应 Header 与 Body 共用的 Request ID。 */
    public static final String ERROR_REQUEST_ID = "commercial-mock-request";

    private final ObjectProvider<SimpleDiscoveryProperties> discovery;
    private final Map<String, Reply> standing = new HashMap<>();
    private final Map<String, Deque<Reply>> ordered = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private volatile Consumer<ResolveCheckoutContextRequestBody> observation = request -> {
    };
    private List<InstanceProperties> removedInstances;

    /** 延迟获取测试 SimpleDiscovery 配置（Mock 端口注册后才能绑定），用于制造无可用实例故障。 */
    public CheckoutCommercialMocker(ObjectProvider<SimpleDiscoveryProperties> discovery) {
        this.discovery = discovery;
    }

    /** 本 Client / 国家 / 币种的每次解析都返回给定 DTO。 */
    public synchronized void mockResolution(long clientId, String country, String currency,
        ResolveCheckoutContextResponseBody body) {
        standing.put(key(clientId, country, currency), body(body));
    }

    /** 本 Client 的 US / USD 解析按调用顺序返回给定响应，优先于常驻响应；耗尽后回到常驻响应。 */
    public synchronized void replies(long clientId, Reply... replies) {
        ordered.put(key(clientId, "US", "USD"), new ArrayDeque<>(List.of(replies)));
    }

    /** 配置本例的调用观察回调。 */
    public void observe(Consumer<ResolveCheckoutContextRequestBody> value) {
        observation = value;
    }

    /** 从服务发现中移除 commercial-service 实例，使真实负载均衡走无实例路径；每例清理时恢复。 */
    public synchronized void removeInstances() {
        if (removedInstances == null) {
            removedInstances = discovery.getObject().getInstances().remove(CommercialClientConfiguration.SERVICE_ID);
        }
    }

    /** 原样返回的响应 DTO。 */
    public static Reply body(ResolveCheckoutContextResponseBody body) {
        return new Reply(body, null, Duration.ZERO);
    }

    /** 合规的第一方 Problem 错误；标题为该状态的标准短语。 */
    public static Reply problem(HttpStatus status, CodeEnum code) {
        var problem = new ApiProblemResponse("about:blank", status.getReasonPhrase(), "commercial Mock 错误",
            code.getCode(), ERROR_REQUEST_ID, "2026-10-24T00:00:00.000000Z", null);
        return new Reply(null, MockHttpFault.json(status.value(), problem), Duration.ZERO);
    }

    /** 错误体不是 Problem 的 HTTP 响应，例如代理返回的 HTML。 */
    public static Reply raw(HttpStatus status, String body) {
        return new Reply(null, MockHttpFault.raw(status.value(), body), Duration.ZERO);
    }

    /** 不发送响应直接断开连接。 */
    public static Reply disconnect() {
        return new Reply(null, MockHttpFault.disconnect(), Duration.ZERO);
    }

    /** 延迟后再返回给定响应，用于超过 acquiring 的读取超时。 */
    public static Reply delayed(Duration delay, Reply reply) {
        return new Reply(reply.body(), reply.fault(), delay);
    }

    /** HTTP Mock 实际调用此处：记录请求与 Header，取出本次响应。 */
    public synchronized Reply next(ResolveCheckoutContextRequestBody request, List<String> callerServices) {
        calls.add(new Call(copy(request), List.copyOf(callerServices)));
        String key = request.getClientId() + "/" + request.getConsumerCountryCode() + "/" + request.getCurrency();
        Deque<Reply> sequence = ordered.get(key);
        Reply reply = sequence != null && !sequence.isEmpty() ? sequence.poll() : standing.get(key);
        if (reply == null) {
            unexpected.add(key);
            throw new AssertionError("未配置的 commercial 结账上下文解析：" + key);
        }
        return reply;
    }

    /** HTTP Mock 在取出响应后、返回前调用本例观察回调。 */
    public void notifyObserver(ResolveCheckoutContextRequestBody request) {
        observation.accept(request);
    }

    /** 本例收到的全部解析请求，按调用顺序。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 断言某 Client 的解析调用次数。 */
    public synchronized void assertCalls(long clientId, int count) {
        assertEquals(count, calls.stream().filter(call -> Long.toString(clientId).equals(call.request().getClientId()))
            .count(), "commercial 解析调用次数");
    }

    /** 断言本例没有调用 commercial。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用 commercial，实际：" + calls.size());
    }

    /** 每例结束检查没有未配置的解析。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在未配置的 commercial 解析：" + unexpected);
    }

    /** 每例清理响应配置、调用与意外调用记录，并恢复被移除的服务实例。 */
    @Override
    public synchronized void clear() {
        if (removedInstances != null) {
            discovery.getObject().getInstances().put(CommercialClientConfiguration.SERVICE_ID, removedInstances);
            removedInstances = null;
        }
        standing.clear();
        ordered.clear();
        calls.clear();
        unexpected.clear();
        observation = request -> {
        };
    }

    /** 匹配键。 */
    static String key(long clientId, String country, String currency) {
        return clientId + "/" + country + "/" + currency;
    }

    /** 复制请求，避免后续修改影响记录。 */
    static ResolveCheckoutContextRequestBody copy(ResolveCheckoutContextRequestBody body) {
        var copy = new ResolveCheckoutContextRequestBody();
        copy.setClientId(body.getClientId());
        copy.setOrderNo(body.getOrderNo());
        copy.setConsumerCountryCode(body.getConsumerCountryCode());
        copy.setCurrency(body.getCurrency());
        copy.setPaymentMethodCode(body.getPaymentMethodCode());
        copy.setLocale(body.getLocale());
        copy.setContextAsOfTime(body.getContextAsOfTime());
        return copy;
    }

    /**
     * 一次解析调用的记录
     *
     * @param request 请求体副本
     * @param callerServices 实际收到的全部 X-Caller-Service Header 值
     */
    public record Call(ResolveCheckoutContextRequestBody request, List<String> callerServices) {
    }

    /**
     * 一次解析响应
     *
     * @param body 正常响应 DTO，故障时为空
     * @param fault HTTP 故障，正常时为空
     * @param delay 写出响应前的延迟
     */
    public record Reply(ResolveCheckoutContextResponseBody body, MockHttpFault fault, Duration delay) {
        /** 是否为 HTTP 故障（Problem 需写出与 Body 一致的 Request ID Header）。 */
        public boolean isFault() {
            return fault != null;
        }
    }
}

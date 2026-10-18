package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * billing 经济事实发布进度接口 Mock（关账步骤 2）：按账簿主体（主体类型/主体标识/币种）配置响应 DTO 与故障；未配置的查询记为意外
 * <li>{@link #mockNothingPublishedByDefault()} 让任意账簿主体返回“没有未发布事实、也没有已发布事实”。</li>
 */
@TestComponent
public class BillingProgressMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "billing-test";

    private final Map<String, Object> answers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean nothingPublishedByDefault;
    private int unexpected;

    /** 任意未单独配置的账簿主体返回零未发布、无已发布事实。 */
    public synchronized void mockNothingPublishedByDefault() {
        nothingPublishedByDefault = true;
    }

    /** 配置账簿主体的未发布数与已发布最大 factId。 */
    public synchronized void mockProgress(String bookEntityType, String bookEntityId, String currency, long pendingCount,
        Long maxPublishedFactId) {
        answers.put(key(bookEntityType, bookEntityId, currency), body(pendingCount, maxPublishedFactId));
    }

    /** 配置账簿主体的读取故障。 */
    public synchronized void mockFailure(String bookEntityType, String bookEntityId, String currency, MockHttpFault fault) {
        answers.put(key(bookEntityType, bookEntityId, currency), fault);
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-18T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的响应或故障。 */
    public synchronized PendingEconomicFactsResponseBody answer(String bookEntityType, String bookEntityId,
        String currency) {
        String key = key(bookEntityType, bookEntityId, currency);
        calls.add(key);
        Object configured = answers.get(key);
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null && nothingPublishedByDefault) {
            return body(0, null);
        }
        if (configured == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(
                ApiProblemProfile.TYPE, "Internal Server Error", "未配置的 pending 查询", "INTERNAL_ERROR", REQUEST_ID,
                "2026-10-18T00:00:00.000000Z", null));
        }
        return (PendingEconomicFactsResponseBody)configured;
    }

    /** 记录一次不在本 Mock 范围内的 billing 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "ledger 不应调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-18T00:00:00.000000Z", null));
    }

    /** 被查询的账簿主体，格式“类型/标识/币种”。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 billing 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        answers.clear();
        calls.clear();
        nothingPublishedByDefault = false;
        unexpected = 0;
    }

    /** 账簿主体键。 */
    static String key(String bookEntityType, String bookEntityId, String currency) {
        return bookEntityType + "/" + bookEntityId + "/" + currency;
    }

    /** 构造响应 DTO。 */
    static PendingEconomicFactsResponseBody body(long pendingCount, Long maxPublishedFactId) {
        PendingEconomicFactsResponseBody body = new PendingEconomicFactsResponseBody();
        body.setPendingCount(Long.toString(pendingCount));
        body.setMaxPendingFactId(null);
        body.setMaxPublishedFactId(maxPublishedFactId == null ? null : maxPublishedFactId.toString());
        return body;
    }
}

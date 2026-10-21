package com.paytech.billing.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

/**
 * platform-support 币种规则全量查询 Mock：配置整份列表响应或故障，记录调用次数；未配置的调用记为意外
 * <li>全局 BeforeEach 配置默认 USD 列表并刷新一次快照；用例可整体改配为其他列表、非法响应或故障。</li>
 */
@TestComponent
public class CurrencySpecMocker implements Clear {
    /** Mock 响应的 Request ID。 */
    public static final String REQUEST_ID = "platform-test";

    /** 已配置的列表响应或故障；未配置为空。 */
    private Object configured;

    private int listCalls;

    private final List<String> unexpected = new ArrayList<>();

    /** 配置 Platform 返回的全部币种规则（整份列表）。 */
    public synchronized void mockCurrencySpecs(CurrencySpecResponseBody... items) {
        CurrencySpecListResponseBody body = new CurrencySpecListResponseBody();
        body.setItems(Arrays.asList(items));
        configured = body;
    }

    /** 原样配置列表响应体，用于缺失 items 等非法响应。 */
    public synchronized void mockCurrencySpecList(CurrencySpecListResponseBody body) {
        configured = body;
    }

    /** 配置列表读取故障。 */
    public synchronized void mockFailure(MockHttpFault fault) {
        configured = fault;
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-20T00:00:00.000000Z", null));
    }

    /** 记录一次列表调用并返回已配置的响应；故障抛出，未配置记为意外。 */
    public synchronized CurrencySpecListResponseBody answerList() {
        listCalls++;
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null) {
            throw unexpectedCall("listCurrencySpecs（未配置）");
        }
        return (CurrencySpecListResponseBody)configured;
    }

    /** 记录一次不在配置范围内的 Platform 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "billing 不应调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-20T00:00:00.000000Z", null));
    }

    /** 全量列表被调用的次数（含全局 BeforeEach 的一次刷新）。 */
    public synchronized int listCalls() {
        return listCalls;
    }

    /** 全局 AfterEach：不允许出现未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的 platform-support 调用");
    }

    /** 全局 BeforeEach：上一例的配置与记录已清除。 */
    public synchronized void assertEmpty() {
        assertAll("Platform Mock 应处于空基线", () -> assertNull(configured), () -> assertEquals(0, listCalls),
            () -> assertEquals(List.of(), unexpected));
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        configured = null;
        listCalls = 0;
        unexpected.clear();
    }
}

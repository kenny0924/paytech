package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

/**
 * platform-support 币种规则查询 Mock：配置全部币种列表 DTO 或故障；未配置的查询与 ledger 不应调用的接口记为意外
 * <li>全局 BeforeEach 配置 USD（2 位、HALF_UP、ACTIVE）并刷新一次快照；用例可整体替换列表或改为故障后再刷新。</li>
 * <li>{@link #listCalls()} 记录列表查询次数，用于证明过账等业务读取不回源。</li>
 */
@TestComponent
public class CurrencySpecMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "platform-test";

    /** 测试默认币种。 */
    public static final String USD = "USD";

    private final List<String> calls = new ArrayList<>();
    private Object configured;
    private int unexpected;

    /** 配置 Platform 返回的全部币种规则；元素允许为空以构造非法响应。 */
    public synchronized void mockCurrencies(CurrencySpecResponseBody... items) {
        CurrencySpecListResponseBody body = new CurrencySpecListResponseBody();
        body.setItems(Arrays.asList(items));
        configured = body;
    }

    /** 原样配置列表响应体（例如缺少 items）。 */
    public synchronized void mockResponse(CurrencySpecListResponseBody body) {
        configured = body;
    }

    /** 配置列表查询故障。 */
    public synchronized void mockFailure(MockHttpFault fault) {
        configured = fault;
    }

    /** 默认 USD：2 位、HALF_UP、ACTIVE。 */
    public static CurrencySpecResponseBody usd() {
        return active(USD, 2);
    }

    /** ACTIVE、HALF_UP 的币种规则。 */
    public static CurrencySpecResponseBody active(String currency, int minorUnit) {
        return currency(currency, minorUnit, CurrencySpecStatus.ACTIVE);
    }

    /** 指定状态、HALF_UP 的币种规则；返回新对象，用例可再覆盖字段构造非法值。 */
    public static CurrencySpecResponseBody currency(String currency, int minorUnit, CurrencySpecStatus status) {
        CurrencySpecResponseBody body = new CurrencySpecResponseBody();
        body.setCurrency(currency);
        body.setMinorUnit(minorUnit);
        body.setRoundingMode(CurrencyRoundingMode.HALF_UP.getCode());
        body.setStatus(status.getCode());
        body.setVersion(1);
        body.setUpdateTime("2026-10-20T00:00:00.000000Z");
        return body;
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-20T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的列表或故障；未配置记为意外。 */
    public synchronized CurrencySpecListResponseBody answerList() {
        calls.add("list");
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null) {
            throw unexpectedCall("list (未配置)");
        }
        return (CurrencySpecListResponseBody)configured;
    }

    /** 记录一次不在本 Mock 范围内的 Platform 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "ledger 不应调用 platform " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-20T00:00:00.000000Z", null));
    }

    /** 列表查询次数（含故障响应）。 */
    public synchronized long listCalls() {
        return calls.stream().filter("list"::equals).count();
    }

    /** 全局 AfterEach：不允许出现未配置或范围外的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 platform 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        configured = null;
        calls.clear();
        unexpected = 0;
    }
}

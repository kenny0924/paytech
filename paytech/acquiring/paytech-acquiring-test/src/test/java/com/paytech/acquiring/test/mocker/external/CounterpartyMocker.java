package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.counterparty.api.customer.response.CardSavingEligibilityResponseBody;
import com.paytech.counterparty.api.customer.response.CustomerSummaryResponseBody;
import com.paytech.counterparty.api.customer.response.ResolveCustomerResponseBody;
import com.paytech.counterparty.api.enums.CardSavingIneligibleReason;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.counterparty.api.enums.CustomerStatus;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * counterparty 内部合同 Mock：只配置 counterparty-api 响应 DTO 与故障，记录 acquiring 发起的调用。
 * <li>Customer 的存在、状态、编号与存卡资格都由场景显式配置；未配置的调用记为意外并返回 500。</li>
 */
@TestComponent
public class CounterpartyMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "counterparty-test";

    private final Map<String, ResolveCustomerResponseBody> resolutions = new HashMap<>();
    private final Map<Long, CustomerSummaryResponseBody> customers = new HashMap<>();
    private final Map<String, Object> eligibility = new HashMap<>();
    private MockHttpFault resolveFault;
    private final List<Call> calls = new ArrayList<>();
    private int unexpected;

    /**
     * 一次调用
     *
     * @param operation RESOLVE、GET 或 ELIGIBILITY
     * @param clientId Client（GET 时为空）
     * @param customerId Customer 主键（RESOLVE 时为空）
     * @param consumerReference 消费者引用（仅 RESOLVE）
     */
    public record Call(String operation, String clientId, String customerId, String consumerReference) {}

    /** 配置一个 Customer：同一 Client 同一引用建或取都返回它，按主键也能读到。 */
    public synchronized void mockCustomer(long clientId, String consumerReference, long customerId, String customerNo) {
        resolutions.put(clientId + "|" + consumerReference, new ResolveCustomerResponseBody(Long.toString(customerId),
            customerNo, CustomerStatus.ACTIVE.getCode(), true));
        customers.put(customerId, new CustomerSummaryResponseBody(Long.toString(customerId), customerNo,
            Long.toString(clientId), CustomerStatus.ACTIVE.getCode()));
    }

    /** 配置建或取不可用。 */
    public synchronized void mockResolveFailure(MockHttpFault fault) {
        resolveFault = fault;
    }

    /** 配置存卡资格可用。 */
    public synchronized void mockEligible(long clientId, long customerId, String customerNo) {
        eligibility.put(clientId + "|" + customerId, new CardSavingEligibilityResponseBody(true, null, customerNo));
    }

    /** 配置存卡资格不可用的原因。 */
    public synchronized void mockIneligible(long clientId, long customerId, CardSavingIneligibleReason reason,
        String customerNo) {
        eligibility.put(clientId + "|" + customerId,
            new CardSavingEligibilityResponseBody(false, reason.getCode(), customerNo));
    }

    /** 配置存卡资格读取的故障或原始响应体。 */
    public synchronized void mockEligibilityFailure(long clientId, long customerId, MockHttpFault fault) {
        eligibility.put(clientId + "|" + customerId, fault);
    }

    /** 503 Problem，与 Foundation 依赖不可用的线上形态一致。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-18T00:00:00.000000Z", null));
    }

    /** 建或取：返回已配置的 Customer，第二次起 created=false。 */
    public synchronized ResolveCustomerResponseBody resolve(String clientId, String consumerReference) {
        calls.add(new Call("RESOLVE", clientId, null, consumerReference));
        if (resolveFault != null) {
            throw resolveFault;
        }
        var key = clientId + "|" + consumerReference;
        var configured = resolutions.get(key);
        if (configured == null) {
            throw unexpected();
        }
        resolutions.put(key, new ResolveCustomerResponseBody(configured.getCustomerId(), configured.getCustomerNo(),
            configured.getStatus(), false));
        return configured;
    }

    /** 按主键读取；未配置的主键按 counterparty 合同返回 404 CUSTOMER_NOT_FOUND。 */
    public synchronized CustomerSummaryResponseBody customer(String customerId) {
        calls.add(new Call("GET", null, customerId, null));
        var configured = customers.get(Long.parseLong(customerId));
        if (configured == null) {
            throw MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
                "Not Found", "Customer 不存在", CounterpartyErrorCode.CUSTOMER_NOT_FOUND.getCode(), REQUEST_ID,
                "2026-10-18T00:00:00.000000Z", null));
        }
        return configured;
    }

    /** 存卡资格：返回已配置的结论或故障。 */
    public synchronized CardSavingEligibilityResponseBody eligibility(String clientId, String customerId) {
        calls.add(new Call("ELIGIBILITY", clientId, customerId, null));
        var configured = eligibility.get(clientId + "|" + customerId);
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null) {
            throw unexpected();
        }
        return (CardSavingEligibilityResponseBody)configured;
    }

    /** 返回调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 指定操作的调用记录。 */
    public synchronized List<Call> calls(String operation) {
        return calls.stream().filter(call -> call.operation().equals(operation)).toList();
    }

    /** 全局 AfterEach：不允许出现未配置的 counterparty 调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 counterparty 调用");
    }

    /** 每例清除配置、故障和调用记录。 */
    @Override
    public synchronized void clear() {
        resolutions.clear();
        customers.clear();
        eligibility.clear();
        resolveFault = null;
        calls.clear();
        unexpected = 0;
    }

    private MockHttpFault unexpected() {
        unexpected++;
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "未配置的 counterparty 调用", "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-18T00:00:00.000000Z", null));
    }
}

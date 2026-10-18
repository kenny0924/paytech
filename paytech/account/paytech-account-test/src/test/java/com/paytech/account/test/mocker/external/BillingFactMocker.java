package com.paytech.account.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * billing 经济事实列表 Mock（余额重建）：按账簿主体配置事实 DTO，按第 11a 步游标合同分页返回；其余 billing 调用与未配置的账簿主体记为意外。
 */
@TestComponent
public class BillingFactMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "billing-test";

    private final Map<String, List<EconomicFactResponseBody>> facts = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private int unexpected;

    /** 配置账簿主体的全部已批准事实。 */
    public synchronized void mockFacts(String bookEntityType, String bookEntityId, String currency,
        List<EconomicFactResponseBody> items) {
        facts.put(key(bookEntityType, bookEntityId, currency), items.stream()
            .sorted(Comparator.comparingLong(item -> Long.parseLong(item.getFactId()))).toList());
    }

    /** 游标分页：返回 factId 大于游标的前 limit 条，未读完时给出下一游标。 */
    public synchronized EconomicFactListResponseBody list(String bookEntityType, String bookEntityId, String currency,
        String fromFactId, String limit) {
        String key = key(bookEntityType, bookEntityId, currency);
        calls.add("list:" + key + ":" + fromFactId);
        List<EconomicFactResponseBody> all = facts.get(key);
        if (all == null) {
            throw unexpectedCall("listEconomicFacts " + key);
        }
        long cursor = fromFactId == null ? 0 : Long.parseLong(fromFactId);
        int size = limit == null ? 100 : Integer.parseInt(limit);
        List<EconomicFactResponseBody> page = all.stream().filter(item -> Long.parseLong(item.getFactId()) > cursor)
            .limit(size).toList();
        EconomicFactListResponseBody body = new EconomicFactListResponseBody();
        body.setItems(page);
        body.setNextFromFactId(page.size() < size ? null : page.getLast().getFactId());
        return body;
    }

    /** 记录一次不在本 Mock 范围内的 billing 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "account 不应调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-18T00:00:00.000000Z", null));
    }

    /** 已记录的调用。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 billing 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        facts.clear();
        calls.clear();
        unexpected = 0;
    }

    /** 账簿主体键。 */
    static String key(String bookEntityType, String bookEntityId, String currency) {
        return bookEntityType + "/" + bookEntityId + "/" + currency;
    }
}

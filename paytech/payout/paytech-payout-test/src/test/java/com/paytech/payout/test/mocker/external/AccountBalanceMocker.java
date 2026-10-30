package com.paytech.payout.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * account 余额列表（technique 8.2，提供方 DTO）的 Mock：按"Owner 类型 + Owner ID"返回预设列表或故障
 * <li>未配置的 Owner 与单查询记为意外调用。</li>
 */
@TestComponent
public class AccountBalanceMocker implements Clear {
    private static final String CLIENTS = "clients:";

    private static final String PARTNERS = "partners:";

    private final Map<String, List<ClientBalanceResponseBody>> clientResponses = new HashMap<>();

    private final Map<String, List<PartnerBalanceResponseBody>> partnerResponses = new HashMap<>();

    private final Map<String, MockHttpFault> faults = new HashMap<>();

    private final List<String> calls = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    /** 配置 Client 的余额列表。 */
    public synchronized void mockClientPositions(long clientId, List<ClientBalanceResponseBody> positions) {
        clientResponses.put(CLIENTS + clientId, List.copyOf(positions));
    }

    /** 配置 Local Partner 的余额列表。 */
    public synchronized void mockPartnerPositions(long partnerId, List<PartnerBalanceResponseBody> positions) {
        partnerResponses.put(PARTNERS + partnerId, List.copyOf(positions));
    }

    /** 配置 Client 余额列表的 HTTP 故障。 */
    public synchronized void failClientPositions(long clientId, MockHttpFault fault) {
        faults.put(CLIENTS + clientId, fault);
    }

    /** Mock Controller 调用：Client 列表。 */
    public synchronized List<ClientBalanceResponseBody> clientPositions(String clientId) {
        return lookup(CLIENTS + clientId, clientResponses);
    }

    /** Mock Controller 调用：Partner 列表。 */
    public synchronized List<PartnerBalanceResponseBody> partnerPositions(String partnerId) {
        return lookup(PARTNERS + partnerId, partnerResponses);
    }

    /** Mock Controller 调用：不在 payout 调用范围的接口，记为意外调用并返回待抛出的错误。 */
    public synchronized AssertionError unexpected(String key) {
        calls.add(key);
        unexpected.add(key);
        return new AssertionError("account 非预期调用: " + key);
    }

    private <T> List<T> lookup(String key, Map<String, List<T>> responses) {
        calls.add(key);
        if (faults.containsKey(key)) {
            throw faults.get(key);
        }
        List<T> response = responses.get(key);
        if (response == null) {
            unexpected.add(key);
            throw new AssertionError("account 余额列表未配置: " + key);
        }
        return response;
    }

    /** 本例调用记录。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 每例开始时必须为空基线。 */
    public synchronized void assertEmpty() {
        assertTrue(clientResponses.isEmpty() && partnerResponses.isEmpty() && faults.isEmpty() && calls.isEmpty()
            && unexpected.isEmpty(), "account Mock 应处于空基线");
    }

    /** 意外调用检查。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 account 调用：" + unexpected);
    }

    /** 每例结束清理。 */
    @Override
    public synchronized void clear() {
        clientResponses.clear();
        partnerResponses.clear();
        faults.clear();
        calls.clear();
        unexpected.clear();
    }
}

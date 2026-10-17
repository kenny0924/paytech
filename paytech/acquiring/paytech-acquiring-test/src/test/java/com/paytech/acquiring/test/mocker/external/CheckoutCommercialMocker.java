package com.paytech.commerce.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.commerce.domainservice.port.customer.CheckoutCommercialPort;
import com.paytech.foundation.testsupport.api.Clear;

/** 本轮固定 Client 接口的测试替身；配置实际 DTO，不宣称覆盖未接入的 CCT HTTP Wire。 */
@TestComponent
@Primary
public class CheckoutCommercialMocker implements CheckoutCommercialPort, Clear {
    private final Map<String, CommercialFacts> replies = new HashMap<>();
    private final Map<String, RuntimeException> failures = new HashMap<>();
    private final List<InitializationRequest> calls = new ArrayList<>();
    private final List<Boolean> transactions = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private Consumer<InitializationRequest> observation = request -> {
    };

    public synchronized void mockFacts(long clientId, String country, String currency, CommercialFacts facts) {
        String key = clientId + "/" + country + "/" + currency;
        replies.put(key, facts);
        failures.remove(key);
    }

    public synchronized void mockFailure(long clientId, RuntimeException failure) {
        failures.put(clientId + "/US/USD", failure);
    }

    public synchronized void observe(Consumer<InitializationRequest> value) {
        observation = value;
    }

    @Override
    public CommercialFacts resolve(InitializationRequest request) {
        CommercialFacts reply;
        RuntimeException failure;
        Consumer<InitializationRequest> observer;
        synchronized (this) {
            calls.add(request);
            transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
            String key = request.getClientId() + "/" + request.getCountry() + "/" + request.getCurrency();
            failure = failures.get(key);
            reply = replies.get(key);
            observer = observation;
            if (!replies.containsKey(key) && failure == null) {
                unexpected.add(key);
                throw new AssertionError("未配置商业准备响应：" + key);
            }
        }
        observer.accept(request);
        if (failure != null) {
            throw failure;
        }
        return reply;
    }

    public synchronized void assertCalls(long clientId, int count) {
        assertEquals(count, calls.stream().filter(r -> r.getClientId() == clientId).count());
    }

    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty());
    }

    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), unexpected.toString());
    }

    public synchronized List<Boolean> transactionStates() {
        return List.copyOf(transactions);
    }

    @Override
    public synchronized void clear() {
        replies.clear();
        failures.clear();
        calls.clear();
        transactions.clear();
        unexpected.clear();
        observation = request -> {
        };
    }
}

package com.paytech.consumer.test.mocker.external;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.card.response.StoredCardListResponseBody;

/** Payment 已存卡 Mock：只配置响应 DTO 与故障，记录调用的 Client、Customer 与操作。 */
@TestComponent
public class StoredCardMocker implements Clear {
    private List<StoredCardListResponseBody.StoredCard> cards;
    private boolean deleteFound = true;
    private MockHttpFault fault;
    private final List<Call> calls = new ArrayList<>();

    /** 一次调用：操作、Client、Customer 与已存卡 Token（列出与失效时为空）。 */
    public record Call(String operation, String clientId, String customerId, String storedCardToken) {}

    /** 配置列出的已存卡。 */
    public synchronized void mockCards(List<StoredCardListResponseBody.StoredCard> value) {
        cards = List.copyOf(value);
    }

    /** 配置删除时已存卡是否存在；不存在时返回 404。 */
    public synchronized void mockDeleteFound(boolean value) {
        deleteFound = value;
    }

    /** 配置网络或下游故障，对所有操作生效。 */
    public synchronized void mockFailure(MockHttpFault value) {
        fault = value;
    }

    /** 记录并返回列表。 */
    public synchronized StoredCardListResponseBody list(String clientId, String customerId) {
        calls.add(new Call("LIST", clientId, customerId, null));
        throwIfFailing();
        var body = new StoredCardListResponseBody();
        body.setStoredCards(cards == null ? List.of() : cards);
        return body;
    }

    /** 记录删除；返回已存卡是否存在。 */
    public synchronized boolean delete(String clientId, String customerId, String storedCardToken) {
        calls.add(new Call("DELETE", clientId, customerId, storedCardToken));
        throwIfFailing();
        return deleteFound;
    }

    /** 记录失效并返回固定条数。 */
    public synchronized int invalidate(String clientId, String customerId) {
        calls.add(new Call("INVALIDATE", clientId, customerId, null));
        throwIfFailing();
        return 1;
    }

    /** 返回调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 每例清除响应、故障和调用记录。 */
    @Override
    public synchronized void clear() {
        cards = null;
        deleteFound = true;
        fault = null;
        calls.clear();
    }

    private void throwIfFailing() {
        if (fault != null) {
            throw fault;
        }
    }
}

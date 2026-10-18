package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 在真实 RestClient 发请求的线程观察事务；不替换客户端或响应（类名沿用 10 §2.3：Payment 与 card 两侧各一份）。
 * <li>第 12 步起 Payment 不再访问 Vault：本观察覆盖 card、channel-connector 与 Risk 的全部出站调用，断言均在数据库事务外。</li>
 */
@TestComponent
public class VaultTransactionObserver implements Clear {
    private final List<Boolean> transactions = new ArrayList<>();

    /** 只记录事务标志，不读取 Header、Token 或请求体。 */
    public synchronized void observe() {
        transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** 所有外部访问必须发生于数据库事务之外。 */
    public synchronized void assertOutsideTransactions() {
        assertFalse(transactions.contains(Boolean.TRUE), "出站 HTTP 调用发生在数据库事务内");
    }

    /** 正向场景必须实际经过观察点，避免空集合断言造成假通过。 */
    public synchronized void assertObservedOnce() {
        assertEquals(1, transactions.size());
        assertOutsideTransactions();
    }

    /** 每例清理观察结果。 */
    @Override
    public synchronized void clear() {
        transactions.clear();
    }
}

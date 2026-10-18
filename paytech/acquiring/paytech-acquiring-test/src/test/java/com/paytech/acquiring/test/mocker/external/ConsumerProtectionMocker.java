package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.domainservice.port.customer.ConsumerProtectionPort;
import com.paytech.foundation.testsupport.api.Clear;

/** 当前 Protect 尚无 HTTP Client；仅替换外部端口，不能据此宣称 Vault 协议已覆盖。 */
@TestComponent
@Primary
public class ConsumerProtectionMocker implements ConsumerProtectionPort, Clear {
    private final Map<ConsumerDetails, ProtectedConsumer> replies = new HashMap<>();
    private final Map<ConsumerDetails, RuntimeException> failures = new HashMap<>();
    private final List<ConsumerDetails> calls = new ArrayList<>();
    private final List<Boolean> transactions = new ArrayList<>();
    private Consumer<ConsumerDetails> observer = details -> {};
    private boolean unexpected;

    /** 精确匹配规范化后的完整 DTO，允许显式配置畸形空响应。 */
    public synchronized void mockResult(ConsumerDetails details, ProtectedConsumer reply) {
        replies.put(details, reply);
        failures.remove(details);
    }

    /** 配置保护失败，不在生产组件中添加故障开关。 */
    public synchronized void mockFailure(ConsumerDetails details, RuntimeException failure) {
        failures.put(details, failure);
    }

    /** 在外部调用窗口观察或制造真实数据库竞争。 */
    public synchronized void observe(Consumer<ConsumerDetails> observation) {
        observer = observation;
    }

    /** 记录实际事务状态；回调在锁外执行，允许两个 HTTP 请求同时到达屏障。 */
    @Override
    public ProtectedConsumer protect(ConsumerDetails details) {
        ProtectedConsumer reply;
        RuntimeException failure;
        Consumer<ConsumerDetails> observation;
        synchronized (this) {
            calls.add(details);
            transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (!replies.containsKey(details) && !failures.containsKey(details)) {
                unexpected = true;
                throw new AssertionError("未配置当前消费者资料的 Protect 响应");
            }
            reply = replies.get(details);
            failure = failures.get(details);
            observation = observer;
        }
        observation.accept(details);
        if (failure != null) {
            throw failure;
        }
        return reply;
    }

    /** 保存入口不能通过 Reveal 重新加载资料。 */
    @Override
    public synchronized ConsumerDetails reveal(ProtectedConsumer values) {
        unexpected = true;
        throw new AssertionError("保存消费者资料不应调用 Reveal");
    }

    /** 校验 DTO、次数和所有调用均在事务外。 */
    public synchronized void assertCalls(ConsumerDetails details, int count) {
        assertCalls(java.util.Collections.nCopies(count, details));
    }

    /** 完整替换时校验不同资料的调用顺序，不能沿用旧保护结果。 */
    public synchronized void assertCalls(List<ConsumerDetails> expected) {
        assertEquals(expected, calls);
        assertEquals(java.util.Collections.nCopies(expected.size(), false), transactions);
    }

    /** 校验参数或业务拒绝没有访问保护服务。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用消费者保护服务");
        assertFalse(unexpected, "出现未配置的保护调用");
    }

    /** 每例后清空全部配置和记录；即使清理前断言失败也不污染下一例。 */
    @Override
    public synchronized void clear() {
        boolean wasUnexpected = unexpected;
        boolean calledInTransaction = transactions.contains(true);
        replies.clear();
        failures.clear();
        calls.clear();
        transactions.clear();
        observer = details -> {};
        unexpected = false;
        assertAll(() -> assertFalse(wasUnexpected, "出现未配置的消费者保护调用"),
            () -> assertFalse(calledInTransaction, "事务内不允许调用外部保护服务"));
    }
}

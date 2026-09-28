package com.paytech.platform.support.test.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.delivery.integration.provider.EmailProvider;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;

/** 尚未选定真实供应商；本期在 EmailProvider 边界配置显式结果，仅位于测试制品。 */
@TestComponent
public class MockEmailProvider implements EmailProvider, Clear {

    private final Map<String, Deque<EmailProviderResult>> sends = new HashMap<>();
    private final Map<String, EmailProviderResult> queries = new HashMap<>();
    private final List<EmailProviderRequest> calls = new ArrayList<>();
    private final List<String> queryCalls = new ArrayList<>();
    private int unexpected;
    private SendPause nextSendPause;

    /** 此编码只由测试 Fixture 创建，不写入正式初始化 DML。 */
    @Override
    public String providerCode() {
        return "NOTIFICATION_TEST_EMAIL";
    }

    /** Mock 合同使用稳定原 Attempt 身份作为请求号。 */
    @Override
    public String requestNo(long attemptId) {
        return "email-" + attemptId;
    }

    /** 每个收件人必须显式配置发送证据。 */
    public synchronized void expectSend(String email, EmailProviderResult.Outcome... outcomes) {
        var queue = new ArrayDeque<EmailProviderResult>();
        for (var outcome : outcomes)
            queue.add(new EmailProviderResult(outcome, null, outcome.getCode()));
        sends.put(email, queue);
    }

    /** 原操作查询独立配置；NOT_FOUND 等模糊结果应使用 UNKNOWN。 */
    public synchronized void expectQuery(String requestNo, EmailProviderResult.Outcome outcome) {
        queries.put(requestNo, new EmailProviderResult(outcome, null, outcome.getCode()));
    }

    /** 保存仅含合成测试值的瞬时请求记录，并证明发送发生在事务外。 */
    @Override
    public EmailProviderResult send(EmailProviderRequest request) {
        EmailProviderResult result;
        SendPause pause;
        synchronized (this) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                unexpected++;
                throw new IllegalStateException("事务内邮件发送");
            }
            calls.add(request);
            var responses = sends.get(request.getEmail());
            if (responses == null || responses.isEmpty()) {
                unexpected++;
                throw new IllegalStateException("未配置邮件发送");
            }
            result = responses.removeFirst();
            pause = nextSendPause;
            nextSendPause = null;
        }
        if (pause != null)
            pause.block();
        return result;
    }

    /** 在正式外发边界暂停一次响应，重现旧执行者晚回写。 */
    public synchronized SendPause pauseNextSend() {
        nextSendPause = new SendPause();
        return nextSendPause;
    }

    /** 有界控制供应商响应；用例必须在 finally 释放。 */
    public static final class SendPause {
        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);

        /** 等待调用已进入外部发送，避免线程时序假设。 */
        public void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "外发未进入暂停点");
        }

        /** 放行响应并让真实 Dispatcher 完成回写。 */
        public void release() {
            released.countDown();
        }

        /** 后台最多等待八秒，防止失败用例挂住进程。 */
        void block() {
            entered.countDown();
            try {
                assertTrue(released.await(8, java.util.concurrent.TimeUnit.SECONDS), "外发暂停未释放");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("测试外发已中断", failure);
            }
        }
    }

    /** 查询原请求号且不发送邮件。 */
    @Override
    public synchronized EmailProviderResult query(String requestNo, String messageId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            unexpected++;
            throw new IllegalStateException("事务内邮件查询");
        }
        queryCalls.add(requestNo);
        if (!queries.containsKey(requestNo)) {
            unexpected++;
            throw new IllegalStateException("未配置邮件查询");
        }
        return queries.get(requestNo);
    }

    /** 返回当前用例的调用快照供只读断言。 */
    public synchronized List<EmailProviderRequest> calls() {
        return List.copyOf(calls);
    }

    /** 读取查询身份快照。 */
    public synchronized List<String> queryCalls() {
        return List.copyOf(queryCalls);
    }

    /** 即使业务吞下供应商异常，未配置调用仍使测试失败。 */
    public synchronized void assertExpected() {
        assertEquals(0, unexpected);
    }

    /** 清除全部当前用例 Mock 状态。 */
    @Override
    public synchronized void clear() {
        sends.clear();
        queries.clear();
        calls.clear();
        queryCalls.clear();
        unexpected = 0;
        nextSendPause = null;
    }
}

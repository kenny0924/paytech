package com.paytech.platform.support.auto.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.DELIVERED;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;

/**
 * 普通邮件模块入口的原子受理、独立收件人和前置拒绝。
 * @see SubmitNotificationProcessor#process(SubmitNotificationRequest)
 */
class SubmitNotificationTest extends NotificationTestBase {

    @Autowired
    SubmitNotificationProcessor processor;
    @Autowired
    PlatformTransactionManager transactionManager;

    /** 一次批量受理后分别发送，语言和参数按收件人覆盖。 */
    @Test
    void acceptsIndependentRecipients() {
        provider.expectSend("alice@example.com", DELIVERED);
        provider.expectSend("bob@example.com", DELIVERED);
        var request = NotificationRequestBuilder.template();
        request.setRecipients(List.of(request.getRecipients().getFirst(),
            NotificationRequestBuilder.recipient("bob@example.com", "en-US", "Bob")));
        var response = processor.process(request);
        assertTrue(response.getRequestId() > 0);
        assertEquals(2, response.getAcceptedCount());
        fixture.awaitIdle();
        assertEquals(1, mapper.requests());
        assertEquals(2, mapper.deliveries().size());
        assertEquals(2, mapper.attempts().size());
        for (var delivery : mapper.deliveries()) {
            assertEquals("DELIVERED", delivery.get("status"));
            assertEquals(1, ((Number) delivery.get("attempt_count")).intValue());
        }
        var calls = provider.calls();
        assertEquals(2, calls.size());
        var alice = calls.stream().filter(c -> c.getEmail().equals("alice@example.com")).findFirst().orElseThrow();
        var bob = calls.stream().filter(c -> c.getEmail().equals("bob@example.com")).findFirst().orElseThrow();
        assertEquals("通知 Alice", alice.getSubject());
        assertEquals("Maintenance：Alice", alice.getBody());
        assertEquals("Hello Bob", bob.getSubject());
        assertEquals("Maintenance: Bob", bob.getBody());
        assertTrue(mapper.events().isEmpty());
    }

    /** 大小写规范化后重复的邮箱整单拒绝。 */
    @Test
    void rejectsDuplicateMailboxBeforeVault() {
        var request = NotificationRequestBuilder.template();
        request.setRecipients(List.of(request.getRecipients().getFirst(),
            NotificationRequestBuilder.recipient("ALICE@example.com", "en-US", "Other")));
        assertThrows(FailException.class, () -> processor.process(request));
        assertEquals(0, mapper.requests());
        assertTrue(mapper.deliveries().isEmpty());
        assertTrue(provider.calls().isEmpty());
        vault.assertNoCalls();
    }

    /** 任一语言缺失均在远端保护和本地写入前失败。 */
    @Test
    void rejectsWholeRequestForMissingLanguage() {
        var request = NotificationRequestBuilder.template();
        request.setRecipients(List.of(request.getRecipients().getFirst(),
            NotificationRequestBuilder.recipient("bob@example.com", "fr-FR", "Bob")));
        assertThrows(FailException.class, () -> processor.process(request));
        assertEquals(0, mapper.requests());
        assertTrue(mapper.events().isEmpty());
        vault.assertNoCalls();
    }

    /** 同业务身份不创建第二批 Delivery，也不通过重复请求推进发送。 */
    @Test
    void sourceIdentityConflictsWithoutResending() {
        provider.expectSend("alice@example.com", DELIVERED);
        var request = NotificationRequestBuilder.template();
        processor.process(request);
        fixture.awaitIdle();
        assertThrows(ConcurrentRequestException.class, () -> processor.process(request));
        assertEquals(1, mapper.requests());
        assertEquals(1, provider.calls().size());
    }

    /** 第二条 Delivery 触发数据库唯一冲突，首条及其补偿任务也必须回滚。 */
    @Test
    void lateDatabaseConflictRollsBackEntireAcceptance() {
        vault.allow("bob@example.com", "vlt_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        var request = NotificationRequestBuilder.template();
        request.setRecipients(List.of(request.getRecipients().getFirst(),
            NotificationRequestBuilder.recipient("bob@example.com", "en-US", "Bob")));
        assertThrows(ConcurrentRequestException.class, () -> processor.process(request));
        fixture.awaitIdle();
        assertEquals(0, mapper.requests());
        assertTrue(mapper.deliveries().isEmpty());
        assertTrue(mapper.attempts().isEmpty());
        assertEquals(0, mapper.notificationTaskCount());
        assertTrue(mapper.events().isEmpty());
        assertTrue(provider.calls().isEmpty());
    }

    /** 基础校验由父模板实际执行，空列表不会查询数据或调用远端。 */
    @Test
    void validatesNestedInputThroughProcessor() {
        var request = NotificationRequestBuilder.template();
        request.setRecipients(List.of());
        assertThrows(FailException.class, () -> processor.process(request));
        assertEquals(0, mapper.requests());
        vault.assertNoCalls();
    }

    /** 调用方必须先提交自己的事务，不能让 Vault 隐藏在外层事务。 */
    @Test
    void rejectsCallerTransaction() {
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactionManager)
            .execute(status -> processor.process(NotificationRequestBuilder.template())));
        assertEquals(0, mapper.requests());
        vault.assertNoCalls();
    }
}

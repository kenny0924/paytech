package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;
import com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource;
import com.paytech.platform.support.delivery.ext.job.NotificationEventPollJob;
import com.paytech.platform.support.delivery.service.processor.PollNotificationEventsProcessor;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.delivery.service.processor.request.PollNotificationEventsRequest;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;
import com.paytech.platform.support.test.builder.SesEventBuilder;

/**
 * SES 投递事件经真实 AWS SDK 客户端从测试 SQS 终端拉取：送达、退信、拒发、投诉回写发送记录，
 * 重复与乱序事件结果确定，旧尝试与结果未知尝试的规则，匹配不到与无法解析的消息，以及拉取失败的运行状态。
 * SES 事件字段与 SQS 错误类型在生产代码中没有常量，按字面值构造与断言。
 *
 * @see NotificationEventPollJob#poll()
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationEventPollTest extends NotificationTestBase {

    static final String ALICE = "alice@example.com";

    static final String QUEUE = "https://sqs.ap-southeast-1.amazonaws.com/123456789012/notification-events";

    /** 与 NotificationTestMapper.sesProvider 写入账号表的密钥 Token 一致。 */
    static final String CREDENTIAL_TOKEN = "vlt_sesCredentialTokenForLoopbackMock000000";

    static final String VAULT_ACCESS_KEY = "AKIAVAULTSYNTHETIC01";

    static final String VAULT_SECRET_MARKER = "VAULT-SECRET-MARKER";

    static final String VAULT_CREDENTIAL =
        "{\"accessKeyId\":\"" + VAULT_ACCESS_KEY + "\",\"secretAccessKey\":\"" + VAULT_SECRET_MARKER + "\"}";

    static final long WINDOW_ELAPSED_SECONDS = Duration.ofMinutes(11).toSeconds();

    @Autowired SubmitNotificationProcessor processor;
    @Autowired NotificationEventPollJob job;
    @Autowired PollNotificationEventsProcessor pollProcessor;

    /** SES 为主账号、配置事件队列，Vault 中有合法密钥对。 */
    @BeforeEach
    void setUpEventQueue() {
        mapper.sesProvider();
        mapper.useSes();
        mapper.sesEventQueue(QUEUE);
        vault.allowCredential(CREDENTIAL_TOKEN, VAULT_CREDENTIAL);
    }

    /** 送达：SENT → DELIVERED，事件结果与时间写入尝试，消息删除；账号运行状态更新且不推进版本，敏感值不进日志。 */
    @Test
    void test_deliveryMarksDelivered(CapturedOutput output) {
        Object attemptId = sendAccepted("ses-message-1");
        int version = version();
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        var attempt = mapper.attemptEvents().getFirst();
        assertEquals(NotificationEventOutcome.DELIVERED.getCode(), attempt.get("event_outcome"));
        assertNull(attempt.get("event_outcome_code"));
        assertEquals(Instant.parse(SesEventBuilder.EVENT_TIME), instant(attempt.get("event_outcome_time")));
        assertEquals(List.of("rh-1"), sqs.deleted());
        var status = mapper.sesEventStatus();
        assertNotNull(status.get("event_poll_time"));
        assertNotNull(status.get("event_receive_time"));
        assertNull(status.get("event_poll_error"));
        assertEquals(version, version(), "运行状态不推进账号版本");
        var receive = sqs.requests().getFirst();
        assertEquals(QUEUE, receive.body().path("QueueUrl").asString());
        assertTrue(receive.headers().get("authorization").contains("Credential=" + VAULT_ACCESS_KEY + "/"),
            "必须使用 Vault 中的 Access Key 签名");
        assertTrue(receive.headers().get("authorization").contains("/ap-southeast-1/sqs/aws4_request"),
            "按队列 URL 的区域签名");
        assertEquals(2, vault.credentialRevealCount(), "发送一次、本轮拉取一次");
        assertNoSensitiveOutput(output);
    }

    /** 硬退信、拒发使投递失败；软退信只记录、状态不变；分类码只保留安全字符。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("undeliveredEvents")
    void test_bounceAndRejectOutcomes(String name, String body, NotificationEventOutcome outcome, String code,
        NotificationDeliveryStatus delivery) {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, body.replace("ATTEMPT", attemptId.toString()));

        job.poll();

        assertDelivery(delivery);
        var attempt = mapper.attemptEvents().getFirst();
        assertEquals(outcome.getCode(), attempt.get("event_outcome"));
        assertEquals(code, attempt.get("event_outcome_code"));
        assertEquals(List.of("rh-1"), sqs.deleted());
    }

    /** 名称、事件、结果、分类码、投递状态。 */
    static Stream<Arguments> undeliveredEvents() {
        return Stream.of(
            Arguments.of("硬退信", SesEventBuilder.bounce("ATTEMPT", "ses-message-1", "Permanent", "General"),
                NotificationEventOutcome.HARD_BOUNCE, "Permanent/General", NotificationDeliveryStatus.FAILED),
            Arguments.of("抑制列表", SesEventBuilder.bounce("ATTEMPT", "ses-message-1", "Permanent",
                "OnAccountSuppressionList"), NotificationEventOutcome.HARD_BOUNCE,
                "Permanent/OnAccountSuppressionList", NotificationDeliveryStatus.FAILED),
            Arguments.of("软退信", SesEventBuilder.bounce("ATTEMPT", "ses-message-1", "Transient", "MailboxFull"),
                NotificationEventOutcome.SOFT_BOUNCE, "Transient/MailboxFull", NotificationDeliveryStatus.SENT),
            Arguments.of("拒发", SesEventBuilder.reject("ATTEMPT", "ses-message-1", "Bad content <script>"),
                NotificationEventOutcome.REJECTED, "Reject/Bad content script", NotificationDeliveryStatus.FAILED));
    }

    /** 送达后投诉：保持已送达并记录首个投诉；重复送达与第二个投诉不改变事实，但消息都被删除。 */
    @Test
    void test_complaintAfterDeliveryAndDuplicates() {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"),
            SesEventBuilder.complaint(attemptId, "ses-message-1", "abuse"),
            SesEventBuilder.delivery(attemptId, "ses-message-1"),
            SesEventBuilder.complaint(attemptId, "ses-message-1", "fraud"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        var attempt = mapper.attemptEvents().getFirst();
        assertEquals(NotificationEventOutcome.DELIVERED.getCode(), attempt.get("event_outcome"));
        assertEquals("abuse", attempt.get("complaint_code"));
        assertEquals(Instant.parse(SesEventBuilder.EVENT_TIME), instant(attempt.get("complaint_time")));
        assertEquals(List.of("rh-1", "rh-2", "rh-3", "rh-4"), sqs.deleted());
    }

    /** 乱序：硬退信晚于送达覆盖为失败；之后再到的送达与软退信不使结果倒退。 */
    @Test
    void test_outOfOrderEventsDoNotRegress() {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"),
            SesEventBuilder.bounce(attemptId, "ses-message-1", "Permanent", "General"),
            SesEventBuilder.delivery(attemptId, "ses-message-1"),
            SesEventBuilder.bounce(attemptId, "ses-message-1", "Transient", "General"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.FAILED);
        var attempt = mapper.attemptEvents().getFirst();
        assertEquals(NotificationEventOutcome.HARD_BOUNCE.getCode(), attempt.get("event_outcome"));
        assertEquals("Permanent/General", attempt.get("event_outcome_code"));
        assertEquals(4, sqs.deleted().size());
    }

    /** 结果未知的尝试收到送达：尝试确认受理（来源 PROVIDER_EVENT）并补 MessageId，投递送达，待重发任务结束且不再发送。 */
    @Test
    void test_unknownAttemptConfirmedByDelivery() {
        Object attemptId = sendUnknown();
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-late-1"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        var attempt = mapper.attemptEvents().getFirst();
        assertEquals(NotificationAttemptStatus.ACCEPTED.getCode(), attempt.get("status"));
        assertEquals("ses-late-1", attempt.get("provider_message_id"));
        assertEquals(NotificationResolutionSource.PROVIDER_EVENT.getCode(), attempt.get("resolution_source"));
        assertEquals("EVENT_DELIVERY", attempt.get("resolution_code"));

        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();
        assertTrue(mapper.events().isEmpty(), "已送达后补偿任务结束");
        assertEquals(1, ses.requests().size(), "不再重发");
    }

    /** 结果未知的尝试只收到软退信：确认受理并转为已发送，避免安全窗口后重发。 */
    @Test
    void test_unknownAttemptConfirmedBySoftBounce() {
        Object attemptId = sendUnknown();
        sqs.messages(QUEUE, SesEventBuilder.bounce(attemptId, "ses-late-1", "Transient", "MailboxFull"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.SENT);
        assertEquals(NotificationAttemptStatus.ACCEPTED.getCode(), mapper.attemptEvents().getFirst().get("status"));
    }

    /** 旧尝试（已按安全窗口重发）送达：投递记为已送达；旧尝试的硬退信只记在旧尝试上，不影响当前尝试。 */
    @Test
    void test_previousAttemptEvents() {
        ses.fail(ALICE, 500, "InternalFailure", "boom").accept(ALICE, "ses-message-2");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();
        assertDelivery(NotificationDeliveryStatus.SENT);
        Object oldAttempt = mapper.attemptEvents().getFirst().get("attempt_id");

        sqs.messages(QUEUE, SesEventBuilder.bounce(oldAttempt, "ses-old-1", "Permanent", "General"));
        job.poll();
        assertDelivery(NotificationDeliveryStatus.SENT);
        assertEquals(NotificationEventOutcome.HARD_BOUNCE.getCode(), mapper.attemptEvents().getFirst().get("event_outcome"));

        sqs.clear();
        sqs.messages(QUEUE, SesEventBuilder.delivery(mapper.attemptEvents().getLast().get("attempt_id"), "ses-message-2"));
        job.poll();
        assertDelivery(NotificationDeliveryStatus.DELIVERED);
    }

    /** 旧尝试送达而当前尝试仍未知：投递直接记为已送达。 */
    @Test
    void test_previousAttemptDeliveredWhileCurrentUnknown() {
        ses.fail(ALICE, 500, "InternalFailure", "boom").fail(ALICE, 500, "InternalFailure", "boom");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();
        assertDelivery(NotificationDeliveryStatus.UNKNOWN);

        sqs.messages(QUEUE, SesEventBuilder.delivery(mapper.attemptEvents().getFirst().get("attempt_id"), "ses-old-1"));
        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        assertEquals(NotificationAttemptStatus.UNKNOWN.getCode(), mapper.attemptEvents().getLast().get("status"));
    }

    /** 尝试仍在发送中：事件延后处理，消息不删除。 */
    @Test
    void test_processingAttemptDeferred() {
        Object attemptId = sendAccepted("ses-message-1");
        mapper.interruptSend();
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"));

        job.poll();

        assertTrue(sqs.deleted().isEmpty(), "延后处理的消息留在队列");
        assertNull(mapper.attemptEvents().getFirst().get("event_outcome"));
        assertNull(mapper.sesEventStatus().get("event_poll_error"), "延后不是拉取失败");
    }

    /** 匹配不到（无标签、尝试不存在、消息标识不一致）与不处理的类型：不写业务表，消息删除。 */
    @Test
    void test_unmatchedAndIgnoredConsumed() {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, SesEventBuilder.deliveryWithoutTag("ses-message-1"),
            SesEventBuilder.delivery(999_999_999L, "ses-message-1"),
            SesEventBuilder.delivery(attemptId, "ses-other-message"),
            SesEventBuilder.other("Send", attemptId, "ses-message-1"),
            SesEventBuilder.other("Open", attemptId, "ses-message-1"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.SENT);
        assertNull(mapper.attemptEvents().getFirst().get("event_outcome"));
        assertEquals(List.of("rh-1", "rh-2", "rh-3", "rh-4", "rh-5"), sqs.deleted());
    }

    /** 无法解析的消息留在队列由死信队列兜底；同批其他消息照常处理，原文不进日志。 */
    @Test
    void test_invalidMessageKept(CapturedOutput output) {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, "not-json " + SesEventBuilder.RECIPIENT, "{\"eventType\":\"Delivery\"}",
            SesEventBuilder.delivery(attemptId, "ses-message-1").replace(SesEventBuilder.EVENT_TIME, "yesterday"),
            SesEventBuilder.delivery(attemptId, "ses-message-1"));

        job.poll();

        assertEquals(List.of("rh-4"), sqs.deleted());
        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        assertNoSensitiveOutput(output);
    }

    /** 未开启 Raw 投递时取 SNS 信封中的 Message。 */
    @Test
    void test_snsEnvelopeUnwrapped() {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, SesEventBuilder.snsEnvelope(SesEventBuilder.delivery(attemptId, "ses-message-1")));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
    }

    /** 拉取失败按类型记录错误码、不写最近拉取时间、不影响投递；下一轮成功后清空错误。 */
    @ParameterizedTest(name = "{1} -> {2}")
    @MethodSource("pollErrors")
    void test_pollFailureRecorded(int status, String errorType, String code) {
        sqs.failReceive(QUEUE, status, errorType);

        job.poll();

        var failed = mapper.sesEventStatus();
        assertEquals(code, failed.get("event_poll_error"));
        assertNull(failed.get("event_poll_time"));

        sqs.messages(QUEUE);
        job.poll();
        var recovered = mapper.sesEventStatus();
        assertNull(recovered.get("event_poll_error"));
        assertNotNull(recovered.get("event_poll_time"));
        assertNull(recovered.get("event_receive_time"), "空批不算收到事件");
    }

    /** HTTP 状态、SQS 错误类型、期望错误码。 */
    static Stream<Arguments> pollErrors() {
        return Stream.of(Arguments.of(403, "AccessDenied", "SQS_ACCESS_DENIED"),
            Arguments.of(400, "InvalidClientTokenId", "SQS_ACCESS_DENIED"),
            Arguments.of(400, "QueueDoesNotExist", "SQS_QUEUE_NOT_FOUND"),
            Arguments.of(400, "AWS.SimpleQueueService.NonExistentQueue", "SQS_QUEUE_NOT_FOUND"),
            Arguments.of(400, "RequestThrottled", "SQS_THROTTLED"),
            Arguments.of(500, "InternalError", "SQS_UNAVAILABLE"),
            Arguments.of(400, "InvalidParameterValue", "SQS_CLIENT_ERROR"));
    }

    /** Vault 不可用：本轮不访问 SQS，记为凭据不可用。 */
    @Test
    void test_credentialUnavailableSkipsQueue() {
        vault.credentialUnavailable();

        job.poll();

        assertEquals("CREDENTIAL_UNAVAILABLE", mapper.sesEventStatus().get("event_poll_error"));
        assertTrue(sqs.requests().isEmpty());
    }

    /** 删除失败：事件已回写，错误码记入运行状态（消息会再次到达，回写幂等）。 */
    @Test
    void test_deleteFailureRecorded() {
        Object attemptId = sendAccepted("ses-message-1");
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"))
            .failDelete(QUEUE, 403, "AccessDenied");

        job.poll();

        assertDelivery(NotificationDeliveryStatus.DELIVERED);
        assertEquals("SQS_ACCESS_DENIED", mapper.sesEventStatus().get("event_poll_error"));
    }

    /** 满批继续拉取下一批，直到不足一批或达到批数上限（测试配置为 2）。 */
    @Test
    void test_fullBatchContinues() {
        Object attemptId = sendAccepted("ses-message-1");
        String[] full = Stream.generate(() -> SesEventBuilder.other("Send", attemptId, "ses-message-1"))
            .limit(10).toArray(String[]::new);
        sqs.messages(QUEUE, full).messages(QUEUE, full);

        job.poll();

        assertEquals(20, sqs.deleted().size());
        assertEquals(2, sqs.requests().stream().filter(r -> r.target().endsWith("ReceiveMessage")).count());
    }

    /** 库中队列 URL 不合法（绕过接口校验写入）：不发起 SQS 请求，记为客户端错误。 */
    @Test
    void test_malformedQueueUrlRecorded() {
        mapper.sesEventQueue("https://example.com/not-a-queue");

        job.poll();

        assertEquals("SQS_CLIENT_ERROR", mapper.sesEventStatus().get("event_poll_error"));
        assertTrue(sqs.requests().isEmpty());
    }

    /** 提交时已被 SES 拒绝的尝试不会有投递事件：同标签事件视为匹配不到，不改变失败事实。 */
    @Test
    void test_rejectedAttemptUnmatched() {
        ses.fail(ALICE, 400, "MessageRejected", "rejected");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        Object attemptId = mapper.attemptEvents().getFirst().get("attempt_id");
        sqs.messages(QUEUE, SesEventBuilder.delivery(attemptId, "ses-message-1"));

        job.poll();

        assertDelivery(NotificationDeliveryStatus.FAILED);
        assertNull(mapper.attemptEvents().getFirst().get("event_outcome"));
        assertEquals(List.of("rh-1"), sqs.deleted());
    }

    /** 单账号入口：账号在两轮之间被删除或不是 SES 类型时直接跳过，不读 Vault、不访问 SQS、不写运行状态。 */
    @Test
    void test_processorSkipsAccountNoLongerEligible() {
        var missing = pollProcessor.process(new PollNotificationEventsRequest(999_999_999L));
        var notSes = pollProcessor.process(new PollNotificationEventsRequest(890004L));

        assertEquals(0, missing.getReceived());
        assertNull(notSes.getError());
        assertEquals(0, vault.credentialRevealCount());
        assertTrue(sqs.requests().isEmpty());
    }

    /** 未配置事件队列或未录入密钥的账号不拉取（任何 SQS 请求都会成为意外调用）。 */
    @Test
    void test_accountsWithoutQueueSkipped() {
        mapper.sesEventQueue(null);

        job.poll();

        assertTrue(sqs.requests().isEmpty());
        assertNull(mapper.sesEventStatus().get("event_poll_time"));
    }

    /** 发送一封 SES 受理的邮件，返回尝试身份。 */
    Object sendAccepted(String messageId) {
        ses.accept(ALICE, messageId);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertDelivery(NotificationDeliveryStatus.SENT);
        return mapper.attemptEvents().getFirst().get("attempt_id");
    }

    /** 发送一封 SES 返回 5xx（结果未知）的邮件，返回尝试身份。 */
    Object sendUnknown() {
        ses.fail(ALICE, 500, "InternalFailure", "boom");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertDelivery(NotificationDeliveryStatus.UNKNOWN);
        return mapper.attemptEvents().getFirst().get("attempt_id");
    }

    /** 唯一投递的当前状态。 */
    void assertDelivery(NotificationDeliveryStatus expected) {
        assertEquals(expected.getCode(), mapper.deliveries().getFirst().get("status"));
    }

    /** SES 测试账号版本。 */
    int version() {
        return ((Number) mapper.sesEventStatus().get("version")).intValue();
    }

    /** 收件地址、邮件头、密钥与 Token 不进入日志。 */
    static void assertNoSensitiveOutput(CapturedOutput output) {
        String all = output.getAll();
        assertFalse(all.contains(SesEventBuilder.RECIPIENT), "事件收件地址不得进入日志");
        assertFalse(all.contains(SesEventBuilder.SUBJECT_HEADER), "事件邮件头不得进入日志");
        assertFalse(all.contains(VAULT_SECRET_MARKER), "密钥不得进入日志");
        assertFalse(all.contains(CREDENTIAL_TOKEN), "凭据 Token 不得进入日志");
    }

    /** 数据库时间列转为 Instant（UTC）。 */
    static Instant instant(Object value) {
        LocalDateTime local = value instanceof Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
        return local.toInstant(ZoneOffset.UTC);
    }
}

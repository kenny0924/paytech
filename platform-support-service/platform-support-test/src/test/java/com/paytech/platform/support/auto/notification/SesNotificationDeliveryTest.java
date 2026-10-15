package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.ext.eventhandler.NotificationDeliveryHandler;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;
import com.paytech.platform.support.test.mocker.external.SesMocker;

/**
 * Amazon SES 适配器经真实 AWS SDK 客户端访问测试 SES 终端：受理即完成、SES 错误分类、
 * 结果未知后的安全窗口重发与次数上限，以及每次发送前从 Vault 读取 IAM 密钥对。
 * SES 结果码（如 SES_ACCEPTED）在生产代码中没有常量，按字面值断言。
 *
 * @see NotificationDeliveryHandler#handle(CompensationEvent)
 * @see SesEmailProvider#send(EmailProviderRequest)
 */
@ExtendWith(OutputCaptureExtension.class)
class SesNotificationDeliveryTest extends NotificationTestBase {

    static final String ALICE = "alice@example.com";

    /** 超过默认 10 分钟安全窗口。 */
    static final long WINDOW_ELAPSED_SECONDS = Duration.ofMinutes(11).toSeconds();

    /** 大于测试读超时 1 秒，确保客户端在收到响应前超时。 */
    static final long BEYOND_READ_TIMEOUT_MS = 1500;

    /** SES 测试账号与备用 Mock 账号的 Fixture 主键。 */
    static final long SES_PROVIDER_ID = 890007L;

    static final long FALLBACK_PROVIDER_ID = 890006L;

    /** 与 application-mock.yml 的 notification.ses.credential-token 一致。 */
    static final String CREDENTIAL_TOKEN = "vlt_sesCredentialTokenForLoopbackMock000000";

    /** Vault 中合成密钥对的 Access Key 与 Secret；Secret 用于验证不进入日志。 */
    static final String VAULT_ACCESS_KEY = "AKIAVAULTSYNTHETIC01";

    static final String VAULT_SECRET_MARKER = "VAULT-SECRET-MARKER";

    static final String VAULT_CREDENTIAL =
        "{\"accessKeyId\":\"" + VAULT_ACCESS_KEY + "\",\"secretAccessKey\":\"" + VAULT_SECRET_MARKER + "\"}";

    @Autowired SubmitNotificationProcessor processor;
    @Autowired Clock clock;

    /** 本类全部用例的主发送账号为 SES，Vault 中有合法密钥对。 */
    @BeforeEach
    void setUpSesRoute() {
        mapper.sesProvider();
        mapper.useSes();
        vault.allowCredential(CREDENTIAL_TOKEN, VAULT_CREDENTIAL);
    }

    /** SES 受理即 SENT 完成：请求字段正确、以 Vault 密钥签名、保存 MessageId，补偿任务结束且不再查询。 */
    @Test
    void test_acceptedCompletesWithoutQuery(CapturedOutput output) {
        ses.accept(ALICE, "ses-message-1");

        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();

        assertDelivery(NotificationDeliveryStatus.SENT, 1);
        var attempt = mapper.attempts().getFirst();
        assertEquals(NotificationAttemptStatus.ACCEPTED.getCode(), attempt.get("status"));
        assertEquals("ses-message-1", attempt.get("provider_message_id"));
        assertEquals("SES_ACCEPTED", attempt.get("response_code"));
        assertEquals("ses-" + attempt.get("attempt_id"), attempt.get("provider_request_no"));
        assertTrue(mapper.events().isEmpty(), "受理即完成，不保留查询任务");
        assertSendEmailRequest(onlyRequest(), attempt.get("provider_request_no").toString());
        assertTrue(provider.calls().isEmpty(), "主账号为 SES 时不调用 Mock Provider");
        assertEquals(1, vault.credentialRevealCount(), "每次发送前读取一次凭据");
        assertTrue(onlyRequest().headers().get("authorization").contains("Credential=" + VAULT_ACCESS_KEY + "/"),
            "必须使用 Vault 中的 Access Key 签名");
        assertFalse(output.getAll().contains(VAULT_SECRET_MARKER), "密钥不得进入日志");
        assertFalse(output.getAll().contains(CREDENTIAL_TOKEN), "凭据 Token 不得进入日志");
    }

    /** Vault 暂不可用时不登记尝试、不调用 SES；恢复后同一任务正常发送且只计一次发送。 */
    @Test
    void test_credentialUnavailableDefersWithoutAttempt() {
        vault.credentialUnavailable();
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();

        assertDelivery(NotificationDeliveryStatus.PENDING, 0);
        assertTrue(ses.requests().isEmpty());
        assertEquals(1, mapper.events().size(), "任务保留待重试");

        vault.allowCredential(CREDENTIAL_TOKEN, VAULT_CREDENTIAL);
        ses.accept(ALICE, "ses-message-1");
        scanDue();

        assertDelivery(NotificationDeliveryStatus.SENT, 1);
        assertTrue(mapper.events().isEmpty());
    }

    /** Vault 中的值不是合法密钥对时失败关闭：不登记尝试、不调用 SES，值不进入日志。 */
    @ParameterizedTest
    @MethodSource("malformedCredentials")
    void test_malformedCredentialDefersWithoutAttempt(String value, CapturedOutput output) {
        vault.allowCredential(CREDENTIAL_TOKEN, value);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();

        assertDelivery(NotificationDeliveryStatus.PENDING, 0);
        assertTrue(ses.requests().isEmpty());
        assertFalse(output.getAll().contains(VAULT_SECRET_MARKER), "不合法的值也不得进入日志");
    }

    /** 非 JSON、空对象、缺少密钥、字段类型错误。 */
    static Stream<String> malformedCredentials() {
        return Stream.of("not-json " + VAULT_SECRET_MARKER, "{}",
            "{\"accessKeyId\":\"" + VAULT_ACCESS_KEY + "\"}",
            "{\"accessKeyId\":1,\"secretAccessKey\":\"" + VAULT_SECRET_MARKER + "\"}");
    }

    /** 已 SENT 的投递在补偿重入时直接成功，不再调用 SES。 */
    @Test
    void test_sentReentryDoesNotCallSes() {
        ses.accept(ALICE, "ses-message-1");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        String deliveryId = mapper.deliveries().getFirst().get("delivery_id").toString();

        var result = handler.handle(new CompensationEvent(1, deliveryId, handler.eventType(), "-", null, 2));

        assertEquals(CompensationHandlerResult.Outcome.SUCCESS, result.outcome());
        assertEquals(1, ses.requests().size());
        assertDelivery(NotificationDeliveryStatus.SENT, 1);
    }

    /** SES 错误按类型映射为可重试、永久拒绝或未知；只保存结果码，SES 原文不进入数据库与日志。 */
    @ParameterizedTest(name = "{1} -> {2}")
    @MethodSource("sesErrors")
    void test_sesErrorClassification(int status, String errorType, String code, NotificationDeliveryStatus delivery,
        NotificationAttemptStatus attempt, CapturedOutput output) {
        ses.fail(ALICE, status, errorType, "rejected " + ALICE + " SES-RAW-MARKER");

        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();

        assertDelivery(delivery, 1);
        var saved = mapper.attempts().getFirst();
        assertEquals(attempt.getCode(), saved.get("status"));
        assertEquals(code, saved.get("response_code"));
        assertNull(saved.get("provider_message_id"));
        boolean terminal = delivery == NotificationDeliveryStatus.FAILED;
        assertEquals(terminal, mapper.events().isEmpty(), "仅确定失败结束任务");
        assertFalse(output.getAll().contains("SES-RAW-MARKER"), "SES 原始错误文字不得进入日志");
        assertEquals(1, ses.requests().size(), "适配器与 SDK 都不得暗中重试");
    }

    /** SES 错误类型、期望结果码与状态；类型名与 SES v2 错误 Shape 一致。 */
    static Stream<Arguments> sesErrors() {
        var retryWait = NotificationDeliveryStatus.RETRY_WAIT;
        var failed = NotificationDeliveryStatus.FAILED;
        var attemptFailed = NotificationAttemptStatus.FAILED;
        return Stream.of(Arguments.of(429, "TooManyRequestsException", "SES_THROTTLED", retryWait, attemptFailed),
            Arguments.of(400, "LimitExceededException", "SES_QUOTA_EXCEEDED", retryWait, attemptFailed),
            Arguments.of(400, "SendingPausedException", "SES_ACCOUNT_PAUSED", retryWait, attemptFailed),
            Arguments.of(400, "AccountSuspendedException", "SES_ACCOUNT_SUSPENDED", retryWait, attemptFailed),
            Arguments.of(400, "MailFromDomainNotVerifiedException", "SES_SENDER_NOT_VERIFIED", retryWait, attemptFailed),
            Arguments.of(404, "NotFoundException", "SES_CONFIGURATION_NOT_FOUND", retryWait, attemptFailed),
            Arguments.of(400, "MessageRejected", "SES_MESSAGE_REJECTED", failed, attemptFailed),
            Arguments.of(400, "BadRequestException", "SES_BAD_REQUEST", failed, attemptFailed),
            Arguments.of(403, "AccessDeniedException", "SES_CREDENTIAL_REJECTED", retryWait, attemptFailed),
            Arguments.of(403, "UnrecognizedClientException", "SES_CREDENTIAL_REJECTED", retryWait, attemptFailed),
            Arguments.of(403, "SignatureDoesNotMatch", "SES_CREDENTIAL_REJECTED", retryWait, attemptFailed),
            Arguments.of(400, "UnexpectedClientException", "SES_CLIENT_ERROR", failed, attemptFailed),
            Arguments.of(500, "InternalFailure", "SES_SERVER_ERROR", NotificationDeliveryStatus.UNKNOWN,
                NotificationAttemptStatus.UNKNOWN));
    }

    /** SES 节流后按现有路由规则切到备用账号重发。 */
    @Test
    void test_throttledRetriesOnFallbackAccount() {
        mapper.fallbackProvider();
        mapper.useFallback();
        ses.fail(ALICE, 429, "TooManyRequestsException", "slow down");
        provider.expectSend(ALICE, EmailProviderResult.Outcome.DELIVERED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertDelivery(NotificationDeliveryStatus.RETRY_WAIT, 1);

        mapper.retryDue(clock.instant());
        scanDue();

        assertDelivery(NotificationDeliveryStatus.DELIVERED, 2);
        var attempts = mapper.attempts();
        assertEquals(SES_PROVIDER_ID, ((Number) attempts.getFirst().get("provider_id")).longValue());
        assertEquals(FALLBACK_PROVIDER_ID, ((Number) attempts.getLast().get("provider_id")).longValue());
        assertEquals(1, ses.requests().size());
    }

    /** 读超时为 UNKNOWN；窗口内不重发，窗口过后新建尝试并受理，旧尝试保留 UNKNOWN。 */
    @Test
    void test_timeoutResendsOnlyAfterWindow() {
        ses.acceptAfter(ALICE, "late-message", BEYOND_READ_TIMEOUT_MS).accept(ALICE, "ses-message-2");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mockServer.awaitIdle();
        assertDelivery(NotificationDeliveryStatus.UNKNOWN, 1);
        assertEquals("SES_RESULT_UNKNOWN", mapper.attempts().getFirst().get("response_code"));

        scanDue();
        assertDelivery(NotificationDeliveryStatus.UNKNOWN, 1);
        assertEquals(1, ses.requests().size(), "安全窗口内不得重发");

        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();

        assertDelivery(NotificationDeliveryStatus.SENT, 2);
        var attempts = mapper.attempts();
        assertEquals(NotificationAttemptStatus.UNKNOWN.getCode(), attempts.getFirst().get("status"));
        assertEquals(NotificationAttemptStatus.ACCEPTED.getCode(), attempts.getLast().get("status"));
        assertEquals("ses-message-2", attempts.getLast().get("provider_message_id"));
        assertEquals(2, ses.requests().size());
        assertEquals(2, vault.revealCount(), "每次实际发送前瞬时 Reveal");
        assertTrue(mapper.events().isEmpty());
    }

    /** 5xx、断连、缺少 MessageId 均为 UNKNOWN；第三次仍未知且窗口过后终止任务，Delivery 保持 UNKNOWN。 */
    @Test
    void test_unknownExhaustedStopsTask() {
        ses.fail(ALICE, 500, "InternalFailure", "boom").disconnect(ALICE).acceptWithoutMessageId(ALICE);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertEquals("SES_SERVER_ERROR", mapper.attempts().getFirst().get("response_code"));

        for (int resend = 0; resend < 2; resend++) {
            mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
            scanDue();
        }
        assertDelivery(NotificationDeliveryStatus.UNKNOWN, 3);
        assertEquals(List.of("SES_SERVER_ERROR", "SES_RESULT_UNKNOWN", "SES_RESULT_UNKNOWN"),
            mapper.attempts().stream().map(row -> row.get("response_code")).toList());

        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();

        assertDelivery(NotificationDeliveryStatus.UNKNOWN, 3);
        var event = mapper.events().getFirst();
        assertEquals(CompensationEventStatus.FAILED.getCode(), event.get("execute_status"));
        assertEquals("NOTIFICATION_UNKNOWN_EXHAUSTED", event.get("last_error_code"));
        assertEquals(3, ses.requests().size());
    }

    /** 次数已满时遗留 PROCESSING 只收敛为发送中断的 UNKNOWN，不再重发，任务终止。 */
    @Test
    void test_interruptedSendAtAttemptLimitStops() {
        ses.fail(ALICE, 500, "InternalFailure", "a").fail(ALICE, 500, "InternalFailure", "b")
            .fail(ALICE, 500, "InternalFailure", "c");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        for (int resend = 0; resend < 2; resend++) {
            mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
            scanDue();
        }
        mapper.interruptSend();
        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();

        assertDelivery(NotificationDeliveryStatus.UNKNOWN, 3);
        var last = mapper.attempts().getLast();
        assertEquals(NotificationAttemptStatus.UNKNOWN.getCode(), last.get("status"));
        assertEquals("SEND_INTERRUPTED", last.get("response_code"));
        assertEquals(CompensationEventStatus.FAILED.getCode(), mapper.events().getFirst().get("execute_status"));
        assertEquals(3, ses.requests().size());
    }

    /** 外发后崩溃遗留 PROCESSING：窗口内不接管，窗口后记为发送中断的 UNKNOWN 并重发。 */
    @Test
    void test_interruptedSendResendsAfterWindow() {
        ses.fail(ALICE, 500, "InternalFailure", "boom").accept(ALICE, "ses-message-2");
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mapper.interruptSend();

        scanDue();
        assertDelivery(NotificationDeliveryStatus.PROCESSING, 1);
        assertEquals(1, ses.requests().size(), "安全窗口内不得接管仍可能在进行的发送");

        mapper.shiftAttemptsBack(WINDOW_ELAPSED_SECONDS);
        scanDue();

        assertDelivery(NotificationDeliveryStatus.SENT, 2);
        var interrupted = mapper.attempts().getFirst();
        assertEquals(NotificationAttemptStatus.UNKNOWN.getCode(), interrupted.get("status"));
        assertEquals("SEND_INTERRUPTED", interrupted.get("response_code"));
        assertNull(interrupted.get("resolved_time"), "未确认的尝试不写确认时间");
    }

    /** 检查唯一投递的状态与已创建发送次数。 */
    void assertDelivery(NotificationDeliveryStatus status, int attemptCount) {
        var delivery = mapper.deliveries().getFirst();
        assertEquals(status.getCode(), delivery.get("status"));
        assertEquals(attemptCount, ((Number) delivery.get("attempt_count")).intValue());
        assertEquals(attemptCount, mapper.attempts().size());
    }

    /** 本例唯一一次 SES 请求。 */
    SesMocker.Received onlyRequest() {
        assertEquals(1, ses.requests().size(), "SES 请求次数");
        return ses.requests().getFirst();
    }

    /** SendEmail 只有一个收件人、按当前模板渲染的纯文本、配置的发件人与 Configuration Set，并以标签携带请求号。 */
    void assertSendEmailRequest(SesMocker.Received request, String requestNo) {
        assertEquals("POST", request.method());
        assertEquals(SesMocker.SEND_EMAIL_PATH, request.path());
        assertTrue(request.headers().get("authorization").startsWith("AWS4-HMAC-SHA256"), "保留 SigV4 签名");
        var body = request.body();
        assertEquals("noreply@example.com", body.path("FromEmailAddress").asString());
        assertEquals(1, body.path("Destination").path("ToAddresses").size());
        assertEquals(ALICE, request.recipient());
        var simple = body.path("Content").path("Simple");
        assertEquals("通知 Alice", simple.path("Subject").path("Data").asString());
        assertEquals("Maintenance：Alice", simple.path("Body").path("Text").path("Data").asString());
        assertEquals("UTF-8", simple.path("Body").path("Text").path("Charset").asString());
        assertEquals("notification-test", body.path("ConfigurationSetName").asString());
        var tags = body.path("EmailTags");
        assertEquals(1, tags.size());
        assertEquals(Map.of(SesEmailProvider.REQUEST_NO_TAG, requestNo),
            Map.of(tags.get(0).path("Name").asString(), tags.get(0).path("Value").asString()));
    }
}

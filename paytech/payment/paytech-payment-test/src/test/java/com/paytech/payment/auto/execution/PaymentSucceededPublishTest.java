package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.ChannelConnectorMocker.Candidate;
import com.paytech.payment.test.probe.PaymentResultProbe;

/**
 * PaymentSucceeded 双写（design/08 §3、D34）：从 pay 的真实 HTTP 入口收款成功，补偿任务提交后经本机 RabbitMQ 同时发布到
 * 旧 topic_payment_result（现有报文，Commerce 尚未切换）与 payment.events（paytech-messaging Schema v1 信封）。
 *
 * <p>
 * 每例用两个临时排他队列分别绑定两个生产交换机观察：成功时两边各恰好一条、信封与最小 Payload 正确、Payload Hash 可重算、
 * 不含卡数据与付款人信息，两次发布均确认后补偿任务删除；支付失败与处理中两边都不发布。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PaymentSucceededPublishTest extends AbstractPayCardTest {
    @Autowired
    private PaymentResultProbe results;

    @Value("${payment.event.instance-id}")
    private String instanceId;

    private Candidate candidate;

    /** 本类公共：MID 候选、风控通过与两个临时观察队列。 */
    @BeforeEach
    void setUpClassMocks() {
        candidate = channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        results.bind();
    }

    /** 删除本例临时队列。 */
    @AfterEach
    void tearDownClassMocks() {
        results.unbind();
    }

    /** 成功：旧 Topic 仍收到一条现有报文，payment.events 收到一条 v1 新信封；两者 Payload 相同、Hash 正确，补偿任务在确认后删除。 */
    @Test
    void test_succeededPublishedOnce() {
        channel.succeeded("T-PUB-1");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        var message = results.awaitSucceeded(attemptId);
        var envelope = results.awaitEnvelope(attemptId);

        var attempt = mapper.attempt(attemptId);
        assertEquals(AttemptStatus.SUCCEEDED.getCode(), attempt.get("status"));
        var expected = new LinkedHashMap<String, String>();
        expected.put("clientId", CLIENT_ID);
        expected.put("orderId", String.valueOf(paid.submissionId));
        expected.put("sourceType", "COMMERCE_ORDER");
        expected.put("paymentIntentId", String.valueOf(paid.intentId));
        expected.put("paymentAttemptId", String.valueOf(attemptId));
        expected.put("sourceSubmissionId", String.valueOf(paid.submissionId));
        expected.put("entityNo", ENTITY_NO);
        expected.put("amount", AMOUNT);
        expected.put("currency", CURRENCY);
        expected.put("capturedAmount", AMOUNT);
        expected.put("paymentMethodCode", PAYMENT_METHOD_CODE);
        expected.put("channelId", String.valueOf(candidate.channelId()));
        expected.put("channelAccountId", String.valueOf(candidate.channelAccountId()));
        expected.put("midId", String.valueOf(candidate.midId()));
        expected.put("channelTransactionRef", "T-PUB-1");
        long version = ((Number)attempt.get("version")).longValue();
        String[] forbidden = {PAN, paid.token, CONSUMER_REFERENCE, CONSUMER_IP, CONSUMER_USER_AGENT, RETURN_URL,
            "consumerReference", "cardToken", "card", "cvv", "clientIp", "userAgent"};
        message.envelope(attemptId, version).payload(expected).payloadHashRecomputable().excludes(forbidden);
        envelope.envelope(attemptId, version, instanceId).payload(expected).excludes(forbidden);
        assertTrue(mapper.compensationEvents(PaymentSucceededHandler.EVENT_TYPE, String.valueOf(attemptId)).isEmpty(),
            "发布确认后补偿任务应删除");
    }

    /** 原提交重放不重复登记或发布。 */
    @Test
    void test_replayDoesNotPublishAgain() {
        channel.succeeded("T-PUB-R");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());
        results.awaitSucceeded(attemptId);
        results.awaitEnvelope(attemptId);

        paid.pay();

        results.assertNoSucceeded(attemptId);
    }

    /** 支付失败：不登记补偿任务，也不发布。 */
    @Test
    void test_failedNotPublished() {
        channel.declined("T-PUB-D", "ISSUER_PAYMENT_REJECTED", "Transaction failed at issuer end.");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        assertEquals(AttemptStatus.FAILED.getCode(), mapper.attempt(attemptId).get("status"));
        results.assertNoSucceeded(attemptId);
    }

    /** 3DS 待认证（处理中）：不登记补偿任务，也不发布。 */
    @Test
    void test_processingNotPublished() {
        channel.pending("T-PUB-P", "https://3ds.local.invalid/c");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        results.assertNoSucceeded(attemptId);
    }
}

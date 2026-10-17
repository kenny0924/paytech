package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.payment.test.probe.PaymentResultProbe;

/**
 * PaymentSucceeded 发布：从 pay 的真实 HTTP 入口收款成功，补偿任务提交后经本机 RabbitMQ 发布到 topic_payment_result。
 *
 * <p>
 * 每例用临时排他队列绑定生产 Exchange 观察：成功恰好发布一条、信封与最小 Payload 正确、Payload Hash 可重算、不含卡数据与付款人信息，
 * 发布确认后补偿任务删除；支付失败不登记、不发布。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PaymentSucceededPublishTest extends AbstractPayCardTest {
    @Autowired
    private PaymentResultProbe results;

    private Fixture fixture;

    /** 本类公共：渠道三元组、风控通过与临时观察队列。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.cardFixture("PAYERMAX", ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        results.bind();
    }

    /** 删除本例临时队列。 */
    @AfterEach
    void tearDownClassMocks() {
        results.unbind();
    }

    /** 成功：发布一条 PaymentSucceeded，信封、Payload 与 Hash 正确，补偿任务在确认后删除。 */
    @Test
    void test_succeededPublishedOnce() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-PUB-1","channelStatus":"SUCCESS"}""");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        var message = results.awaitSucceeded(attemptId);

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
        expected.put("channelId", String.valueOf(fixture.getChannel().getChannelId()));
        expected.put("channelAccountId", String.valueOf(fixture.getAccount().getChannelAccountId()));
        expected.put("midId", String.valueOf(fixture.getMid().getMidId()));
        expected.put("channelTransactionRef", "T-PUB-1");
        message.envelope(attemptId, ((Number)attempt.get("version")).longValue()).payload(expected)
            .payloadHashRecomputable()
            .excludes(PAN, paid.token, CONSUMER_REFERENCE, CONSUMER_IP, CONSUMER_USER_AGENT, RETURN_URL, "consumerReference",
                "cardToken", "card", "cvv", "clientIp", "userAgent");
        assertTrue(mapper.compensationEvents(PaymentSucceededHandler.EVENT_TYPE, String.valueOf(attemptId)).isEmpty(),
            "发布确认后补偿任务应删除");
    }

    /** 原提交重放不重复登记或发布。 */
    @Test
    void test_replayDoesNotPublishAgain() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-PUB-R","channelStatus":"SUCCESS"}""");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());
        results.awaitSucceeded(attemptId);

        paid.pay();

        results.assertNoSucceeded(attemptId);
    }

    /** 支付失败：不登记补偿任务，也不发布。 */
    @Test
    void test_failedNotPublished() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"FAILED","channelTransactionRef":"T-PUB-D","channelStatus":"FAILED",
             "failureCode":"ISSUER_PAYMENT_REJECTED","failureMessage":"Transaction failed at issuer end."}""");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        assertEquals(AttemptStatus.FAILED.getCode(), mapper.attempt(attemptId).get("status"));
        results.assertNoSucceeded(attemptId);
    }

    /** 3DS 待认证（处理中）：不登记补偿任务，也不发布。 */
    @Test
    void test_processingNotPublished() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"PENDING","nextAction":{"type":"THREE_DS_CHALLENGE","redirectUrl":"https://3ds.local.invalid/c"},
             "channelTransactionRef":"T-PUB-P","channelStatus":"PENDING"}""");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        results.assertNoSucceeded(attemptId);
    }
}

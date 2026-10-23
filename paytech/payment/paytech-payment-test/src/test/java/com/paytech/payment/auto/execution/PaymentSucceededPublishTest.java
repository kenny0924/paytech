package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.foundation.compensation.api.CompensationExecutionSequencer;
import com.paytech.foundation.messaging.core.autoconfigure.MessagingProperties;
import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.messaging.publish.UnroutablePolicy;

import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.CardApiMocker;
import com.paytech.payment.test.mocker.ChannelConnectorMocker.Candidate;

/**
 * PaymentSucceeded 发布（design/08 §3、D34）：从 pay 的真实 HTTP 入口收款成功，补偿任务提交后经本机 RabbitMQ 发布到
 * payment.events（paytech-messaging Schema v2 信封，design/05 §2.3：追加 instrumentRef、consentToSave、customerId 供 card 存卡；
 * 卡凭证验证与存卡不在支付成功事务内执行）；第 08 步起旧 Topic 停发。
 *
 * <p>
 * 每例用一个临时排他队列绑定 payment.events 观察：成功时恰好一条、信封与最小 Payload 正确、Payload Hash 经消费端解码器校验、
 * 不含卡数据、读卡票据与付款人信息，发布确认后补偿任务删除；v2 末尾的 BIN 库卡属性未识别为 null、识别时为冻结值；支付失败与处理中都不发布。发布端为 FAIL 策略并按聚合串行。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PaymentSucceededPublishTest extends AbstractPayCardTest {
    @Value("${payment.event.instance-id}")
    private String instanceId;

    @Autowired
    private MessagingProperties messagingProperties;

    @Autowired
    private AggregateKeySequencer sequencer;

    @Autowired
    private ObjectProvider<CompensationExecutionSequencer> sequencerProvider;

    private Candidate candidate;

    /** 本类公共：MID 候选与风控通过；两个临时观察队列由全局 BeforeEach 绑定。 */
    @BeforeEach
    void setUpClassMocks() {
        candidate = channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /**
     * 成功：payment.events 收到一条 v2 信封，Payload 与 Hash 正确，补偿任务在确认后删除
     * <li>卡域字段为工具引用、保存意愿与 Customer；末尾渠道交易引用为该 PAY 引用的内部 ID 与类型 PAY。</li>
     */
    @Test
    void test_succeededPublishedOnce() {
        channel.succeeded("T-PUB-1");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

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
        // v2 追加的 cardBrand、cardLevel 合法含 card 前缀，只禁止卡对象键 "card"。
        String[] forbidden = {PAN, paid.token, CONSUMER_REFERENCE, CONSUMER_IP, CONSUMER_USER_AGENT, RETURN_URL,
            "consumerReference", "cardToken", "\"card\"", "cvv", "clientIp", "userAgent"};
        String[] tickets = cardApi.tickets().toArray(String[]::new);
        assertEquals(1, tickets.length, "每次资金动作签发一张读卡票据");
        envelope.envelope(attemptId, version, instanceId).payload(expected).excludes(forbidden).excludes(tickets)
            .cardFields((String)mapper.instrumentTokens().getFirst().get("instrumentRef"), false, null)
            .cardAttributes(null, null, null)
            .channelReference(String.valueOf(mapper.transactionRefId("T-PUB-1")), ActionType.PAY.getCode());
        assertTrue(mapper.compensationEvents(PaymentSucceededHandler.EVENT_TYPE, String.valueOf(attemptId)).isEmpty(),
            "发布确认后补偿任务应删除");
    }

    /** 识别的 BIN（BIN 库需求 AC-10）：v2 Payload 末尾带 Token 冻结的卡组织、借贷记与卡等级，schemaVersion 仍为 2。 */
    @Test
    void test_succeededCarriesCardAttributes() {
        cardApi.binRange(CardApiMocker.bin(PAN), CardApiMocker.attributes(CardBrand.MASTERCARD.getCode(),
            CardFundingType.PREPAID.getCode(), "PLATINUM", null, COUNTRY_CODE, "GCC"));
        channel.succeeded("T-PUB-BIN");
        long attemptId = attemptId(newPayment().pay());

        var envelope = results.awaitEnvelope(attemptId);

        long version = ((Number)mapper.attempt(attemptId).get("version")).longValue();
        envelope.envelope(attemptId, version, instanceId)
            .cardAttributes(CardBrand.MASTERCARD.getCode(), CardFundingType.PREPAID.getCode(), "PLATINUM");
    }

    /** 原提交重放不重复登记或发布。 */
    @Test
    void test_replayDoesNotPublishAgain() {
        channel.succeeded("T-PUB-R");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());
        results.awaitEnvelope(attemptId);

        paid.pay();

        results.assertNoSucceeded(attemptId);
    }

    /**
     * 发布端装配：未路由按 FAIL 抛错由补偿任务重试（第 08 步由 ACK_AND_WARN 改回），补偿 Dispatcher 使用按聚合串行的编排器。
     * <li>FAIL 时的重试行为与同聚合串行顺序由 paytech-messaging 的 SequencedPublishAutoTest 以真实 Dispatcher 与 Broker 覆盖；
     * 共享 Broker 上 payment.events 可能已有其他服务的订阅绑定，本服务测试无法稳定构造“无订阅方”。</li>
     */
    @Test
    void test_publisherFailsUnroutableAndSequencesByAttempt() {
        assertEquals(UnroutablePolicy.FAIL, messagingProperties.getPublisher().getUnroutablePolicy());
        assertTrue(messagingProperties.getProducer().getSequencer().isEnabled());
        assertSame(sequencer, sequencerProvider.getIfUnique());
        assertEquals(sequencer.laneOf("8000001"), sequencer.laneOf("8000001"));
        assertEquals("8000001", AggregateKeySequencer.aggregateOf(
            PaymentSucceededHandler.buildEvent(8_000_001L).eventKey()));
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

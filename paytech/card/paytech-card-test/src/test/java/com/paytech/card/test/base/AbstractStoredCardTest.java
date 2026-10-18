package com.paytech.card.test.base;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import com.paytech.card.test.probe.PaymentEventPublisher;

/**
 * 已存卡相关测试的公共固件：经真实 HTTP 入口创建同意保存的工具，再扮演 Payment 发布 PaymentSucceeded v2，由真实消费链路生成已存卡。不放 {@code @Test}。
 */
public abstract class AbstractStoredCardTest extends AbstractCardApiTest {
    /** 消费完成的最长等待时间。 */
    protected static final Duration CONSUME_WAIT = Duration.ofSeconds(15);

    /** 新的支付尝试 ID。 */
    protected static long attempt() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }

    /**
     * 以手输卡创建工具并发布一次支付成功，等待 card 消费完成
     *
     * @param customerId Customer；访客为 null
     * @param consentToSave 保存意愿
     * @param expiryYear 卡有效期年份
     * @return 工具引用
     */
    protected String paySucceeded(String customerId, boolean consentToSave, String expiryYear) {
        String ref = (String)createInstrument(instrumentBody(submission(), card(PAN, expiryYear, CARDHOLDER_NAME),
            customerId, consentToSave)).get("instrumentRef");
        publishAndAwait(attempt(), ref, consentToSave, customerId);
        return ref;
    }

    /** 发布一条 v2 PaymentSucceeded 并等待本服务记录消费。 */
    protected void publishAndAwait(long attemptId, String instrumentRef, boolean consentToSave, String customerId) {
        int before = mapper.consumedEventIds().size();
        payment.publish(2, attemptId, 3, PaymentEventPublisher.payload(attemptId, instrumentRef, consentToSave, customerId));
        await().atMost(CONSUME_WAIT).until(() -> mapper.consumedEventIds().size() > before);
    }
}

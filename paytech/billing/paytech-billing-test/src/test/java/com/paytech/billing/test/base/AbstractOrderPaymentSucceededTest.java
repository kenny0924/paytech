package com.paytech.billing.test.base;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_CHARGE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_SERVICE_PARTY_ID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.billing.test.builder.ChannelCostResponseBuilder;
import com.paytech.billing.test.builder.ClearingAccountResponseBuilder;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.FinanceEntityResponseBuilder;
import com.paytech.billing.test.checker.PaymentFactChecker;
import com.paytech.billing.test.dal.BillingTestMapper;
import com.paytech.billing.test.mocker.external.CommercialPriceMocker;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * OrderPaymentSucceeded 消费入口测试的公共调用与上游配置（不含 @Test）
 * <li>{@link #consumed(DomainEvent)} 经真实 acquiring.events 发布并等到 billing 登记消费；{@link #deadLettered(DomainEvent)} 等到进入
 * billing.acquiring.dlq。</li>
 * <li>mock* 方法按条款版本、MID 与 Party 配置四个上游的 DTO；各测试类在自己的 BeforeEach 或用例内选择需要的默认上游。</li>
 */
public abstract class AbstractOrderPaymentSucceededTest extends MockTestBase {
    /** 等待消费终态的上限（含投递上限前的退避）。 */
    protected static final Duration CONSUME_WAIT = Duration.ofSeconds(20);

    /** Client 默认手续费组件。 */
    protected static final long CLIENT_FEE_COMPONENT_ID = 59001L;

    /** Partner 分润组件。 */
    protected static final long PARTNER_SHARE_COMPONENT_ID = 59101L;

    /** 平台服务费组件。 */
    protected static final long SERVICE_CHARGE_COMPONENT_ID = 59201L;

    @Autowired
    protected BillingTestMapper testMapper;

    /** 发布并等到 billing 登记消费（Handler 成功）；返回事件 ID。 */
    protected String consumed(DomainEvent event) {
        String eventId = acquiringEvents.publish(event);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return eventId;
    }

    /** 发布并等到 DLQ 中累计有 expected 条消息；不登记消费。 */
    protected void deadLettered(DomainEvent event, int expected) {
        String eventId = acquiringEvents.publish(event);
        await().atMost(CONSUME_WAIT).until(() -> acquiringEvents.deadLetters() == expected);
        assertEquals(0, testMapper.countConsumedEvent(eventId), "进入 DLQ 的事件不应登记消费");
    }

    /** 发布并等到第一条 DLQ 消息。 */
    protected void deadLettered(DomainEvent event) {
        deadLettered(event, 1);
    }

    /** 无事实、无发布任务。 */
    protected void assertNoFact() {
        assertAll("不应登记事实", () -> assertEquals(0, testMapper.countFacts()),
            () -> assertEquals(0, testMapper.countCompensationEvents()));
    }

    /** 读取该支付尝试的唯一事实。 */
    protected PaymentFactChecker fact(long paymentAttemptId) {
        return new PaymentFactChecker(testMapper, paymentAttemptId);
    }

    /** 默认清算账户与渠道成本（Client 定价由用例或类配置）。 */
    protected void mockChannel() {
        channelCosts.mock(Long.toString(MID_ID), ChannelCostResponseBuilder.template().build());
        clearingAccounts.mock(Long.toString(MID_ID), ClearingAccountResponseBuilder.template().build());
    }

    /** 无 Partner 的默认上游：Client 手续费 2.9%、最低 3.00。 */
    protected void mockPlatformDefaults() {
        mockChannel();
        mockClientPrice(CommercialPriceResponseBuilder.clientPricing().components(
            CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029").min("3.00").build()).build());
    }

    /** Partner 默认上游：Client 手续费 10%，分润为 Client 手续费 80%，平台服务费为毛额 2%，Finance Entity 已存在。 */
    protected void mockPartnerDefaults() {
        mockChannel();
        mockClientPrice(CommercialPriceResponseBuilder.clientPricing().components(
            CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.10").build()).build());
        commercialPrices.mock(CommercialPriceMocker.key(CommercialCodes.TermType.PARTNER_SHARE, PARTNER_SHARE_VERSION_ID),
            CommercialPriceResponseBuilder.partnerShare().components(CommercialPriceComponentBuilder
                .percentage(PARTNER_SHARE_COMPONENT_ID, "0.80")
                .base(CalculationBase.ACTUAL_CLIENT_FEE).build()).build());
        commercialPrices.mock(
            CommercialPriceMocker.key(CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE, PLATFORM_CHARGE_VERSION_ID),
            CommercialPriceResponseBuilder.serviceCharge().components(
                CommercialPriceComponentBuilder.percentage(SERVICE_CHARGE_COMPONENT_ID, "0.02").build()).build());
        financeEntities.mock(Long.toString(PLATFORM_SERVICE_PARTY_ID), FinanceEntityResponseBuilder.template().build());
    }

    /** 配置 Client 定价命中结果。 */
    protected void mockClientPrice(CommercialPriceResponseBody price) {
        commercialPrices.mock(CommercialPriceMocker.key(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_PRICING_VERSION_ID),
            price);
    }
}

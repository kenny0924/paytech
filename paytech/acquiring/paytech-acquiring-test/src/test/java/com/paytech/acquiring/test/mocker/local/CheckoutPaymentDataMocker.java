package com.paytech.acquiring.test.mocker.local;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.test.builder.checkout.CreateCheckoutContextBuilder;
import com.paytech.acquiring.test.dal.*;
import com.paytech.foundation.token.PublicTokenGenerator;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/** 自有订单由 CheckoutDataMocker 统一清理；不调用被测业务制造 Arrange。 */
@TestComponent
public class CheckoutPaymentDataMocker {
    private final CheckoutDataMocker ids;
    private final OrderQueryTestMapper orders;
    private final CheckoutPaymentTestMapper mapper;
    private final TransactionTemplate transaction;

    public CheckoutPaymentDataMocker(
            CheckoutDataMocker ids,
            OrderQueryTestMapper orders,
            CheckoutPaymentTestMapper mapper,
            PlatformTransactionManager manager) {
        this.ids = ids;
        this.orders = orders;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }

    public CreateCheckoutContext mockPrepared(boolean consumer) {
        var now = Instant.parse("2026-09-08T00:00:00Z");
        var context =
                CreateCheckoutContextBuilder.template()
                        .orderId(ids.reserveOrderId())
                        .clientId(ids.clientId())
                        .now(now)
                        .build();
        context.getSession()
                .setPublicToken(
                        "chk_"
                                + PublicTokenGenerator.generate(
                                        Long.toString(context.getOrder().getOrderId())));
        context.getOrder().setClientOrderId("intent-" + context.getOrder().getOrderId());
        transaction.executeWithoutResult(
                status -> {
                    orders.insertOrder(context.getOrder());
                    mapper.completeOrder(context.getOrder().getOrderId());
                    orders.insertSession(context.getSession());
                    context.getItems().forEach(orders::insertItem);
                    orders.insertRefund(context.getRefundPosition());
                    orders.insertFunds(context.getFundsAvailability());
                    mapper.insertContext(context.getFrozen());
                    mapper.insertLegal(context.getFrozen());
                    mapper.insertContract(context.getFrozen());
                    if (consumer) {
                        mapper.insertConsumer(context.getOrder().getOrderId(), now);
                    }
                });
        return context;
    }

    public void changeVersion(long orderId) {
        mapper.changeVersion(orderId);
    }
}

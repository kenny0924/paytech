package com.paytech.acquiring.service.processor.order;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.constant.Locks;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.order.OrderExpiryContext;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerAcceptanceRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderExpiryService;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.TerminationReason;
import com.paytech.acquiring.service.processor.order.request.ExpireOrderRequest;
import com.paytech.acquiring.service.processor.order.response.ExpireOrderResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 单个到期订单的过期收尾，由过期扫描逐单调用，无 HTTP 入口。
 * <li>与消费者支付提交共用订单资源锁，TRY_LOCK 取不到时由模板抛出并发冲突，扫描计为跳过。</li>
 * <li>到期只阻止新的支付；EXPIRED 只在确认没有 Intent，或 Payment 明确已终止、Intent 不存在后写入，不按本机时钟覆盖资金结果。</li>
 * <li>已有 Intent 或接受证据时，先单独提交终止意图，再在事务外以 ORDER_EXPIRED 终止；PENDING、ALREADY_PAID、
 * REVERSAL_REQUIRED 与 Payment 不可用均不写入，订单保持原状态等待下一轮或 PaymentSucceeded。</li>
 */
@Component
@AllArgsConstructor
public class ExpireOrderProcessor extends AbstractLockProcessor<ExpireOrderRequest, ExpireOrderResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(ExpireOrderProcessor.class);

    private final OrderExpiryService orderExpiryService;
    private final CommerceOrderRepository commerceOrderRepository;
    private final PaymentTerminationClient paymentClient;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ConsumerAcceptanceRepository consumerAcceptanceRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;

    @Override
    protected LockCommand lockCommand(ExpireOrderRequest request) {
        return LockCommand.tryLock(Locks.getOrderLock(request.getOrderId()));
    }

    @Override
    protected ExpireOrderResponse processWithinLock(ExpireOrderRequest request) {
        // 锁内重新读取订单事实，已终结或未到期的扫描结果直接跳过。
        var context = buildContext(request);
        if (context.isClosed() || !context.isDue()) {
            return ExpireOrderResponse.SKIPPED;
        }

        // 不可能存在 Intent 时本地收尾。
        if (context.canExpireLocally()) {
            orderExpiryService.expire(context);
            return ExpireOrderResponse.EXPIRED;
        }

        // 先提交终止意图，阻止新的支付提交，再在事务外终止 Intent。
        orderExpiryService.requestTermination(context);
        return terminatePayment(context, request.getPendingAlertAfter());
    }

    /** 一次读取订单、实际关联的会话、支付投影与接受证据；订单缺失或 Hosted Checkout 缺会话属于数据不一致。 */
    OrderExpiryContext buildContext(ExpireOrderRequest request) {
        var order = commerceOrderRepository.findByOrderId(request.getOrderId());
        if (order == null) {
            throw new ErrorException("过期扫描选中的订单不存在: " + request.getOrderId());
        }
        var session = getAndCheckSession(order);
        var payment = orderPaymentProjectionRepository.findByOrderId(order.getOrderId());
        boolean paymentStarted = consumerAcceptanceRepository.existsByOrderId(order.getOrderId());
        return new OrderExpiryContext(order, session, payment, paymentStarted, request.getNow());
    }

    /** 按实际关联查询会话；Hosted Checkout 必须存在，Direct Pay 允许未关联。 */
    CheckoutSession getAndCheckSession(CommerceOrder order) {
        var session = checkoutSessionRepository.findByOrderId(order.getOrderId());
        if (order.getCreationMode() == OrderCreationMode.HOSTED_CHECKOUT && session == null) {
            throw new ErrorException("Hosted Checkout 订单缺失 Session: " + order.getOrderId());
        }
        return session;
    }

    /**
     * 以过期原因终止订单的 Intent，并按决定收尾或保持开放
     * <li>有支付投影时直接使用其 Intent ID，否则由终止客户端按来源定位；不创建或补绑定 Intent。</li>
     * <li>Payment 404 表示 Intent 不存在，与已终止一样本地收尾；Payment 不可用返回 RETRY；其余错误原样抛出。</li>
     *
     * @param context 已验证的过期上下文
     * @param pendingAlertAfter 终止意图写入后仍 PENDING 的告警阈值
     * @return 本单收尾结果
     */
    ExpireOrderResponse terminatePayment(OrderExpiryContext context, Duration pendingAlertAfter) {
        var order = context.getOrder();
        var request = new PaymentTerminationClient.Request(order.getClientId(), order.getOrderId(),
            context.paymentIntentId(), TerminationReason.ORDER_EXPIRED);
        PaymentTerminationClient.Result result;
        try {
            result = paymentClient.terminate(request);
        } catch (ServiceUnavailableException unavailable) {
            return ExpireOrderResponse.RETRY;
        } catch (FailException failure) {
            if (!CommonErrorCode.NOT_FOUND.getCode().equals(failure.getCode())) {
                throw failure;
            }
            orderExpiryService.expire(context);
            return ExpireOrderResponse.EXPIRED;
        }
        if (result == null || result.status() == null || result.orderId() != order.getOrderId()) {
            throw new CheckoutUpstreamProtocolException("Payment 终止响应归属不一致");
        }
        return applyDecision(context, result.status(), pendingAlertAfter);
    }

    /** TERMINATED 本地收尾；其余决定不写入，只记录需要关注的日志，PENDING 超过告警阈值时记 WARN。 */
    ExpireOrderResponse applyDecision(OrderExpiryContext context, PaymentTerminationClient.Status status,
        Duration pendingAlertAfter) {
        long orderId = context.getOrder().getOrderId();
        switch (status) {
            case TERMINATED -> {
                orderExpiryService.expire(context);
                return ExpireOrderResponse.EXPIRED;
            }
            case PENDING -> {
                if (context.isPendingBeyond(pendingAlertAfter)) {
                    LOG.warn("Order expiry termination still pending beyond {}; manual handling required, "
                        + "orderId={}, intentId={}", pendingAlertAfter, orderId, context.paymentIntentId());
                }
                return ExpireOrderResponse.PENDING;
            }
            case ALREADY_PAID -> {
                LOG.info("Expired order already paid in Payment; waiting for PaymentSucceeded, orderId={}, intentId={}",
                    orderId, context.paymentIntentId());
                return ExpireOrderResponse.ALREADY_PAID;
            }
            case REVERSAL_REQUIRED -> {
                LOG.warn("Expired order has an unreleased authorization; manual handling required, "
                    + "orderId={}, intentId={}", orderId, context.paymentIntentId());
                return ExpireOrderResponse.REVERSAL_REQUIRED;
            }
            default -> throw new CheckoutUpstreamProtocolException("Payment 终止决定未知");
        }
    }
}

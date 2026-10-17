package com.paytech.acquiring.service.processor.order;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.order.OrderCancellationContext;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerAcceptanceRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderCancellationService;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient;
import com.paytech.acquiring.service.processor.order.request.CancelOrderRequest;
import com.paytech.acquiring.service.processor.order.response.CancelOrderResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 取消未支付订单；重复请求读取订单事实，不保存请求幂等记录。 */
@Component
@AllArgsConstructor
public class CancelOrderProcessor
        extends AbstractProcessor<CancelOrderRequest, CancelOrderResponse> {
    private final CommerceOrderRepository commerceOrderRepository;
    private final OrderCancellationService cancellationService;
    private final PaymentTerminationClient paymentClient;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ConsumerAcceptanceRepository acceptances;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;

    /** 按归属、原取消结果、业务校验、本地保存、外部终止和响应的顺序处理。 */
    @Override
    protected CancelOrderResponse processRequest(CancelOrderRequest request) {
        // 查询订单及原取消结果。
        var order = getAndCheckOrder(request);
        var session = getAndCheckSession(order);
        var context = checkAndGetContext(order, session);
        if (context != null) {
            return processResponse(context);
        }

        // 验证新请求，构造完整上下文。
        context = buildCancelOrderContext(order, session);
        if (context.canCancelLocally()) {
            cancellationService.cancel(context);
            return processResponse(context);
        }

        // 先提交终止意图，阻止首次支付继续绑定和提交。
        cancellationService.requestTermination(context);

        // 只请求取消；Payment 明确已终止或不存在时才关闭本地订单。
        if (terminatePayment(context)) {
            cancellationService.cancel(context);
        }

        // 从本次已保存事实构造响应。
        return processResponse(context);
    }

    /** 按可信 Client 查询；不存在或属于其他 Client 时统一返回不存在。 */
    CommerceOrder getAndCheckOrder(CancelOrderRequest request) {
        var order =
                commerceOrderRepository.findByOrderNo(request.getClientId(), request.getOrderNo());
        if (order == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getCreationMode() == null) {
            throw new ErrorException("订单缺失创建方式");
        }
        return order;
    }

    /** 按实际关联查询会话；Hosted Checkout 必须存在，Direct Pay 允许未关联。 */
    CheckoutSession getAndCheckSession(CommerceOrder order) {
        var session = checkoutSessionRepository.findByOrderId(order.getOrderId());
        if (order.getCreationMode() == OrderCreationMode.HOSTED_CHECKOUT && session == null) {
            throw new ErrorException("Hosted Checkout 订单缺失 Session");
        }
        return session;
    }

    /** 已取消直接返回原时间，不再检查新请求资格、查询支付或执行写入。 */
    OrderCancellationContext checkAndGetContext(CommerceOrder order, CheckoutSession session) {
        if (order.getOrderStatus() != OrderStatus.CANCELLED) {
            return null;
        }
        if (order.getCancelledAt() == null
                || order.getPaidAt() != null
                || session != null
                        && (session.getSessionStatus() != CheckoutSessionStatus.CANCELLED
                                || session.getCancelledAt() == null)) {
            throw new ErrorException("取消终态不完整");
        }
        return new OrderCancellationContext(order, session, null, false);
    }

    /** 新请求完成资格判断后，按需查询支付投影，一次构造完整上下文。 */
    OrderCancellationContext buildCancelOrderContext(CommerceOrder order, CheckoutSession session) {
        checkCancellationAllowed(order, session);
        var payment = orderPaymentProjectionRepository.findByOrderId(order.getOrderId());
        if (payment != null && payment.hasSuccessfulPayment()) {
            throw new FailException(CommerceCheckoutErrorCode.REVERSAL_REQUIRED);
        }
        return new OrderCancellationContext(
                order, session, payment, acceptances.existsByOrderId(order.getOrderId()));
    }

    /** 已支付必须撤销或退款，已过期不能取消；只检查实际存在的会话状态。 */
    void checkCancellationAllowed(CommerceOrder order, CheckoutSession session) {
        if (order.getOrderStatus() == OrderStatus.PAID || order.getPaidAt() != null) {
            throw new FailException(CommerceCheckoutErrorCode.REVERSAL_REQUIRED);
        }
        if (order.getOrderStatus() == OrderStatus.EXPIRED
                || session != null && session.getSessionStatus() == CheckoutSessionStatus.EXPIRED) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_CANCELLABLE);
        }
        if (order.getOrderStatus() != OrderStatus.CREATED
                && order.getOrderStatus() != OrderStatus.OPEN) {
            throw new ErrorException("订单取消状态不一致");
        }
        if (session != null
                && session.getSessionStatus() != CheckoutSessionStatus.PREPARING
                && session.getSessionStatus() != CheckoutSessionStatus.OPEN
                && session.getSessionStatus() != CheckoutSessionStatus.PAYMENT_PROCESSING) {
            throw new ErrorException("订单取消时 Session 状态不一致");
        }
    }

    /**
     * 按订单业务身份终止，不创建或补绑定 Intent。 仅明确的 Intent 不存在错误视为无需终止；超时保留待定，其余错误原样抛出。
     */
    boolean terminatePayment(OrderCancellationContext context) {
        var order = context.getOrder();
        var request = new PaymentTerminationClient.Request(order.getClientId(), order.getOrderId());
        PaymentTerminationClient.Result result;
        try {
            result = paymentClient.terminate(request);
        } catch (ServiceUnavailableException exception) {
            return false;
        } catch (FailException exception) {
            if (CommonErrorCode.NOT_FOUND.getCode().equals(exception.getCode())) {
                return true;
            }
            throw exception;
        }
        if (result == null || result.status() == null || result.orderId() != order.getOrderId()) {
            throw new CheckoutUpstreamProtocolException("Payment 终止响应归属不一致");
        }
        if (result.status() == PaymentTerminationClient.Status.REVERSAL_REQUIRED) {
            throw new FailException(CommerceCheckoutErrorCode.REVERSAL_REQUIRED);
        }
        return result.status() == PaymentTerminationClient.Status.TERMINATED;
    }

    /** 直接使用原取消或本次提交的事实，不重查、不输出内部版本。 */
    CancelOrderResponse processResponse(OrderCancellationContext context) {
        var order = context.getOrder();
        boolean pending = order.getOrderStatus() != OrderStatus.CANCELLED;
        var session = context.getSession();
        return new CancelOrderResponse(
                order.getOrderNo(),
                pending,
                pending ? null : order.getCancelledAt(),
                pending || session == null ? null : session.getSessionStatus());
    }
}

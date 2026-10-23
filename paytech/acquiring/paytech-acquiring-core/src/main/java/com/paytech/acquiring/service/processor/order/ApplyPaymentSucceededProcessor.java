package com.paytech.acquiring.service.processor.order;

import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.order.PaymentSucceededContext;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderRefundPositionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderPaymentService;
import com.paytech.acquiring.service.processor.order.request.ApplyPaymentSucceededRequest;
import com.paytech.acquiring.service.processor.order.response.ApplyPaymentSucceededResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 落地 Payment PaymentSucceeded 事件；由 MQ 监听器调用
 * <li>按订单核对 Client、Intent、经营主体、金额与币种；不一致或订单不存在时业务拒绝，由监听器转入死信队列。</li>
 * <li>同一事件（事件键与载荷 Hash 相同）或同一成功尝试重复投递时直接返回；同一事件键不同 Hash 或已有其他成功尝试时拒绝。</li>
 * <li>订单已取消或过期只记录支付投影并告警，订单与会话不变，资金由人工处理；不发布 OrderPaymentSucceeded。</li>
 * <li>首次推进 PAID 时在同一事务登记 ORDER_PAYMENT_SUCCEEDED，提交后发布 OrderPaymentSucceeded；重复事件不再登记。</li>
 * <li>不加 Redis 锁：订单、会话、投影、退款位置与资金投影均由条件更新保护，冲突整体回滚后由消息重投处理。</li>
 */
@Component
@AllArgsConstructor
public class ApplyPaymentSucceededProcessor
    extends AbstractProcessor<ApplyPaymentSucceededRequest, ApplyPaymentSucceededResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(ApplyPaymentSucceededProcessor.class);

    private final OrderPaymentService orderPaymentService;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRefundPositionRepository orderRefundPositionRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;

    @Override
    protected ApplyPaymentSucceededResponse processRequest(ApplyPaymentSucceededRequest request) {
        // 归属与业务防重
        var order = getAndCheckOrder(request);
        var payment = getAndCheckPayment(request);
        if (isApplied(request, payment)) {
            return ApplyPaymentSucceededResponse.DUPLICATE;
        }
        checkAmount(request, order);

        // 已关闭订单只记录资金事实
        if (order.statusIn(OrderStatus.CANCELLED, OrderStatus.EXPIRED)) {
            orderPaymentService.recordClosedOrderPayment(buildContext(request, order, null, payment, null));
            LOG.warn("Payment succeeded for closed order {} with status {}; manual handling required",
                order.getOrderId(), order.getOrderStatus().getCode());
            return ApplyPaymentSucceededResponse.RECORDED_FOR_CLOSED_ORDER;
        }
        if (order.getOrderStatus() != OrderStatus.OPEN) {
            throw new BizCheckFailException("订单状态不允许记录支付成功: " + order.getOrderId() + ", 状态: "
                + order.getOrderStatus().getCode());
        }

        // 一次事务推进五项事实
        var context = buildContext(request, order, getAndCheckSession(order), payment, getAndCheckRefundPosition(order));
        orderPaymentService.applySucceeded(context);
        return ApplyPaymentSucceededResponse.APPLIED;
    }

    /** 订单必须存在且属于事件 Client。 */
    CommerceOrder getAndCheckOrder(ApplyPaymentSucceededRequest request) {
        var order = commerceOrderRepository.findByOrderId(request.getOrderId());
        if (order == null || order.getClientId() != request.getClientId()) {
            throw new BizCheckFailException("支付成功事件的订单不存在或不属于该 Client: " + request.getOrderId());
        }
        return order;
    }

    /** 订单必须已绑定同一 Intent。 */
    OrderPaymentProjection getAndCheckPayment(ApplyPaymentSucceededRequest request) {
        var payment = orderPaymentProjectionRepository.findByOrderId(request.getOrderId());
        if (payment == null || payment.getPaymentIntentId() != request.getPaymentIntentId()) {
            throw new BizCheckFailException("支付成功事件的 Intent 与订单不一致: " + request.getOrderId());
        }
        return payment;
    }

    /**
     * 判断事件是否已经落地
     * <li>同一事件键：Hash 相同为重复，不同则拒绝。</li>
     * <li>已有成功尝试：相同尝试为重复，不同尝试为重复成功，拒绝待人工处理。</li>
     */
    boolean isApplied(ApplyPaymentSucceededRequest request, OrderPaymentProjection payment) {
        if (request.getEventKey().equals(payment.getLastAttemptEventKey())) {
            if (!request.getPayloadHash().equals(payment.getLastAttemptPayloadHash())) {
                throw new BizCheckFailException("同一支付成功事件的载荷不一致: " + request.getOrderId());
            }
            return true;
        }
        if (payment.getSuccessfulAttemptId() == null) {
            return false;
        }
        if (payment.getSuccessfulAttemptId() != request.getPaymentFacts().getPaymentAttemptId()) {
            throw new BizCheckFailException("订单已有其他成功支付尝试: " + request.getOrderId());
        }
        return true;
    }

    /** 经营主体、币种、应付与已捕获金额必须等于订单冻结事实；本期不支持部分捕获。 */
    void checkAmount(ApplyPaymentSucceededRequest request, CommerceOrder order) {
        var total = order.getTotalAmount();
        if (!request.getEntityNo().equals(order.getEntityNo()) || !request.getCurrency().equals(order.getCurrency())
            || total == null || request.getAmount().compareTo(total) != 0
            || request.getPaymentFacts().getCapturedAmount().compareTo(total) != 0) {
            throw new BizCheckFailException("支付成功事件的经营主体或金额与订单不一致: " + request.getOrderId());
        }
    }

    /** Hosted Checkout 必须存在会话，Direct Pay 允许未关联。 */
    CheckoutSession getAndCheckSession(CommerceOrder order) {
        var session = checkoutSessionRepository.findByOrderId(order.getOrderId());
        if (order.getCreationMode() == OrderCreationMode.HOSTED_CHECKOUT && session == null) {
            throw new ErrorException("Hosted Checkout 订单缺失 Session: " + order.getOrderId());
        }
        return session;
    }

    /** 创建时已初始化退款位置；缺失属于本库数据错误。 */
    OrderRefundPosition getAndCheckRefundPosition(CommerceOrder order) {
        var position = orderRefundPositionRepository.findByOrderId(order.getOrderId());
        if (position == null) {
            throw new ErrorException("订单缺失退款金额位置: " + order.getOrderId());
        }
        return position;
    }

    /** 一次构造落地所需的上下文；时间按数据库精度截断。 */
    static PaymentSucceededContext buildContext(ApplyPaymentSucceededRequest request, CommerceOrder order,
        CheckoutSession session, OrderPaymentProjection payment, OrderRefundPosition refundPosition) {
        var facts = request.getPaymentFacts();
        return new PaymentSucceededContext(order, session, payment, refundPosition, facts.getPaymentAttemptId(),
            facts.getCapturedAmount(), request.getEventKey(), request.getPayloadHash(), request.getSourceVersion(),
            request.getOccurredAt().truncatedTo(ChronoUnit.MICROS),
            facts.getPaymentSucceededTime().truncatedTo(ChronoUnit.MICROS), facts);
    }
}

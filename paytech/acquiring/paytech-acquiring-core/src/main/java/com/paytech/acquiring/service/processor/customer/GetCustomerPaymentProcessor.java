package com.paytech.acquiring.service.processor.customer;

import static com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND;
import static com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode.PAYMENT_SUBMISSION_NOT_FOUND;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.domain.checkout.CheckoutPublicToken;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.acquiring.domain.customer.CustomerPaymentQueryContext;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.acquiring.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.service.processor.customer.request.GetCustomerPaymentRequest;
import com.paytech.acquiring.service.processor.customer.response.CustomerPaymentResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 按已授权订单和 Intent 查询 Payment 原提交，不读取命令或响应快照。
 * <li>经 Payment 刷新接口读取（3DS 全链路 technique 3.3.6）：仍在验证或确认中时由 Payment 限频触发一次查单确认，本服务不写库。</li>
 */
@Component
@AllArgsConstructor
public class GetCustomerPaymentProcessor
    extends AbstractProcessor<GetCustomerPaymentRequest, CustomerPaymentResponse> {
    private final Clock clock;
    private final CheckoutCardPaymentPort payment;
    private final CommerceOrderRepository orderRepository;
    private final CheckoutSessionRepository sessionRepository;
    private final OrderPaymentProjectionRepository paymentRepository;

    /** 查询身份只做格式校验；订单归属由服务器数据库确定。 */
    @Override
    protected void validate(GetCustomerPaymentRequest request) {
        if (!CheckoutPublicToken.isValid(request.getCheckoutToken())) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        if (request.getSubmissionId() <= 0) {
            throw new InvalidParameterException("支付提交编号必须为正整数");
        }
    }

    /** 按需查询归属、构建上下文、读取 Payment 并组装响应，全程不写库或开启事务。 */
    @Override
    protected CustomerPaymentResponse processRequest(GetCustomerPaymentRequest request) {
        var session = getAndCheckSession(request.getCheckoutToken());
        var order = getAndCheckOrder(session.getOrderId());
        var projection = getAndCheckPayment(order.getOrderId());
        var context = new CustomerPaymentQueryContext(order, projection, request.getSubmissionId());
        var result = queryPayment(context);
        return processResponse(context, result);
    }

    /** Token 仅用于定位原会话，不加载订单关联聚合。 */
    CheckoutSession getAndCheckSession(String checkoutToken) {
        var session = sessionRepository.findByPublicToken(checkoutToken);
        if (session == null) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        return session;
    }

    /** 校验原订单的访问期限；已支付或取消不影响有效期内查询原支付。 */
    CommerceOrder getAndCheckOrder(long orderId) {
        var order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new FailException(CHECKOUT_NOT_FOUND);
        }
        order.checkExpiry(clock.instant());
        return order;
    }

    /** 只读取该订单已绑定的 Intent，不查询合同、接受或消费者资料。 */
    OrderPaymentProjection getAndCheckPayment(long orderId) {
        var projection = paymentRepository.findByOrderId(orderId);
        if (projection == null) {
            throw new FailException(PAYMENT_SUBMISSION_NOT_FOUND);
        }
        return projection;
    }

    /**
     * 刷新查询同订单、Intent 和提交编号的结果
     * <li>Payment 未找到时返回 PAYMENT_SUBMISSION_NOT_FOUND，不推断为支付失败。</li>
     * <li>Payment 不可用时与提交响应未知相同降级：返回空结果，由响应保持处理中并继续轮询。</li>
     */
    PaymentResult queryPayment(CustomerPaymentQueryContext context) {
        var reference = context.paymentReference();
        PaymentResult result;
        try {
            result = payment.refresh(context.getOrder().getClientId(), reference);
        } catch (ServiceUnavailableException unavailable) {
            return null;
        }
        if (result == null) {
            throw new FailException(PAYMENT_SUBMISSION_NOT_FOUND);
        }
        result.validateQueried(reference, clock.instant());
        return result;
    }

    /** 复用已有上下文，仅按当前时间检查原期限；不为普通状态变化重复查库或返回版本冲突。 */
    CustomerPaymentResponse processResponse(CustomerPaymentQueryContext context, PaymentResult result) {
        context.getOrder().checkExpiry(clock.instant());
        return new CustomerPaymentResponse(context.paymentReference(), context.getOrder().getExpiresAt(), result);
    }
}

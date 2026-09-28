package com.paytech.commerce.service.processor.customer;

import static com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND;
import static com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode.PAYMENT_SUBMISSION_NOT_FOUND;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.domain.checkout.CheckoutPublicToken;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.commerce.domain.customer.CustomerPaymentQueryContext;
import com.paytech.commerce.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.commerce.domainservice.repository.checkout.CheckoutSessionRepository;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.commerce.service.processor.customer.request.GetCustomerPaymentRequest;
import com.paytech.commerce.service.processor.customer.response.CustomerPaymentResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 按已授权订单和 Intent 查询 Payment 原提交，不读取命令或响应快照。 */
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

    /** 只读查询同订单、Intent 和提交编号的结果；未找到不推断为支付失败。 */
    PaymentResult queryPayment(CustomerPaymentQueryContext context) {
        var reference = context.paymentReference();
        var result = payment.query(reference);
        if (result == null) {
            throw new FailException(PAYMENT_SUBMISSION_NOT_FOUND);
        }
        result.validate(reference, clock.instant());
        return result;
    }

    /** 复用已有上下文，仅按当前时间检查原期限；不为普通状态变化重复查库或返回版本冲突。 */
    CustomerPaymentResponse processResponse(CustomerPaymentQueryContext context, PaymentResult result) {
        context.getOrder().checkExpiry(clock.instant());
        return new CustomerPaymentResponse(context.paymentReference(), context.getOrder().getExpiresAt(), result);
    }
}

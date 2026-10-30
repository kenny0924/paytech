package com.paytech.acquiring.service.processor.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRecord;
import com.paytech.acquiring.domain.checkout.CheckoutPublicOrigin;
import com.paytech.acquiring.domain.checkout.CheckoutPublicToken;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.domain.enums.OrderPaymentStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.order.GetOrderContext;
import com.paytech.acquiring.domainservice.repository.order.OrderRepository;
import com.paytech.acquiring.service.processor.order.request.GetOrderRequest;
import com.paytech.acquiring.service.processor.order.response.GetOrderResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** Client 订单查询编排；按需读取本库事实，不开事务、不加锁、不推进支付。 */
@Component
@AllArgsConstructor
public class GetOrderProcessor extends AbstractProcessor<GetOrderRequest, GetOrderResponse> {
    private final Clock clock;
    private final OrderRepository repository;

    /** 单字段约束由 HTTP 入口处理，这里只检查两个查询标识的互斥关系。 */
    @Override
    protected void validate(GetOrderRequest request) {
        super.validate(request);
        if ((request.getOrderNo() == null) == (request.getClientOrderId() == null)) {
            throw new InvalidParameterException("订单查询必须且只能提供一个订单标识");
        }
    }

    /** 先核对归属，再逐项查询、验证本地事实，最后构建有限响应。 */
    @Override
    protected GetOrderResponse processRequest(GetOrderRequest request) {
        var order = findOrder(request);
        var context = buildContext(order);
        validateOrderFacts(context);
        return buildResponse(context);
    }

    /** 查询条件包含可信 Client；未知和其他 Client 的订单统一返回不存在。 */
    OrderRecord findOrder(GetOrderRequest request) {
        var order = repository.find(request.getClientId(), request.getOrderNo(), request.getClientOrderId(), false);
        if (order == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 只加载订单详情响应实际需要的本库记录，一次构造上下文。 */
    GetOrderContext buildContext(OrderRecord order) {
        var items = repository.items(order.clientId(), order.orderId());
        var payment = repository.payment(order.orderId());
        var refund = repository.refund(order.orderId());
        var funds = repository.funds(order.orderId());
        return new GetOrderContext(order, items, payment, refund, funds, consumerCountry(order));
    }

    /** 检查必需投影和退款额度；不得把缺失事实解释为零或成功。 */
    void validateOrderFacts(GetOrderContext context) {
        var order = context.order();
        var refund = context.refund();
        var funds = context.funds();
        if (order.creationMode() == null
            || order.creationMode() == OrderCreationMode.HOSTED_CHECKOUT && order.sessionStatus() == null
            || refund == null || funds == null || context.items().isEmpty()
            || !order.currency().equals(refund.getCurrency()) || !order.currency().equals(funds.currency())
            || context.items().stream().anyMatch(item -> !order.currency().equals(item.currency()))
            || refund.getRefundableBaseAmount().signum() < 0 || refund.getReservedAmount().signum() < 0
            || refund.getRefundedAmount().signum() < 0 || refund.getRefundableBaseAmount().subtract(refund.getReservedAmount())
                .subtract(refund.getRefundedAmount()).signum() < 0) {
            throw new ErrorException("订单必需事实缺失或退款金额不一致");
        }
        validatePayment(context);
        validateFunds(context);
        validateAmounts(order);
    }

    /** 校验本地支付投影的币种、尝试身份及已支付订单的成功事实。 */
    void validatePayment(GetOrderContext context) {
        var order = context.order();
        var payment = context.payment();
        if (payment != null && (!order.currency().equals(payment.currency()) || payment.capturedAmount().signum() < 0
            || (payment.lastAttemptId() == null) != (payment.lastAttemptStatus() == null))) {
            throw new ErrorException("订单支付投影不一致");
        }
        if (order.orderStatus() == OrderStatus.PAID
            && (payment == null || payment.successfulAttemptId() == null || order.paidAt() == null)) {
            throw new ErrorException("已支付订单缺少成功支付事实");
        }
    }

    /** 资金成熟状态必须具有匹配的金额和时刻，未成熟时不伪造可用事实。 */
    void validateFunds(GetOrderContext context) {
        var funds = context.funds();
        boolean finalFunds = funds.status() == FundsAvailabilityStatus.AVAILABLE
            || funds.status() == FundsAvailabilityStatus.FULLY_OFFSET;
        if (funds.status() == null
            || finalFunds && (funds.availableAmount() == null || funds.availableAt() == null
                || (funds.status() == FundsAvailabilityStatus.AVAILABLE ? funds.availableAmount().signum() <= 0
                    : funds.availableAmount().signum() != 0))
            || !finalFunds && (funds.availableAmount() != null || funds.availableAt() != null)) {
            throw new ErrorException("资金可用投影不一致");
        }
    }

    /**
     * 当前报价金额必须齐全且平衡；准备前、提前结束的 Checkout 或 BLOCKED（无法正确计税）允许金额为空
     * <li>金额状态与总额一致：BLOCKED 时总额为空，FINAL 时总额必须存在，ESTIMATED 可为建单暂估或准备前的空金额；有总额时必须平衡。</li>
     */
    void validateAmounts(OrderRecord order) {
        boolean finalAmount = order.totalAmount() != null;
        if (finalAmount != (order.taxAmount() != null) || finalAmount != (order.roundingAmount() != null)
            || finalAmount && order.netAmount().add(order.taxAmount()).add(order.roundingAmount())
                .compareTo(order.totalAmount()) != 0
            || order.amountStatus() == null || order.amountStatus() == OrderAmountStatus.BLOCKED && finalAmount
            || order.amountStatus() == OrderAmountStatus.FINAL && !finalAmount) {
            throw new ErrorException("订单最终金额不完整");
        }
        if (order.amountStatus() == OrderAmountStatus.BLOCKED) {
            return;
        }
        if (!finalAmount && order.sessionStatus() != null && order.sessionStatus() != CheckoutSessionStatus.PREPARING
            && order.sessionStatus() != CheckoutSessionStatus.EXPIRED
            && order.sessionStatus() != CheckoutSessionStatus.CANCELLED) {
            throw new ErrorException("可支付 Checkout 缺失最终金额");
        }
        if (!finalAmount && order.sessionStatus() == null && order.orderStatus() != OrderStatus.CREATED
            && order.orderStatus() != OrderStatus.CANCELLED && order.orderStatus() != OrderStatus.EXPIRED) {
            throw new ErrorException("可支付订单缺失最终金额");
        }
    }

    /** 使用订单或原冻结上下文中的国家，不读取创建命令快照。 */
    String consumerCountry(OrderRecord order) {
        String country = order.consumerCountryCode();
        if (country == null || !country.matches("[A-Z]{2}")) {
            throw new ErrorException("订单缺少可信消费者国家");
        }
        return country;
    }

    /** 复用原 Session 坐标；到期后不再返回链接，不延长期限或生成新 Token。 */
    String checkoutUrl(OrderRecord order) {
        if (!clock.instant().isBefore(order.expiresAt())) {
            return null;
        }
        try {
            String token = CheckoutPublicToken.parse(order.publicToken()).value();
            return new CheckoutPublicOrigin(order.publicOrigin()).checkoutUrl(token);
        } catch (IllegalArgumentException exception) {
            throw new ErrorException("Checkout 冻结的公开 URL 坐标已损坏", exception);
        }
    }

    /** 未决 Attempt 优先，避免 Intent 事件先到时把 UNKNOWN 当作失败。 */
    OrderPaymentStatus paymentStatus(OrderPaymentRecord payment) {
        if (payment == null) {
            return OrderPaymentStatus.NOT_STARTED;
        }
        if (payment.lastAttemptStatus() != null && switch (payment.lastAttemptStatus()) {
            case CREATED, RISK_CHECKING, ROUTING, PROCESSING, REQUIRES_ACTION, UNKNOWN -> true;
            default -> false;
        }) {
            return OrderPaymentStatus.PROCESSING;
        }
        return switch (payment.intentStatus()) {
            case SUCCEEDED -> OrderPaymentStatus.SUCCEEDED;
            case PROCESSING, REQUIRES_ACTION -> OrderPaymentStatus.PROCESSING;
            case FAILED -> OrderPaymentStatus.FAILED;
            case CANCELLED -> OrderPaymentStatus.CANCELLED;
            case EXPIRED -> OrderPaymentStatus.EXPIRED;
            case REQUIRES_PAYMENT -> payment.lastAttemptStatus() == null ? OrderPaymentStatus.NOT_STARTED
                : switch (payment.lastAttemptStatus()) {
                    case FAILED, RISK_REJECTED, RISK_FAILED, LIMIT_REJECTED -> OrderPaymentStatus.FAILED;
                    default -> OrderPaymentStatus.PROCESSING;
                };
        };
    }

    /** 完成状态和金额计算，只将有限业务结果交给 HTTP Converter。 */
    GetOrderResponse buildResponse(GetOrderContext context) {
        var order = context.order();
        var payment = context.payment();
        var refund = context.refund();
        var funds = context.funds();
        boolean finalAmount = order.amountStatus() == OrderAmountStatus.FINAL;
        var items = context.items().stream()
            .map(item -> new GetOrderResponse.Item(item.clientItemId(), item.itemName(), item.itemDescription(),
                item.productTypeCode(), item.taxCategoryCode(), item.quantity(), item.unitAmount(),
                item.lineDiscountAmount()))
            .toList();
        var amounts = new GetOrderResponse.Amounts(order.merchandiseAmount(), order.discountAmount(), order.netAmount(),
            order.taxAmount(), order.roundingAmount(), order.totalAmount(),
            payment == null ? BigDecimal.ZERO : payment.capturedAmount());
        var refundSummary = new GetOrderResponse.RefundSummary(refund.getReservedAmount(), refund.getRefundedAmount(),
            refund.getRefundableBaseAmount().subtract(refund.getReservedAmount()).subtract(refund.getRefundedAmount()));
        var fundsAvailability = new GetOrderResponse.FundsAvailability(funds.status(), funds.availableAmount(),
            funds.scheduledAt(), funds.availableAt(), funds.asOf(), funds.updateTime());
        var checkout = order.sessionStatus() == null ? null
            : new GetOrderResponse.Checkout(order.sessionStatus(), checkoutUrl(order), order.expiresAt());
        var updatedAt = Stream
            .of(order.updateTime(), order.sessionUpdateTime(), refund.getUpdateTime(), funds.updateTime(),
                payment == null ? order.createTime() : payment.updateTime())
            .filter(Objects::nonNull).max(Instant::compareTo).orElseThrow();
        return GetOrderResponse.builder().orderNo(order.orderNo()).clientOrderId(order.clientOrderId())
            .creationMode(order.creationMode()).orderStatus(order.orderStatus()).checkout(checkout)
            .ready(order.sessionStatus() != CheckoutSessionStatus.PREPARING && finalAmount)
            .amountStatus(order.amountStatus())
            .paymentStatus(paymentStatus(payment)).currency(order.currency())
            .consumerCountryCode(context.consumerCountryCode()).items(items).amounts(amounts)
            .lastAttemptStatus(payment == null ? null : payment.lastAttemptStatus()).refundSummary(refundSummary)
            .fundsAvailability(fundsAvailability).createdAt(order.createTime()).expiresAt(order.expiresAt())
            .paidAt(order.paidAt()).updatedAt(updatedAt).build();
    }
}

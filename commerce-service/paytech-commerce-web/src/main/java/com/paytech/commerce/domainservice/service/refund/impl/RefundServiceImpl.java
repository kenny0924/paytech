package com.paytech.commerce.domainservice.service.refund.impl;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.order.OrderRecord;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.domain.enums.PaymentIntentStatus;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.commerce.domain.refund.RefundCreationContext;
import com.paytech.commerce.domainservice.repository.order.OrderRefundPositionRepository;
import com.paytech.commerce.domainservice.repository.order.OrderRepository;
import com.paytech.commerce.domainservice.repository.refund.RefundRepository;
import com.paytech.commerce.domainservice.service.refund.RefundService;
import com.paytech.commerce.ext.job.RefundSubmissionHandler;
import com.paytech.commerce.integration.payment.PaymentRefundClient;
import com.paytech.commerce.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 退款本地持久化；Payment 由提交后的 Handler 在事务外调用。 */
@Service
@AllArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundRepository refunds;
    private final OrderRepository orders;
    private final OrderRefundPositionRepository positions;
    private final CommerceTransactionExecutor transactions;
    /** Registry 启动时需要创建退款 Handler，登记器在业务调用时取得，避免相互构造。 */
    private final ObjectProvider<CompensationEventRegistrar> registrars;

    /**
     * REQUIRED 事务保存已验证的 Context，不重新加载业务上下文。
     * <li>唯一索引保护业务退款号，条件 SQL 保护已支付资格及剩余额度。</li>
     * <li>任务登记只写本地数据库，公共组件在提交后唤醒 Handler。</li>
     */
    @Override
    public void saveCreateRefundContext(RefundCreationContext context) {
        transactions.write(() -> {
            var refund = context.getRefund();
            refunds.insert(refund);
            positions.reserve(context.getPosition(), context.getOrder(), context.getPayment(),
                refund.getRefundAmount(), refund.getCreateTime());
            registrars.getObject().register(RefundSubmissionHandler.buildEvent(refund.getRefundRequestNo()));
            return null;
        });
    }

    /** 在调用方事务锁内重建提交请求并推进处理中状态；终态返回 null。 */
    @Override
    public PaymentRefundClient.Request prepare(String no, Instant now) {
        var row = refunds.find(null, no, null, false);
        if (row == null) {
            throw new ErrorException("退款提交任务缺失本地退款");
        }
        var order = order(row.getClientId(), row.getOrderNo(), true);
        var position = positions.lockByOrderId(order.orderId());
        var refund = refunds.find(row.getClientId(), no, null, true);
        validate(refund);
        if (refund.getRefundStatus().terminal()) {
            return null;
        }
        validatePosition(order, position);
        requirePaid(order, position);
        if (position.getReservedAmount().compareTo(refund.getRefundAmount()) < 0) {
            throw new ErrorException("退款预占事实缺失");
        }
        if (refund.getRefundStatus() == RefundStatus.PENDING) {
            refunds.update(refund, RefundStatus.PROCESSING, null, null, null, now);
        }
        var payment = orders.payment(order.orderId());
        return new PaymentRefundClient.Request(refund.getClientId(), order.orderNo(), payment.paymentIntentId(),
            refund.getRefundRequestId(), no, "commerce-refund-" + refund.getRefundRequestId(), refund.getRefundAmount(),
            refund.getCurrency(), refund.getReasonCode());
    }

    /** 在调用方短事务内核对 Payment 结果，绑定渠道号并原子消费或释放预占。 */
    @Override
    public boolean complete(PaymentRefundClient.Request request, PaymentRefundClient.Result result, Instant now) {
        if (result == null || result.status() == null || result.paymentIntentId() != request.paymentIntentId()
            || result.refundRequestId() != request.refundRequestId() || result.channelRefundId() <= 0
            || result.amount() == null || result.amount().compareTo(request.amount()) != 0
            || !request.currency().equals(result.currency())) {
            throw new ErrorException("Payment 退款响应坐标不一致");
        }
        var order = order(request.clientId(), request.orderNo(), true);
        var position = positions.lockByOrderId(order.orderId());
        var refund = refunds.find(request.clientId(), request.refundRequestNo(), null, true);
        validate(refund);
        var payment = orders.payment(order.orderId());
        if (refund.getRefundRequestId() != request.refundRequestId() || refund.getOrderId() != order.orderId()
            || payment == null || payment.paymentIntentId() != request.paymentIntentId()
            || refund.getRefundAmount().compareTo(request.amount()) != 0 || !refund.getCurrency().equals(request.currency())
            || refund.getChannelRefundId() != null && refund.getChannelRefundId() != result.channelRefundId()) {
            throw new ErrorException("退款绑定事实发生冲突");
        }
        RefundStatus status = RefundStatus.valueOf(result.status().name());
        if (refund.getRefundStatus().terminal()) {
            if (status.terminal() && status != refund.getRefundStatus()) {
                throw new ErrorException("退款终态冲突");
            }
            return true;
        }
        validatePosition(order, position);
        if (position.getReservedAmount().compareTo(refund.getRefundAmount()) < 0) {
            throw new ErrorException("退款预占不足");
        }
        if (status.terminal()) {
            positions.settle(position, refund.getRefundAmount(), status == RefundStatus.SUCCEEDED, now);
        }
        if (status != refund.getRefundStatus() || refund.getChannelRefundId() == null) {
            refunds.update(refund, status, result.channelRefundId(),
                status == RefundStatus.FAILED ? "PAYMENT_REFUND_FAILED"
                    : status == RefundStatus.CANCELLED ? "PAYMENT_REFUND_CANCELLED" : null,
                status.terminal() ? now : null, now);
        }
        // TODO(Payment/Platform)：正式结果由 Payment 事实驱动，并衔接已批准的 Client Webhook；此处不通知 Finance。
        return status.terminal();
    }

    /** 读取归属当前 Client 的订单，既有提交任务路径使用锁定读取。 */
    private OrderRecord order(long clientId, String no, boolean lock) {
        var result = orders.find(clientId, no, null, lock);
        if (result == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        return result;
    }

    /** 退款提交只能基于已支付订单及其一致的本地 Payment 权威投影。 */
    private void requirePaid(OrderRecord order, OrderRefundPosition position) {
        if (order.orderStatus() != OrderStatus.PAID || order.paidAt() == null) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
        var payment = orders.payment(order.orderId());
        if (payment == null || payment.intentStatus() != PaymentIntentStatus.SUCCEEDED || payment.successfulAttemptId() == null
            || !order.currency().equals(payment.currency()) || order.totalAmount() == null
            || payment.capturedAmount().compareTo(order.totalAmount()) != 0
            || position.getRefundableBaseAmount().compareTo(payment.capturedAmount()) != 0) {
            throw new ErrorException("已支付订单缺少一致的退款权威投影");
        }
    }

    /** 核对金额位置归属，自身金额不变量由对象检查。 */
    private void validatePosition(OrderRecord order, OrderRefundPosition position) {
        if (position == null || !order.currency().equals(position.getCurrency())) {
            throw new ErrorException("退款金额位置不一致");
        }
        position.validateAmounts();
    }

    /** 任务所需退款必须存在，自身事实校验由对象负责。 */
    private void validate(RefundRequest refund) {
        if (refund == null) {
            throw new ErrorException("退款持久化事实缺失");
        }
        refund.validateFacts();
    }

}

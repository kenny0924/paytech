package com.paytech.acquiring.service.processor.refund;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.domain.refund.RefundCreationContext;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderRefundPositionRepository;
import com.paytech.acquiring.domainservice.repository.refund.RefundRepository;
import com.paytech.acquiring.domainservice.service.refund.RefundService;
import com.paytech.acquiring.service.processor.refund.request.CreateRefundRequest;
import com.paytech.acquiring.service.processor.refund.response.CreateRefundResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.token.PublicTokenGenerator;

import lombok.AllArgsConstructor;

/** 创建退款；业务查询在事务外，预占、退款和提交任务由 Service 原子保存。 */
@Component
@AllArgsConstructor
public class CreateRefundProcessor extends AbstractProcessor<CreateRefundRequest, CreateRefundResponse> {
    private final CommerceOrderRepository commerceOrderRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;
    private final OrderRefundPositionRepository orderRefundPositionRepository;
    private final RefundRepository refundRepository;
    private final RefundService refundService;
    private final CommerceIdentifierGenerator identifierGenerator;
    private final Clock clock;

    /** 原退款先退出；只有新申请才检查当前资格、保存和登记提交任务。 */
    @Override
    protected CreateRefundResponse processRequest(CreateRefundRequest request) {
        // 核对订单归属和原业务退款。
        var order = getAndCheckOrder(request);
        var amount = request.refundAmount(order.getCurrency());
        var context = checkAndGetContext(request, order, amount);
        if (context != null) {
            return processResponse(context);
        }

        // 新退款按需加载支付和额度，完成业务校验并构造上下文。
        context = buildCreateRefundContext(request, order, amount);

        // 本地原子保存；任务由公共组件在提交后唤醒。
        context = saveCreateRefundContext(request, context, amount);

        // 使用原退款或本次实际保存的事实，不重查响应所需数据。
        return processResponse(context);
    }

    /** 按可信 Client 读取订单，未知和跨 Client 均返回不存在。 */
    CommerceOrder getAndCheckOrder(CreateRefundRequest request) {
        var order = commerceOrderRepository.findByOrderNo(request.getClientId(), request.getOrderNo());
        if (order == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 仅比较原申请内容，不重查成功支付、不再验证当前可退余额或推进提交。 */
    RefundCreationContext checkAndGetContext(CreateRefundRequest request, CommerceOrder order, Money amount) {
        var refund = refundRepository.findByClientRefundId(request.getClientId(), request.getClientRefundId());
        if (refund == null) {
            return null;
        }
        refund.validateFacts();
        if (!refund.matchesCreation(order.getOrderId(), amount.getAmount(), order.getCurrency(),
            request.getReasonCode(), request.getReasonSummary())) {
            throw new FailException(CommerceCheckoutErrorCode.CLIENT_REFUND_ID_CONFLICT);
        }
        return new RefundCreationContext(order, null, null, refund);
    }

    /** 校验新申请的已支付资格，并一次构造保存所需的数据库对象。 */
    RefundCreationContext buildCreateRefundContext(CreateRefundRequest request, CommerceOrder order, Money amount) {
        if (order.getOrderStatus() != OrderStatus.PAID || order.getPaidAt() == null) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR);
        }
        var payment = orderPaymentProjectionRepository.findByOrderId(order.getOrderId());
        var position = orderRefundPositionRepository.findByOrderId(order.getOrderId());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var refund = RefundRequest.builder().refundRequestId(identifierGenerator.nextId())
            .refundRequestNo("rfd_" + PublicTokenGenerator.generate(Long.toString(order.getClientId()),
                order.getOrderNo(), request.getClientRefundId()))
            .orderId(order.getOrderId()).clientId(order.getClientId()).requestOrigin(RefundRequestOrigin.CLIENT)
            .clientRefundId(request.getClientRefundId()).refundAmount(amount.getAmount()).currency(order.getCurrency())
            .reasonCode(request.getReasonCode()).reasonSummary(request.getReasonSummary())
            .refundStatus(RefundStatus.PENDING).version(1).createTime(now).updateTime(now).build();
        var context = new RefundCreationContext(order, payment, position, refund);
        context.validateCreation();
        return context;
    }

    /** 唯一键竞争回滚后只读取获胜的原申请；不重试事务或重复预占。 */
    RefundCreationContext saveCreateRefundContext(CreateRefundRequest request, RefundCreationContext context,
        Money amount) {
        try {
            refundService.saveCreateRefundContext(context);
            return context;
        } catch (DuplicateKeyException exception) {
            var existing = checkAndGetContext(request, context.getOrder(), amount);
            if (existing != null) {
                return existing;
            }
            throw exception;
        }
    }

    /** 返回不可变创建回执；渠道后续状态不改变受理金额和创建时刻。 */
    CreateRefundResponse processResponse(RefundCreationContext context) {
        var refund = context.getRefund();
        String amount;
        try {
            amount = Money.of(refund.getCurrency(), refund.getRefundAmount()).toAmountString();
        } catch (IllegalArgumentException | InvalidParameterException exception) {
            throw new ErrorException("退款金额币种精度不一致", exception);
        }
        return new CreateRefundResponse(refund.getRefundRequestNo(), refund.getClientRefundId(),
            context.getOrder().getOrderNo(), amount, refund.getCurrency(), refund.getCreateTime());
    }
}

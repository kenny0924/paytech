package com.paytech.commerce.service.processor.refund;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.order.OrderRefundPositionRepository;
import com.paytech.commerce.service.processor.refund.request.GetRefundableAmountRequest;
import com.paytech.commerce.service.processor.refund.response.GetRefundableAmountResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 查询本地可退款金额，不开启事务、不加锁、不预占额度。 */
@Component
@AllArgsConstructor
public class GetRefundableAmountProcessor extends AbstractProcessor<GetRefundableAmountRequest, GetRefundableAmountResponse> {
    private final CommerceOrderRepository commerceOrderRepository;
    private final OrderRefundPositionRepository orderRefundPositionRepository;

    /** 先确认订单归属，再读取退款位置并构造金额快照。 */
    @Override
    protected GetRefundableAmountResponse processRequest(GetRefundableAmountRequest request) {
        var order = getAndCheckOrder(request);
        var position = getAndCheckRefundPosition(order);
        return processResponse(order.getOrderNo(), position);
    }

    /** 未知与跨 Client 订单统一返回不存在，不加载商品、会话或其他投影。 */
    CommerceOrder getAndCheckOrder(GetRefundableAmountRequest request) {
        var order = commerceOrderRepository.findByOrderNo(request.getClientId(), request.getOrderNo());
        if (order == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 退款位置与订单同时创建，缺失或币种不符不能解释为可退零额。 */
    OrderRefundPosition getAndCheckRefundPosition(CommerceOrder order) {
        var position = orderRefundPositionRepository.findByOrderId(order.getOrderId());
        if (position == null || !order.getCurrency().equals(position.getCurrency())) {
            throw new ErrorException("订单退款位置缺失或币种不一致");
        }
        position.validateAmounts();
        return position;
    }

    /** 使用同一位置记录的金额、更新时间和版本，不以查询时间代替事实时点。 */
    GetRefundableAmountResponse processResponse(String orderNo, OrderRefundPosition position) {
        var amount = Money.of(position.getCurrency(), position.refundableAmount()).toAmountString();
        return new GetRefundableAmountResponse(orderNo, position.getCurrency(), amount, position.getUpdateTime(),
            position.getVersion());
    }
}

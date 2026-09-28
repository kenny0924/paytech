package com.paytech.commerce.service.processor.refund;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.refund.RefundRepository;
import com.paytech.commerce.service.processor.refund.request.GetRefundRequest;
import com.paytech.commerce.service.processor.refund.response.GetRefundResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 按需读取当前退款，不开启事务、不加锁、不推进退款任务。 */
@Component
@AllArgsConstructor
public class GetRefundProcessor extends AbstractProcessor<GetRefundRequest, GetRefundResponse> {
    private final RefundRepository refundRepository;
    private final CommerceOrderRepository commerceOrderRepository;

    /** 先读取当前 Client 的退款，再读取关联订单号并构造响应。 */
    @Override
    protected GetRefundResponse processRequest(GetRefundRequest request) {
        var refund = getAndCheckRefund(request);
        var orderNo = getAndCheckOrderNo(request.getClientId(), refund.getOrderId());
        return processResponse(refund, orderNo);
    }

    /** 未知和跨 Client 退款统一返回不存在，不泄露其他 Client 的资源。 */
    RefundRequest getAndCheckRefund(GetRefundRequest request) {
        var refund = refundRepository.findByRefundRequestNo(request.getClientId(), request.getRefundRequestNo());
        if (refund == null) {
            throw new FailException(CommerceCheckoutErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }

    /** 只读取展示所需的公开订单号；关联订单不可见时沿用原退款不存在语义。 */
    String getAndCheckOrderNo(long clientId, long orderId) {
        var orderNo = commerceOrderRepository.findOrderNoByOrderId(clientId, orderId);
        if (orderNo == null) {
            throw new FailException(CommerceCheckoutErrorCode.REFUND_NOT_FOUND);
        }
        return orderNo;
    }

    /** 校验已加载的退款事实并输出金额字符串，数据库对象不穿透 HTTP 边界。 */
    GetRefundResponse processResponse(RefundRequest refund, String orderNo) {
        refund.validateFacts();
        String amount;
        try {
            amount = Money.of(refund.getCurrency(), refund.getRefundAmount()).toAmountString();
        } catch (InvalidParameterException exception) {
            // 金额和币种来自本库，转换失败属于系统错误。
            throw new ErrorException("退款金额币种精度不一致", exception);
        }
        return new GetRefundResponse(refund.getRefundRequestNo(), refund.getClientRefundId(), refund.getRequestOrigin(),
            orderNo, amount, refund.getCurrency(), refund.getRefundStatus(), refund.getReasonCode(),
            refund.getStatusReasonCode(), refund.getCreateTime(), refund.getCompletedAt(), refund.getUpdateTime(),
            refund.getVersion());
    }
}

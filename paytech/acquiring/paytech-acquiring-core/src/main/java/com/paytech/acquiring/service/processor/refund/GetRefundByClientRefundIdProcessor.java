package com.paytech.acquiring.service.processor.refund;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.refund.RefundRepository;
import com.paytech.acquiring.service.processor.refund.request.GetRefundByClientRefundIdRequest;
import com.paytech.acquiring.service.processor.refund.response.GetRefundResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 按 Client 业务退款号读取当前退款，不开启事务或推进退款任务。 */
@Component
@AllArgsConstructor
public class GetRefundByClientRefundIdProcessor
    extends AbstractProcessor<GetRefundByClientRefundIdRequest, GetRefundResponse> {
    private final RefundRepository refundRepository;
    private final CommerceOrderRepository commerceOrderRepository;

    /** 先读取当前 Client 的业务退款，再补充公开订单号并构造响应。 */
    @Override
    protected GetRefundResponse processRequest(GetRefundByClientRefundIdRequest request) {
        var refund = getAndCheckRefund(request);
        var orderNo = getAndCheckOrderNo(request.getClientId(), refund.getOrderId());
        return processResponse(refund, orderNo);
    }

    /** 单表查询仅匹配当前 Client 的 CLIENT 来源退款，未命中统一返回不存在。 */
    RefundRequest getAndCheckRefund(GetRefundByClientRefundIdRequest request) {
        var refund = refundRepository.findByClientRefundId(request.getClientId(), request.getClientRefundId());
        if (refund == null) {
            throw new FailException(CommerceCheckoutErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }

    /** 只读取当前 Client 的公开订单号，关联订单不可见时沿用退款不存在语义。 */
    String getAndCheckOrderNo(long clientId, long orderId) {
        var orderNo = commerceOrderRepository.findOrderNoByOrderId(clientId, orderId);
        if (orderNo == null) {
            throw new FailException(CommerceCheckoutErrorCode.REFUND_NOT_FOUND);
        }
        return orderNo;
    }

    /** 使用已加载事实构造详情；本库金额转换失败归类为系统错误，保留原异常原因。 */
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

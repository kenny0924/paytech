package com.paytech.commerce.service.processor.refund;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domain.refund.RefundCursor;
import com.paytech.commerce.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.commerce.domainservice.repository.refund.RefundRepository;
import com.paytech.commerce.service.processor.refund.request.ListOrderRefundsRequest;
import com.paytech.commerce.service.processor.refund.response.ListOrderRefundsResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 按需查询订单退款，不开启事务、不加锁、不推进退款任务。 */
@Component
@AllArgsConstructor
public class ListOrderRefundsProcessor extends AbstractProcessor<ListOrderRefundsRequest, ListOrderRefundsResponse> {
    private final RefundRepository refundRepository;
    private final CommerceOrderRepository commerceOrderRepository;

    /** 先确认订单归属，再验证游标、读取单表分页并构造响应。 */
    @Override
    protected ListOrderRefundsResponse processRequest(ListOrderRefundsRequest request) {
        var orderId = getAndCheckOrderId(request);
        var cursor = checkAndGetCursor(request, orderId);
        var refunds = refundRepository.list(request.getClientId(), orderId,
            cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.refundRequestNo(),
            request.getLimit() + 1);
        return processResponse(request.getOrderNo(), refunds, request.getLimit());
    }

    /** 只查询建立分页范围所需的订单主键；未知或跨 Client 统一返回不存在。 */
    long getAndCheckOrderId(ListOrderRefundsRequest request) {
        var orderId = commerceOrderRepository.findOrderIdByOrderNo(request.getClientId(), request.getOrderNo());
        if (orderId == null) {
            throw new FailException(CommerceCheckoutErrorCode.ORDER_NOT_FOUND);
        }
        return orderId;
    }

    /** 首屏不查锚点；续页核对游标对应的原退款、订单和不可变创建时间。 */
    RefundCursor checkAndGetCursor(ListOrderRefundsRequest request, long orderId) {
        if (request.getCursor() == null) {
            return null;
        }
        var cursor = RefundCursor.decode(request.getCursor());
        var anchor = refundRepository.findByRefundRequestNo(request.getClientId(), cursor.refundRequestNo());
        if (anchor == null || anchor.getOrderId() != orderId || !cursor.createdAt().equals(anchor.getCreateTime())) {
            throw new FailException(CommerceCheckoutErrorCode.INVALID_CURSOR);
        }
        return cursor;
    }

    /** 多取一条只用于判断是否还有下一页；游标取实际返回页的最后一条。 */
    ListOrderRefundsResponse processResponse(String orderNo, List<RefundRequest> refunds, int limit) {
        var items = refunds.stream().limit(limit).map(refund -> refundResult(orderNo, refund)).toList();
        String nextCursor = null;
        if (refunds.size() > limit) {
            var last = items.getLast();
            nextCursor = new RefundCursor(last.getCreatedAt(), last.getRefundRequestNo()).encode();
        }
        return new ListOrderRefundsResponse(items, nextCursor);
    }

    /** 校验已加载事实并输出金额字符串，数据库对象不穿透 HTTP 边界。 */
    ListOrderRefundsResponse.Refund refundResult(String orderNo, RefundRequest refund) {
        refund.validateFacts();
        String amount;
        try {
            amount = Money.of(refund.getCurrency(), refund.getRefundAmount()).toAmountString();
        } catch (InvalidParameterException exception) {
            // 金额来自本库，数据错误不能按客户端参数错误返回 400。
            throw new ErrorException("退款金额币种精度不一致", exception);
        }
        return new ListOrderRefundsResponse.Refund(refund.getRefundRequestNo(), refund.getClientRefundId(),
            refund.getRequestOrigin(), orderNo, amount, refund.getCurrency(), refund.getRefundStatus(),
            refund.getReasonCode(), refund.getStatusReasonCode(), refund.getCreateTime(), refund.getCompletedAt(),
            refund.getUpdateTime(), refund.getVersion());
    }
}

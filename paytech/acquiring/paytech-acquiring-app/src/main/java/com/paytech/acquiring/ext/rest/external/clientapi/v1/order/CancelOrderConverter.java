package com.paytech.commerce.ext.rest.external.clientapi.v1.order;

import com.paytech.commerce.client.api.v1.enums.CancellationReason;
import com.paytech.commerce.client.api.v1.enums.CancellationStatus;
import com.paytech.commerce.client.api.v1.order.response.CancelOrderResponseBody;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.service.processor.order.request.CancelOrderRequest;
import com.paytech.commerce.service.processor.order.response.CancelOrderResponse;

/** 取消接口的协议转换；不查询、判断取消资格或持有事务。 */
public final class CancelOrderConverter {
    /** 禁止实例化无状态转换器。 */
    private CancelOrderConverter() {}

    /** 将可信 Header 和 Path 转换为内部请求，不为无 Body 接口增加空包装。 */
    public static CancelOrderRequest request(long clientId, String orderNo) {
        return new CancelOrderRequest(clientId, orderNo);
    }

    /** 200 使用原取消事实，202 仅输出待定字段。 */
    public static CancelOrderResponseBody response(CancelOrderResponse response) {
        var body = CancelOrderResponseBody.builder().orderNo(response.getOrderNo());
        if (response.isPending()) {
            return body.cancellationStatus(CancellationStatus.PENDING.getCode())
                .reasonCode(CancellationReason.PAYMENT_IN_PROGRESS.getCode()).build();
        }
        return body.orderStatus(OrderStatus.CANCELLED.getCode())
            .checkoutStatus(response.getCheckoutStatus() == null ? null : response.getCheckoutStatus().getCode())
            .cancelledAt(response.getCancelledAt()).build();
    }
}

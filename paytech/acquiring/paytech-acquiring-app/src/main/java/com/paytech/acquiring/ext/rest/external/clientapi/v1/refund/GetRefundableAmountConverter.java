package com.paytech.commerce.ext.rest.external.clientapi.v1.refund;

import com.paytech.commerce.client.api.v1.refund.response.GetRefundableAmountResponseBody;
import com.paytech.commerce.service.processor.refund.request.GetRefundableAmountRequest;
import com.paytech.commerce.service.processor.refund.response.GetRefundableAmountResponse;

/** 可退款金额查询的协议转换，不查询数据库或计算额度。 */
public final class GetRefundableAmountConverter {
    /** 禁止实例化无状态转换器。 */
    private GetRefundableAmountConverter() {}

    /** 将可信 Client 和路径订单号转换为内部请求，不增加 GET Body。 */
    public static GetRefundableAmountRequest request(long clientId, String orderNo) {
        return new GetRefundableAmountRequest(clientId, orderNo);
    }

    /** 将内部金额快照转换为原有公开字段。 */
    public static GetRefundableAmountResponseBody response(GetRefundableAmountResponse response) {
        return new GetRefundableAmountResponseBody(response.getOrderNo(), response.getCurrency(),
            response.getRefundableAmount(), response.getAsOf(), Math.toIntExact(response.getVersion()));
    }
}

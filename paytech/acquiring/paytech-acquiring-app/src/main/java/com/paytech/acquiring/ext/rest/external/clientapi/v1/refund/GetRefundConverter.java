package com.paytech.commerce.ext.rest.external.clientapi.v1.refund;

import com.paytech.commerce.client.api.v1.refund.response.GetRefundResponseBody;
import com.paytech.commerce.service.processor.refund.request.GetRefundByClientRefundIdRequest;
import com.paytech.commerce.service.processor.refund.request.GetRefundRequest;
import com.paytech.commerce.service.processor.refund.response.GetRefundResponse;

/** 退款详情的协议转换，不查询数据库或判断资源归属。 */
public final class GetRefundConverter {
    /** 禁止实例化无状态转换器。 */
    private GetRefundConverter() {}

    /** 将可信 Client 和路径退款号转换为内部请求，不增加 GET Body。 */
    public static GetRefundRequest request(long clientId, String refundRequestNo) {
        return new GetRefundRequest(clientId, refundRequestNo);
    }

    /** 将可信 Client 和 Query 中的业务退款号转换为内部请求。 */
    public static GetRefundByClientRefundIdRequest requestByClientRefundId(long clientId, String clientRefundId) {
        return new GetRefundByClientRefundIdRequest(clientId, clientRefundId);
    }

    /** 将内部枚举和表示版本转换为既有接口字段。 */
    public static GetRefundResponseBody response(GetRefundResponse response) {
        return new GetRefundResponseBody(response.getRefundRequestNo(), response.getClientRefundId(),
            response.getRequestOrigin().getCode(), response.getOrderNo(), response.getAmount(), response.getCurrency(),
            response.getRefundStatus().getCode(), response.getReasonCode(), response.getStatusReasonCode(),
            response.getCreatedAt(), response.getCompletedAt(), response.getUpdatedAt(),
            Math.toIntExact(response.getVersion()));
    }
}

package com.paytech.commerce.ext.rest.external.clientapi.v1.refund;

import com.paytech.commerce.client.api.v1.refund.response.ListOrderRefundsResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.ListOrderRefundsResponseBody.RefundInfo;
import com.paytech.commerce.service.processor.refund.request.ListOrderRefundsRequest;
import com.paytech.commerce.service.processor.refund.response.ListOrderRefundsResponse;

/** 退款列表的协议转换，不查询数据库或判断游标归属。 */
public final class ListOrderRefundsConverter {
    /** 禁止实例化无状态转换器。 */
    private ListOrderRefundsConverter() {}

    /** 将可信身份、订单路径和分页参数转换为内部请求，不增加 GET Body。 */
    public static ListOrderRefundsRequest request(long clientId, String orderNo, int limit, String cursor) {
        return new ListOrderRefundsRequest(clientId, orderNo, limit, cursor);
    }

    /** 按既有 JSON 字段输出本页退款和可空下一页游标。 */
    public static ListOrderRefundsResponseBody response(ListOrderRefundsResponse response) {
        return new ListOrderRefundsResponseBody(response.getItems().stream().map(ListOrderRefundsConverter::refundInfo)
            .toList(), response.getNextCursor());
    }

    /** 将内部枚举与表示版本转换为接口字段，不输出持久化对象。 */
    static RefundInfo refundInfo(ListOrderRefundsResponse.Refund refund) {
        return new RefundInfo(refund.getRefundRequestNo(), refund.getClientRefundId(), refund.getRequestOrigin().getCode(),
            refund.getOrderNo(), refund.getAmount(), refund.getCurrency(), refund.getRefundStatus().getCode(),
            refund.getReasonCode(), refund.getStatusReasonCode(), refund.getCreatedAt(), refund.getCompletedAt(),
            refund.getUpdatedAt(), Math.toIntExact(refund.getVersion()));
    }
}

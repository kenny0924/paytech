package com.paytech.commerce.ext.rest.external.clientapi.v1.refund;

import com.paytech.commerce.client.api.v1.refund.request.CreateRefundRequestBody;
import com.paytech.commerce.client.api.v1.refund.response.CreateRefundResponseBody;
import com.paytech.commerce.service.processor.refund.request.CreateRefundRequest;
import com.paytech.commerce.service.processor.refund.response.CreateRefundResponse;

/** 退款创建的协议转换，不查询数据库或判断业务资格。 */
public final class CreateRefundConverter {
    /** 禁止实例化无状态转换器。 */
    private CreateRefundConverter() {}

    /** 合并可信身份、订单路径与 Body，形成内部业务输入。 */
    public static CreateRefundRequest request(long clientId, String orderNo, CreateRefundRequestBody body) {
        return new CreateRefundRequest(clientId, orderNo, body.getClientRefundId(), body.getAmount(),
            body.getReasonCode(), body.getReasonSummary());
    }

    /** 输出有限创建事实，保持原 201 回执的 JSON 字段。 */
    public static CreateRefundResponseBody response(CreateRefundResponse response) {
        return new CreateRefundResponseBody(response.getRefundRequestNo(), response.getClientRefundId(),
            response.getOrderNo(), response.getAmount(), response.getCurrency(), response.getCreatedAt());
    }
}

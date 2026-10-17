package com.paytech.acquiring.test.builder.refund;

import com.paytech.acquiring.api.client.v1.refund.request.CreateRefundRequestBody;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 仅构建 HTTP 请求，非法值原样保留，不计算可退金额。 */
@Setter
@Accessors(fluent = true)
public final class CreateRefundRequestBuilder {
    private String clientRefundId = "refund-main";
    private String amount = "10.00";
    private String reasonCode = "REQUESTED_BY_CUSTOMER";
    private String reasonSummary;

    /** 每例独立的合法申请。 */
    public static CreateRefundRequestBuilder template() {
        return new CreateRefundRequestBuilder();
    }

    /** 不修复负例，也不调用生产转换器。 */
    public CreateRefundRequestBody build() {
        return new CreateRefundRequestBody(clientRefundId, amount, reasonCode, reasonSummary);
    }
}

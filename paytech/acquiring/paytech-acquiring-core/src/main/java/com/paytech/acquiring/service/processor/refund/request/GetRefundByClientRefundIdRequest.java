package com.paytech.commerce.service.processor.refund.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 Client 业务退款号查询的内部输入，字段约束由 HTTP 入口校验。 */
@Getter
@AllArgsConstructor
public class GetRefundByClientRefundIdRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** Client 命名空间内永久唯一、大小写敏感的业务退款号。 */
    private final String clientRefundId;
}

package com.paytech.acquiring.service.processor.refund.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按公开退款号查询的内部输入，字段约束由 HTTP 入口校验。 */
@Getter
@AllArgsConstructor
public class GetRefundRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 不透明、大小写敏感的公开退款号。 */
    private final String refundRequestNo;
}

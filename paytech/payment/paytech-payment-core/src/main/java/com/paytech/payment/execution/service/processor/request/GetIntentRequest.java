package com.paytech.payment.execution.service.processor.request;

import lombok.Getter;
import lombok.Setter;

/** 支付意图查询请求，由 HTTP 入口完成字段校验 */
@Getter
@Setter
public class GetIntentRequest {
    /** 支付意图 ID */
    private Long intentId;

    /** Client ID */
    private Long clientId;
}

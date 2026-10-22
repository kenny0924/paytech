package com.paytech.payment.execution.service.processor.request;

import lombok.Getter;
import lombok.Setter;

/** 按来源业务查询支付意图请求，由 HTTP 入口完成字段校验 */
@Getter
@Setter
public class GetIntentBySourceRequest {
    /** 来源业务类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** Client ID */
    private Long clientId;
}

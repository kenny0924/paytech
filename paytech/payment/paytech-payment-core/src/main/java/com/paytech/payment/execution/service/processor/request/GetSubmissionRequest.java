package com.paytech.payment.execution.service.processor.request;

import lombok.Getter;
import lombok.Setter;

/** 按来源提交查询原支付结果的请求，由 HTTP 入口完成字段校验 */
@Getter
@Setter
public class GetSubmissionRequest {
    /** 支付意图 ID */
    private Long intentId;

    /** 来源提交 ID */
    private Long sourceSubmissionId;

    /** Client ID */
    private Long clientId;
}

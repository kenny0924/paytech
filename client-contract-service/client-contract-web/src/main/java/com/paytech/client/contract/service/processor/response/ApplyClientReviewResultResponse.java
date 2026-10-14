package com.paytech.client.contract.service.processor.response;

/** 审核结果的落地结论；三种结果都应确认消息。 */
public enum ApplyClientReviewResultResponse {
    /** 本次落地。 */
    APPLIED,

    /** 已按同一结果落地，重复投递。 */
    DUPLICATE,

    /** 不再是申请的当前提交或任务，忽略。 */
    STALE
}

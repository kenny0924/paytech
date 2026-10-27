package com.paytech.compliance.service.processor.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 最近一次主案件的客户可见结果。 */
@Getter
@AllArgsConstructor
public class ClientReviewResultResponse {
    /** 申请 ID。 */
    private final long applicationId;
    /** 主案件 ID。 */
    private final long caseId;
    /** 主案件状态。 */
    private final String status;
    /** 已结束案件的结果；进行中为 null。 */
    private final String outcome;
    /** 提交轮次。 */
    private final int roundNo;
    /** 兼容退回分区。 */
    private final String returnSection;
    /** 客户可见说明。 */
    private final String publicNote;
    /** 退回 / 过期案件的需求级错误；其他为空列表。 */
    private final List<ClientReviewCaseResponse.ErrorItem> errors;
    /** 补件期限；仅退回 / 过期填写。 */
    private final Instant dueTime;
    /** 结束时间；进行中为 null。 */
    private final Instant decidedAt;
}

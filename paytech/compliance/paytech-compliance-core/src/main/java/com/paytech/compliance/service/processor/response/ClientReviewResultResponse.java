package com.paytech.compliance.service.processor.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 最近一次主任务的客户可见结果。 */
@Getter
@AllArgsConstructor
public class ClientReviewResultResponse {
    /** CCT 申请 ID。 */
    private final long applicationId;
    /** 主任务 ID。 */
    private final long caseId;
    /** 主任务状态。 */
    private final String status;
    /** 已结束任务的结果；进行中为 null。 */
    private final String outcome;
    /** 退回分区；非退回为 null。 */
    private final String returnSection;
    /** 客户可见说明。 */
    private final String publicNote;
    /** 结束时间；进行中为 null。 */
    private final Instant decidedAt;
}

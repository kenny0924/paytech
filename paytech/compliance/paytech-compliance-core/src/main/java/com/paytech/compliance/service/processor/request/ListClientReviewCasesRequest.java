package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审核任务列表查询；筛选条件均可空。 */
@Getter
@AllArgsConstructor
public class ListClientReviewCasesRequest {
    /** 页码，从 1 开始。 */
    @Positive
    private final int page;
    /** 每页条数。 */
    @Positive
    private final int size;
    /** 主案件状态。 */
    private final String status;
    /** 资格类型。 */
    private final String qualificationType;
    /** CCT 申请 ID。 */
    private final Long applicationId;
}

package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** I03 撤回申请对应的审核案件。 */
@Getter
@AllArgsConstructor
public class WithdrawClientReviewRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 申请 ID，必须与案件来源申请一致。 */
    @Positive
    private final long applicationId;
    /** 撤回人 Platform CUSTOMER Account ID；可空。 */
    private final Long requestedByAccountId;
}

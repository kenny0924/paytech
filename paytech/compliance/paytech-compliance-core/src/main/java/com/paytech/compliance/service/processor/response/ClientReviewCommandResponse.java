package com.paytech.compliance.service.processor.response;

import lombok.Getter;

/** 审核命令结果。 */
@Getter
public class ClientReviewCommandResponse {
    /** 主案件 ID。 */
    private final long caseId;
    /** 命令完成后的主案件状态。 */
    private final String status;
    /** 是否仍有提交后异步执行的工作（重跑筛查）。 */
    private final boolean processing;

    /** 同步完成的命令结果。 */
    public ClientReviewCommandResponse(long caseId, String status) {
        this(caseId, status, false);
    }

    /** 命令结果。 */
    public ClientReviewCommandResponse(long caseId, String status, boolean processing) {
        this.caseId = caseId;
        this.status = status;
        this.processing = processing;
    }
}

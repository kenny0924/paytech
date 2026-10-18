package com.paytech.compliance.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审核命令完成后的主任务坐标。 */
@Getter
@AllArgsConstructor
public class ClientReviewCommandResponse {
    /** 主任务 ID。 */
    private final long caseId;
    /** 主任务状态。 */
    private final String status;
}

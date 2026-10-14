package com.paytech.client.contract.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交前核对的内部请求。 */
@Getter
@AllArgsConstructor
public class CheckClientSubmissionRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话。 */
    @Positive
    private final long sessionId;
    /** 申请 ID。 */
    @Positive
    private final long applicationId;
}

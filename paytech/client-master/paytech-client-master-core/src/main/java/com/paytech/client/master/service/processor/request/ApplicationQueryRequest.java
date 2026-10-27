package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按申请的只读请求（需求清单）。 */
@Getter
@AllArgsConstructor
public class ApplicationQueryRequest {
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

package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** C14 内部请求；身份来自已核对的私网 Header，Body 为空。 */
@Getter
@AllArgsConstructor
public class SubmitClientApplicationRequest {
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

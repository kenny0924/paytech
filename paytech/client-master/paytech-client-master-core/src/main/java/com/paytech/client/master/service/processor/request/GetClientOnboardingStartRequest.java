package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** C01 开始页导航的内部请求。 */
@Getter
@AllArgsConstructor
public class GetClientOnboardingStartRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
}

package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只需 CLIENT 会话核验的只读请求。 */
@Getter
@AllArgsConstructor
public class ClientSessionRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话。 */
    @Positive
    private final long sessionId;
}

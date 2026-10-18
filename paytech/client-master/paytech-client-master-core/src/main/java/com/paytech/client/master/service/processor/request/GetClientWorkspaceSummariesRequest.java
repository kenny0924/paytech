package com.paytech.client.master.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client Console 工作区摘要的内部请求。 */
@Getter
@AllArgsConstructor
public class GetClientWorkspaceSummariesRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 入口已校验的 1—50 个不重复 Client ID。 */
    private final List<Long> clientIds;
}

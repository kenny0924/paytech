package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 针对一个执行发布的校验、激活或查询输入。 */
@Getter
@AllArgsConstructor
public class ExecutionReleaseCommandRequest {
    /** 操作人账号 ID。 */
    @Positive
    private final long actorAccountId;

    /** 执行发布 ID。 */
    @Positive
    private final long releaseId;
}

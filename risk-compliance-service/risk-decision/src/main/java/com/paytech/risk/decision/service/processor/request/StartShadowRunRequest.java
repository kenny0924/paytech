package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启动影子运行的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class StartShadowRunRequest {
    /** 操作人账号 ID。 */
    @Positive
    private final long actorAccountId;

    /** 候选执行发布 ID。 */
    @Positive
    private final long releaseId;

    /** 目标样本数，1 至 2000。 */
    @Min(1)
    @Max(2000)
    private final int sampleTarget;
}

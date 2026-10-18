package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建执行发布的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class CreateExecutionReleaseRequest {
    /** 操作人账号 ID。 */
    @Positive
    private final long actorAccountId;

    /** 决策点编码，必须支持运行期执行。 */
    @NotBlank
    private final String pointCode;

    /** 冻结的决策流版本 ID。 */
    @Positive
    private final long flowVersionId;
}

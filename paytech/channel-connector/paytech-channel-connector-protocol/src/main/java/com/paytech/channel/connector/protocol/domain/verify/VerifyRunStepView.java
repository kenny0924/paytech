package com.paytech.channel.connector.protocol.domain.verify;

import com.paytech.channel.connector.api.enums.VerifyStepStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 验证运行中的一个步骤。 */
@Getter
@AllArgsConstructor
public class VerifyRunStepView {

    /** 步骤序号。 */
    private final int stepNo;

    /** 步骤 ID。 */
    private final String stepKey;

    /** 标准 API。 */
    private final String standardApiCode;

    /** 步骤结论。 */
    private final VerifyStepStatus stepStatus;

    /** 实际输入。 */
    private final JsonNode resolvedInput;

    /** 实际结果。 */
    private final JsonNode result;

    /** 期望核对。 */
    private final JsonNode assertions;

    /** 执行轨迹。 */
    private final JsonNode trace;

    /** 耗时毫秒。 */
    private final Integer elapsedMs;
}

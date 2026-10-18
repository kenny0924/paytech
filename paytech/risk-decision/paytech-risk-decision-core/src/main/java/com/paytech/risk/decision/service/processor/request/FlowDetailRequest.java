package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class FlowDetailRequest {

    /** 流程管理根 ID。 */
    @Positive
    private final long flowId;

    /** 准确版本 ID；为空时查询当前配置。 */
    @Positive
    private final Long flowVersionId;
}

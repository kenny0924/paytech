package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class FlowHistoryRequest {

    /** 流程管理根 ID。 */
    @Positive
    private final long flowId;

    /** 本次浏览的固定业务版本上界。 */
    @NotNull @Positive
    private final Integer maxVersionNo;

    /** 页码，使用公共分页默认值和范围。 */
    private final Integer page;

    /** 每页条数，使用公共分页默认值和范围。 */
    private final Integer size;
}

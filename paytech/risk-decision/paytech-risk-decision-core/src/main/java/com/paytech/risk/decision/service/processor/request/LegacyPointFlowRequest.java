package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class LegacyPointFlowRequest {

    /** 旧点配置准确版本 ID。 */
    @Positive
    private final long pointVersionId;
}

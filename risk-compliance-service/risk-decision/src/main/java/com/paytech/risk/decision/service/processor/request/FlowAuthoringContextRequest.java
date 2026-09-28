package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程只读查询输入，由公共 Processor 触发基础校验。 */
@Getter
@AllArgsConstructor
public class FlowAuthoringContextRequest {

    /** 可选决策点编码，缺省使用 CHECKOUT_PREPARE。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "pointCode 格式无效")
    private final String pointCode;
}

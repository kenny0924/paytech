package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class RuleAuthoringContextRequest {
    /** 决策点编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String pointCode;
}

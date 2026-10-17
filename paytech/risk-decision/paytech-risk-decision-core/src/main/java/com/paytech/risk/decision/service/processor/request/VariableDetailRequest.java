package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 变量 Owner 输入，公共 Processor 模板触发基础校验。 */
@Getter
@AllArgsConstructor
public class VariableDetailRequest {
    /** 永久变量编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String variableCode;
}

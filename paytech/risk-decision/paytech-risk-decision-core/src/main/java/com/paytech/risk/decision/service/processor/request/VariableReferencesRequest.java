package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 变量 Owner 输入，公共 Processor 模板触发基础校验。 */
@Getter
@AllArgsConstructor
public class VariableReferencesRequest {
    /** 永久变量编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String variableCode;

    /** 引用范围。 */
    @NotNull @Pattern(regexp = "ALL|CURRENT|HISTORY")
    private final String scope;

    /** 后续扫描游标，可空。 */
    @Size(max = 2048)
    private final String cursor;

    /** 本页最多返回引用数。 */
    @Min(1) @Max(100)
    private final int size;
}

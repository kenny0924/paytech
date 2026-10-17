package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 变量 Owner 输入，公共 Processor 模板触发基础校验。 */
@Getter
@AllArgsConstructor
public class VariableMembersRequest {
    /** 永久变量编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String variableCode;

    /** 字面搜索关键字，可空。 */
    private final String keyword;

    /** 从 1 开始的页码。 */
    @Min(1) @Max(10000)
    private final int page;

    /** 每页数量。 */
    @Min(1) @Max(100)
    private final int size;

    /** 首次查询根版本，后续页必填。 */
    @Positive
    private final Long expectedVersion;
}

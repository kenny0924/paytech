package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营程序目录查询条件；均可空。 */
@Getter
@AllArgsConstructor
public class ListProgramsRequest {
    /** 辖区代码。 */
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$")
    private final String jurisdictionCode;
    /** 程序状态。 */
    @Pattern(regexp = "^(DRAFT|ACTIVE|RETIRED)$")
    private final String status;
}

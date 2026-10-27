package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** CP-1 程序解析查询。 */
@Getter
@AllArgsConstructor
public class ResolveProgramRequest {
    /** ORGANIZATION 或 INDIVIDUAL。 */
    @NotBlank
    @Pattern(regexp = "^(ORGANIZATION|INDIVIDUAL)$")
    private final String partyType;
    /** CLIENT 或 RELATED_PERSON。 */
    @NotBlank
    @Pattern(regexp = "^(CLIENT|RELATED_PERSON)$")
    private final String roleType;
    /** SIMPLIFIED、STANDARD 或 ENHANCED。 */
    @NotBlank
    @Pattern(regexp = "^(SIMPLIFIED|STANDARD|ENHANCED)$")
    private final String tier;
    /** 辖区代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$")
    private final String jurisdictionCode;
    /** 责任 PayFac；平台自营为空。 */
    private final Long responsiblePartnerId;
}

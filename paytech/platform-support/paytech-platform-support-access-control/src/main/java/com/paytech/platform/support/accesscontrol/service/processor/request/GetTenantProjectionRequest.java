package com.paytech.platform.support.accesscontrol.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 来源查询请求；由公共 Processor 校验来源类型及正整数标识。 */
@Getter
@AllArgsConstructor
public class GetTenantProjectionRequest {
    /** 查询来源类型，本期只接受 CLIENT。 */
    @NotNull @Pattern(regexp = "CLIENT")
    private final String tenantType;

    /** CCT Client 标识。 */
    @Positive
    private final long ownerRefId;
}

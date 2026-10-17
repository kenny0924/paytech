package com.paytech.platform.support.accesscontrol.service.processor.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 来源投影内部请求；字段约束由公共 Processor 在获取锁之前执行。 */
@Getter
@AllArgsConstructor
public class ProvisionTenantRequest {
    /** 经过协议校验的 CCT Client 标识。 */
    @Positive
    private final long ownerRefId;

    /** 经过协议校验的原申请账号标识。 */
    @Positive
    private final long requestedByAccountId;

    /** 经过协议校验的正整数来源序号。 */
    @Min(1)
    private final int sourceVersion;

    /** 经过协议校验的原始来源摘要。 */
    @NotNull @Pattern(regexp = "[a-f0-9]{64}")
    private final String payloadHash;
}

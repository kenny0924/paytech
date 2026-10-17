package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 租户访问用例请求，基础字段约束由公共 Processor 校验。 */
@Getter
@AllArgsConstructor
public class ChangeTenantSecurityStatusRequest {
    /** 可信 Operations 操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信 Operations 会话标识。 */
    @NotBlank
    private final String session;

    /** 路径中的目标租户标识。 */
    @Positive
    private final long tenantId;

    /** 目标安全状态。 */
    @NotNull
    private final OperationsTenantSecurityStatus securityStatus;

    /** 调用方读取的原版本。 */
    @Positive
    private final int expectedVersion;

    /** 已去除首尾空白的非敏感操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;
}

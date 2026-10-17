package com.paytech.platform.support.accesscontrol.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 租户访问用例请求，基础字段约束由公共 Processor 校验。 */
@Getter
@AllArgsConstructor
public class GetTenantMemberRequest {
    /** 可信 Operations 操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信 Operations 会话标识。 */
    @NotBlank
    private final String session;

    /** 路径中的目标租户标识。 */
    @Positive
    private final long tenantId;

    /** 路径中的目标成员标识。 */
    @Positive
    private final long memberId;
}

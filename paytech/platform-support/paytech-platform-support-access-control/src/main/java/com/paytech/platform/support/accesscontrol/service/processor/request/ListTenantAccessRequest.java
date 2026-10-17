package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 租户访问用例请求，基础字段约束由公共 Processor 校验。 */
@Getter
@AllArgsConstructor
public class ListTenantAccessRequest {
    /** 可信 Operations 操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信 Operations 会话标识。 */
    @NotBlank
    private final String session;

    /** 已解析默认值的分页参数，每页最多 100 条。 */
    @NotNull
    private final PagingRequestBody paging;

    /** 租户类型筛选，可空。 */
    private final AccessTenantType tenantType;

    /** 租户标识筛选，可空。 */
    @Positive
    private final Long tenantId;

    /** 所属客户或合作伙伴标识筛选，可空。 */
    @Positive
    private final Long ownerRefId;

    /** 已去除首尾空白的名称筛选，可空。 */
    @Size(max = 255)
    private final String tenantName;

    /** 来源业务状态筛选，可空。 */
    private final OperationsTenantOwnerStatus ownerStatus;

    /** 安全状态筛选，可空。 */
    private final OperationsTenantSecurityStatus securityStatus;

    /** 租户访问管理保留每页最多 100 条的业务上限。 */
    @AssertTrue(message = "每页最多 100 条")
    public boolean isPageSizeValid() { return paging == null || paging.size() <= 100; }
}

package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

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
public class ListTenantMembersRequest {
    /** 可信 Operations 操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信 Operations 会话标识。 */
    @NotBlank
    private final String session;

    /** 路径中的目标租户标识。 */
    @Positive
    private final long tenantId;

    /** 已解析默认值的分页参数，每页最多 100 条。 */
    @NotNull
    private final PagingRequestBody paging;

    /** 账号标识筛选，可空。 */
    @Positive
    private final Long accountId;

    /** 脱敏邮箱显示值筛选，可空。 */
    @Size(max = 255)
    private final String emailMasked;

    /** 成员类型筛选，可空。 */
    private final TenantMemberType memberType;

    /** 成员状态筛选，可空。 */
    private final OperationsMemberStatus memberStatus;

    /** 租户访问管理保留每页最多 100 条的业务上限。 */
    @AssertTrue(message = "每页最多 100 条")
    public boolean isPageSizeValid() { return paging == null || paging.size() <= 100; }
}

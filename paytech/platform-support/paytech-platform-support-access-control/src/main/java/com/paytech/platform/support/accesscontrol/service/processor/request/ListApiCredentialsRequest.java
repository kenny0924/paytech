package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.CredentialFilter;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成 Query 解析的凭证列表请求；身份仍需在用例内实时复核。 */
@Getter
@AllArgsConstructor
public class ListApiCredentialsRequest {

    /** 可信入口提供的 Operations 账号标识。 */
    private final String actor;

    /** 可信入口提供的 Operations 会话标识。 */
    private final String session;

    /** 已校验并补齐默认值的分页参数；本接口每页最多 100 条。 */
    private final PagingRequestBody paging;

    /** 列表与总数共用的 AND 条件；名称已完成 LIKE 字面量转义。 */
    private final CredentialFilter filter;
}

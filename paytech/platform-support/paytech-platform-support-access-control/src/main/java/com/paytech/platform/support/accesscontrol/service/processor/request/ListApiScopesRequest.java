package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Scope 列表或路由选项的已解析查询。 */
@Getter
@AllArgsConstructor
public class ListApiScopesRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 已校验的分页，最多 100 条。 */
    private final PagingRequestBody paging;

    /** 已转义的 SQL 包含查询条件，可空。 */
    private final String keyword;
}

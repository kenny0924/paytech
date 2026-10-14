package com.paytech.platform.support.integration.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 验证运行列表查询。 */
@Getter
@AllArgsConstructor
public class ListVerifyRunsRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 可选用例编码。 */
    private final String caseCode;

    /** 分页。 */
    private final PagingRequestBody paging;
}

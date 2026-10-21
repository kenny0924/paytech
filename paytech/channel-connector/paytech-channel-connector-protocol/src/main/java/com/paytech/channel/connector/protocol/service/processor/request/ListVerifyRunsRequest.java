package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 验证运行列表查询。 */
@Getter
@AllArgsConstructor
public class ListVerifyRunsRequest {

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 可选用例编码。 */
    private final String caseCode;

    /** 分页。 */
    private final PagingRequestBody paging;
}

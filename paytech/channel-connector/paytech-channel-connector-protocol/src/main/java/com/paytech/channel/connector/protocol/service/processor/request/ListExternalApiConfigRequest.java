package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 外部 API 配置列表查询的已规范化输入。 */
@Getter
@AllArgsConstructor
public class ListExternalApiConfigRequest {

    /** 已校验的分页参数，每页最多 100 条。 */
    private final PagingRequestBody paging;

    /** LIKE 包含模式，通配符已转为文字；null 表示不筛选。 */
    private final String keyword;
}

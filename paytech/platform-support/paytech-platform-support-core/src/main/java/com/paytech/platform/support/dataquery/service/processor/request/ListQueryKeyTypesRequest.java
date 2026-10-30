package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询键类型的内部请求。 */
@Getter
@AllArgsConstructor
public class ListQueryKeyTypesRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 状态筛选；null 表示不限。 */
    private final QueryKeyTypeStatus status;

    /** 编码、名称或分组关键字；null 表示不限。 */
    @Size(max = 64)
    private final String keyword;

    /** 已校验的分页参数。 */
    @NotNull
    private final PagingRequestBody paging;
}

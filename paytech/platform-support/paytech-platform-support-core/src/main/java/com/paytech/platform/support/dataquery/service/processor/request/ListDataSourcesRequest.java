package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询数据源的内部请求。 */
@Getter
@AllArgsConstructor
public class ListDataSourcesRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 状态筛选；null 表示不限（不含 DELETED）。 */
    private final DataSourceStatus status;

    /** 引擎筛选；null 表示不限。 */
    private final DataSourceEngineType engineType;

    /** 编码或名称关键字；null 表示不限。 */
    @Size(max = 64)
    private final String keyword;

    /** 已校验的分页参数。 */
    @NotNull
    private final PagingRequestBody paging;
}

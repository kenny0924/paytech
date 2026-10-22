package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询模板的内部请求。 */
@Getter
@AllArgsConstructor
public class ListQueryTemplatesRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 当前版本数据源筛选；null 表示不限。 */
    private final Long dataSourceId;

    /** 状态筛选；null 表示不限。 */
    private final QueryTemplateStatus status;

    /** 编码或名称关键字；null 表示不限。 */
    @Size(max = 64)
    private final String keyword;

    /** 已校验的分页参数。 */
    @NotNull
    private final PagingRequestBody paging;
}

package com.paytech.platform.support.localization.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布历史分页查询请求。 */
@Getter
@AllArgsConstructor
public class ReleaseListQuery {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 已校验的分页。 */
    @NotNull
    private final PagingRequestBody paging;
}

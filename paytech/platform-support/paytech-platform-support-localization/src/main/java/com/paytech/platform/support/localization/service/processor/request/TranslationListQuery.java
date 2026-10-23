package com.paytech.platform.support.localization.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.localization.core.enums.TranslationState;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 翻译工作台分页查询请求。 */
@Getter
@AllArgsConstructor
public class TranslationListQuery {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 目标语言。 */
    @NotBlank
    private final String locale;

    /** 命名空间编码；为 null 不限。 */
    private final String namespaceCode;

    /** 填写状态；为 null 不限。 */
    private final TranslationState state;

    /** 关键字，已去除首尾空白；为 null 不限。 */
    private final String keyword;

    /** 已校验的分页。 */
    @NotNull
    private final PagingRequestBody paging;
}

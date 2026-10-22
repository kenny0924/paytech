package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 试运行草稿的内部请求。 */
@Getter
@AllArgsConstructor
public class TrialQueryTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 模板内容。 */
    @NotNull
    private final QueryTemplateDraft draft;

    /** 调用方参数 JSON 对象，可为 null。 */
    private final JsonNode params;

    /** OFFSET 页码，可为 null。 */
    private final Integer page;

    /** 页大小，可为 null。 */
    private final Integer size;

    /** 是否返回总数。 */
    private final boolean withTotal;

    /** 游标，可为 null。 */
    @Size(max = 4096)
    private final String cursor;
}

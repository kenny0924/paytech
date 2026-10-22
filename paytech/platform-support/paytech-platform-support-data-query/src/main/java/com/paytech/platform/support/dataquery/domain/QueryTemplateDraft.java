package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 编辑中的模板内容：保存、校验与试运行共用；参数声明保持调用方提交的 JSON。 */
@Getter
@AllArgsConstructor
public final class QueryTemplateDraft {

    /** 数据源 ID。 */
    private final long dataSourceId;

    /** 正文。 */
    private final String body;

    /** 参数声明 JSON 数组；null 视为空数组。 */
    private final JsonNode parameters;

    /** 出参列声明；没有时为空列表。 */
    private final List<QueryColumnRule> columns;

    /** 约束。 */
    private final QueryConstraints constraints;
}

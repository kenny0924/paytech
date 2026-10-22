package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板列表的一项：模板头、当前版本的数据源、分页方式与参数声明。 */
@Getter
@AllArgsConstructor
public final class QueryTemplateSummary {

    /** 模板头（不含当前版本内容）。 */
    private final QueryTemplateDetail template;

    /** 当前版本的数据源编码。 */
    private final String dataSourceCode;

    /** 当前版本的数据源名称。 */
    private final String dataSourceName;

    /** 当前版本的数据源 ID。 */
    private final long dataSourceId;

    /** 当前版本的分页方式。 */
    private final QueryPagingMode pagingMode;

    /** 当前版本的参数声明，按声明顺序；声明无法读取时为空列表。 */
    private final List<QueryParameterDeclaration> parameters;
}

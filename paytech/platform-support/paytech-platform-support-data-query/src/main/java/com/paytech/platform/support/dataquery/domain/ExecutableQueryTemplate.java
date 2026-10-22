package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行接口按编码加载的事实：模板、当前版本与该版本的数据源。 */
@Getter
@AllArgsConstructor
public final class ExecutableQueryTemplate {

    /** 模板与当前版本内容。 */
    private final QueryTemplateDetail template;

    /** 当前版本的数据源；记录缺失时为 null。 */
    private final DataSourceDetail dataSource;
}

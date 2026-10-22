package com.paytech.platform.support.dataquery.engine;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只读校验需要的数据源事实：引擎类型与默认库；不含连接信息。 */
@Getter
@AllArgsConstructor
public final class QuerySource {

    /** 引擎类型，决定方言。 */
    private final DataSourceEngineType engineType;

    /** 默认库；为 null 表示连接整个实例，正文中的表必须写成 库.表。 */
    private final String defaultDatabase;

    /**
     * 是否指定了默认库。
     *
     * @return true 表示未限定库名的表按默认库解析
     */
    public boolean hasDefaultDatabase() {
        return defaultDatabase != null && !defaultDatabase.isBlank();
    }

    /**
     * 对应的方言，由引擎类型选定。
     *
     * @return 方言
     */
    public SqlDialect dialect() {
        return SqlDialect.forEngine(engineType);
    }
}

package com.paytech.platform.support.dataquery.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;

/** 按数据源类型选用引擎实现；首期只有 JDBC。 */
@Component
public class QueryEngines {

    private final List<QueryEngine> engines;

    /**
     * 构造。
     *
     * @param engines 全部引擎实现
     */
    public QueryEngines(List<QueryEngine> engines) {
        this.engines = List.copyOf(engines);
    }

    /**
     * 选用引擎。
     *
     * @param engineType 数据源类型
     * @return 引擎
     * @throws ErrorException 没有实现支持该类型
     */
    public QueryEngine forType(DataSourceEngineType engineType) {
        return engines.stream().filter(engine -> engine.engineTypes().contains(engineType)).findFirst()
            .orElseThrow(() -> new ErrorException("没有支持该数据源类型的查询引擎: " + engineType.getCode()));
    }
}

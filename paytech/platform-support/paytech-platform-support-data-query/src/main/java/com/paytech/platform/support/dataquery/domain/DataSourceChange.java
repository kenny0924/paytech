package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次数据源写入前后的事实，供审计与响应；无变化时 before 与 after 版本相同。 */
@Getter
@AllArgsConstructor
public final class DataSourceChange {

    /** 写入前；新建时为 null。 */
    private final DataSourceDetail before;

    /** 写入后。 */
    private final DataSourceDetail after;

    /**
     * 是否实际写入。
     *
     * @return 新建或版本变化为 true
     */
    public boolean changed() {
        return before == null || before.getVersion() != after.getVersion();
    }
}

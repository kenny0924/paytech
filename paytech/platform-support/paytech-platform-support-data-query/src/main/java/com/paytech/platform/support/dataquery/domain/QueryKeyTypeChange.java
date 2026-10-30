package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次键类型写入前后的事实，供审计与响应；新建时 before 为 null，启停无变化时版本相同。 */
@Getter
@AllArgsConstructor
public final class QueryKeyTypeChange {

    /** 写入前；新建时为 null。 */
    private final QueryKeyTypeDetail before;

    /** 写入后。 */
    private final QueryKeyTypeDetail after;

    /**
     * 是否实际写入。
     *
     * @return 新建或版本变化为 true
     */
    public boolean changed() {
        return before == null || before.getVersion() != after.getVersion();
    }
}

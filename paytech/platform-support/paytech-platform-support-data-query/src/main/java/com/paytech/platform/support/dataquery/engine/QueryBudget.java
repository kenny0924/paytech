package com.paytech.platform.support.dataquery.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次执行的资源上限：主查询与计数共用同一超时预算。 */
@Getter
@AllArgsConstructor
public final class QueryBudget {

    /** 超时秒数，min(模板, 系统上限)，至少 1。 */
    private final int timeoutSeconds;

    /** 结果累计字节上限。 */
    private final long maxResponseBytes;
}

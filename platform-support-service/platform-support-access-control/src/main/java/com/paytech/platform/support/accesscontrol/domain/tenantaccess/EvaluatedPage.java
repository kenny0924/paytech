package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 同一快照内的授权查询结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatedPage<T> {
    /** 复用公共分页结果。 */
    private PagingResponseBody<T> page;

    /** 本次数据库 UTC 计算时刻。 */
    private Instant evaluatedAt;
}

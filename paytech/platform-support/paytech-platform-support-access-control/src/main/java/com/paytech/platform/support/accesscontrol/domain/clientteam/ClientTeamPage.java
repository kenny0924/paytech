package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 团队列表的一页：同一授权范围与筛选条件下的数据库分页结果及计算时刻。 */
@Getter
@AllArgsConstructor
public class ClientTeamPage<T> {
    /** 当前页。 */
    private final List<T> items;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;

    /** 符合条件的总数。 */
    private final long total;

    /** 本次 UTC 计算时刻，用于推导到期状态。 */
    private final Instant asOf;

    /** 当前操作人成员，用于标记“自己”。 */
    private final long selfMemberId;
}

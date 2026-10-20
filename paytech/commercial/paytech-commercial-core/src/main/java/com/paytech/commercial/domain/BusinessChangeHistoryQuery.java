package com.paytech.commercial.domain;

import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按聚合根读取变更快照的查询条件。 */
@Getter
@AllArgsConstructor
public class BusinessChangeHistoryQuery {
    /** 聚合根类型 */
    @NotNull
    private final BusinessChangeRecordAggregateRootType aggregateRootType;

    /** 聚合根 ID */
    @Positive
    private final long aggregateRootId;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}

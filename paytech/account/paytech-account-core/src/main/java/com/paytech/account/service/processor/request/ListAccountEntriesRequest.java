package com.paytech.account.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询一个 Owner 账户的流水。 */
@Getter
@AllArgsConstructor
public class ListAccountEntriesRequest {
    /** Owner 账户主键。 */
    @Positive
    private final long accountId;

    /** 已校验的分页参数。 */
    @NotNull
    private final PagingRequestBody paging;
}

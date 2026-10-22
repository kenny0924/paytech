package com.paytech.channel.connector.service.processor.request;

import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本方案列表查询条件。 */
@Getter
@AllArgsConstructor
public class ListChannelCostPlansRequest {
    /** 渠道筛选，可空 */
    private final Long channelId;

    /** 方案状态筛选，可空 */
    private final ChannelCostPlanStatus status;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}

package com.paytech.channel.connector.service.processor.request;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道列表查询条件。 */
@Getter
@AllArgsConstructor
public class ListChannelsRequest {
    /** 类型筛选，可空 */
    private final ChannelType channelType;

    /** 状态筛选，可空 */
    private final ChannelStatus status;

    /** 编码或名称前缀，可空 */
    private final String keyword;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}

package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** MID 只读查询；不建事务，不调用外部依赖。 */
public interface ChannelMidQueryService {

    /**
     * 分页查询 MID 列表项
     *
     * @param criteria 可空筛选与已校验的分页参数
     * @return 按更新时间、MID 主键降序的分页结果
     */
    PagingResponseBody<ChannelMidSummaryView> listMids(ChannelMidListCriteria criteria);
}

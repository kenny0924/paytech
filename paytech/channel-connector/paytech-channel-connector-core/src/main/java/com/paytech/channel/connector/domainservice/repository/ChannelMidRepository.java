package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

/** ChannelMid 单表只读仓储。 */
public interface ChannelMidRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<ChannelMid> findByAccountIds(List<Long> ids);

    /** 按内部主键读取，未找到返回空。 */
    ChannelMid findById(long id);

    /** 分页读取 MID 列表项（同库联查 Channel 编码、账号名称与能力组计数），按更新时间、主键降序。 */
    PagingResponseBody<ChannelMidSummaryView> findPage(ChannelMidListCriteria criteria);

    // ---- CC-B 追加：MID 管理写入 ----

    /** 写入新 MID；同渠道外部 MID 重复按并发冲突拒绝。 */
    void insert(ChannelMid mid);

    /** 未关闭且仍绑定原账号的 MID 改绑；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateAccount(ChannelMid mid, long toAccountId, Instant now);

    /**
     * 以原对象当前状态为条件变更状态并写入激活 / 关闭时间；行数不为 1 按并发冲突拒绝，成功后同步原对象
     *
     * @param mid 原 MID
     * @param toStatus 目标状态
     * @param activatedAt 变更后的首次激活时间，可空
     * @param closedAt 变更后的关闭时间，可空
     * @param now 本次修改时间
     */
    void updateStatus(ChannelMid mid, ChannelStatus toStatus, Instant activatedAt, Instant closedAt, Instant now);

    /** 统计渠道下未关闭的 MID 数。 */
    long countOpenByChannel(long channelId);

    /** 统计当前绑定该账号且未关闭的 MID 数。 */
    long countOpenByAccount(long channelAccountId);

    /** 按渠道统计全部MID数（含已关闭）；空集合返回空映射，没有记录的渠道不出现。 */
    Map<Long, Long> countByChannels(Collection<Long> channelIds);
}

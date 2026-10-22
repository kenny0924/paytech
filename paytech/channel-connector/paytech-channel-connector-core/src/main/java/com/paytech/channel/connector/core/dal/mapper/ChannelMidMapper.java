package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.ChannelRowCount;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;

/** ChannelMid SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelMidMapper {
    /** 按归属读取记录。 */
    List<ChannelMid> findByAccountIds(@Param("ids") List<Long> ids);

    /** 按主键读取记录。 */
    ChannelMid findById(@Param("id") long id);

    /** 按可空筛选读取 MID 列表项，按更新时间、主键降序分页。 */
    List<ChannelMidSummaryView> selectSummaries(@Param("criteria") ChannelMidListCriteria criteria,
        @Param("offset") int offset, @Param("limit") int limit);

    /** 统计相同筛选条件下的 MID 数。 */
    long countSummaries(@Param("criteria") ChannelMidListCriteria criteria);

    /** CC-B 追加：插入新 MID，调用方已填好主键与时间。 */
    int insert(ChannelMid mid);

    /** 以期望原账号为条件改绑当前技术账号；已关闭的 MID 不改。 */
    int updateAccount(@Param("midId") long midId, @Param("fromAccountId") long fromAccountId,
        @Param("toAccountId") long toAccountId, @Param("now") Instant now);

    /** 以期望原状态为条件变更 MID 状态，并写入激活 / 关闭时间。 */
    int updateStatus(@Param("midId") long midId, @Param("fromStatus") ChannelStatus fromStatus,
        @Param("toStatus") ChannelStatus toStatus, @Param("activatedAt") Instant activatedAt,
        @Param("closedAt") Instant closedAt, @Param("now") Instant now);

    /** 统计渠道下未关闭的 MID 数。 */
    long countOpenByChannel(@Param("channelId") long channelId);

    /** 统计当前绑定该账号且未关闭的 MID 数。 */
    long countOpenByAccount(@Param("channelAccountId") long channelAccountId);

    /** 按渠道分组统计全部 MID 数（含已关闭）。 */
    List<ChannelRowCount> countByChannels(@Param("channelIds") Collection<Long> channelIds);
}

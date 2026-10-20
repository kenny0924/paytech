package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelSummaryView;

/** PaymentChannel SQL 参数绑定，SQL 仅位于 XML。 */
public interface PaymentChannelMapper {
    /** 按归属读取记录。 */
    List<PaymentChannel> findByIds(@Param("ids") List<Long> ids);

    /** 按主键读取记录。 */
    PaymentChannel findById(@Param("id") long id);

    // ---- CC-B 追加：渠道管理写入与分页 ----

    /** 插入一条新渠道。 */
    int insert(PaymentChannel record);

    /** 未关闭渠道改名。 */
    int updateName(@Param("channelId") long channelId, @Param("channelName") String channelName,
        @Param("now") Instant now);

    /** 以期望原状态为条件变更渠道状态。 */
    int updateStatus(@Param("channelId") long channelId, @Param("fromStatus") ChannelStatus fromStatus,
        @Param("toStatus") ChannelStatus toStatus, @Param("now") Instant now);

    /** 按状态与编码 / 名称前缀分页读取列表项，含账号与 MID 计数。 */
    List<ChannelSummaryView> selectSummaries(@Param("status") ChannelStatus status, @Param("keyword") String keyword,
        @Param("offset") int offset, @Param("limit") int limit);

    /** 与列表相同条件的总数。 */
    long countSummaries(@Param("status") ChannelStatus status, @Param("keyword") String keyword);
}

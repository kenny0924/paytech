package com.paytech.channel.connector.protocol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;

/** t_channel_call_grant：调用授权 SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelCallGrantMapper {

    /** 按主键读取。 */
    ChannelCallGrant byId(@Param("callGrantId") long callGrantId);

    /** 按「调用方 × 渠道 × 标准 API」唯一组合读取。 */
    ChannelCallGrant byKey(@Param("callerService") String callerService, @Param("channelId") long channelId,
        @Param("standardApiId") long standardApiId);

    /** 插入一条授权。 */
    int insert(@Param("row") ChannelCallGrant row);

    /** 以原版本与原状态为条件变更状态并递增版本。 */
    int updateStatus(@Param("callGrantId") long callGrantId, @Param("version") int version,
        @Param("fromStatus") ChannelCallGrantStatus fromStatus, @Param("toStatus") ChannelCallGrantStatus toStatus,
        @Param("updatedBy") long updatedBy, @Param("now") Instant now);

    /** 统计调用方对渠道某标准 API 编码的 ACTIVE 授权数（0 或 1）。 */
    long countActive(@Param("callerService") String callerService, @Param("channelId") long channelId,
        @Param("standardApiCode") String standardApiCode);

    /** 统计渠道某标准 API 的 ACTIVE 授权数。 */
    long countActiveByStandardApi(@Param("channelId") long channelId, @Param("standardApiId") long standardApiId);

    /** 按筛选条件分页读取授权及标准 API 编码、名称。 */
    List<ChannelCallGrantView> selectViews(@Param("channelId") Long channelId,
        @Param("callerService") String callerService, @Param("standardApiCode") String standardApiCode,
        @Param("grantStatus") ChannelCallGrantStatus grantStatus, @Param("offset") int offset,
        @Param("limit") int limit);

    /** 与分页相同条件的总数。 */
    long countViews(@Param("channelId") Long channelId, @Param("callerService") String callerService,
        @Param("standardApiCode") String standardApiCode, @Param("grantStatus") ChannelCallGrantStatus grantStatus);
}

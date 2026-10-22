package com.paytech.channel.connector.protocol.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;

/** t_channel：渠道身份 SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelMapper {

    /** 按主键读取。 */
    Channel byId(@Param("channelId") long channelId);

    /** 按不可变编码读取。 */
    Channel byCode(@Param("code") String code);

    /** 在调用方事务中按编码锁定渠道行。 */
    Channel lock(@Param("code") String code);

    /** 在调用方事务中按主键锁定渠道行。 */
    Channel lockById(@Param("channelId") long channelId);

    /** 按主键批量读取，按编码排序。 */
    List<Channel> byIds(@Param("ids") Collection<Long> ids);

    /** 插入新渠道。 */
    int insert(@Param("row") Channel row);

    /** 未关闭渠道修改名称与说明并递增版本。 */
    int updateProfile(@Param("channelId") long channelId, @Param("channelName") String channelName,
        @Param("description") String description, @Param("updatedBy") long updatedBy, @Param("now") Instant now);

    /** 按原版本整组替换出站地址并递增版本。 */
    int updateEndpoints(@Param("channelId") long channelId, @Param("version") int version,
        @Param("endpoints") String endpoints, @Param("updatedBy") long updatedBy, @Param("now") Instant now);

    /** 以期望原状态为条件变更渠道状态。 */
    int updateStatus(@Param("channelId") long channelId, @Param("fromStatus") ChannelStatus fromStatus,
        @Param("toStatus") ChannelStatus toStatus, @Param("updatedBy") long updatedBy, @Param("now") Instant now);

    /** 按类型、状态与编码 / 名称前缀分页读取列表项，含出站地址数与 ACTIVE 授权数。 */
    List<ChannelSummary> selectSummaries(@Param("channelType") ChannelType channelType,
        @Param("status") ChannelStatus status, @Param("keyword") String keyword, @Param("offset") int offset,
        @Param("limit") int limit);

    /** 与列表相同条件的总数。 */
    long countSummaries(@Param("channelType") ChannelType channelType, @Param("status") ChannelStatus status,
        @Param("keyword") String keyword);
}

package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChannelStatus;

/** ChannelAccount SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelAccountMapper {
    /** 按归属读取记录。 */
    List<ChannelAccount> findByEntity(@Param("entityNo") String entityNo);

    /** 按主键读取记录。 */
    ChannelAccount findById(@Param("id") long id);

    // ---- CC-B 追加：技术账号管理写入与分页 ----

    /** 插入一条新账号。 */
    int insert(ChannelAccount record);

    /** 未关闭账号修改名称与备注。 */
    int updateProfile(@Param("channelAccountId") long channelAccountId, @Param("accountName") String accountName,
        @Param("remark") String remark, @Param("now") Instant now);

    /** 未关闭账号写入新的 Vault 配置引用。 */
    int updateVaultToken(@Param("channelAccountId") long channelAccountId, @Param("token") String token,
        @Param("now") Instant now);

    /** 以期望原状态为条件变更账号状态。 */
    int updateStatus(@Param("channelAccountId") long channelAccountId, @Param("fromStatus") ChannelStatus fromStatus,
        @Param("toStatus") ChannelStatus toStatus, @Param("now") Instant now);

    /** 统计渠道下未关闭的账号数。 */
    long countOpenByChannel(@Param("channelId") long channelId);

    /** 按渠道、OE 与状态分页读取，按 update_time、主键降序。 */
    List<ChannelAccount> selectPage(@Param("channelId") Long channelId, @Param("entityNo") String entityNo,
        @Param("status") ChannelStatus status, @Param("offset") int offset, @Param("limit") int limit);

    /** 与分页相同条件的总数。 */
    long countPage(@Param("channelId") Long channelId, @Param("entityNo") String entityNo,
        @Param("status") ChannelStatus status);
}

package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 验证账号持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelVerifyAccountMapper {

    /** 按渠道与不可变编码读取账号。 */
    ChannelVerifyAccount byCode(@Param("channelId") long channelId, @Param("code") String code);

    /** 按内部标识读取账号。 */
    ChannelVerifyAccount byId(@Param("verifyAccountId") long verifyAccountId);

    /** 在调用方事务中锁定账号行，供版本校验及条件更新。 */
    ChannelVerifyAccount lock(@Param("channelId") long channelId, @Param("code") String code);

    /** 读取渠道全部验证账号，按编码排序。 */
    List<ChannelVerifyAccount> allByChannel(@Param("channelId") long channelId);

    /** 插入新账号，渠道内编码由唯一索引保护。 */
    int insert(@Param("row") ChannelVerifyAccount row);

    /** 按原版本更新账号内容，并递增版本。 */
    int update(@Param("row") ChannelVerifyAccount row, @Param("accountName") String accountName, @Param("channelCode") String channelCode, @Param("channelAccountId") String channelAccountId, @Param("entityNo") String entityNo, @Param("parameters") String parameters, @Param("credentials") String credentials, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除账号。 */
    int delete(@Param("row") ChannelVerifyAccount row);
}

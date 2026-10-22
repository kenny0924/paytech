package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import java.time.Instant;
import java.util.List;

/** 验证账号的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelVerifyAccountRepository {

    /** 按渠道与不可变编码读取账号。 */
    ChannelVerifyAccount byCode(long channelId, String code);

    /** 按内部标识读取账号。 */
    ChannelVerifyAccount byId(long verifyAccountId);

    /** 在调用方事务中锁定账号行，供版本校验及条件更新。 */
    ChannelVerifyAccount lock(long channelId, String code);

    /** 读取渠道全部验证账号，按编码排序。 */
    List<ChannelVerifyAccount> allByChannel(long channelId);

    /** 插入新账号，渠道内编码由唯一索引保护。 */
    void insert(ChannelVerifyAccount row);

    /** 按原版本更新账号内容，并递增版本。 */
    void update(ChannelVerifyAccount row, String accountName, String channelCode, String channelAccountId, String entityNo, String parameters, String credentials, long updatedBy, Instant updateTime);

    /** 按原版本删除账号。 */
    void delete(ChannelVerifyAccount row);
}

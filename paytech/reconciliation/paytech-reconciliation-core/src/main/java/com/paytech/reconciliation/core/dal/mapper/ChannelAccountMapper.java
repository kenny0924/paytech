package com.paytech.reconciliation.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.reconciliation.core.dal.dataobject.ChannelAccount;

/** t_channel_account：渠道清算账户只读访问。 */
@Mapper
public interface ChannelAccountMapper {

    /** 按 MID 与币种读取 ACTIVE 清算账户。 */
    ChannelAccount selectActiveByMid(@Param("midId") long midId, @Param("currency") String currency);
}

package com.paytech.reconciliation.domainservice.repository;

import com.paytech.reconciliation.core.dal.dataobject.ChannelAccount;

/** t_channel_account：渠道清算账户读取。 */
public interface ChannelAccountRepository {

    /** 按 MID 与币种读取唯一 ACTIVE 清算账户，不存在或未激活返回空。 */
    ChannelAccount findActiveByMid(long midId, String currency);
}

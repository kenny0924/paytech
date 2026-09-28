package com.paytech.payment.execution.domainservice.repository;

import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;

/** 渠道资金交易引用数据访问 */
public interface ChannelTransactionRefRepository {
    /** 追加真实成功交易引用，不覆盖原资金事实。 */
    void insertRef(ChannelTransactionRef ref);
}

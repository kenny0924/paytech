package com.paytech.payment.execution.domainservice.repository;

import java.util.List;

import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;

/** 渠道资金交易引用数据访问 */
public interface ChannelTransactionRefRepository {
    /** 追加真实成功交易引用，不覆盖原资金事实。 */
    void insertRef(ChannelTransactionRef ref);

    /** 读取原业务动作产生的全部资金引用；没有时返回空列表。 */
    List<ChannelTransactionRef> findByAction(long actionId);
}

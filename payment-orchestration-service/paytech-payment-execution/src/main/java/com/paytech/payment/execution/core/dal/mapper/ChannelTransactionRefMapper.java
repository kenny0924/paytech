package com.paytech.payment.execution.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;

/** 渠道交易引用 SQL 映射 */
@Mapper
public interface ChannelTransactionRefMapper {
    /** 插入不可变资金引用。 */
    int insertRef(ChannelTransactionRef ref);
}

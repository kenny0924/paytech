package com.paytech.payment.execution.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;

/** 渠道交易引用 SQL 映射 */
@Mapper
public interface ChannelTransactionRefMapper {
    /** 插入不可变资金引用。 */
    int insertRef(ChannelTransactionRef ref);

    /** 读取原业务动作产生的全部资金引用。 */
    List<ChannelTransactionRef> selectByAction(@Param("actionId") long actionId);
}

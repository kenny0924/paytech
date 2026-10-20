package com.paytech.account.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.paytech.account.core.dal.dataobject.OwnerAccountNoSequence;

/** t_owner_account_no_sequence：账户号序号分配。 */
@Mapper
public interface OwnerAccountNoSequenceMapper {

    /**
     * 不存在时插入序号 1，存在时序号加一；两种情况都把结果写入本连接的 LAST_INSERT_ID
     *
     * @return 受影响行数（插入 1、更新 2）
     */
    int upsertNext(OwnerAccountNoSequence record);

    /** 读取本连接最近一次 LAST_INSERT_ID；必须与 upsertNext 在同一事务（同一连接）内调用。 */
    long selectLastInsertId();
}

package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_account_no_sequence：按账簿与科目分配账户号序号。 */
@Mapper
public interface AccountNoSequenceMapper {

    /**
     * 分配下一序号：不存在时以 1 插入，存在时原子加一；序号写入本连接的 LAST_INSERT_ID()
     *
     * @return 受影响行数（插入 1，更新 2）
     */
    int allocate(@Param("id") long id, @Param("ledgerBookId") long ledgerBookId,
        @Param("chartAccountId") long chartAccountId, @Param("now") Instant now);

    /** 读取本连接最近一次 allocate 分配的序号。 */
    long selectAllocated();
}

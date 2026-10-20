package com.paytech.account.domainservice.repository;

import com.paytech.account.core.dal.dataobject.OwnerAccountNoSequence;

/** t_owner_account_no_sequence：账户号序号分配。 */
public interface OwnerAccountNoSequenceRepository {

    /**
     * 为序列身份分配下一个序号：首次为 1，此后每次加一；必须在开户事务内调用，回滚时序号一并回滚
     *
     * @param sequence 已填主键、主体、币种、科目代码与时间的序列行
     * @return 本次分配的序号，同时写入 {@code sequence.lastValue}
     */
    long allocate(OwnerAccountNoSequence sequence);
}

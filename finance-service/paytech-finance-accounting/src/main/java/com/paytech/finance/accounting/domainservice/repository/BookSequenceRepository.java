package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;

/** t_BookSequence：账簿分录序号读写。 */
public interface BookSequenceRepository {

    /** 插入账簿序号行。 */
    void insert(BookSequence sequence);

    /** 以排他锁读取账簿序号行；必须在事务内调用。 */
    BookSequence lockByLedgerBook(long ledgerBookId);

    /** 把已锁定的序号推进一位，成功后同步原对象。 */
    void advance(BookSequence sequence);

    /** 以共享锁读取账簿序号行；必须在事务内调用。 */
    BookSequence lockSharedByLedgerBook(long ledgerBookId);
}

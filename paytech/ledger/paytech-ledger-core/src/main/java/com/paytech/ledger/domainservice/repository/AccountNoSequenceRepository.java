package com.paytech.ledger.domainservice.repository;

/** t_account_no_sequence：总账账户号序号。 */
public interface AccountNoSequenceRepository {

    /**
     * 为同账簿同科目的新账户分配下一序号
     * <li>必须在建户的本地事务内调用：序号行锁只串行同账簿同科目的新开户，事务回滚后序号可能留下空洞。</li>
     *
     * @return 从 1 开始的序号
     */
    long next(long ledgerBookId, long chartAccountId);
}

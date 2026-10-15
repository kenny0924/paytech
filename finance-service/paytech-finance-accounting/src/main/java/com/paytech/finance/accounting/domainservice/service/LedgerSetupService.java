package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;
import com.paytech.finance.accounting.core.dal.dataobject.FinanceEntity;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;

/** 财务主体、账簿和统一账户的本地创建。 */
public interface LedgerSetupService {

    /** 保存已验证的财务主体；代码或法律 Party 重复时拒绝。 */
    void createFinanceEntity(FinanceEntity entity);

    /** 在同一事务保存账簿及其唯一序号行；同主体同币种重复时整体拒绝。 */
    void createLedgerBook(LedgerBook book, BookSequence sequence);

    /** 保存已验证的统一账户；账户身份重复时拒绝。 */
    void createAccount(Account account);
}

package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domain.LedgerAccountNo;

/** 财务主体、账簿、统一账户的本地创建与科目维护的本地保存。 */
public interface LedgerSetupService {

    /** 保存已验证的财务主体；代码或法律 Party 重复时拒绝。 */
    void createFinanceEntity(FinanceEntity entity);

    /** 在同一事务保存账簿及其唯一序号行；同主体同币种重复时整体拒绝。 */
    void createLedgerBook(LedgerBook book, BookSequence sequence);

    /**
     * 在一个事务内分配账户号序号并保存已验证的统一账户
     *
     * @param account 待插入账户，账户号在本方法内写入
     * @param accountNo 由账簿、科目与主体确定的账户号前缀
     * @throws com.paytech.foundation.common.error.FailException 账户身份重复时 ACCOUNT_ALREADY_EXISTS，整体回滚
     */
    void createAccount(LedgerAccount account, LedgerAccountNo accountNo);

    /**
     * 在一个事务内写入科目（新增或条件更新，校验行数并同步原对象）并追加变更记录
     *
     * @param context 锁内已校验的科目维护上下文
     */
    void saveChartAccountChange(ChartAccountChangeContext context);
}

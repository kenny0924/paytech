package com.paytech.account.domainservice.service;

import java.util.List;

import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BookConsistencyView;

/** 按 ledger 账簿读取本服务的余额与消费进度；只读 mor_account，不加锁、不开事务。 */
public interface BalanceBookService {

    /**
     * 账簿的余额消费进度（design/04 §6）
     *
     * @param book 已经 ledger-api 确认的账簿
     * @return 已处理事实数、最大 factId 与按 Owner 类型的非零余额账户数
     */
    BookConsistencyView consistency(LedgerBookRef book);

    /**
     * 账簿内全部余额账户
     *
     * @param book 已经 ledger-api 确认的账簿
     * @return 按科目代码、核算对象、余额桶排序的余额
     */
    List<AccountBalance> balances(LedgerBookRef book);

    /** 存在余额的全部账簿主体（日终核对的范围）。 */
    List<BookIdentity> bookIdentities();
}

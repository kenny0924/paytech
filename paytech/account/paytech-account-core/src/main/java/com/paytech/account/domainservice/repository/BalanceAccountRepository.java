package com.paytech.account.domainservice.repository;

import java.util.List;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.BalanceBook;
import com.paytech.account.domain.view.OwnerAccountBalanceView;

/** 余额查询读取：本期从共库的 ledger 账簿与账户投影读取，只读。 */
public interface BalanceAccountRepository {

    /** 按 entityNo 与币种读取 OE 账簿，不存在返回空。 */
    BalanceBook findOperatingEntityBook(String entityNo, String currency);

    /** 读取 Owner 在账簿内的全部账户及其科目组合，按账户主键升序。 */
    List<OwnerAccountBalanceView> findOwnerBalances(long ledgerBookId, OwnerType ownerType, String ownerId);
}

package com.paytech.account.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.BalanceBook;
import com.paytech.account.core.dal.mapper.BalanceAccountMapper;
import com.paytech.account.domain.view.OwnerAccountBalanceView;
import com.paytech.account.domainservice.repository.BalanceAccountRepository;

import lombok.AllArgsConstructor;

/** 余额查询读取的 Mapper 委托。 */
@Repository
@AllArgsConstructor
public class BalanceAccountRepositoryImpl implements BalanceAccountRepository {
    private final BalanceAccountMapper mapper;

    /** 按 entityNo 与币种读取 OE 账簿。 */
    @Override
    public BalanceBook findOperatingEntityBook(String entityNo, String currency) {
        return mapper.selectOperatingEntityBook(entityNo, currency);
    }

    /** 读取 Owner 在账簿内的全部账户。 */
    @Override
    public List<OwnerAccountBalanceView> findOwnerBalances(long ledgerBookId, OwnerType ownerType, String ownerId) {
        return mapper.selectOwnerBalances(ledgerBookId, ownerType, ownerId);
    }
}

package com.paytech.account.domainservice.service.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BookConsistencyView;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.service.BalanceBookService;

import lombok.AllArgsConstructor;

/** 账簿维度的余额与消费进度读取。 */
@Service
@AllArgsConstructor
public class BalanceBookServiceImpl implements BalanceBookService {
    /** consistency 固定列出的 Owner 类型。 */
    static final List<OwnerType> REPORTED_OWNERS = List.of(OwnerType.CLIENT, OwnerType.LOCAL_PARTNER,
        OwnerType.CHANNEL_ACCOUNT, OwnerType.CASH_ACCOUNT);

    private final AccountBalanceRepository accountBalanceRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 已处理事实按账簿主体（OE 按 entityNo，Finance Entity 按 financeEntityId）统计；非零余额按账簿主体统计。 */
    @Override
    public BookConsistencyView consistency(LedgerBookRef book) {
        Map<OwnerType, Long> found = accountBalanceRepository.countNonZeroByOwnerType(book.identity());
        Map<OwnerType, Long> nonZero = new EnumMap<>(OwnerType.class);
        REPORTED_OWNERS.forEach(owner -> nonZero.put(owner, found.getOrDefault(owner, 0L)));
        return new BookConsistencyView(book, balanceAppliedFactRepository.countProcessed(book.identity()),
            balanceAppliedFactRepository.maxProcessedFactId(book.identity()), nonZero);
    }

    /** 账簿主体内全部余额。 */
    @Override
    public List<AccountBalance> balances(LedgerBookRef book) {
        return accountBalanceRepository.findByBook(book.identity());
    }

    /** 存在余额的全部账簿主体。 */
    @Override
    public List<BookIdentity> bookIdentities() {
        return accountBalanceRepository.findBookIdentities();
    }
}

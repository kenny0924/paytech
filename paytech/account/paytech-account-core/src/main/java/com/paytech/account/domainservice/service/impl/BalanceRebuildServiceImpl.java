package com.paytech.account.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.FactRecord;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BalanceRebuildView;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.service.BalanceApplicationService;
import com.paytech.account.domainservice.service.BalanceRebuildService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 余额重建的本地短事务；重新应用复用消费路径的同一应用逻辑。 */
@Service
@AllArgsConstructor
public class BalanceRebuildServiceImpl implements BalanceRebuildService {
    private final TransactionTemplate transactionTemplate;
    private final AccountBalanceRepository accountBalanceRepository;
    private final BalanceApplicationService balanceApplicationService;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 校验账簿类型与事实归属后在一个事务内重建。 */
    @Override
    public BalanceRebuildView rebuild(LedgerBookRef book, List<FactRecord> facts) {
        BookIdentity identity = book.identity();
        if (identity.entityType() != BookEntityType.OPERATING_ENTITY) {
            throw new BizCheckFailException("Owner 余额只在经营主体账簿维护，不能重建: 账簿 " + book.ledgerBookId());
        }
        List<FactRecord> ordered = facts.stream().sorted(Comparator.comparingLong(FactRecord::factId)).toList();
        for (FactRecord fact : ordered) {
            if (!identity.equals(fact.book())) {
                throw new BizCheckFailException("重放事实不属于账簿: 事实 " + fact.factId() + ", 账簿 " + book.ledgerBookId());
            }
        }
        long maxFactId = ordered.isEmpty() ? 0 : ordered.getLast().factId();
        return transactionTemplate.execute(status -> {
            accountBalanceRepository.lockByBook(identity);
            if (balanceAppliedFactRepository.countAfter(identity, maxFactId) > 0) {
                throw new ConcurrentRequestException("重建期间有更晚的经济事实被处理，请重试: 账簿 " + book.ledgerBookId());
            }
            balanceAppliedFactRepository.deleteUpTo(identity, maxFactId);
            accountBalanceRepository.resetBook(identity);
            ordered.forEach(balanceApplicationService::apply);
            return new BalanceRebuildView(book, ordered.size(), ordered.isEmpty() ? null : maxFactId,
                accountBalanceRepository.findByBook(identity).size());
        });
    }
}

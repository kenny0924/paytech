package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.core.dal.dataobject.BalanceDiscrepancy;
import com.paytech.account.core.enums.DiscrepancyStatus;
import com.paytech.account.domain.LedgerAccountTotal;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.account.domainservice.repository.BalanceDiscrepancyRepository;
import com.paytech.account.domainservice.service.BalanceReconciliationService;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/** 日终核对：配对比较在内存完成，差异在一个短事务内写入。 */
@Service
@AllArgsConstructor
public class BalanceReconciliationServiceImpl implements BalanceReconciliationService {
    private static final Logger LOG = LoggerFactory.getLogger(BalanceReconciliationServiceImpl.class);

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final AccountBalanceRepository accountBalanceRepository;
    private final BalanceDiscrepancyRepository balanceDiscrepancyRepository;

    /** 读取本服务余额、配对比较并写入差异。 */
    @Override
    public int reconcile(LocalDate reconDate, LedgerBookRef book, List<LedgerAccountTotal> ledgerTotals) {
        Map<String, BigDecimal> account = new TreeMap<>();
        Map<String, Key> keys = new TreeMap<>();
        for (AccountBalance balance : accountBalanceRepository.findByBook(book.identity())) {
            Key key = new Key(balance.getAccountCode(), balance.getOwnerType(), balance.getOwnerId(),
                balance.getBalanceBucket());
            account.put(key.text(), balance.getNetBalance());
            keys.put(key.text(), key);
        }
        Map<String, BigDecimal> ledger = new TreeMap<>();
        for (LedgerAccountTotal total : ledgerTotals) {
            Key key = new Key(total.accountCode(), total.ownerType(), total.ownerId(), total.bucket());
            ledger.put(key.text(), total.netBalance());
            keys.put(key.text(), key);
        }
        List<BalanceDiscrepancy> discrepancies = new ArrayList<>();
        for (String text : new TreeSet<>(keys.keySet())) {
            BigDecimal accountNet = account.get(text);
            BigDecimal ledgerNet = ledger.get(text);
            BigDecimal difference = zero(accountNet).subtract(zero(ledgerNet)).abs();
            if (difference.signum() != 0) {
                discrepancies.add(discrepancy(reconDate, book, keys.get(text), accountNet, ledgerNet, difference));
            }
        }
        if (!discrepancies.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> discrepancies.forEach(balanceDiscrepancyRepository::upsert));
            discrepancies.forEach(d -> LOG.warn("账户余额与 ledger 分录不一致: 账簿 {}, {}/{}/{}/{}, account={}, ledger={}",
                book.ledgerBookId(), d.getAccountCode(), d.getOwnerType().getCode(), d.getOwnerId(),
                d.getBalanceBucket().getCode(), d.getAccountNetBalance(), d.getLedgerNetBalance()));
        }
        return discrepancies.size();
    }

    /** 构造 OPEN 差异。 */
    BalanceDiscrepancy discrepancy(LocalDate reconDate, LedgerBookRef book, Key key, BigDecimal accountNet,
        BigDecimal ledgerNet, BigDecimal difference) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BalanceDiscrepancy discrepancy = new BalanceDiscrepancy();
        discrepancy.setId(idGenerator.nextId());
        discrepancy.setReconDate(reconDate);
        discrepancy.setLedgerBookId(book.ledgerBookId());
        discrepancy.setBookEntityType(book.identity().entityType());
        discrepancy.setBookEntityId(book.identity().entityId());
        discrepancy.setCurrency(book.identity().currency());
        discrepancy.setAccountCode(key.accountCode());
        discrepancy.setOwnerType(key.ownerType());
        discrepancy.setOwnerId(key.ownerId());
        discrepancy.setBalanceBucket(key.bucket());
        discrepancy.setAccountNetBalance(accountNet);
        discrepancy.setLedgerNetBalance(ledgerNet);
        discrepancy.setDifferenceAmount(difference);
        discrepancy.setStatus(DiscrepancyStatus.OPEN);
        discrepancy.setCreateTime(now);
        discrepancy.setUpdateTime(now);
        return discrepancy;
    }

    /** 缺失一侧按零计。 */
    static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 余额配对键。 */
    record Key(String accountCode, OwnerType ownerType, String ownerId, BalanceBucket bucket) {
        String text() {
            return accountCode + "|" + ownerType.getCode() + "|" + ownerId + "|" + bucket.getCode();
        }
    }
}

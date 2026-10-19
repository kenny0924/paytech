package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.enums.MaturityStatus;
import com.paytech.account.domainservice.process.AccountPostingWriter;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.BalanceMaturityService;

import lombok.AllArgsConstructor;

/**
 * 到期转可用
 * <li>锁顺序：事实处理记录 → 入账流行 → Owner 账户按主键升序，与事实入账一致。</li>
 * <li>净贷方金额 = 该事实普通流水与冲回它们的流水在该账户上的贷方减借方；冲回后净额不为正的账户不移动。</li>
 * <li>内部任务不检查账户状态与 PENDING 余额：移动的是该事实自己记入的金额（设计 8.5、8.7），PENDING 因此为负时由写入步骤记 WARN。</li>
 */
@Service
@AllArgsConstructor
public class BalanceMaturityServiceImpl implements BalanceMaturityService {
    /** 可到期转可用的应付账户类型。 */
    static final Set<String> PAYABLE_CODES = Set.of(OwnerAccountType.CLIENT_PAYABLE.getCode(),
        OwnerAccountType.PARTNER_PAYABLE.getCode());

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AccountPostingWriter accountPostingWriter;
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final BalanceMovementRepository balanceMovementRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 读取到期待移动的事实。 */
    @Override
    public List<BalanceAppliedFact> findDue(Instant asOf, long afterId, int limit) {
        return balanceAppliedFactRepository.findMaturityDue(asOf, afterId, limit);
    }

    /** 一个事务内移动该事实全部到期余额并标记 MATURED。 */
    @Override
    public int mature(long factId) {
        Integer moved = transactionTemplate.execute(status -> {
            BalanceAppliedFact fact = balanceAppliedFactRepository.lockByFactId(factId);
            if (fact == null || fact.getMaturityStatus() != MaturityStatus.PENDING) {
                return 0;
            }
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            int count = 0;
            AccountPostingStream stream = null;
            for (Map.Entry<OwnerAccount, BigDecimal> due : dueAmounts(fact).entrySet()) {
                String sourceRef = fact.getFactId() + ":" + fact.getFactVersion() + ":" + due.getKey().getId();
                if (due.getValue().signum() <= 0
                    || balanceMovementRepository.findBySource(MovementSourceType.FACT_MATURITY, sourceRef) != null) {
                    continue;
                }
                stream = stream != null ? stream : accountPostingWriter.lockStream(AccountEntityType.OPERATING_ENTITY,
                    fact.getEntityNo(), fact.getCurrency(), now);
                moveToAvailable(stream, due.getKey(), due.getValue(), sourceRef, now);
                count++;
            }
            balanceAppliedFactRepository.markMatured(fact, now);
            return count;
        });
        return moved == null ? 0 : moved;
    }

    /** 该事实在每个 PENDING 应付账户上的净贷方金额，按账户主键升序。 */
    Map<OwnerAccount, BigDecimal> dueAmounts(BalanceAppliedFact fact) {
        List<AccountEntry> originals = accountEntryRepository.findByPostingIds(List.of(fact.getPostingId())).stream()
            .filter(entry -> entry.getReversesEntryId() == null).toList();
        List<AccountEntry> all = new ArrayList<>(originals);
        all.addAll(accountEntryRepository.findReversalsOf(originals.stream().map(AccountEntry::getId).toList()));
        Map<Long, BigDecimal> net = new TreeMap<>();
        for (AccountEntry entry : all) {
            BigDecimal signed = entry.getEntrySide() == EntrySide.CREDIT ? entry.getAmount() : entry.getAmount().negate();
            net.merge(entry.getAccountId(), signed, BigDecimal::add);
        }
        Map<OwnerAccount, BigDecimal> due = new LinkedHashMap<>();
        for (OwnerAccount account : ownerAccountRepository.findByIds(new ArrayList<>(net.keySet()))) {
            if (account.getBalanceBucket() == BalanceBucket.PENDING && PAYABLE_CODES.contains(account.getAccountCode())) {
                due.put(account, net.get(account.getId()));
            }
        }
        return due;
    }

    /** 从 PENDING 账户移动到同 Owner 的 AVAILABLE 账户（不存在时开立）。 */
    void moveToAvailable(AccountPostingStream stream, OwnerAccount pending, BigDecimal amount, String sourceRef,
        Instant now) {
        OwnerAccountType type = OwnerAccountType.ofCode(pending.getAccountCode());
        OwnerAccount available = accountPostingWriter.resolveAccount(pending.getEntityType(), pending.getEntityId(),
            pending.getCurrency(), type, pending.getOwnerId(), BalanceBucket.AVAILABLE, now);
        BalanceMovement movement = new BalanceMovement();
        movement.setEntityType(pending.getEntityType());
        movement.setEntityId(pending.getEntityId());
        movement.setCurrency(pending.getCurrency());
        movement.setAccountCode(pending.getAccountCode());
        movement.setOwnerType(pending.getOwnerType());
        movement.setOwnerId(pending.getOwnerId());
        movement.setFromBucket(MovementSourceType.FACT_MATURITY.getFromBucket());
        movement.setToBucket(MovementSourceType.FACT_MATURITY.getToBucket());
        movement.setAmount(amount);
        movement.setSourceType(MovementSourceType.FACT_MATURITY);
        movement.setSourceRef(sourceRef);
        accountPostingWriter.postMovement(stream, movement, pending, available, now);
    }
}

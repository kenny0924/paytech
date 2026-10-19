package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.model.StreamRebuildResult;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.AccountPostingStreamRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.repository.AccountEntryRepository.Totals;
import com.paytech.account.domainservice.service.PostingStreamRebuildService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 按入账流重建余额
 * <li>当前只有分片 0，主体 + 币种下的全部账户都属于这条流；锁流行使重建与入账、桶移动串行。</li>
 */
@Service
@AllArgsConstructor
public class PostingStreamRebuildServiceImpl implements PostingStreamRebuildService {
    private static final Totals NO_ENTRIES = new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 0);

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AccountPostingStreamRepository accountPostingStreamRepository;

    /** 逐流各一个事务重算，汇总结果。 */
    @Override
    public StreamRebuildResult rebuild(List<AccountPostingStream> streams) {
        int accounts = 0;
        long entries = 0;
        long maxSequence = 0;
        for (AccountPostingStream stream : streams) {
            StreamRebuildResult result = transactionTemplate.execute(status -> rebuildStream(stream));
            accounts += result.getAccountCount();
            entries += result.getEntryCount();
            maxSequence = Math.max(maxSequence, result.getMaxPostingSequence());
        }
        return new StreamRebuildResult(accounts, entries, maxSequence);
    }

    /** 锁流行与账户后按流水重算，版本加一。 */
    StreamRebuildResult rebuildStream(AccountPostingStream stream) {
        AccountPostingStream locked = accountPostingStreamRepository.lock(stream.getEntityType(), stream.getEntityId(),
            stream.getCurrency(), stream.getShardNo());
        if (locked == null) {
            throw new ErrorException("入账流在重建时不存在: " + stream.getId());
        }
        List<OwnerAccount> accounts = ownerAccountRepository.lockByEntity(locked.getEntityType(), locked.getEntityId(),
            locked.getCurrency());
        Map<Long, Totals> totals = accountEntryRepository.sumByAccountIds(accounts.stream().map(OwnerAccount::getId)
            .toList());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long entries = 0;
        for (OwnerAccount account : accounts) {
            Totals total = totals.getOrDefault(account.getId(), NO_ENTRIES);
            BigDecimal net = account.getNormalSide() == EntrySide.DEBIT ? total.debitTotal().subtract(total.creditTotal())
                : total.creditTotal().subtract(total.debitTotal());
            ownerAccountRepository.applyChange(account, total.debitTotal(), total.creditTotal(), net, now);
            entries += total.entryCount();
        }
        return new StreamRebuildResult(accounts.size(), entries, locked.getLastSequence());
    }
}

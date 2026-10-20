package com.paytech.account.domainservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.view.AccountEntryView;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.AccountPostingRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.OwnerAccountQueryService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** OwnerAccountQueryService 实现：分页与计数各一条 SQL，流水页的批次、事实与移动批量读取。 */
@Service
@AllArgsConstructor
public class OwnerAccountQueryServiceImpl implements OwnerAccountQueryService {
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AccountPostingRepository accountPostingRepository;
    private final BalanceMovementRepository balanceMovementRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 同筛选条件下分页与计数。 */
    @Override
    public PagingResponseBody<OwnerAccount> pageAccounts(AccountEntityType entityType, String entityId, String currency,
        OwnerType ownerType, String ownerId, String accountCode, BalanceBucket bucket, String accountNo,
        PagingRequestBody paging) {
        List<OwnerAccount> items = ownerAccountRepository.findPage(entityType, entityId, currency, ownerType, ownerId,
            accountCode, bucket, accountNo, paging);
        long total = ownerAccountRepository.count(entityType, entityId, currency, ownerType, ownerId, accountCode, bucket,
            accountNo);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 按主键读取账户。 */
    @Override
    public OwnerAccount findAccount(long accountId) {
        return ownerAccountRepository.findById(accountId);
    }

    /** 流水页及其批次、事实与移动来源。 */
    @Override
    public PagingResponseBody<AccountEntryView> pageEntries(long accountId, PagingRequestBody paging) {
        List<AccountEntry> entries = accountEntryRepository.findPageByAccount(accountId, paging);
        long total = accountEntryRepository.countByAccount(accountId);
        Map<Long, AccountPosting> postings = accountPostingRepository.findByIds(entries.stream()
            .map(AccountEntry::getPostingId).distinct().toList()).stream()
            .collect(Collectors.toMap(AccountPosting::getId, Function.identity()));
        Map<Long, BalanceMovement> movements = balanceMovementRepository.findByIds(postings.values().stream()
            .map(AccountPosting::getMovementId).filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(BalanceMovement::getId, Function.identity()));
        Map<Long, BalanceAppliedFact> facts = balanceAppliedFactRepository.findByFactIds(postings.values().stream()
            .map(AccountPosting::getFactId).filter(Objects::nonNull).distinct().toList()).stream()
            .filter(fact -> fact.getPostingId() != null)
            .collect(Collectors.toMap(BalanceAppliedFact::getPostingId, Function.identity()));
        List<AccountEntryView> items = entries.stream().map(entry -> view(entry, postings, facts, movements)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 组合一条流水；所属批次缺失为数据不变量被破坏。 */
    static AccountEntryView view(AccountEntry entry, Map<Long, AccountPosting> postings,
        Map<Long, BalanceAppliedFact> facts, Map<Long, BalanceMovement> movements) {
        AccountPosting posting = postings.get(entry.getPostingId());
        if (posting == null) {
            throw new ErrorException("账户流水缺少入账批次: " + entry.getId());
        }
        return new AccountEntryView(entry, posting, posting.getFactId() == null ? null : facts.get(posting.getId()),
            posting.getMovementId() == null ? null : movements.get(posting.getMovementId()));
    }
}

package com.paytech.account.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domain.view.PostingProgressView;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.AccountPostingRepository;
import com.paytech.account.domainservice.repository.AccountPostingStreamRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository.Progress;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.AccountPostingQueryService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** AccountPostingQueryService 实现：按批次组合流水、账户、事实与移动，各表一次批量读取。 */
@Service
@AllArgsConstructor
public class AccountPostingQueryServiceImpl implements AccountPostingQueryService {
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AccountPostingRepository accountPostingRepository;
    private final BalanceMovementRepository balanceMovementRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;
    private final AccountPostingStreamRepository accountPostingStreamRepository;

    /** OE 主体只看自身流；FE 主体再加引用它的事实所在的 OE 流。 */
    @Override
    public PostingProgressView progress(AccountEntityType entityType, String entityId, String currency) {
        List<AccountPostingStream> streams = new ArrayList<>(accountPostingStreamRepository.findByEntity(entityType,
            entityId, currency));
        if (entityType == AccountEntityType.OPERATING_ENTITY) {
            Progress progress = balanceAppliedFactRepository.operatingEntityProgress(entityId, currency);
            return new PostingProgressView(entityType, entityId, currency, progress.factCount(), progress.maxFactId(),
                streams);
        }
        long financeEntityId = Long.parseLong(entityId);
        Progress progress = balanceAppliedFactRepository.financeEntityProgress(financeEntityId, currency);
        streams.addAll(accountPostingStreamRepository.findByEntities(AccountEntityType.OPERATING_ENTITY,
            balanceAppliedFactRepository.findEntityNosByFinanceEntity(financeEntityId, currency), currency));
        streams.sort(Comparator.comparing((AccountPostingStream stream) -> stream.getEntityType().getCode())
            .thenComparing(AccountPostingStream::getEntityId).thenComparing(AccountPostingStream::getShardNo));
        return new PostingProgressView(entityType, entityId, currency, progress.factCount(), progress.maxFactId(),
            streams);
    }

    /** 一页批次及其组合数据。 */
    @Override
    public List<AccountPostingView> findPostings(AccountEntityType entityType, String entityId, String currency,
        int shardNo, long fromSequence, int limit) {
        return views(accountPostingRepository.findFromSequence(entityType, entityId, currency, shardNo, fromSequence,
            limit));
    }

    /** 按流与序号读取。 */
    @Override
    public AccountPostingView findPosting(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long postingSequence) {
        AccountPosting posting = accountPostingRepository.findBySequence(entityType, entityId, currency, shardNo,
            postingSequence);
        return posting == null ? null : views(List.of(posting)).getFirst();
    }

    /** 按主键读取。 */
    @Override
    public AccountPostingView findPosting(long postingId) {
        AccountPosting posting = accountPostingRepository.findById(postingId);
        return posting == null ? null : views(List.of(posting)).getFirst();
    }

    /** 首次发布确认时写入。 */
    @Override
    public void markPublished(AccountPosting posting, Instant publishedTime) {
        accountPostingRepository.markPublished(posting, publishedTime);
    }

    /** 按原顺序组合批次视图；流水引用的账户、事实批次的处理记录、移动批次的移动记录缺失均为数据不变量被破坏。 */
    List<AccountPostingView> views(List<AccountPosting> postings) {
        if (postings.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AccountEntry>> entries = accountEntryRepository.findByPostingIds(ids(postings, AccountPosting::getId))
            .stream().collect(Collectors.groupingBy(AccountEntry::getPostingId));
        Map<Long, OwnerAccount> accounts = ownerAccountRepository.findByIds(entries.values().stream().flatMap(List::stream)
            .map(AccountEntry::getAccountId).distinct().toList()).stream()
            .collect(Collectors.toMap(OwnerAccount::getId, Function.identity()));
        Map<Long, BalanceAppliedFact> facts = balanceAppliedFactRepository.findByFactIds(ids(postings,
            AccountPosting::getFactId)).stream().filter(fact -> fact.getPostingId() != null)
            .collect(Collectors.toMap(BalanceAppliedFact::getPostingId, Function.identity()));
        Map<Long, BalanceMovement> movements = balanceMovementRepository.findByIds(ids(postings,
            AccountPosting::getMovementId)).stream().collect(Collectors.toMap(BalanceMovement::getId, Function.identity()));
        return postings.stream().map(posting -> view(posting, entries, accounts, facts, movements)).toList();
    }

    /** 组合一个批次。 */
    static AccountPostingView view(AccountPosting posting, Map<Long, List<AccountEntry>> entries,
        Map<Long, OwnerAccount> accounts, Map<Long, BalanceAppliedFact> facts, Map<Long, BalanceMovement> movements) {
        BalanceAppliedFact fact = posting.getFactId() == null ? null : facts.get(posting.getId());
        BalanceMovement movement = posting.getMovementId() == null ? null : movements.get(posting.getMovementId());
        if (fact == null && movement == null) {
            throw new ErrorException("入账批次缺少来源记录: " + posting.getId());
        }
        List<AccountPostingView.Line> lines = entries.getOrDefault(posting.getId(), List.of()).stream()
            .sorted(Comparator.comparing(AccountEntry::getLineNo))
            .map(entry -> new AccountPostingView.Line(entry, account(accounts, entry))).toList();
        return new AccountPostingView(posting, fact, movement, lines);
    }

    /** 流水引用的 Owner 账户；缺失为数据不变量被破坏。 */
    static OwnerAccount account(Map<Long, OwnerAccount> accounts, AccountEntry entry) {
        OwnerAccount account = accounts.get(entry.getAccountId());
        if (account == null) {
            throw new ErrorException("账户流水引用的 Owner 账户不存在: " + entry.getId());
        }
        return account;
    }

    /** 批次中非空的关联主键，去重。 */
    static List<Long> ids(List<AccountPosting> postings, Function<AccountPosting, Long> key) {
        return postings.stream().map(key).filter(Objects::nonNull).distinct().toList();
    }
}

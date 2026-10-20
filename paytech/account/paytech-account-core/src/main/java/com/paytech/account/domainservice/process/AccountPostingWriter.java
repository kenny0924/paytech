package com.paytech.account.domainservice.process;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.dal.dataobject.OwnerAccountNoSequence;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.core.enums.MovementStatus;
import com.paytech.account.domain.PostingLine;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.AccountPostingRepository;
import com.paytech.account.domainservice.repository.AccountPostingStreamRepository;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.account.domainservice.repository.OwnerAccountNoSequenceRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.service.handler.AccountPostedHandler;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 入账批次的写入步骤：事实入账、桶移动与到期转可用三个 Service 共用（technique 2.1「链路与事务」）
 * <li>只在调用方已开启的 mor_account 本地事务内执行，不访问外部依赖；AccountPosted 以补偿事件登记，提交后由 Handler 发布。</li>
 * <li>锁顺序由调用方保证为：已处理事实行（事实入账、到期）→ 入账流行 → Owner 账户按主键升序；本类只负责后两步。</li>
 * <li>入账流当前固定分片 0：同一主体 + 币种内全部入账串行分配连续序号（设计 8.1）。</li>
 * <li>新开户在同一事务内按主体 + 币种 + 科目代码取账户号序号；序列键是入账流键的细分，调用方已持有流行锁，序列行锁不引入新的锁顺序。</li>
 */
@Component
@AllArgsConstructor
public class AccountPostingWriter {
    /** 当前唯一分片。 */
    public static final int SHARD_NO = 0;

    private static final Logger LOG = LoggerFactory.getLogger(AccountPostingWriter.class);

    private final IdGenerator idGenerator;
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AccountPostingRepository accountPostingRepository;
    private final BalanceMovementRepository balanceMovementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final AccountPostingStreamRepository accountPostingStreamRepository;
    private final OwnerAccountNoSequenceRepository ownerAccountNoSequenceRepository;

    /**
     * 锁定入账流，不存在时创建
     * <li>并发首次创建由唯一键拦截，转为并发冲突后由调用方重试。</li>
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param now 当前时间
     * @return 已持有排他锁的流
     */
    public AccountPostingStream lockStream(AccountEntityType entityType, String entityId, String currency, Instant now) {
        requireTransaction();
        AccountPostingStream stream = accountPostingStreamRepository.lock(entityType, entityId, currency, SHARD_NO);
        if (stream != null) {
            return stream;
        }
        stream = new AccountPostingStream();
        stream.setId(idGenerator.nextId());
        stream.setEntityType(entityType);
        stream.setEntityId(entityId);
        stream.setCurrency(currency);
        stream.setShardNo(SHARD_NO);
        stream.setLastSequence(0L);
        stream.setCreateTime(now);
        stream.setUpdateTime(now);
        accountPostingStreamRepository.insert(stream);
        return accountPostingStreamRepository.lock(entityType, entityId, currency, SHARD_NO);
    }

    /**
     * 读取 Owner 账户，不存在时按账户类型目录开立 ACTIVE 零余额账户；已存在的账户（含账户号）不做任何修改
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param accountType 账户类型
     * @param ownerId 核算对象标识
     * @param bucket 余额桶，必须在账户类型允许的集合内
     * @param now 当前时间
     * @return 账户
     */
    public OwnerAccount resolveAccount(AccountEntityType entityType, String entityId, String currency,
        OwnerAccountType accountType, String ownerId, BalanceBucket bucket, Instant now) {
        if (!accountType.allows(accountType.getOwnerType(), bucket)) {
            throw new ErrorException("Owner 账户类型不允许该余额桶: " + accountType.getCode() + "/" + bucket.getCode());
        }
        OwnerAccount account = ownerAccountRepository.findByIdentity(entityType, entityId, currency,
            accountType.getCode(), accountType.getOwnerType(), ownerId, bucket);
        if (account != null) {
            return account;
        }
        return openAccount(entityType, entityId, currency, accountType, ownerId, bucket, now);
    }

    /** 开立零余额账户：在调用方事务内取账户号序号后插入；并发开立同一账户由唯一键转为并发冲突，序号随事务回滚。 */
    OwnerAccount openAccount(AccountEntityType entityType, String entityId, String currency, OwnerAccountType accountType,
        String ownerId, BalanceBucket bucket, Instant now) {
        requireTransaction();
        OwnerAccount account = new OwnerAccount();
        account.setId(idGenerator.nextId());
        account.setAccountNo(accountNo(entityType, entityId, currency, accountType,
            allocateAccountNoSequence(entityType, entityId, currency, accountType, now)));
        account.setEntityType(entityType);
        account.setEntityId(entityId);
        account.setCurrency(currency);
        account.setAccountCode(accountType.getCode());
        account.setOwnerType(accountType.getOwnerType());
        account.setOwnerId(ownerId);
        account.setBalanceBucket(bucket);
        account.setNormalSide(accountType.getNormalSide());
        account.setDebitTotal(BigDecimal.ZERO);
        account.setCreditTotal(BigDecimal.ZERO);
        account.setNetBalance(BigDecimal.ZERO);
        account.setBalanceVersion(0L);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        ownerAccountRepository.insert(account);
        return account;
    }

    /** 主体 + 币种 + 科目代码的下一个账户号序号。 */
    long allocateAccountNoSequence(AccountEntityType entityType, String entityId, String currency,
        OwnerAccountType accountType, Instant now) {
        OwnerAccountNoSequence sequence = new OwnerAccountNoSequence();
        sequence.setId(idGenerator.nextId());
        sequence.setEntityType(entityType);
        sequence.setEntityId(entityId);
        sequence.setCurrency(currency);
        sequence.setAccountCode(accountType.getCode());
        sequence.setCreateTime(now);
        sequence.setUpdateTime(now);
        return ownerAccountNoSequenceRepository.allocate(sequence);
    }

    /**
     * 组装可读账户号 {主体段}-{币种}-{科目编号}-S{序号}
     * <li>主体段：OE 为 entityNo，Finance Entity 为 FE 加主体主键；序号至少 6 位、不足补零（2026-10-19 科目分级与可读账户号决定第 7 条）。</li>
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param accountType 账户类型，提供科目编号
     * @param sequence 序号，从 1 起
     * @return 账户号，例如 AE001-USD-22010101-S000001
     */
    public static String accountNo(AccountEntityType entityType, String entityId, String currency,
        OwnerAccountType accountType, long sequence) {
        String entitySegment = entityType == AccountEntityType.FINANCE_ENTITY
            ? entityType.getAggregatePrefix() + entityId : entityId;
        return String.format("%s-%s-%s-S%06d", entitySegment, currency, accountType.getChartAccountNo(), sequence);
    }

    /**
     * 写一个计费事实批次：分配序号、写批次与流水、更新余额并登记 AccountPosted
     *
     * @param stream 已锁定的入账流
     * @param postingId 预先分配的批次主键（已处理事实记录引用它）
     * @param factId 计费事实 ID
     * @param factVersion 计费事实版本
     * @param lines 流水，可为空列表（0 流水批次）
     * @param now 入账时间
     * @return 已写入的批次
     */
    public AccountPosting postFact(AccountPostingStream stream, long postingId, long factId, long factVersion,
        List<PostingLine> lines, Instant now) {
        AccountPosting posting = newPosting(postingId, PostingSourceType.ECONOMIC_FACT);
        posting.setFactId(factId);
        posting.setFactVersion(factVersion);
        return post(stream, posting, lines, now);
    }

    /**
     * 写一次桶移动：移动记录、BALANCE_MOVEMENT 批次与转出 / 转入两条流水
     * <li>转出桶记账户正常方向的反方向，转入桶记正常方向；余额与状态检查由调用方在锁定账户后完成。</li>
     *
     * @param stream 已锁定的入账流
     * @param movement 已填好来源、账户身份、桶与金额的移动记录
     * @param from 转出账户
     * @param to 转入账户
     * @param now 入账时间
     * @return 已写入的批次
     */
    public AccountPosting postMovement(AccountPostingStream stream, BalanceMovement movement, OwnerAccount from,
        OwnerAccount to, Instant now) {
        AccountPosting posting = newPosting(idGenerator.nextId(), PostingSourceType.BALANCE_MOVEMENT);
        movement.setId(idGenerator.nextId());
        movement.setPostingId(posting.getId());
        movement.setMovementStatus(MovementStatus.APPLIED);
        movement.setCreateTime(now);
        movement.setUpdateTime(now);
        balanceMovementRepository.insert(movement);
        posting.setMovementId(movement.getId());
        EntrySide toSide = to.getNormalSide();
        EntrySide fromSide = toSide == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
        return post(stream, posting, List.of(new PostingLine(from, fromSide, movement.getAmount(), null, null),
            new PostingLine(to, toSide, movement.getAmount(), null, null)), now);
    }

    /** 分配序号、写批次与流水、按账户主键升序更新余额并登记发布任务。 */
    AccountPosting post(AccountPostingStream stream, AccountPosting posting, List<PostingLine> lines, Instant now) {
        requireTransaction();
        accountPostingStreamRepository.advance(stream, now);
        posting.setEntityType(stream.getEntityType());
        posting.setEntityId(stream.getEntityId());
        posting.setCurrency(stream.getCurrency());
        posting.setShardNo(stream.getShardNo());
        posting.setPostingSequence(stream.getLastSequence());
        posting.setEntryCount(lines.size());
        posting.setPostedTime(now);
        posting.setCreateTime(now);
        accountPostingRepository.insert(posting);
        accountEntryRepository.insertAll(entries(posting, lines, now));
        applyBalances(lines, now);
        compensationEventRegistrar.register(AccountPostedHandler.buildEvent(posting.getEntityType(),
            posting.getEntityId(), posting.getCurrency(), posting.getShardNo(), posting.getPostingSequence()));
        return posting;
    }

    /** 按行号从 1 起生成流水。 */
    List<AccountEntry> entries(AccountPosting posting, List<PostingLine> lines, Instant now) {
        List<AccountEntry> entries = new ArrayList<>();
        for (PostingLine line : lines) {
            AccountEntry entry = new AccountEntry();
            entry.setId(idGenerator.nextId());
            entry.setPostingId(posting.getId());
            entry.setLineNo(entries.size() + 1);
            entry.setAccountId(line.account().getId());
            entry.setEntrySide(line.entrySide());
            entry.setAmount(line.amount());
            entry.setFactLineNo(line.factLineNo());
            entry.setReversesEntryId(line.reversesEntryId());
            entry.setCreateTime(now);
            entries.add(entry);
        }
        return entries;
    }

    /** 汇总每个账户的借贷增量，按主键升序锁定后以版本 CAS 写入；净额为负时记 WARN。 */
    void applyBalances(List<PostingLine> lines, Instant now) {
        Map<Long, BigDecimal[]> deltas = new TreeMap<>();
        for (PostingLine line : lines) {
            BigDecimal[] delta = deltas.computeIfAbsent(line.account().getId(), id -> new BigDecimal[] {BigDecimal.ZERO,
                BigDecimal.ZERO});
            int index = line.entrySide() == EntrySide.DEBIT ? 0 : 1;
            delta[index] = delta[index].add(line.amount());
        }
        List<OwnerAccount> locked = ownerAccountRepository.lockByIds(new ArrayList<>(deltas.keySet()));
        if (locked.size() != deltas.size()) {
            throw new ErrorException("入账流水引用了不存在的 Owner 账户");
        }
        for (OwnerAccount account : locked) {
            BigDecimal[] delta = deltas.get(account.getId());
            BigDecimal debit = account.getDebitTotal().add(delta[0]);
            BigDecimal credit = account.getCreditTotal().add(delta[1]);
            BigDecimal net = account.getNormalSide() == EntrySide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
            ownerAccountRepository.applyChange(account, debit, credit, net, now);
            if (net.signum() < 0) {
                LOG.warn("Owner 账户净余额为负: account {}, {}/{}/{}/{}", account.getId(), account.getAccountCode(),
                    account.getOwnerType().getCode(), account.getOwnerId(), account.getBalanceBucket().getCode());
            }
        }
    }

    /** 新批次：主键与来源类型，其余字段在分配序号后填写。 */
    static AccountPosting newPosting(long postingId, PostingSourceType sourceType) {
        AccountPosting posting = new AccountPosting();
        posting.setId(postingId);
        posting.setSourceType(sourceType);
        return posting;
    }

    /** 调用方必须已开启本地事务。 */
    static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("入账必须在调用方事务内执行");
        }
    }
}

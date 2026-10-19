package com.paytech.account.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.MaturityStatus;
import com.paytech.account.domain.BalanceEffect;
import com.paytech.account.domain.FactRecord;
import com.paytech.account.domain.PostingLine;
import com.paytech.account.domainservice.process.AccountPostingWriter;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.BalanceApplicationService;
import com.paytech.account.domainservice.validator.FactBalanceMappingRule;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 计费事实入账
 * <li>锁顺序：① 本事实处理记录（fact_id 行锁）→ 被替代事实处理记录 → ② 事实主体 + 币种的入账流行 → ③ Owner 账户按主键升序 CAS。</li>
 * <li>不变量：每个 Owner 账户的借贷累计等于其全部流水之和；冲回只冲被替代事实自身的普通行（不冲它的冲回行），被冲回事实状态改为 REVERSED。</li>
 * <li>事实入账不检查账户状态（设计 8.7）；CLOSED 账户入账时记 WARN。</li>
 */
@Service
@AllArgsConstructor
public class BalanceApplicationServiceImpl implements BalanceApplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(BalanceApplicationServiceImpl.class);

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountPostingWriter accountPostingWriter;
    private final AccountEntryRepository accountEntryRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 幂等入账一条计费事实。 */
    @Override
    public AppliedFactStatus apply(FactRecord fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("计费事实入账必须在调用方事务内执行");
        }
        BalanceAppliedFact existing = balanceAppliedFactRepository.lockByFactId(fact.factId());
        if (existing != null && existing.getApplyStatus() != AppliedFactStatus.PENDING_SUPERSEDED) {
            return existing.getApplyStatus();
        }
        Instant now = now();
        BalanceAppliedFact prior = lockPrior(fact);
        AccountPostingStream stream = accountPostingWriter.lockStream(AccountEntityType.OPERATING_ENTITY, fact.entityNo(),
            fact.currency(), now);
        List<PostingLine> lines = new ArrayList<>(reversalLines(prior));
        List<BalanceEffect> effects = existing == null ? FactBalanceMappingRule.effects(fact) : List.of();
        lines.addAll(effectLines(fact, effects, now));
        long postingId = idGenerator.nextId();
        accountPostingWriter.postFact(stream, postingId, fact.factId(), fact.factVersion(), lines, now);
        linkPrior(fact, prior, now);
        if (existing != null) {
            skip(existing, fact, postingId, now);
            return AppliedFactStatus.SKIPPED;
        }
        balanceAppliedFactRepository.insert(newAppliedFact(fact, postingId, maturityStatus(fact, effects), now));
        return AppliedFactStatus.APPLIED;
    }

    /**
     * 锁定被替代事实的处理记录
     * <li>被替代事实已入账时，必须与本事实同一主体与币种，冲回才落在同一入账流。</li>
     *
     * @return 被替代事实的处理记录；本事实不替代任何事实或被替代事实尚未到达时为空
     */
    BalanceAppliedFact lockPrior(FactRecord fact) {
        if (fact.supersedesFactId() == null) {
            return null;
        }
        BalanceAppliedFact prior = balanceAppliedFactRepository.lockByFactId(fact.supersedesFactId());
        if (prior != null && prior.getApplyStatus() == AppliedFactStatus.APPLIED
            && (!Objects.equals(prior.getEntityNo(), fact.entityNo()) || !Objects.equals(prior.getCurrency(), fact.currency()))) {
            throw new BizCheckFailException("替代事实与被替代事实的主体或币种不同: " + fact.factId() + " 替代 "
                + fact.supersedesFactId());
        }
        return prior;
    }

    /** 被替代事实已入账时，按其普通流水生成方向相反、同账户同金额的冲回行。 */
    List<PostingLine> reversalLines(BalanceAppliedFact prior) {
        if (prior == null || prior.getApplyStatus() != AppliedFactStatus.APPLIED) {
            return List.of();
        }
        List<AccountEntry> originals = accountEntryRepository.findByPostingIds(List.of(prior.getPostingId())).stream()
            .filter(entry -> entry.getReversesEntryId() == null).toList();
        Map<Long, OwnerAccount> accounts = new HashMap<>();
        ownerAccountRepository.findByIds(originals.stream().map(AccountEntry::getAccountId).distinct().toList())
            .forEach(account -> accounts.put(account.getId(), account));
        return originals.stream().map(entry -> new PostingLine(accounts.get(entry.getAccountId()),
            FactBalanceMappingRule.opposite(entry.getEntrySide()), entry.getAmount(), entry.getFactLineNo(), entry.getId()))
            .toList();
    }

    /** 映射出的流水：解析或开立 Owner 账户，CLOSED 账户记 WARN 后照常入账。 */
    List<PostingLine> effectLines(FactRecord fact, List<BalanceEffect> effects, Instant now) {
        Map<String, OwnerAccount> resolved = new LinkedHashMap<>();
        List<PostingLine> lines = new ArrayList<>();
        for (BalanceEffect effect : effects) {
            OwnerAccount account = resolved.computeIfAbsent(effect.accountType().getCode() + "|" + effect.ownerId() + "|"
                + effect.bucket().getCode(), key -> accountPostingWriter.resolveAccount(AccountEntityType.OPERATING_ENTITY,
                    fact.entityNo(), fact.currency(), effect.accountType(), effect.ownerId(), effect.bucket(), now));
            if (account.getAccountStatus() == AccountStatus.CLOSED) {
                LOG.warn("计费事实入账到已关闭的 Owner 账户: fact {}, account {}", fact.factId(), account.getId());
            }
            lines.add(new PostingLine(account, effect.entrySide(), effect.amount(), effect.factLineNo(), null));
        }
        return lines;
    }

    /**
     * 记录替代关系
     * <li>被替代事实已入账：标记 REVERSED（冲回行已写入本批次）。</li>
     * <li>被替代事实尚未到达：以本事实版本减一预登记为 PENDING_SUPERSEDED，到达时只生成 SKIPPED 批次。</li>
     * <li>被替代事实已冲回、跳过或预登记：只补记替代关系。</li>
     */
    void linkPrior(FactRecord fact, BalanceAppliedFact prior, Instant now) {
        if (fact.supersedesFactId() == null) {
            return;
        }
        if (prior == null) {
            BalanceAppliedFact placeholder = newAppliedFact(fact, null, MaturityStatus.NOT_APPLICABLE, now);
            placeholder.setFactId(fact.supersedesFactId());
            placeholder.setFactVersion(fact.factVersion() - 1);
            placeholder.setFactType(null);
            placeholder.setSupersedesFactId(null);
            placeholder.setSupersededByFactId(fact.factId());
            placeholder.setApplyStatus(AppliedFactStatus.PENDING_SUPERSEDED);
            placeholder.setBalanceAvailableTime(null);
            placeholder.setAppliedTime(null);
            balanceAppliedFactRepository.insert(placeholder);
            return;
        }
        AppliedFactStatus next = prior.getApplyStatus() == AppliedFactStatus.APPLIED ? AppliedFactStatus.REVERSED
            : prior.getApplyStatus();
        prior.setSupersededByFactId(fact.factId());
        balanceAppliedFactRepository.updateStatus(prior, next, now);
    }

    /**
     * 预登记的被替代事实到达：补全处理信息并标记 SKIPPED，本批次不含它自己的流水
     * <li>它若还替代更早且已入账的事实，冲回行仍写在本批次（三个以上版本乱序到达）；ledger 照抄 SKIPPED 批次中的流水行，不应用规则行
     * （2026-10-19 主会话确认）。</li>
     */
    void skip(BalanceAppliedFact placeholder, FactRecord fact, long postingId, Instant now) {
        placeholder.setFactType(fact.factType());
        placeholder.setSupersedesFactId(fact.supersedesFactId());
        placeholder.setPostingId(postingId);
        placeholder.setBalanceAvailableTime(fact.balanceAvailableTime());
        placeholder.setMaturityStatus(MaturityStatus.NOT_APPLICABLE);
        placeholder.setAppliedTime(now);
        balanceAppliedFactRepository.updateStatus(placeholder, AppliedFactStatus.SKIPPED, now);
    }

    /** 有到期时间且有 PENDING 贷方流水时等待到期任务，否则不适用。 */
    static MaturityStatus maturityStatus(FactRecord fact, List<BalanceEffect> effects) {
        boolean pendingCredit = effects.stream().anyMatch(effect -> effect.bucket() == BalanceBucket.PENDING
            && effect.entrySide() == EntrySide.CREDIT);
        return fact.balanceAvailableTime() != null && pendingCredit ? MaturityStatus.PENDING : MaturityStatus.NOT_APPLICABLE;
    }

    /** 已入账的处理记录；事实头主体与 Finance Entity 用于进度统计。 */
    BalanceAppliedFact newAppliedFact(FactRecord fact, Long postingId, MaturityStatus maturityStatus, Instant now) {
        BalanceAppliedFact applied = new BalanceAppliedFact();
        applied.setId(idGenerator.nextId());
        applied.setFactId(fact.factId());
        applied.setFactVersion(fact.factVersion());
        applied.setFactType(fact.factType());
        applied.setSupersedesFactId(fact.supersedesFactId());
        applied.setEntityNo(fact.entityNo());
        applied.setFinanceEntityId(fact.financeEntityId());
        applied.setCurrency(fact.currency());
        applied.setApplyStatus(AppliedFactStatus.APPLIED);
        applied.setPostingId(postingId);
        applied.setBalanceAvailableTime(fact.balanceAvailableTime());
        applied.setMaturityStatus(maturityStatus);
        applied.setAppliedTime(now);
        applied.setCreateTime(now);
        applied.setUpdateTime(now);
        return applied;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

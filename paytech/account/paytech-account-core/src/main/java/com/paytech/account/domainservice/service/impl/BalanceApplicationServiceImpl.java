package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.core.dal.dataobject.BalanceAppliedEntry;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.BalanceStatus;
import com.paytech.account.core.enums.EntrySide;
import com.paytech.account.core.enums.NormalSide;
import com.paytech.account.domain.BalanceEffect;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.FactRecord;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.account.domainservice.service.BalanceApplicationService;
import com.paytech.account.domainservice.validator.FactBalanceMappingRule;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 经济事实到余额的应用
 * <li>顺序：锁定本事实处理记录 → 已处理即返回 → 处理替代关系（冲回或预登记被替代事实）→ 预登记的本事实标记 SKIPPED，否则映射余额变化、
 * 登记 APPLIED 并按余额主键升序以版本 CAS 写入。</li>
 * <li>不变量：每个余额的借贷累计等于状态为 APPLIED 的事实的余额变化之和；冲回不新增变化行，被冲回事实状态改为 REVERSED。</li>
 * <li>并发：同一余额被两个分区同时更新或创建时，以并发冲突失败并由消费重投重试；处理记录以 fact_id 行锁与唯一键串行。</li>
 */
@Service
@AllArgsConstructor
public class BalanceApplicationServiceImpl implements BalanceApplicationService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountBalanceRepository accountBalanceRepository;
    private final BalanceAppliedFactRepository balanceAppliedFactRepository;

    /** 幂等应用一条经济事实。 */
    @Override
    public AppliedFactStatus apply(FactRecord fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("余额应用必须在调用方事务内执行");
        }
        BalanceAppliedFact existing = balanceAppliedFactRepository.lockByFactId(fact.factId());
        if (existing != null && existing.getApplyStatus() != AppliedFactStatus.PENDING_SUPERSEDED) {
            return existing.getApplyStatus();
        }
        handleSupersedes(fact);
        if (existing != null) {
            return skipSuperseded(existing, fact);
        }
        List<BalanceEffect> effects = FactBalanceMappingRule.effects(fact);
        BalanceAppliedFact applied = newAppliedFact(fact, fact.factId(), fact.factVersion(), AppliedFactStatus.APPLIED);
        applied.setLinesHash(linesHash(fact, effects));
        balanceAppliedFactRepository.insert(applied);
        applyEffects(fact, applied, effects);
        return AppliedFactStatus.APPLIED;
    }

    /**
     * 处理替代关系
     * <li>被替代事实已应用：冲回其余额变化并标记 REVERSED。</li>
     * <li>被替代事实尚未到达：以本事实版本减一预登记为 PENDING_SUPERSEDED，到达时不应用。</li>
     * <li>被替代事实已冲回、跳过或预登记：只补记替代关系。</li>
     */
    void handleSupersedes(FactRecord fact) {
        if (fact.supersedesFactId() == null) {
            return;
        }
        BalanceAppliedFact prior = balanceAppliedFactRepository.lockByFactId(fact.supersedesFactId());
        if (prior == null) {
            BalanceAppliedFact placeholder = newAppliedFact(fact, fact.supersedesFactId(), fact.factVersion() - 1,
                AppliedFactStatus.PENDING_SUPERSEDED);
            placeholder.setFactType(null);
            placeholder.setSupersedesFactId(null);
            placeholder.setSupersededByFactId(fact.factId());
            placeholder.setAppliedTime(null);
            balanceAppliedFactRepository.insert(placeholder);
            return;
        }
        AppliedFactStatus next = prior.getApplyStatus();
        if (prior.getApplyStatus() == AppliedFactStatus.APPLIED) {
            reverse(prior, fact);
            next = AppliedFactStatus.REVERSED;
        }
        prior.setSupersededByFactId(fact.factId());
        balanceAppliedFactRepository.updateStatus(prior, next);
    }

    /** 预登记的被替代事实到达：补全处理信息并标记 SKIPPED，不改变余额。 */
    AppliedFactStatus skipSuperseded(BalanceAppliedFact placeholder, FactRecord fact) {
        placeholder.setFactType(fact.factType());
        placeholder.setSupersedesFactId(fact.supersedesFactId());
        placeholder.setLinesHash(sha256(fact.factId() + "|" + fact.factVersion() + "|" + AppliedFactStatus.SKIPPED.getCode()));
        placeholder.setAppliedTime(now());
        balanceAppliedFactRepository.updateStatus(placeholder, AppliedFactStatus.SKIPPED);
        return AppliedFactStatus.SKIPPED;
    }

    /** 按被替代事实已应用的余额变化反向写入，最近事实记为替代事实。 */
    void reverse(BalanceAppliedFact prior, FactRecord fact) {
        Map<Long, BigDecimal[]> deltas = new TreeMap<>();
        for (BalanceAppliedEntry entry : balanceAppliedFactRepository.findEntries(prior.getId())) {
            EntrySide side = entry.getEntrySide() == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
            add(deltas, entry.getBalanceId(), side, entry.getAmount());
        }
        writeBalances(fact, deltas);
    }

    /** 解析或创建余额账户、登记余额变化，再按余额主键升序写入累计值。 */
    void applyEffects(FactRecord fact, BalanceAppliedFact applied, List<BalanceEffect> effects) {
        Map<Long, BigDecimal[]> deltas = new TreeMap<>();
        List<BalanceAppliedEntry> entries = new ArrayList<>();
        Map<String, AccountBalance> resolved = new LinkedHashMap<>();
        for (BalanceEffect effect : effects) {
            AccountBalance balance = resolved.computeIfAbsent(identityKey(effect), key -> resolveBalance(fact, effect));
            add(deltas, balance.getId(), effect.entrySide(), effect.amount());
            entries.add(newEntry(fact, applied, effect, balance));
        }
        balanceAppliedFactRepository.insertEntries(entries);
        writeBalances(fact, deltas);
    }

    /** 余额账户存在时读取，否则创建零余额账户。 */
    AccountBalance resolveBalance(FactRecord fact, BalanceEffect effect) {
        BookIdentity book = fact.book();
        AccountBalance balance = accountBalanceRepository.findByIdentity(book, effect.accountCode(), effect.ownerType(),
            effect.ownerId(), effect.bucket());
        if (balance != null) {
            return balance;
        }
        balance = newBalance(book, effect);
        accountBalanceRepository.insert(balance);
        return balance;
    }

    /** 按余额主键升序以版本 CAS 写入新的累计值。 */
    void writeBalances(FactRecord fact, Map<Long, BigDecimal[]> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        Map<Long, AccountBalance> balances = accountBalanceRepository.findByIds(new ArrayList<>(deltas.keySet())).stream()
            .collect(Collectors.toMap(AccountBalance::getId, balance -> balance));
        for (Map.Entry<Long, BigDecimal[]> delta : deltas.entrySet()) {
            AccountBalance balance = balances.get(delta.getKey());
            if (balance == null) {
                throw new ErrorException("余额账户不存在: " + delta.getKey());
            }
            BigDecimal debit = balance.getDebitTotal().add(delta.getValue()[0]);
            BigDecimal credit = balance.getCreditTotal().add(delta.getValue()[1]);
            BigDecimal net = balance.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
            accountBalanceRepository.applyChange(balance, debit, credit, net, fact.factId(), fact.factVersion());
        }
    }

    /** 累加一个余额的借或贷增量。 */
    static void add(Map<Long, BigDecimal[]> deltas, long balanceId, EntrySide side, BigDecimal amount) {
        BigDecimal[] delta = deltas.computeIfAbsent(balanceId, id -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        int index = side == EntrySide.DEBIT ? 0 : 1;
        delta[index] = delta[index].add(amount);
    }

    /** 余额身份键：科目|Owner 类型|Owner|余额桶（同一事实的余额都在同一账簿主体）。 */
    static String identityKey(BalanceEffect effect) {
        return effect.accountCode() + "|" + effect.ownerType().getCode() + "|" + effect.ownerId() + "|"
            + effect.bucket().getCode();
    }

    /** 处理记录：事实头主体用于按账簿统计。 */
    BalanceAppliedFact newAppliedFact(FactRecord fact, long factId, long factVersion, AppliedFactStatus status) {
        Instant now = now();
        BalanceAppliedFact applied = new BalanceAppliedFact();
        applied.setId(idGenerator.nextId());
        applied.setFactId(factId);
        applied.setFactVersion(factVersion);
        applied.setFactType(fact.factType());
        applied.setSupersedesFactId(fact.supersedesFactId());
        applied.setEntityNo(fact.entityNo());
        applied.setFinanceEntityId(fact.financeEntityId());
        applied.setCurrency(fact.currency());
        applied.setApplyStatus(status);
        applied.setAppliedTime(now);
        applied.setCreateTime(now);
        applied.setUpdateTime(now);
        return applied;
    }

    /** 零余额 ACTIVE 余额账户，正常方向由映射规则给出。 */
    AccountBalance newBalance(BookIdentity book, BalanceEffect effect) {
        Instant now = now();
        AccountBalance balance = new AccountBalance();
        balance.setId(idGenerator.nextId());
        balance.setBookEntityType(book.entityType());
        balance.setBookEntityId(book.entityId());
        balance.setCurrency(book.currency());
        balance.setAccountCode(effect.accountCode());
        balance.setOwnerType(effect.ownerType());
        balance.setOwnerId(effect.ownerId());
        balance.setBalanceBucket(effect.bucket());
        balance.setNormalSide(effect.normalSide());
        balance.setDebitTotal(BigDecimal.ZERO);
        balance.setCreditTotal(BigDecimal.ZERO);
        balance.setNetBalance(BigDecimal.ZERO);
        balance.setBalanceVersion(0L);
        balance.setStatus(BalanceStatus.ACTIVE);
        balance.setCreateTime(now);
        balance.setUpdateTime(now);
        return balance;
    }

    /** 一行余额变化。 */
    BalanceAppliedEntry newEntry(FactRecord fact, BalanceAppliedFact applied, BalanceEffect effect,
        AccountBalance balance) {
        BalanceAppliedEntry entry = new BalanceAppliedEntry();
        entry.setId(idGenerator.nextId());
        entry.setAppliedFactId(applied.getId());
        entry.setFactId(fact.factId());
        entry.setLineNo(effect.lineNo());
        entry.setBalanceId(balance.getId());
        entry.setEntrySide(effect.entrySide());
        entry.setAmount(effect.amount());
        entry.setCreateTime(applied.getCreateTime());
        return entry;
    }

    /** 余额变化规范文本的 SHA-256：首行“factId|factVersion”，随后每个变化一行。 */
    static String linesHash(FactRecord fact, List<BalanceEffect> effects) {
        String text = fact.factId() + "|" + fact.factVersion() + effects.stream().map(BalanceEffect::canonical)
            .map(line -> "\n" + line).collect(Collectors.joining());
        return sha256(text);
    }

    /** SHA-256 小写十六进制。 */
    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorException("运行环境缺少 SHA-256", e);
        }
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

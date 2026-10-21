package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.configuration.BankMatchSuggestionProperties;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domain.BankMatching;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.service.processor.request.SuggestBankMatchesRequest;
import lombok.AllArgsConstructor;

/**
 * 为一个银行账户出一对一匹配建议（BANK_MATCH_SUGGEST 任务，设计 F2 自动建议规则，AC-10）
 * <li>锁 bank-match-suggestion:{cashAccountId}，并发任务不重复出建议；建议只是 PROPOSED，不改流水与预期流水的金额（确认均为人工）。</li>
 * <li>候选：已导入对账单中 UNMATCHED 的流水，与同一银行账户 OPEN / PARTIALLY_CONFIRMED 的预期流水；方向一致、币种一致、剩余金额相等、
 * 起息日与预期日相差不超过 N 个工作日（{@link BankMatchSuggestionProperties}）。</li>
 * <li>一对一：已有 PROPOSED 建议的流水或预期流水不再建议；任何状态（含已拒绝）已出现过的同一对不再建议。多候选时取日期最接近、主键最小的。</li>
 */
@Component
@AllArgsConstructor
public class SuggestBankMatchesProcessor extends AbstractLockProcessor<SuggestBankMatchesRequest, List<BankMatch>> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final BankMatchService bankMatchService;
    private final BankMatchRepository bankMatchRepository;
    private final BankMatchSuggestionProperties suggestionProperties;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    /** 建议任务的银行账户互斥锁。 */
    @Override
    protected LockCommand lockCommand(SuggestBankMatchesRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.bankMatchSuggestion(request.getCashAccountId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected List<BankMatch> processWithinLock(SuggestBankMatchesRequest request) {
        // 候选
        List<BankStatementLine> lines = bankStatementLineRepository.listOpen(request.getCashAccountId(), null).stream()
            .filter(line -> line.getMatchStatus() == LineMatchStatus.UNMATCHED).toList();
        List<ExpectedBankMovement> movements = expectedBankMovementRepository.listOpen(request.getCashAccountId());
        if (lines.isEmpty() || movements.isEmpty()) {
            return List.of();
        }

        // 一对一配对
        List<BankMatch> proposals = pair(lines, movements, suggestionProperties.getSuggestionWindowBusinessDays());

        // 本地保存
        bankMatchService.propose(proposals);
        return proposals;
    }

    /** 逐条流水取最合适且未被占用的预期流水。 */
    List<BankMatch> pair(List<BankStatementLine> lines, List<ExpectedBankMovement> movements, int window) {
        Set<Long> busyMovements = new HashSet<>();
        Set<String> seenPairs = new HashSet<>();
        for (ExpectedBankMovement movement : movements) {
            collect(bankMatchRepository.listByExpected(movement.getId(), null), busyMovements, seenPairs);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<BankMatch> proposals = new ArrayList<>();
        for (BankStatementLine line : lines) {
            List<BankMatch> existing = bankMatchRepository.listByLine(line.getId(), null);
            if (existing.stream().anyMatch(match -> match.getStatus() == BankMatchStatus.PROPOSED)) {
                continue;
            }
            collect(existing, busyMovements, seenPairs);
            ExpectedBankMovement chosen = movements.stream().filter(movement -> !busyMovements.contains(movement.getId()))
                .filter(movement -> !seenPairs.contains(key(line.getId(), movement.getId())))
                .filter(movement -> eligible(line, movement, window))
                .min(Comparator.comparing((ExpectedBankMovement movement) -> BankMatching.businessDaysBetween(
                    movement.getExpectedDate(), line.getValueDate())).thenComparing(ExpectedBankMovement::getId))
                .orElse(null);
            if (chosen != null) {
                busyMovements.add(chosen.getId());
                proposals.add(proposal(line, chosen, now));
            }
        }
        return proposals;
    }

    /** 记录已占用的预期流水（有 PROPOSED 建议）与出现过的配对。 */
    static void collect(List<BankMatch> matches, Set<Long> busyMovements, Set<String> seenPairs) {
        for (BankMatch match : matches) {
            seenPairs.add(key(match.getBankStatementLineId(), match.getExpectedMovementId()));
            if (match.getStatus() == BankMatchStatus.PROPOSED) {
                busyMovements.add(match.getExpectedMovementId());
            }
        }
    }

    /** 方向、币种、剩余金额相等且日期在窗口内。 */
    static boolean eligible(BankStatementLine line, ExpectedBankMovement movement, int window) {
        BigDecimal remaining = BankMatching.movementRemaining(movement);
        return line.getDirection() == movement.getDirection()
            && remaining.compareTo(BankMatching.lineRemaining(line)) == 0 && remaining.signum() > 0
            && BankMatching.businessDaysBetween(movement.getExpectedDate(), line.getValueDate()) <= window;
    }

    /** 配对键。 */
    static String key(Long lineId, Long movementId) {
        return lineId + ":" + movementId;
    }

    /** PROPOSED 建议（AUTO）。 */
    BankMatch proposal(BankStatementLine line, ExpectedBankMovement movement, Instant now) {
        BankMatch match = new BankMatch();
        match.setId(idGenerator.nextId());
        match.setBankStatementLineId(line.getId());
        match.setExpectedMovementId(movement.getId());
        match.setMatchedAmount(BankMatching.lineRemaining(line));
        match.setMatchMethod(MatchMethod.AUTO);
        match.setStatus(BankMatchStatus.PROPOSED);
        match.setVersion(1L);
        match.setCreateTime(now);
        match.setUpdateTime(now);
        return match;
    }
}

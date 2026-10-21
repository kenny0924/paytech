package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.ChannelStatementLines;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;
import com.paytech.reconciliation.domain.PlatformTxns;
import com.paytech.reconciliation.domain.ReconcileGate;
import com.paytech.reconciliation.domain.StatementMatchContext;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementMatchService;
import com.paytech.reconciliation.domainservice.service.ExpectedBankMovementService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.MatchChannelStatementRequest;

import lombok.AllArgsConstructor;

/**
 * 渠道账单逐行匹配与对平（本地任务 CHANNEL_STATEMENT_MATCH，设计 F1 规则 1–7 与对平门禁，technique D-05、D-10）
 * <li>锁 channel-statement:{id}；账单不在 MATCHING 时直接返回。先在内存制订计划，再在一个本地事务内保存匹配、开立 / 自动结案异常、开成本偏差、
 * 尝试对平；任何版本冲突回滚整个任务，由补偿重试。</li>
 * <li>交易行（UNMATCHED，或挂着 OPEN 的 CHANNEL_LINE_UNMATCHED 而重试的行）：跨账单查重（更早的未作废账单里有引用、行类型、方向、金额都相同的行
 * → DUPLICATE_SETTLEMENT，该行最近一条重复结算异常已 RESOLVED 则跳过查重）；同交易号 OPEN / PARTIALLY_MATCHED 平台流水命中多条
 * → AMBIGUOUS_REFERENCE；未命中或方向不符 → 继续等待（期满由等待期满任务开 CHANNEL_LINE_UNMATCHED）；行金额超过剩余 → AMOUNT_MISMATCH；
 * 否则匹配，实际费用 = 行上费用 + 本账单同交易号 FEE 行，平台流水累加进度。重试行匹配成功时原未命中异常自动结案。</li>
 * <li>非交易行：ADJUSTMENT 与不带交易号的 FEE 行 → UNEXPLAINED_ADJUSTMENT；带交易号的 FEE 行在同交易号交易行已匹配到平台流水时并入（MATCHED），
 * 本账单没有同交易号交易行 → UNEXPLAINED_ADJUSTMENT，否则等待。行级异常的差异金额为该行的带符号净额贡献。</li>
 * <li>本账单覆盖的平台流水已完全匹配且 |实际费用 − 预估| 超过清算账户容差 → COST_DEVIATION（阻塞；同一平台流水只开一次，作废自动结案的除外）。</li>
 * <li>门禁五项全部满足 → 对平：平台流水结清、账单 RECONCILED（净额 NONE 直接 CASH_CONFIRMED）、生成预期银行流水、登记两类事件。</li>
 * <li>集合式执行（缺陷 D-1）：待规划交易行的候选平台流水、跨账单相同行及其账单、重复结算的最近异常都按交易号 / 主键分段批量读取，
 * 规划只在内存进行；保存时行、平台流水、异常都按段多行写入，仍以版本为条件。查询次数与行数无关（每 1,000 个键一次）。</li>
 */
@Component
@AllArgsConstructor
public class MatchChannelStatementProcessor extends AbstractLockProcessor<MatchChannelStatementRequest, Boolean> {
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ReconExceptionService reconExceptionService;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final ExpectedBankMovementService expectedBankMovementService;
    private final ChannelStatementMatchService channelStatementMatchService;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 渠道账单资源锁。 */
    @Override
    protected LockCommand lockCommand(MatchChannelStatementRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.channelStatement(request.getStatementId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected Boolean processWithinLock(MatchChannelStatementRequest request) {
        // 查询账单
        ChannelStatement statement = channelStatementRepository.findById(request.getStatementId());
        if (statement == null || statement.getStatus() != ChannelStatementStatus.MATCHING) {
            return false;
        }

        // 构建上下文、批量加载候选与匹配计划
        StatementMatchContext context = buildContext(statement);
        prepareCandidates(context);
        context.getLines().stream().filter(line -> ChannelStatementLines.isTransactionLine(line.getLineType()))
            .forEach(line -> planTransactionLine(context, line));
        context.getLines().stream().filter(line -> !ChannelStatementLines.isTransactionLine(line.getLineType()))
            .filter(line -> line.getMatchStatus() == LineMatchStatus.UNMATCHED)
            .forEach(line -> planNonTransactionLine(context, line));

        // 本地保存与对平
        return transactionTemplate.execute(status -> saveAndReconcile(context));
    }

    /** 账单、清算账户、全部行与本账单未结异常。 */
    StatementMatchContext buildContext(ChannelStatement statement) {
        return new StatementMatchContext(statement, clearingAccountRepository.findById(statement.getClearingAccountId()),
            channelStatementLineRepository.listByStatement(statement.getId()),
            reconExceptionRepository.listActiveByStatement(statement.getId()),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 一次加载待规划交易行的候选平台流水与跨账单重复判定。 */
    void prepareCandidates(StatementMatchContext context) {
        List<ChannelStatementLine> pending = context.getLines().stream()
            .filter(line -> ChannelStatementLines.isTransactionLine(line.getLineType()))
            .filter(line -> line.getMatchStatus() == LineMatchStatus.UNMATCHED || retryableUnmatched(context, line) != null)
            .toList();
        Map<ExternalReference, List<PlatformTxn>> candidates = platformTxnRepository.listByReferences(
            context.getClearingAccount().getId(), references(pending)).stream()
            .collect(Collectors.groupingBy(txn -> new ExternalReference(txn.getReferenceType(),
                txn.getExternalTransactionId())));
        List<ChannelStatementLine> fresh = pending.stream()
            .filter(line -> line.getMatchStatus() == LineMatchStatus.UNMATCHED).toList();
        context.prepare(candidates, duplicatedLineIds(context, fresh));
    }

    /** 行的去重交易号集合。 */
    static Set<ExternalReference> references(List<ChannelStatementLine> lines) {
        return lines.stream().map(ExternalReference::of).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 一条交易行的匹配计划（规则 1–6）。 */
    void planTransactionLine(StatementMatchContext context, ChannelStatementLine line) {
        ReconException unmatched = retryableUnmatched(context, line);
        if (line.getMatchStatus() != LineMatchStatus.UNMATCHED && unmatched == null) {
            return;
        }
        boolean fresh = unmatched == null;
        if (fresh && context.duplicated(line)) {
            planException(context, line, ReconExceptionType.DUPLICATE_SETTLEMENT, "更早的未作废账单已有相同交易行");
            return;
        }
        List<PlatformTxn> hits = context.candidateTxns(line).stream().filter(PlatformTxns::matchable).toList();
        if (hits.size() > 1) {
            if (fresh) {
                planException(context, line, ReconExceptionType.AMBIGUOUS_REFERENCE, "命中多条平台流水: "
                    + hits.stream().map(txn -> String.valueOf(txn.getId())).collect(Collectors.joining(",")));
            }
            return;
        }
        if (hits.isEmpty() || hits.getFirst().getDirection() != ChannelStatementLines.platformDirection(line.getLineType())) {
            return;
        }
        planTxnMatch(context, line, hits.getFirst(), unmatched);
    }

    /** 命中唯一平台流水：金额超过剩余开 AMOUNT_MISMATCH，否则匹配并累计平台流水进度。 */
    void planTxnMatch(StatementMatchContext context, ChannelStatementLine line, PlatformTxn txn,
        ReconException unmatched) {
        StatementMatchContext.TxnProgress progress = context.progressOf(txn);
        if (line.getAmount().compareTo(progress.remaining()) > 0) {
            if (unmatched == null) {
                planException(context, line, ReconExceptionType.AMOUNT_MISMATCH, "行金额 " + line.getAmount().toPlainString()
                    + " 超过平台流水 " + txn.getId() + " 剩余 " + progress.remaining().toPlainString());
            }
            return;
        }
        BigDecimal fee = context.actualFee(line);
        progress.cover(line.getAmount(), fee);
        context.planLine(new StatementMatchContext.LineMatch(line, txn.getId(), MatchMethod.AUTO, fee,
            LineMatchStatus.MATCHED));
        if (unmatched != null) {
            context.getExceptionsToAutoClose().add(unmatched);
        }
    }

    /** 一条非交易行的计划（规则 7）。 */
    void planNonTransactionLine(StatementMatchContext context, ChannelStatementLine line) {
        if (line.getLineType() == ChannelLineType.ADJUSTMENT || !ChannelStatementLines.hasReference(line)) {
            planException(context, line, ReconExceptionType.UNEXPLAINED_ADJUSTMENT, "调整行或不带交易号的费用行，需归类");
            return;
        }
        List<ChannelStatementLine> owners = context.transactionLinesOf(line);
        if (owners.isEmpty()) {
            planException(context, line, ReconExceptionType.UNEXPLAINED_ADJUSTMENT, "带交易号的费用行在本账单没有对应交易行");
            return;
        }
        if (owners.stream().anyMatch(owner -> plannedTxnId(context, owner) != null)) {
            context.planLine(new StatementMatchContext.LineMatch(line, null, MatchMethod.AUTO, null,
                LineMatchStatus.MATCHED));
        }
    }

    /** 交易行在本次计划后匹配到的平台流水；未匹配为空。 */
    static Long plannedTxnId(StatementMatchContext context, ChannelStatementLine line) {
        return context.plannedMatch(line).map(StatementMatchContext.LineMatch::getPlatformTxnId)
            .orElse(line.getPlatformTxnId());
    }

    /** 挂在 EXCEPTION 行上、仍为 OPEN 的未命中异常（期满后平台流水才到，可重试匹配）。 */
    static ReconException retryableUnmatched(StatementMatchContext context, ChannelStatementLine line) {
        if (line.getMatchStatus() != LineMatchStatus.EXCEPTION) {
            return null;
        }
        return context.activeLineException(line, ReconExceptionType.CHANNEL_LINE_UNMATCHED)
            .filter(exception -> exception.getStatus() == ReconExceptionStatus.OPEN).orElse(null);
    }

    /**
     * 跨账单查重（规则 6）：更早导入、未作废的账单里有引用、行类型、方向、金额都相同的行，且该行最近一条重复结算异常未被处置接受（RESOLVED）
     *
     * @param context 匹配上下文
     * @param fresh 待规划的 UNMATCHED 交易行
     * @return 判定为重复的行主键
     */
    Set<Long> duplicatedLineIds(StatementMatchContext context, List<ChannelStatementLine> fresh) {
        List<ChannelStatementLine> earlierLines = channelStatementLineRepository.listByReferences(
            context.getClearingAccount().getId(), references(fresh)).stream()
            .filter(other -> other.getStatementId() < context.getStatement().getId()).toList();
        Set<Long> countedStatements = channelStatementRepository.findByIds(earlierLines.stream()
            .map(ChannelStatementLine::getStatementId).distinct().toList()).stream()
            .filter(MatchChannelStatementProcessor::countsForDuplicate).map(ChannelStatement::getId)
            .collect(Collectors.toSet());
        Set<DuplicateKey> earlierKeys = earlierLines.stream()
            .filter(other -> countsForDuplicate(countedStatements, other)).map(DuplicateKey::of)
            .collect(Collectors.toSet());
        List<Long> sameLineIds = fresh.stream().filter(line -> earlierKeys.contains(DuplicateKey.of(line)))
            .map(ChannelStatementLine::getId).toList();
        Set<Long> accepted = reconExceptionRepository.listLatestBySources(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            sameLineIds, ReconExceptionType.DUPLICATE_SETTLEMENT).stream()
            .filter(latest -> latest.getStatus() == ReconExceptionStatus.RESOLVED).map(ReconException::getSourceId)
            .collect(Collectors.toSet());
        return sameLineIds.stream().filter(id -> !accepted.contains(id)).collect(Collectors.toSet());
    }

    /** 该行所在账单是否参与查重。 */
    static boolean countsForDuplicate(Set<Long> countedStatements, ChannelStatementLine line) {
        return countedStatements.contains(line.getStatementId());
    }

    /** 跨账单查重比较的键：交易号、行类型、方向、金额（去掉末尾 0 后比较，等同 compareTo）。 */
    record DuplicateKey(ExternalReference reference, ChannelLineType lineType, SettlementDirection direction,
        BigDecimal amount) {
        /** 行的查重键。 */
        static DuplicateKey of(ChannelStatementLine line) {
            return new DuplicateKey(ExternalReference.of(line), line.getLineType(), line.getDirection(),
                line.getAmount().stripTrailingZeros());
        }
    }

    /** 参与查重的账单状态：匹配中或已对平（未作废、未被拒）。 */
    static boolean countsForDuplicate(ChannelStatement statement) {
        return statement.getStatus() == ChannelStatementStatus.MATCHING
            || statement.getStatus() == ChannelStatementStatus.RECONCILED
            || statement.getStatus() == ChannelStatementStatus.CASH_CONFIRMED;
    }

    /** 行转 EXCEPTION 并开立行级异常，差异为该行带符号净额贡献。 */
    static void planException(StatementMatchContext context, ChannelStatementLine line, ReconExceptionType type,
        String detail) {
        context.planLine(new StatementMatchContext.LineMatch(line, null, null, null, LineMatchStatus.EXCEPTION));
        context.getExceptionsToOpen().add(exception(context, type, ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line.getId(), ChannelStatementLines.netContribution(line), "第 " + line.getLineNo() + " 行：" + detail));
    }

    /** 交易层异常命令：主体取清算账户的经营主体。 */
    static OpenReconExceptionCommand exception(StatementMatchContext context, ReconExceptionType type,
        ReconExceptionSourceType sourceType, Long sourceId, BigDecimal difference, String detail) {
        return OpenReconExceptionCommand.builder().exceptionType(type).sourceType(sourceType).sourceId(sourceId)
            .statementId(context.getStatement().getId()).clearingAccountId(context.getClearingAccount().getId())
            .entityType(ReconEntityType.OPERATING_ENTITY).entityId(context.getClearingAccount().getEntityNo())
            .currency(context.getStatement().getCurrency()).differenceAmount(difference).detail(detail).build();
    }

    /** 事务内：保存计划、异常、成本偏差，门禁通过则对平。 */
    Boolean saveAndReconcile(StatementMatchContext context) {
        channelStatementMatchService.saveMatches(context);
        reconExceptionService.autoCloseAll(context.getExceptionsToAutoClose());
        reconExceptionService.openAll(context.getExceptionsToOpen());
        List<PlatformTxn> coveredTxns = coveredTxns(context);
        reconExceptionService.openAll(costDeviations(context, coveredTxns));
        ReconcileGate gate = ReconcileGate.evaluate(context.getStatement(), context.getClearingAccount(),
            context.getLines(), reconExceptionRepository.listActiveByStatement(context.getStatement().getId()));
        if (!gate.passed()) {
            return false;
        }
        channelStatementMatchService.reconcile(context, coveredTxns);
        if (context.getStatement().getCashDirection() != CashDirection.NONE) {
            expectedBankMovementService.createChannelSettlement(context.getStatement(), context.getClearingAccount());
        }
        return true;
    }

    /** 本账单行匹配到的平台流水（事务内重新读取）。 */
    List<PlatformTxn> coveredTxns(StatementMatchContext context) {
        List<Long> ids = context.getLines().stream().map(ChannelStatementLine::getPlatformTxnId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, PlatformTxn> byId = platformTxnRepository.findByIds(ids).stream()
            .collect(Collectors.toMap(PlatformTxn::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * 完全匹配且费用偏差超过容差时开 COST_DEVIATION（technique D-05）；同一平台流水已开过（作废自动结案的除外）不再开
     *
     * @param context 匹配上下文
     * @param coveredTxns 本账单覆盖的平台流水
     * @return 待开立的成本偏差
     */
    List<OpenReconExceptionCommand> costDeviations(StatementMatchContext context, List<PlatformTxn> coveredTxns) {
        BigDecimal tolerance = context.getClearingAccount().getCostToleranceAmount();
        List<PlatformTxn> deviated = coveredTxns.stream().filter(txn -> txn.getStatus() == PlatformTxnStatus.MATCHED)
            .filter(txn -> PlatformTxns.feeDifference(txn).abs().compareTo(tolerance) > 0).toList();
        Set<Long> alreadyOpened = reconExceptionRepository.listLatestBySources(ReconExceptionSourceType.PLATFORM_TXN,
            deviated.stream().map(PlatformTxn::getId).toList(), ReconExceptionType.COST_DEVIATION).stream()
            .filter(latest -> latest.getStatus() != ReconExceptionStatus.AUTO_CLOSED).map(ReconException::getSourceId)
            .collect(Collectors.toSet());
        return deviated.stream().filter(txn -> !alreadyOpened.contains(txn.getId()))
            .map(txn -> costDeviation(context, txn, tolerance)).toList();
    }

    /** 一笔平台流水的成本偏差命令，差异 = 实际 − 预估。 */
    static OpenReconExceptionCommand costDeviation(StatementMatchContext context, PlatformTxn txn, BigDecimal tolerance) {
        return exception(context, ReconExceptionType.COST_DEVIATION, ReconExceptionSourceType.PLATFORM_TXN, txn.getId(),
            PlatformTxns.feeDifference(txn), "平台流水 " + txn.getId() + " 实际渠道费 " + txn.getActualFee().toPlainString()
                + " 与预估 " + txn.getExpectedFee().toPlainString() + " 之差超过容差 " + tolerance.toPlainString());
    }
}

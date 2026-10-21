package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次渠道账单匹配任务的上下文（CHANNEL_STATEMENT_MATCH，technique D-10）
 * <li>持有账单、清算账户、全部行与本账单未结异常；匹配计划只在内存记录目标值，保存时由 Repository 条件更新后同步原对象。</li>
 * <li>同一平台流水在本次被多行覆盖（部分匹配）时，按 {@link TxnProgress} 累计。</li>
 * <li>集合式匹配：候选平台流水与跨账单重复行由处理器按交易号批量加载后放入（{@link #prepare}）；行、异常、同交易号费用行与计划结果
 * 都建了内存索引，逐行规划不再查库、也不再扫描全部行。</li>
 */
@Getter
public class StatementMatchContext {
    private final ChannelStatement statement;

    private final ClearingAccount clearingAccount;

    private final List<ChannelStatementLine> lines;

    private final List<ReconException> activeExceptions;

    private final Instant now;

    private final List<LineMatch> lineMatches = new ArrayList<>();

    private final Map<Long, TxnProgress> txnProgress = new LinkedHashMap<>();

    private final List<OpenReconExceptionCommand> exceptionsToOpen = new ArrayList<>();

    private final List<ReconException> exceptionsToAutoClose = new ArrayList<>();

    /** 按挂靠对象 ID + 类型索引的未结异常（同键取主键最小的一条）。 */
    private final Map<Long, Map<ReconExceptionType, ReconException>> activeExceptionIndex = new HashMap<>();

    /** 本账单交易行按交易号分组。 */
    private final Map<ExternalReference, List<ChannelStatementLine>> transactionLinesByReference = new HashMap<>();

    /** 本账单带交易号 FEE 行按交易号累计的「金额 + 行上费用」。 */
    private final Map<ExternalReference, BigDecimal> referencedFees = new HashMap<>();

    /** 每行在本次计划中的结果（按对象身份）。 */
    private final Map<ChannelStatementLine, LineMatch> lineMatchIndex = new IdentityHashMap<>();

    /** 按交易号批量加载的同清算账户平台流水（应收、应付）。 */
    private Map<ExternalReference, List<PlatformTxn>> candidateTxns = Map.of();

    /** 跨账单查重命中（规则 6）且重复结算未被处置接受的行。 */
    private Set<Long> duplicateLineIds = Set.of();

    /** 以已加载的事实构造，并建立行与异常的内存索引。 */
    public StatementMatchContext(ChannelStatement statement, ClearingAccount clearingAccount,
        List<ChannelStatementLine> lines, List<ReconException> activeExceptions, Instant now) {
        this.statement = statement;
        this.clearingAccount = clearingAccount;
        this.lines = lines;
        this.activeExceptions = activeExceptions;
        this.now = now;
        activeExceptions.forEach(exception -> activeExceptionIndex.computeIfAbsent(exception.getSourceId(),
            id -> new EnumMap<>(ReconExceptionType.class)).putIfAbsent(exception.getExceptionType(), exception));
        lines.forEach(this::indexLine);
    }

    /** 交易行按交易号分组；带交易号 FEE 行累计到交易号。 */
    void indexLine(ChannelStatementLine line) {
        ExternalReference reference = ExternalReference.of(line);
        if (reference == null) {
            return;
        }
        if (ChannelStatementLines.isTransactionLine(line.getLineType())) {
            transactionLinesByReference.computeIfAbsent(reference, key -> new ArrayList<>()).add(line);
        } else if (line.getLineType() == ChannelLineType.FEE) {
            referencedFees.merge(reference, line.getAmount().add(line.getFeeAmount()), BigDecimal::add);
        }
    }

    /**
     * 放入批量加载的候选
     *
     * @param candidateTxns 按交易号分组的平台流水
     * @param duplicateLineIds 跨账单重复且未被接受的行
     */
    public void prepare(Map<ExternalReference, List<PlatformTxn>> candidateTxns, Set<Long> duplicateLineIds) {
        this.candidateTxns = candidateTxns;
        this.duplicateLineIds = duplicateLineIds;
    }

    /** 本账单某行上未结的指定类型异常。 */
    public Optional<ReconException> activeLineException(ChannelStatementLine line,
        ReconExceptionType type) {
        return Optional.ofNullable(activeExceptionIndex.getOrDefault(line.getId(), Map.of()).get(type));
    }

    /** 与交易行同交易号的平台流水（批量加载值，未按状态过滤）。 */
    public List<PlatformTxn> candidateTxns(ChannelStatementLine line) {
        ExternalReference reference = ExternalReference.of(line);
        return reference == null ? List.of() : candidateTxns.getOrDefault(reference, List.of());
    }

    /** 该行是否跨账单重复（规则 6）。 */
    public boolean duplicated(ChannelStatementLine line) {
        return duplicateLineIds.contains(line.getId());
    }

    /** 本账单与该行同交易号的交易行。 */
    public List<ChannelStatementLine> transactionLinesOf(ChannelStatementLine line) {
        ExternalReference reference = ExternalReference.of(line);
        return reference == null ? List.of() : transactionLinesByReference.getOrDefault(reference, List.of());
    }

    /** 交易行的实际渠道费：行上费用加本账单同交易号 FEE 行（同 {@link ChannelStatementLines#actualFee}）。 */
    public BigDecimal actualFee(ChannelStatementLine transactionLine) {
        ExternalReference reference = ExternalReference.of(transactionLine);
        BigDecimal referenced = reference == null ? BigDecimal.ZERO : referencedFees.getOrDefault(reference, BigDecimal.ZERO);
        return transactionLine.getFeeAmount().add(referenced);
    }

    /** 某行在本次计划中的结果；未计划为空。 */
    public Optional<LineMatch> plannedMatch(ChannelStatementLine line) {
        return Optional.ofNullable(lineMatchIndex.get(line));
    }

    /** 某行在本次计划中的目标状态；未计划时取当前状态。 */
    public LineMatchStatus plannedStatus(ChannelStatementLine line) {
        return plannedMatch(line).map(LineMatch::getStatus).orElse(line.getMatchStatus());
    }

    /** 记录一行的匹配结果。 */
    public void planLine(LineMatch match) {
        lineMatches.add(match);
        lineMatchIndex.putIfAbsent(match.getLine(), match);
    }

    /** 平台流水在本次计划后的进度；首次触及时以当前持久化值起算。 */
    public TxnProgress progressOf(PlatformTxn txn) {
        return txnProgress.computeIfAbsent(txn.getId(), id -> new TxnProgress(txn, txn.getMatchedAmount(),
            txn.getActualFee()));
    }

    /** 一行的匹配结果：目标平台流水、方式、实际费用与状态；释放或开异常时引用列为空。 */
    @Getter
    @AllArgsConstructor
    public static class LineMatch {
        private final ChannelStatementLine line;

        private final Long platformTxnId;

        private final MatchMethod matchMethod;

        private final BigDecimal actualFee;

        private final LineMatchStatus status;
    }

    /** 平台流水在本次计划中的累计进度。 */
    @Getter
    public static class TxnProgress {
        private final PlatformTxn txn;

        private BigDecimal matchedAmount;

        private BigDecimal actualFee;

        /** 以当前值起算。 */
        TxnProgress(PlatformTxn txn, BigDecimal matchedAmount, BigDecimal actualFee) {
            this.txn = txn;
            this.matchedAmount = matchedAmount;
            this.actualFee = actualFee;
        }

        /** 尚未被账单行覆盖的金额。 */
        public BigDecimal remaining() {
            return txn.getExpectedAmount().subtract(matchedAmount);
        }

        /** 追加一行覆盖。 */
        public void cover(BigDecimal amount, BigDecimal fee) {
            matchedAmount = matchedAmount.add(amount);
            actualFee = actualFee.add(fee);
        }

        /** 由已匹配金额推出的状态：0 为 OPEN，未满为 PARTIALLY_MATCHED，满额为 MATCHED。 */
        public PlatformTxnStatus status() {
            return PlatformTxns.progressStatus(txn, matchedAmount);
        }
    }
}

package com.paytech.reconciliation.domainservice.service.impl;

import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.ReconResolutionRules;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.PlatformTxns;
import com.paytech.reconciliation.domain.ReconciliationEventPayloads;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.ReconciliationEventQueryService;

import lombok.AllArgsConstructor;

/** 从账单、行、平台流水、处置与预期银行流水重建两类交易层对账事件载荷。 */
@Service
@AllArgsConstructor
public class ReconciliationEventQueryServiceImpl implements ReconciliationEventQueryService {
    private final BankMatchRepository bankMatchRepository;
    private final PlatformTxnRepository platformTxnRepository;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ReconResolutionRepository reconResolutionRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;
    private final BankStatementLineRepository bankStatementLineRepository;

    /** 账单对平后重建 ChannelBatchReconciled。 */
    @Override
    public ChannelBatchReconciledPayload channelBatchReconciled(long statementId) {
        ChannelStatement statement = channelStatementRepository.findById(statementId);
        if (statement == null || statement.getReconciledTime() == null) {
            return null;
        }
        ClearingAccount account = clearingAccountRepository.findById(statement.getClearingAccountId());
        List<ChannelStatementLine> lines = channelStatementLineRepository.listByStatement(statementId);
        ChannelBatchReconciledPayload payload = header(statement, account);
        fillTotals(payload, lines);
        payload.setClassifiedLines(classifiedLines(statement.getCurrency(), lines));
        payload.setPlatformTxnCount((int)lines.stream().map(ChannelStatementLine::getPlatformTxnId)
            .filter(id -> id != null).distinct().count());
        ExpectedBankMovement movement = expectedBankMovementRepository.findBySource(
            ExpectedMovementType.CHANNEL_SETTLEMENT, statementId);
        payload.setExpectedMovementId(movement == null ? null : movement.getId());
        return payload;
    }

    /** 平台流水 SETTLED 且费用有差额时重建 ActualChannelCostConfirmed。 */
    @Override
    public ActualChannelCostConfirmedPayload actualChannelCostConfirmed(long platformTxnId) {
        PlatformTxn txn = platformTxnRepository.findById(platformTxnId);
        if (txn == null || txn.getStatus() != PlatformTxnStatus.SETTLED
            || PlatformTxns.feeDifference(txn).signum() == 0) {
            return null;
        }
        ChannelStatement statement = lastReconciledStatement(txn);
        if (statement == null) {
            return null;
        }
        ClearingAccount account = clearingAccountRepository.findById(txn.getClearingAccountId());
        return costPayload(txn, account, statement);
    }

    /** 账单头字段。 */
    static ChannelBatchReconciledPayload header(ChannelStatement statement, ClearingAccount account) {
        String currency = statement.getCurrency();
        ChannelBatchReconciledPayload payload = new ChannelBatchReconciledPayload();
        payload.setStatementId(statement.getId());
        payload.setClearingAccountId(account.getId());
        payload.setClearingAccountCode(account.getClearingAccountCode());
        payload.setEntityNo(account.getEntityNo());
        payload.setCurrency(currency);
        payload.setChannelBatchRef(statement.getChannelBatchRef());
        payload.setRevision(statement.getRevision());
        payload.setSettlementDate(statement.getSettlementDate());
        payload.setCashDirection(statement.getCashDirection().getCode());
        payload.setNetAmount(ReconciliationEventPayloads.scaled(currency, statement.getNetAmount()));
        payload.setReconciledTime(statement.getReconciledTime());
        return payload;
    }

    /** 应收、应付与实际费用合计：按已匹配到平台流水的交易行方向汇总。 */
    static void fillTotals(ChannelBatchReconciledPayload payload, List<ChannelStatementLine> lines) {
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        for (ChannelStatementLine line : lines) {
            if (line.getPlatformTxnId() == null) {
                continue;
            }
            if (line.getDirection() == SettlementDirection.CREDIT) {
                receivable = receivable.add(line.getAmount());
            } else {
                payable = payable.add(line.getAmount());
            }
            fee = line.getActualFee() == null ? fee : fee.add(line.getActualFee());
        }
        String currency = payload.getCurrency();
        payload.setReceivableTotal(ReconciliationEventPayloads.scaled(currency, receivable));
        payload.setPayableTotal(ReconciliationEventPayloads.scaled(currency, payable));
        payload.setActualFeeTotal(ReconciliationEventPayloads.scaled(currency, fee));
    }

    /** 已归类调整行：行上有处置 ID 的行，按行号升序，金额与归类码取自已应用的处置。 */
    List<ChannelBatchReconciledPayload.ClassifiedLine> classifiedLines(String currency,
        List<ChannelStatementLine> lines) {
        List<ChannelStatementLine> classified = lines.stream().filter(line -> line.getResolutionId() != null)
            .sorted(Comparator.comparing(ChannelStatementLine::getLineNo)).toList();
        Map<Long, ReconResolution> resolutions = reconResolutionRepository.findByIds(classified.stream()
            .map(ChannelStatementLine::getResolutionId).toList()).stream()
            .collect(Collectors.toMap(ReconResolution::getId, Function.identity()));
        return classified.stream().map(line -> {
            ReconResolution resolution = resolutions.get(line.getResolutionId());
            ChannelBatchReconciledPayload.ClassifiedLine item = new ChannelBatchReconciledPayload.ClassifiedLine();
            item.setLineNo(line.getLineNo());
            item.setClassificationCode(resolution.getClassificationCode().getCode());
            item.setAmount(ReconciliationEventPayloads.scaled(currency, resolution.getAmount()));
            item.setResolutionId(resolution.getId());
            return item;
        }).toList();
    }

    /** 覆盖该平台流水的账单中最后对平的一张。 */
    ChannelStatement lastReconciledStatement(PlatformTxn txn) {
        List<Long> statementIds = channelStatementLineRepository.listByPlatformTxn(txn.getId()).stream()
            .map(ChannelStatementLine::getStatementId).distinct().toList();
        return channelStatementRepository.findByIds(statementIds).stream()
            .filter(statement -> statement.getStatus() == ChannelStatementStatus.RECONCILED
                || statement.getStatus() == ChannelStatementStatus.CASH_CONFIRMED)
            .max(Comparator.comparing(ChannelStatement::getReconciledTime)).orElse(null);
    }

    /** 成本确认载荷。 */
    static ActualChannelCostConfirmedPayload costPayload(PlatformTxn txn, ClearingAccount account,
        ChannelStatement statement) {
        String currency = txn.getCurrency();
        ActualChannelCostConfirmedPayload payload = new ActualChannelCostConfirmedPayload();
        payload.setPlatformTxnId(txn.getId());
        payload.setBillingFactId(txn.getBillingFactId());
        payload.setClearingAccountId(account.getId());
        payload.setClearingAccountCode(account.getClearingAccountCode());
        payload.setEntityNo(txn.getEntityNo());
        payload.setCurrency(currency);
        payload.setTxnSource(txn.getTxnSource().getCode());
        payload.setExpectedFee(ReconciliationEventPayloads.scaled(currency, txn.getExpectedFee()));
        payload.setActualFee(ReconciliationEventPayloads.scaled(currency, txn.getActualFee()));
        payload.setDifference(ReconciliationEventPayloads.scaled(currency, PlatformTxns.feeDifference(txn)));
        payload.setChannelTransactionRefId(txn.getChannelTransactionRefId());
        payload.setReferenceType(txn.getReferenceType());
        payload.setExternalTransactionId(txn.getExternalTransactionId());
        payload.setStatementId(statement.getId());
        payload.setConfirmedTime(statement.getReconciledTime());
        return payload;
    }

    /** 渠道预期流水的已确认匹配重建 ChannelSettlementConfirmed。 */
    @Override
    public ChannelSettlementConfirmedPayload channelSettlementConfirmed(long bankMatchId) {
        BankMatch match = bankMatchRepository.findById(bankMatchId);
        if (match == null || match.getStatus() != BankMatchStatus.CONFIRMED) {
            return null;
        }
        ExpectedBankMovement movement = expectedBankMovementRepository.findById(match.getExpectedMovementId());
        if (movement.getMovementType() != ExpectedMovementType.CHANNEL_SETTLEMENT) {
            return null;
        }
        ChannelStatement statement = channelStatementRepository.findById(movement.getSourceId());
        ClearingAccount account = clearingAccountRepository.findById(statement.getClearingAccountId());
        BankStatementLine line = bankStatementLineRepository.findById(match.getBankStatementLineId());
        return settlementPayload(match, movement, account, line);
    }

    /** 已应用的 CLASSIFY 处置（来源为平台流水、预期银行流水、银行流水）重建 ReconAdjustmentApproved。 */
    @Override
    public ReconAdjustmentApprovedPayload reconAdjustmentApproved(long resolutionId) {
        ReconResolution resolution = reconResolutionRepository.findById(resolutionId);
        if (resolution == null || resolution.getStatus() != ResolutionStatus.APPLIED
            || resolution.getResolutionType() != ResolutionType.CLASSIFY) {
            return null;
        }
        ReconException exception = reconExceptionRepository.findById(resolution.getExceptionId());
        if (!ReconResolutionRules.publishesAdjustment(exception.getSourceType())) {
            return null;
        }
        return adjustmentPayload(resolution, exception);
    }

    /** 到账确认载荷。 */
    static ChannelSettlementConfirmedPayload settlementPayload(BankMatch match, ExpectedBankMovement movement,
        ClearingAccount account, BankStatementLine line) {
        String currency = movement.getCurrency();
        ChannelSettlementConfirmedPayload payload = new ChannelSettlementConfirmedPayload();
        payload.setBankMatchId(match.getId());
        payload.setExpectedMovementId(movement.getId());
        payload.setStatementId(movement.getSourceId());
        payload.setClearingAccountId(account.getId());
        payload.setClearingAccountCode(account.getClearingAccountCode());
        payload.setCashAccountId(movement.getCashAccountId());
        payload.setEntityNo(movement.getEntityNo());
        payload.setCurrency(currency);
        payload.setDirection(movement.getDirection().getCode());
        payload.setMatchedAmount(ReconciliationEventPayloads.scaled(currency, match.getMatchedAmount()));
        payload.setBankStatementLineId(line.getId());
        payload.setValueDate(line.getValueDate());
        payload.setConfirmedTime(match.getDecisionTime());
        return payload;
    }

    /** 对账调整载荷：主体、币种取自异常（来源父链确定）。 */
    static ReconAdjustmentApprovedPayload adjustmentPayload(ReconResolution resolution, ReconException exception) {
        ReconAdjustmentApprovedPayload payload = new ReconAdjustmentApprovedPayload();
        payload.setResolutionId(resolution.getId());
        payload.setExceptionId(exception.getId());
        payload.setSourceType(exception.getSourceType().getCode());
        payload.setSourceId(exception.getSourceId());
        payload.setClassificationCode(resolution.getClassificationCode().getCode());
        payload.setEntityType(exception.getEntityType().getCode());
        payload.setEntityId(exception.getEntityId());
        payload.setCurrency(exception.getCurrency());
        payload.setAmount(ReconciliationEventPayloads.scaled(exception.getCurrency(), resolution.getAmount()));
        boolean platformTxn = exception.getSourceType() == ReconExceptionSourceType.PLATFORM_TXN;
        payload.setClearingAccountId(platformTxn ? exception.getClearingAccountId() : null);
        payload.setCashAccountId(platformTxn ? null : exception.getCashAccountId());
        payload.setMakerId(resolution.getMakerId());
        payload.setCheckerId(resolution.getCheckerId());
        payload.setApprovedContentHash(resolution.getApprovedContentHash());
        payload.setApprovedTime(resolution.getDecisionTime());
        return payload;
    }
}

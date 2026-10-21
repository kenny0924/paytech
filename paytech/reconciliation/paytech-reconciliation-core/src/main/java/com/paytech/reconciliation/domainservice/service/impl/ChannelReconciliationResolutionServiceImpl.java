package com.paytech.reconciliation.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.ChannelStatementLines;
import com.paytech.reconciliation.domain.PlatformTxns;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.service.ChannelReconciliationResolutionService;
import com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler;

import lombok.AllArgsConstructor;

/** 交易层处置应用；事务传播为 REQUIRED，加入 C 线处置批准事务。 */
@Service
@AllArgsConstructor
public class ChannelReconciliationResolutionServiceImpl implements ChannelReconciliationResolutionService {
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 校验后把行匹配到平台流水并累加平台流水进度。 */
    @Override
    public ChannelStatementLine applyLineRematch(Long lineId, Long platformTxnId, Long resolutionId) {
        return transactionTemplate.execute(status -> {
            ChannelStatementLine line = loadOpenLine(lineId);
            PlatformTxn txn = loadRematchTarget(line, platformTxnId);
            List<ChannelStatementLine> statementLines = channelStatementLineRepository.listByStatement(
                line.getStatementId());
            BigDecimal fee = ChannelStatementLines.actualFee(line, statementLines);
            channelStatementLineRepository.updateMatch(line, txn.getId(), MatchMethod.MANUAL, now(), fee, resolutionId,
                LineMatchStatus.MATCHED);
            BigDecimal matched = txn.getMatchedAmount().add(line.getAmount());
            platformTxnRepository.updateProgress(txn, matched, txn.getActualFee().add(fee), txn.getSettledAmount(),
                PlatformTxns.progressStatus(txn, matched));
            return line;
        });
    }

    /** 行写处置 ID 并转 MATCHED。 */
    @Override
    public ChannelStatementLine applyLineClassification(Long lineId, Long resolutionId) {
        return transactionTemplate.execute(status -> {
            ChannelStatementLine line = loadOpenLine(lineId);
            channelStatementLineRepository.updateMatch(line, null, null, now(), null, resolutionId,
                LineMatchStatus.MATCHED);
            return line;
        });
    }

    /** 剩余未结清金额视为结清。 */
    @Override
    public PlatformTxn applyPlatformTxnClassification(Long platformTxnId, BigDecimal signedAmount) {
        return transactionTemplate.execute(status -> {
            PlatformTxn txn = platformTxnRepository.findById(platformTxnId);
            if (txn == null || txn.getStatus() == PlatformTxnStatus.SETTLED) {
                throw new BizCheckFailException("平台流水不存在或已结清，不能归类: " + platformTxnId);
            }
            BigDecimal unsettled = txn.getExpectedAmount().subtract(txn.getSettledAmount());
            if (signedAmount == null || signedAmount.abs().compareTo(unsettled) != 0) {
                throw new BizCheckFailException("归类金额与平台流水未结清金额不符: 平台流水 " + platformTxnId + ", 未结清 "
                    + unsettled.toPlainString() + ", 归类金额 " + signedAmount);
            }
            platformTxnRepository.updateProgress(txn, txn.getMatchedAmount(), txn.getActualFee(),
                txn.getExpectedAmount(), PlatformTxnStatus.SETTLED);
            return txn;
        });
    }

    /** 行 EXCEPTION → UNMATCHED。 */
    @Override
    public ChannelStatementLine releaseLineForRematching(Long lineId) {
        return transactionTemplate.execute(status -> {
            ChannelStatementLine line = loadOpenLine(lineId);
            if (line.getMatchStatus() != LineMatchStatus.EXCEPTION) {
                throw new BizCheckFailException("账单行不在异常状态，不能交还匹配: " + lineId + ", 当前状态 "
                    + line.getMatchStatus().getCode());
            }
            channelStatementLineRepository.updateMatch(line, null, null, null, null, null, LineMatchStatus.UNMATCHED);
            return line;
        });
    }

    /** 登记处置来源的匹配任务。 */
    @Override
    public void scheduleStatementEvaluation(Long statementId, Long resolutionId) {
        transactionTemplate.executeWithoutResult(status -> compensationEventRegistrar.register(
            ChannelStatementMatchHandler.buildResolutionEvent(statementId, resolutionId)));
    }

    /** 读取尚未匹配（UNMATCHED / EXCEPTION）且所属账单 MATCHING 的行。 */
    ChannelStatementLine loadOpenLine(Long lineId) {
        ChannelStatementLine line = channelStatementLineRepository.findById(lineId);
        if (line == null || line.getMatchStatus() == LineMatchStatus.MATCHED) {
            throw new BizCheckFailException("账单行不存在或已匹配: " + lineId);
        }
        ChannelStatement statement = channelStatementRepository.findById(line.getStatementId());
        if (statement.getStatus() != ChannelStatementStatus.MATCHING) {
            throw new BizCheckFailException("账单行所属渠道账单不在匹配中，不能处置: 账单行 " + lineId + ", 账单状态 "
                + statement.getStatus().getCode());
        }
        return line;
    }

    /** 重新匹配目标：同清算账户、方向一致、可匹配、金额不超剩余。 */
    PlatformTxn loadRematchTarget(ChannelStatementLine line, Long platformTxnId) {
        PlatformTxn txn = platformTxnRepository.findById(platformTxnId);
        String context = "账单行 " + line.getId() + ", 平台流水 " + platformTxnId;
        if (txn == null || !txn.getClearingAccountId().equals(line.getClearingAccountId())) {
            throw new BizCheckFailException("重新匹配的平台流水不存在或不属于同一清算账户: " + context);
        }
        if (txn.getDirection() != ChannelStatementLines.platformDirection(line.getLineType())) {
            throw new BizCheckFailException("重新匹配的平台流水方向与账单行类型不一致: " + context);
        }
        if (!PlatformTxns.matchable(txn)) {
            throw new BizCheckFailException("重新匹配的平台流水已完全匹配或已结清: " + context);
        }
        BigDecimal remaining = txn.getExpectedAmount().subtract(txn.getMatchedAmount());
        if (line.getAmount().compareTo(remaining) > 0) {
            throw new BizCheckFailException("账单行金额超过平台流水剩余金额: " + context + ", 行金额 "
                + line.getAmount().toPlainString() + ", 剩余 " + remaining.toPlainString());
        }
        return txn;
    }

    /** 写入时间：UTC 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

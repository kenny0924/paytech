package com.paytech.reconciliation.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domain.BankMatching;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.service.handler.ChannelSettlementConfirmedHandler;
import lombok.AllArgsConstructor;

/** 银行匹配确认与银行层归类；事务传播为 REQUIRED，加入调用方事务。 */
@Service
@AllArgsConstructor
public class BankMatchServiceImpl implements BankMatchService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final BankMatchRepository bankMatchRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    /** 插入建议。 */
    @Override
    public void propose(List<BankMatch> proposals) {
        if (proposals.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> proposals.forEach(bankMatchRepository::insert));
    }

    /** 确认全部分配并更新流水进度。 */
    @Override
    public List<BankMatch> confirm(BankMatchContext context) {
        return transactionTemplate.execute(status -> {
            Instant now = now();
            List<BankMatch> confirmed = new ArrayList<>();
            for (BankMatchContext.Allocation allocation : context.getAllocations()) {
                confirmed.add(confirmAllocation(context, allocation, now));
            }
            BankStatementLine line = context.getLine();
            BigDecimal matched = context.newLineMatchedAmount();
            bankStatementLineRepository.updateMatch(line, matched, BankMatching.lineStatus(line, matched));
            return confirmed;
        });
    }

    /** 一条分配：匹配转 / 建 CONFIRMED，预期流水累加已确认金额，渠道来源推进账单并登记到账事件。 */
    BankMatch confirmAllocation(BankMatchContext context, BankMatchContext.Allocation allocation, Instant now) {
        BankMatch match = allocation.getProposal();
        if (match == null) {
            match = newMatch(context.getLine(), allocation.getMovement(), allocation.getAmount(), now);
            match.setMatchMethod(context.getMatchMethod());
            match.setStatus(BankMatchStatus.CONFIRMED);
            match.setResolutionId(context.getResolutionId());
            match.setOperatorId(context.getOperatorId());
            match.setDecisionTime(now);
            bankMatchRepository.insert(match);
        } else {
            bankMatchRepository.decide(match, BankMatchStatus.CONFIRMED, context.getOperatorId(), now);
        }
        ExpectedBankMovement movement = allocation.getMovement();
        BigDecimal confirmedAmount = movement.getConfirmedAmount().add(allocation.getAmount());
        saveMovementProgress(movement, confirmedAmount, movement.getClassifiedAmount(),
            context.getChannelStatements().get(movement.getId()));
        if (movement.getMovementType() == ExpectedMovementType.CHANNEL_SETTLEMENT) {
            compensationEventRegistrar.register(ChannelSettlementConfirmedHandler.buildEvent(match.getId()));
        }
        return match;
    }

    /** 拒绝建议。 */
    @Override
    public void reject(BankMatch proposal, Long operatorId) {
        transactionTemplate.executeWithoutResult(status -> bankMatchRepository.decide(proposal,
            BankMatchStatus.REJECTED, operatorId, now()));
    }

    /** 预期流水归类。 */
    @Override
    public void classifyMovement(ExpectedBankMovement movement, ChannelStatement channelStatement, BigDecimal amount) {
        transactionTemplate.executeWithoutResult(status -> saveMovementProgress(movement, movement.getConfirmedAmount(),
            movement.getClassifiedAmount().add(amount), channelStatement));
    }

    /** 银行流水归类。 */
    @Override
    public void classifyLine(BankStatementLine line) {
        transactionTemplate.executeWithoutResult(status -> bankStatementLineRepository.updateMatch(line,
            line.getMatchedAmount(), LineMatchStatus.MATCHED));
    }

    /** 更新预期流水进度；渠道来源时账单到账确认金额取已确认 + 已归类，满额 CASH_CONFIRMED。 */
    void saveMovementProgress(ExpectedBankMovement movement, BigDecimal confirmedAmount, BigDecimal classifiedAmount,
        ChannelStatement channelStatement) {
        expectedBankMovementRepository.updateProgress(movement, confirmedAmount, classifiedAmount,
            BankMatching.movementStatus(movement, confirmedAmount, classifiedAmount));
        if (channelStatement == null) {
            return;
        }
        BigDecimal cashConfirmed = confirmedAmount.add(classifiedAmount);
        ChannelStatementStatus target = cashConfirmed.compareTo(channelStatement.getNetAmount()) == 0
            ? ChannelStatementStatus.CASH_CONFIRMED : ChannelStatementStatus.RECONCILED;
        channelStatementRepository.updateCashConfirmed(channelStatement, cashConfirmed, target);
    }

    /** 新匹配记录（状态与决定字段由调用方填写）。 */
    BankMatch newMatch(BankStatementLine line, ExpectedBankMovement movement, BigDecimal amount, Instant now) {
        BankMatch match = new BankMatch();
        match.setId(idGenerator.nextId());
        match.setBankStatementLineId(line.getId());
        match.setExpectedMovementId(movement.getId());
        match.setMatchedAmount(amount);
        match.setVersion(1L);
        match.setCreateTime(now);
        match.setUpdateTime(now);
        return match;
    }

    /** 写入时间：UTC 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

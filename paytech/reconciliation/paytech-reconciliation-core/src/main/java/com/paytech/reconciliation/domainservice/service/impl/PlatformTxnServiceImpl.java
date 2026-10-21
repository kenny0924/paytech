package com.paytech.reconciliation.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.PlatformTxnFact;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.service.PlatformTxnService;
import com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler;

import lombok.AllArgsConstructor;

/** 平台流水登记；事务传播为 REQUIRED，与消费登记（t_consumed_event）同一事务提交。 */
@Service
@AllArgsConstructor
public class PlatformTxnServiceImpl implements PlatformTxnService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 校验清算账户与外部交易号唯一性后登记，并唤醒同交易号的待匹配账单。 */
    @Override
    public PlatformTxn record(PlatformTxnFact fact) {
        return transactionTemplate.execute(status -> {
            PlatformTxn existing = platformTxnRepository.findByBillingFactId(fact.getBillingFactId());
            if (existing != null) {
                return existing;
            }
            ClearingAccount account = checkClearingAccount(fact);
            checkReferenceUnused(fact);
            PlatformTxn txn = build(fact, account, clock.instant().truncatedTo(ChronoUnit.MICROS));
            platformTxnRepository.insert(txn);
            scheduleWaitingStatements(txn);
            return txn;
        });
    }

    /** 清算账户存在且 ACTIVE（否则可重试），经营主体与币种一致（否则拒收）。 */
    ClearingAccount checkClearingAccount(PlatformTxnFact fact) {
        ClearingAccount account = clearingAccountRepository.findById(fact.getClearingAccountId());
        if (account == null || account.getStatus() != ClearingAccountStatus.ACTIVE) {
            throw new ErrorException("计费事实的清算账户不存在或已停用，等待清算账户就绪后重试: 清算账户 "
                + fact.getClearingAccountId() + ", 事实 " + fact.getBillingFactId());
        }
        if (!account.getEntityNo().equals(fact.getEntityNo()) || !account.getCurrency().equals(fact.getCurrency())) {
            throw new BizCheckFailException("计费事实的经营主体或币种与清算账户不一致: 事实 " + fact.getBillingFactId()
                + ", 事实主体 " + fact.getEntityNo() + "/" + fact.getCurrency() + ", 清算账户 " + account.getEntityNo() + "/"
                + account.getCurrency());
        }
        return account;
    }

    /** 同一清算账户 + 外部交易号 + 方向只能由一条事实登记。 */
    void checkReferenceUnused(PlatformTxnFact fact) {
        boolean used = platformTxnRepository.listByReference(fact.getClearingAccountId(), fact.getReferenceType(),
            fact.getExternalTransactionId()).stream().anyMatch(txn -> txn.getDirection() == fact.getDirection());
        if (used) {
            throw new BizCheckFailException("同一外部交易号与方向已登记平台流水: 清算账户 " + fact.getClearingAccountId()
                + ", 交易号 " + fact.getReferenceType() + "/" + fact.getExternalTransactionId() + ", 事实 "
                + fact.getBillingFactId());
        }
    }

    /** 为同交易号仍有待匹配行（UNMATCHED，或已挂未命中异常）的 MATCHING 账单登记匹配任务。 */
    void scheduleWaitingStatements(PlatformTxn txn) {
        List<Long> statementIds = channelStatementLineRepository.listByReference(txn.getClearingAccountId(),
            txn.getReferenceType(), txn.getExternalTransactionId()).stream()
            .filter(line -> line.getMatchStatus() != LineMatchStatus.MATCHED)
            .map(ChannelStatementLine::getStatementId).distinct().toList();
        for (ChannelStatement statement : channelStatementRepository.findByIds(statementIds)) {
            if (statement.getStatus() == ChannelStatementStatus.MATCHING) {
                compensationEventRegistrar.register(
                    ChannelStatementMatchHandler.buildPlatformTxnEvent(statement.getId(), txn.getId()));
            }
        }
    }

    /** 由事实构造 OPEN 平台流水。 */
    PlatformTxn build(PlatformTxnFact fact, ClearingAccount account, Instant now) {
        PlatformTxn txn = new PlatformTxn();
        txn.setId(idGenerator.nextId());
        txn.setClearingAccountId(account.getId());
        txn.setDirection(fact.getDirection());
        txn.setTxnSource(fact.getTxnSource());
        txn.setBillingFactId(fact.getBillingFactId());
        txn.setEntityNo(fact.getEntityNo());
        txn.setSourceRefType(fact.getSourceRefType());
        txn.setSourceRefId(fact.getSourceRefId());
        txn.setChannelTransactionRefId(fact.getChannelTransactionRefId());
        txn.setReferenceType(fact.getReferenceType());
        txn.setExternalTransactionId(fact.getExternalTransactionId());
        txn.setCurrency(fact.getCurrency());
        txn.setExpectedAmount(fact.getExpectedAmount());
        txn.setExpectedFee(fact.getExpectedFee());
        txn.setMatchedAmount(BigDecimal.ZERO);
        txn.setActualFee(BigDecimal.ZERO);
        txn.setSettledAmount(BigDecimal.ZERO);
        txn.setStatus(PlatformTxnStatus.OPEN);
        txn.setBusinessTime(fact.getBusinessTime());
        txn.setVersion(1L);
        txn.setCreateTime(now);
        txn.setUpdateTime(now);
        return txn;
    }
}

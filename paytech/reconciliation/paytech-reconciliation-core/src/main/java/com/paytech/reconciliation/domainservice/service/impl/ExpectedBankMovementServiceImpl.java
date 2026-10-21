package com.paytech.reconciliation.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.service.handler.BankMatchSuggestHandler;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.service.ExpectedBankMovementService;

import lombok.AllArgsConstructor;

/**
 * 预期银行流水生成；事务传播为 REQUIRED，与账单对平同一事务提交
 * <li>同一事务登记该银行账户的 BANK_MATCH_SUGGEST（子类型 EXPECTED_MOVEMENT-&lt;id&gt;），对仍未匹配的流水重新出建议（设计 F2，C 线）。</li>
 */
@Service
@AllArgsConstructor
public class ExpectedBankMovementServiceImpl implements ExpectedBankMovementService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    /** 生成渠道净额的预期银行流水并登记匹配建议任务。 */
    @Override
    public ExpectedBankMovement createChannelSettlement(ChannelStatement statement, ClearingAccount clearingAccount) {
        ExpectedBankMovement movement = channelSettlement(statement, clearingAccount);
        transactionTemplate.executeWithoutResult(status -> {
            expectedBankMovementRepository.insert(movement);
            compensationEventRegistrar.register(BankMatchSuggestHandler.buildMovementEvent(movement.getCashAccountId(),
                movement.getId()));
        });
        return movement;
    }

    /** 由账单与清算账户构造 OPEN 预期流水。 */
    ExpectedBankMovement channelSettlement(ChannelStatement statement, ClearingAccount clearingAccount) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ExpectedBankMovement movement = new ExpectedBankMovement();
        movement.setId(idGenerator.nextId());
        movement.setMovementType(ExpectedMovementType.CHANNEL_SETTLEMENT);
        movement.setSourceId(statement.getId());
        movement.setCashAccountId(clearingAccount.getSettlementCashAccountId());
        movement.setEntityNo(clearingAccount.getEntityNo());
        movement.setDirection(statement.getCashDirection() == CashDirection.CREDIT ? SettlementDirection.CREDIT
            : SettlementDirection.DEBIT);
        movement.setCurrency(statement.getCurrency());
        movement.setExpectedAmount(statement.getNetAmount());
        movement.setExpectedDate(statement.getSettlementDate());
        movement.setConfirmedAmount(BigDecimal.ZERO);
        movement.setClassifiedAmount(BigDecimal.ZERO);
        movement.setStatus(ExpectedMovementStatus.OPEN);
        movement.setVersion(1L);
        movement.setCreateTime(now);
        movement.setUpdateTime(now);
        return movement;
    }
}

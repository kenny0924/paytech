package com.paytech.reconciliation.domainservice.service.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domain.view.BankStatementLineDetailView;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import lombok.AllArgsConstructor;

/** 银行层查询与确认匹配上下文装载；只读。 */
@Service
@AllArgsConstructor
public class BankReconciliationQueryServiceImpl implements BankReconciliationQueryService {
    private final BankMatchRepository bankMatchRepository;
    private final CashAccountRepository cashAccountRepository;
    private final BankStatementRepository bankStatementRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;
    private final ReconExceptionRepository reconExceptionRepository;

    /** 装载流水、对账单、预期流水与渠道来源账单。 */
    @Override
    public BankMatchContext loadMatchContext(Long bankStatementLineId, List<BankMatchContext.AllocationInput> allocations,
        MatchMethod matchMethod, Long operatorId, Long resolutionId) {
        BankStatementLine line = bankStatementLineRepository.findById(bankStatementLineId);
        if (line == null) {
            throw new BizCheckFailException("银行流水不存在: " + bankStatementLineId);
        }
        BankStatement statement = bankStatementRepository.findById(line.getStatementId());
        Map<Long, ChannelStatement> channelStatements = new HashMap<>();
        List<BankMatchContext.Allocation> loaded = allocations.stream().map(input -> {
            ExpectedBankMovement movement = loadMovement(input.getExpectedMovementId());
            if (movement.getMovementType() == ExpectedMovementType.CHANNEL_SETTLEMENT) {
                channelStatements.put(movement.getId(), channelStatementRepository.findById(movement.getSourceId()));
            }
            return new BankMatchContext.Allocation(movement, input.getAmount(), input.getProposal());
        }).toList();
        return new BankMatchContext(line, statement, loaded, channelStatements, matchMethod, operatorId, resolutionId);
    }

    /** 读取预期流水，不存在为业务拒绝。 */
    ExpectedBankMovement loadMovement(Long expectedMovementId) {
        ExpectedBankMovement movement = expectedBankMovementRepository.findById(expectedMovementId);
        if (movement == null) {
            throw new BizCheckFailException("预期银行流水不存在: " + expectedMovementId);
        }
        return movement;
    }

    /** 读取银行账户。 */
    @Override
    public CashAccount getCashAccount(long cashAccountId) {
        CashAccount account = cashAccountRepository.findById(cashAccountId);
        if (account == null) {
            throw new BizCheckFailException("银行账户不存在: " + cashAccountId);
        }
        return account;
    }

    /** 当前 ACTIVE 银行账户。 */
    @Override
    public CashAccount getCurrentCashAccount(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose purpose) {
        List<CashAccount> active = cashAccountRepository.listByScope(entityType, entityId, currency, purpose,
            CashAccountStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new BizCheckFailException("没有有效的银行账户: " + entityType.getCode() + " " + entityId + ", " + currency
                + ", " + purpose.getCode());
        }
        return active.getFirst();
    }

    /** 分页查询银行账户。 */
    @Override
    public PagingResponseBody<CashAccount> listCashAccounts(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose purpose, CashAccountStatus status, PagingRequestBody paging) {
        List<CashAccount> items = cashAccountRepository.findPage(entityType, entityId, currency, purpose, status,
            paging.offset(), paging.size());
        long total = cashAccountRepository.countPage(entityType, entityId, currency, purpose, status);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 读取银行对账单。 */
    @Override
    public BankStatement getBankStatement(long bankStatementId) {
        BankStatement statement = bankStatementRepository.findById(bankStatementId);
        if (statement == null) {
            throw new BizCheckFailException("银行对账单不存在: " + bankStatementId);
        }
        return statement;
    }

    /** 详情页读取对账单，不存在为 404。 */
    @Override
    public BankStatement readBankStatement(long bankStatementId) {
        BankStatement statement = bankStatementRepository.findById(bankStatementId);
        if (statement == null) {
            throw new FailException(ReconciliationErrorCode.BANK_STATEMENT_NOT_FOUND, "银行对账单不存在: " + bankStatementId);
        }
        return statement;
    }

    /** 银行流水详情：流水、对账单、银行账户、匹配（预期流水按主键批量读取）与未结异常，共 6 次查询。 */
    @Override
    public BankStatementLineDetailView readBankStatementLine(long bankStatementLineId) {
        BankStatementLine line = bankStatementLineRepository.findById(bankStatementLineId);
        if (line == null) {
            throw new FailException(ReconciliationErrorCode.BANK_STATEMENT_LINE_NOT_FOUND,
                "银行流水不存在: " + bankStatementLineId);
        }
        List<BankMatch> matches = bankMatchRepository.listByLine(line.getId(), null);
        Map<Long, ExpectedBankMovement> movements = expectedBankMovementRepository.findByIds(matches.stream()
            .map(BankMatch::getExpectedMovementId).distinct().toList()).stream()
            .collect(Collectors.toMap(ExpectedBankMovement::getId, Function.identity()));
        List<BankStatementLineDetailView.MatchItem> items = matches.stream()
            .map(match -> new BankStatementLineDetailView.MatchItem(match, movements.get(match.getExpectedMovementId())))
            .toList();
        return new BankStatementLineDetailView(line, bankStatementRepository.findById(line.getStatementId()),
            cashAccountRepository.findById(line.getCashAccountId()), items,
            reconExceptionRepository.listActiveBySource(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()));
    }

    /** 分页查询银行对账单。 */
    @Override
    public PagingResponseBody<BankStatement> listBankStatements(Long cashAccountId, BankStatementStatus status,
        PagingRequestBody paging) {
        List<BankStatement> items = bankStatementRepository.findPage(cashAccountId, status, paging.offset(),
            paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            bankStatementRepository.countPage(cashAccountId, status));
    }

    /** 分页查询银行流水。 */
    @Override
    public PagingResponseBody<BankStatementLine> listBankStatementLines(Long statementId, Long cashAccountId,
        LineMatchStatus matchStatus, SettlementDirection direction, LocalDate valueDateFrom, LocalDate valueDateTo,
        PagingRequestBody paging) {
        List<BankStatementLine> items = bankStatementLineRepository.findPage(statementId, cashAccountId, matchStatus,
            direction, valueDateFrom, valueDateTo, paging.offset(), paging.size());
        long total = bankStatementLineRepository.countPage(statementId, cashAccountId, matchStatus, direction,
            valueDateFrom, valueDateTo);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 分页查询预期银行流水。 */
    @Override
    public PagingResponseBody<ExpectedBankMovement> listExpectedMovements(Long cashAccountId,
        ExpectedMovementType movementType, Long sourceId, ExpectedMovementStatus status, PagingRequestBody paging) {
        List<ExpectedBankMovement> items = expectedBankMovementRepository.findPage(cashAccountId, movementType, sourceId,
            status, paging.offset(), paging.size());
        long total = expectedBankMovementRepository.countPage(cashAccountId, movementType, sourceId, status);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 分页查询银行匹配。 */
    @Override
    public PagingResponseBody<BankMatch> listBankMatches(Long bankStatementLineId, Long expectedMovementId,
        BankMatchStatus status, PagingRequestBody paging) {
        if (bankStatementLineId == null && expectedMovementId == null) {
            throw new InvalidParameterException("bankStatementLineId 与 expectedMovementId 至少给一个");
        }
        List<BankMatch> items = bankMatchRepository.findPage(bankStatementLineId, expectedMovementId, status,
            paging.offset(), paging.size());
        long total = bankMatchRepository.countPage(bankStatementLineId, expectedMovementId, status);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 匹配所属预期流水的币种。 */
    @Override
    public String currencyOf(BankMatch match) {
        return loadMovement(match.getExpectedMovementId()).getCurrency();
    }
}

package com.paytech.reconciliation.auto.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.ChannelLineMatchChange;
import com.paytech.reconciliation.domain.ChannelLineSearch;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domain.PlatformTxnProgressChange;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.test.base.MockTestBase;
import com.paytech.reconciliation.test.builder.BankStatementBuilder;
import com.paytech.reconciliation.test.builder.CashAccountBuilder;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.ExpectedBankMovementBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.builder.ReconExceptionBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 12 张业务表的 MyBatis 映射与条件更新（真实本地 mor_reconciliation）：每张表的插入、主键 / 业务键读取、分页与计数、唯一键冲突，以及
 * 条件更新成功后同步对象、版本或原状态不符时抛并发冲突且不改对象
 * <li>业务入口的场景测试由 B / C 线覆盖；本类守住共享持久化层的 SQL、枚举 Code 映射与 UTC 时间映射，B / C 扩展 Mapper 时同步补充。</li>
 *
 * @see ClearingAccountRepository
 * @see PlatformTxnRepository
 * @see ChannelStatementLineRepository
 * @see BankMatchRepository
 * @see ReconExceptionRepository
 */
class PersistenceMappingTest extends MockTestBase {
    @Autowired
    private ClearingAccountRepository clearingAccounts;

    @Autowired
    private ClearingAccountMidRepository clearingAccountMids;

    @Autowired
    private PlatformTxnRepository platformTxns;

    @Autowired
    private ChannelStatementRepository channelStatements;

    @Autowired
    private ChannelStatementLineRepository channelStatementLines;

    @Autowired
    private CashAccountRepository cashAccounts;

    @Autowired
    private BankStatementRepository bankStatements;

    @Autowired
    private BankStatementLineRepository bankStatementLines;

    @Autowired
    private ExpectedBankMovementRepository expectedMovements;

    @Autowired
    private BankMatchRepository bankMatches;

    @Autowired
    private ReconExceptionRepository reconExceptions;

    @Autowired
    private ReconResolutionRepository reconResolutions;

    /** 清算账户：编码唯一、配置与状态的条件更新；MID + 币种唯一、按 MID 读取与摘除。 */
    @Test
    void test_clearingAccountAndMid() {
        ClearingAccount account = ClearingAccountBuilder.template().build();
        clearingAccounts.insert(account);
        ClearingAccount duplicate = ClearingAccountBuilder.template()
            .with(item -> item.setClearingAccountCode(account.getClearingAccountCode())).build();
        assertThrows(ConcurrentRequestException.class, () -> clearingAccounts.insert(duplicate));

        ClearingAccount loaded = clearingAccounts.findByCode(account.getClearingAccountCode());
        assertEquals(ClearingAccountStatus.ACTIVE, loaded.getStatus());
        assertEquals(0, new BigDecimal("0").compareTo(loaded.getCostToleranceAmount()));
        assertEquals(1, clearingAccounts.countPage(ReconciliationTestConstants.ENTITY_NO, null,
            ReconciliationTestConstants.CURRENCY, ClearingAccountStatus.ACTIVE));
        assertEquals(List.of(account.getId()), clearingAccounts.findPage(null, ReconciliationTestConstants.CHANNEL_CODE,
            null, null, 0, 10).stream().map(ClearingAccount::getId).toList());

        clearingAccounts.updateSettings(loaded, 5, new BigDecimal("0.10"), 1, 42L);
        assertEquals(2L, loaded.getVersion());
        ClearingAccount stale = clearingAccounts.findById(account.getId());
        stale.setVersion(1L);
        assertThrows(ConcurrentRequestException.class, () -> clearingAccounts.changeStatus(stale,
            ClearingAccountStatus.INACTIVE));
        assertEquals(ClearingAccountStatus.ACTIVE, stale.getStatus());
        clearingAccounts.changeStatus(loaded, ClearingAccountStatus.INACTIVE);
        clearingAccounts.increaseVersion(loaded);
        ClearingAccount reloaded = clearingAccounts.findById(account.getId());
        assertEquals(4L, reloaded.getVersion());
        assertEquals(42L, reloaded.getSettlementCashAccountId());
        assertEquals(ClearingAccountStatus.INACTIVE, reloaded.getStatus());

        long midId = ReconciliationTestConstants.nextId();
        ClearingAccountMid mid = ClearingAccountBuilder.mid(account, midId);
        clearingAccountMids.insert(mid);
        assertThrows(ConcurrentRequestException.class,
            () -> clearingAccountMids.insert(ClearingAccountBuilder.mid(account, midId)));
        assertEquals(mid.getId(), clearingAccountMids.findByMidAndCurrency(midId, account.getCurrency()).getId());
        assertEquals(1, clearingAccountMids.listByMidIds(List.of(midId, midId + 1)).size());
        clearingAccountMids.delete(mid);
        assertThrows(ConcurrentRequestException.class, () -> clearingAccountMids.delete(mid));
        assertEquals(List.of(), clearingAccountMids.listByClearingAccount(account.getId()));
    }

    /** 平台流水与渠道账单 / 行：业务键读取、按交易号跨账单读取、等待期查询与条件更新。 */
    @Test
    void test_platformTxnAndChannelStatement() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        PlatformTxn payment = PlatformTxnBuilder.payment(account, "TXN-1", "100.00", "0.45").build();
        platformTxns.insert(payment);
        assertEquals(payment.getId(), platformTxns.findByBillingFactId(payment.getBillingFactId()).getId());
        assertEquals(1, platformTxns.listByReference(account.getId(), payment.getReferenceType(), "TXN-1").size());
        Instant from = payment.getBusinessTime().minus(1, ChronoUnit.MINUTES);
        assertEquals(1, platformTxns.countPage(account.getId(), null, PlatformTxnStatus.OPEN, null, "TXN-1", from, null));
        platformTxns.updateProgress(payment, new BigDecimal("100.00"), new BigDecimal("0.45"), BigDecimal.ZERO,
            PlatformTxnStatus.MATCHED);
        assertEquals(PlatformTxnStatus.MATCHED, platformTxns.findById(payment.getId()).getStatus());

        ChannelStatement statement = ChannelStatementBuilder.matching(account, "BATCH-1", "100.00", "0.45", "99.55",
            CashDirection.CREDIT, 2).build();
        ChannelStatementLine line = ChannelStatementBuilder.line(statement, 1, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, "100.00", "0.45", payment.getReferenceType(), "TXN-1");
        ChannelStatementLine adjustment = ChannelStatementBuilder.line(statement, 2, ChannelLineType.ADJUSTMENT,
            SettlementDirection.DEBIT, "0", "0", null, null);
        adjustment.setRawAttributes("{\"memo\":\"fee rebate\"}");
        channelStatements.insert(statement);
        channelStatementLines.insertBatch(List.of(line, adjustment));
        assertThrows(ConcurrentRequestException.class, () -> channelStatementLines.insertBatch(List.of(
            ChannelStatementBuilder.line(statement, 2, ChannelLineType.FEE, SettlementDirection.DEBIT, "1", "0", null,
                null))));

        assertEquals(1, channelStatements.listByBatch(account.getId(), "BATCH-1").size());
        assertEquals(1, channelStatements.countPage(account.getId(), null, ChannelStatementStatus.MATCHING,
            statement.getSettlementDate(), statement.getSettlementDate()));
        assertEquals(List.of(1, 2), channelStatementLines.listByStatement(statement.getId()).stream()
            .map(ChannelStatementLine::getLineNo).toList());
        assertEquals("{\"memo\": \"fee rebate\"}", channelStatementLines.listByStatementAndLineNos(statement.getId(),
            List.of(2)).getFirst().getRawAttributes());
        assertEquals(1, channelStatementLines.listByReference(account.getId(), payment.getReferenceType(),
            "TXN-1").size());
        assertEquals(2, channelStatementLines.listUnmatchedCreatedBefore(account.getId(),
            Instant.now().plus(1, ChronoUnit.MINUTES), 10).size());
        assertEquals(List.of(line.getId()), channelStatementLines.listWaitExpired(account.getId(),
            Instant.now().plus(1, ChronoUnit.MINUTES), 10).stream().map(ChannelStatementLine::getId).toList());

        Instant matchedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        channelStatementLines.updateMatch(line, payment.getId(), MatchMethod.AUTO, matchedTime, new BigDecimal("0.45"),
            null, LineMatchStatus.MATCHED);
        ChannelStatementLine matched = channelStatementLines.listByPlatformTxn(payment.getId()).getFirst();
        assertEquals(LineMatchStatus.MATCHED, matched.getMatchStatus());
        assertEquals(matchedTime, matched.getMatchedTime());
        assertEquals(1, channelStatementLines.countPage(statement.getId(), LineMatchStatus.UNMATCHED, null));
        assertEquals(List.of(payment.getId()), platformTxns.findPage(null, statement.getId(), null, null, null, null,
            null, 0, 10).stream().map(PlatformTxn::getId).toList());

        channelStatements.changeStatus(statement, ChannelStatementStatus.RECONCILED, null, matchedTime);
        ChannelStatement wrongStatus = channelStatements.findById(statement.getId());
        wrongStatus.setStatus(ChannelStatementStatus.MATCHING);
        assertThrows(ConcurrentRequestException.class, () -> channelStatements.changeStatus(wrongStatus,
            ChannelStatementStatus.VOIDED, null, null));
        channelStatements.updateCashConfirmed(statement, new BigDecimal("99.55"),
            ChannelStatementStatus.CASH_CONFIRMED);
        ChannelStatement confirmed = channelStatements.findById(statement.getId());
        assertEquals(ChannelStatementStatus.CASH_CONFIRMED, confirmed.getStatus());
        assertEquals(matchedTime, confirmed.getReconciledTime());
    }

    /** 银行层：账户复核、对账单与流水、预期银行流水、匹配建议的确认与重复决定冲突。 */
    @Test
    void test_bankLayer() {
        CashAccount account = CashAccountBuilder.template()
            .with(item -> item.setStatus(CashAccountStatus.PENDING_VERIFICATION)).build();
        cashAccounts.insert(account);
        cashAccounts.updateReview(account, CashAccountStatus.ACTIVE, null, account.getContentHash(),
            account.getMakerId(), ReconciliationTestConstants.CHECKER_ID);
        assertEquals(1, cashAccounts.listByScope(ReconEntityType.OPERATING_ENTITY,
            ReconciliationTestConstants.ENTITY_NO,
            ReconciliationTestConstants.CURRENCY, AccountPurpose.CHANNEL_SETTLEMENT, CashAccountStatus.ACTIVE).size());
        cashAccounts.updateReview(account, CashAccountStatus.ACTIVE, CashAccountPendingAction.CLOSE, "1".repeat(64),
            ReconciliationTestConstants.MAKER_ID, null);
        assertEquals(CashAccountPendingAction.CLOSE, cashAccounts.findById(account.getId()).getPendingAction());

        BankStatement statement = BankStatementBuilder.imported(account, "STMT-1", "-10.00", "57.40", 1).build();
        BankStatementLine credit = BankStatementBuilder.line(statement, 1, "BANK-1", SettlementDirection.CREDIT,
            "67.40");
        bankStatements.insert(statement);
        bankStatementLines.insertBatch(List.of(credit));
        assertEquals(statement.getId(), bankStatements.findLatestImported(account.getId()).getId());
        assertEquals(0, new BigDecimal("-10.00").compareTo(bankStatements.findByRef(account.getId(),
            "STMT-1").getOpeningBalance()));
        assertEquals(1, bankStatementLines.listByTransactionRefs(account.getId(), List.of("BANK-1", "BANK-2")).size());
        assertEquals(1, bankStatementLines.listOpen(account.getId(), SettlementDirection.CREDIT).size());
        assertEquals(0, bankStatementLines.countPage(null, account.getId(), null, SettlementDirection.DEBIT, null,
            null));

        ClearingAccount clearing = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement channelStatement = data.channelStatement(ChannelStatementBuilder.matching(clearing, "BATCH-2",
            "67.55", "0", "67.55", CashDirection.CREDIT, 0).build(), List.of());
        ExpectedBankMovement movement = ExpectedBankMovementBuilder.channelSettlement(channelStatement, account,
            SettlementDirection.CREDIT, "67.55").build();
        expectedMovements.insert(movement);
        assertThrows(ConcurrentRequestException.class, () -> expectedMovements.insert(ExpectedBankMovementBuilder
            .channelSettlement(channelStatement, account, SettlementDirection.CREDIT, "67.55").build()));
        assertEquals(movement.getId(), expectedMovements.findBySource(ExpectedMovementType.CHANNEL_SETTLEMENT,
            channelStatement.getId()).getId());
        assertEquals(1, expectedMovements.listOpen(account.getId()).size());

        BankMatch proposal = proposal(credit, movement, "67.40");
        bankMatches.insert(proposal);
        assertEquals(1, bankMatches.listByLine(credit.getId(), BankMatchStatus.PROPOSED).size());
        Instant decided = Instant.now().truncatedTo(ChronoUnit.MICROS);
        bankMatches.decide(proposal, BankMatchStatus.CONFIRMED, ReconciliationTestConstants.MAKER_ID, decided);
        BankMatch again = bankMatches.findById(proposal.getId());
        again.setVersion(1L);
        assertThrows(ConcurrentRequestException.class,
            () -> bankMatches.decide(again, BankMatchStatus.REJECTED, ReconciliationTestConstants.MAKER_ID, decided));
        assertEquals(1, bankMatches.countPage(null, movement.getId(), BankMatchStatus.CONFIRMED));

        bankStatementLines.updateMatch(credit, new BigDecimal("67.40"), LineMatchStatus.MATCHED);
        expectedMovements.updateProgress(movement, new BigDecimal("67.40"), new BigDecimal("0.15"),
            ExpectedMovementStatus.CONFIRMED);
        assertEquals(List.of(), bankStatementLines.listOpen(account.getId(), null));
        assertEquals(List.of(), expectedMovements.listOpen(account.getId()));
    }

    /** 异常活动槽位：同对象同类型只有一个未结异常，结案后可再开；处置的复核条件更新。 */
    @Test
    void test_exceptionAndResolution() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement statement = ChannelStatementBuilder.matching(account, "BATCH-3", "5.00", "0", "5.00",
            CashDirection.CREDIT, 1).build();
        ChannelStatementLine line = ChannelStatementBuilder.line(statement, 1, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, "5.00", "0", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "TXN-3");
        data.channelStatement(statement, List.of(line));

        ReconException exception = ReconExceptionBuilder.onChannelLine(line, ReconExceptionType.CHANNEL_LINE_UNMATCHED,
            "5.00").build();
        reconExceptions.insert(exception);
        assertThrows(ConcurrentRequestException.class, () -> reconExceptions.insert(ReconExceptionBuilder
            .onChannelLine(line, ReconExceptionType.CHANNEL_LINE_UNMATCHED, "5.00").build()));
        assertEquals(exception.getId(), reconExceptions.findActive(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line.getId(), ReconExceptionType.CHANNEL_LINE_UNMATCHED).getId());
        assertEquals(1, reconExceptions.countPage(List.of(ReconExceptionStatus.OPEN, ReconExceptionStatus.UNDER_REVIEW),
            null, null, account.getId(), null, statement.getId()));

        ReconResolution resolution = resolution(exception);
        reconResolutions.insert(resolution);
        reconExceptions.changeStatus(exception, ReconExceptionStatus.UNDER_REVIEW, null);
        Instant decided = Instant.now().truncatedTo(ChronoUnit.MICROS);
        reconResolutions.review(resolution, ResolutionStatus.APPLIED, ReconciliationTestConstants.CHECKER_ID,
            CheckerDecision.APPROVED, resolution.getContentHash(), decided);
        assertThrows(ConcurrentRequestException.class, () -> reconResolutions.review(reconResolutions.findById(
            resolution.getId()), ResolutionStatus.REJECTED, ReconciliationTestConstants.CHECKER_ID,
            CheckerDecision.REJECTED, resolution.getContentHash(), decided));
        reconExceptions.changeStatus(exception, ReconExceptionStatus.RESOLVED, decided);

        ReconException closed = reconExceptions.findById(exception.getId());
        assertNull(closed.getActiveSlot());
        assertEquals(true, closed.getBlocking());
        assertNull(reconExceptions.findActive(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, line.getId(),
            ReconExceptionType.CHANNEL_LINE_UNMATCHED));
        assertEquals(ReconExceptionStatus.RESOLVED, reconExceptions.findLatestBySource(
            ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, line.getId(), ReconExceptionType.CHANNEL_LINE_UNMATCHED)
            .getStatus());
        assertEquals(List.of(ResolutionStatus.APPLIED), reconResolutions.listByException(exception.getId()).stream()
            .map(ReconResolution::getStatus).toList());
        reconExceptions.insert(ReconExceptionBuilder.onChannelLine(line, ReconExceptionType.CHANNEL_LINE_UNMATCHED,
            "5.00").build());
        assertEquals(1, reconExceptions.listActiveByStatement(statement.getId()).size());
    }

    /** 跨账单查询（渠道流水页）：连接账单按状态过滤、默认排除口径、交易号精确、交易日期区间、排序与计数；对象上全部类型的未结异常。 */
    @Test
    void test_channelLineSearchAndActiveBySource() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement matching = ChannelStatementBuilder.matching(account, "BATCH-S1", "3.00", "0", "3.00",
            CashDirection.CREDIT, 2).build();
        ChannelStatementLine early = ChannelStatementBuilder.line(matching, 1, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, "1.00", "0", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "SRCH-1");
        early.setTransactionDate(LocalDate.of(2026, 10, 1));
        ChannelStatementLine undated = ChannelStatementBuilder.line(matching, 2, ChannelLineType.ADJUSTMENT,
            SettlementDirection.CREDIT, "2.00", "0", null, null);
        undated.setTransactionDate(null);
        data.channelStatement(matching, List.of(early, undated));
        ChannelStatement voided = ChannelStatementBuilder.matching(account, "BATCH-S2", "1.00", "0", "1.00",
            CashDirection.CREDIT, 1).with(item -> item.setStatus(ChannelStatementStatus.VOIDED)).build();
        ChannelStatementLine voidedLine = ChannelStatementBuilder.line(voided, 1, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, "1.00", "0", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "SRCH-1");
        voidedLine.setTransactionDate(LocalDate.of(2026, 10, 5));
        data.channelStatement(voided, List.of(voidedLine));

        ChannelLineSearch defaults = ChannelLineSearch.builder().clearingAccountId(account.getId()).build();
        assertEquals(List.of(early.getId(), undated.getId()), channelStatementLines.searchPage(defaults, 0, 10).stream()
            .map(ChannelStatementLine::getId).toList());
        assertEquals(2, channelStatementLines.countSearch(defaults));
        ChannelLineSearch byReference = ChannelLineSearch.builder().statementStatus(ChannelStatementStatus.VOIDED)
            .referenceType(ReconciliationTestConstants.PAY_REFERENCE_TYPE).externalTransactionId("SRCH-1")
            .transactionDateFrom(LocalDate.of(2026, 10, 5)).transactionDateTo(LocalDate.of(2026, 10, 5))
            .matchStatus(LineMatchStatus.UNMATCHED).lineType(ChannelLineType.PAYMENT).build();
        assertEquals(List.of(voidedLine.getId()), channelStatementLines.searchPage(byReference, 0, 10).stream()
            .map(ChannelStatementLine::getId).toList());
        assertEquals(1, channelStatementLines.countSearch(byReference));

        ReconException open = ReconExceptionBuilder.onChannelLine(early, ReconExceptionType.CHANNEL_LINE_UNMATCHED,
            "1.00").build();
        reconExceptions.insert(open);
        ReconException other = ReconExceptionBuilder.onChannelLine(early, ReconExceptionType.AMOUNT_MISMATCH, "1.00")
            .build();
        reconExceptions.insert(other);
        reconExceptions.changeStatus(other, ReconExceptionStatus.RESOLVED, Instant.now().truncatedTo(ChronoUnit.MICROS));
        assertEquals(List.of(open.getId()), reconExceptions.listActiveBySource(
            ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, early.getId()).stream().map(ReconException::getId).toList());
        assertEquals(List.of(), reconExceptions.listActiveBySource(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            undated.getId()));
    }

    /** 匹配任务的批量读写（缺陷 D-1）：按交易号集合读取、多行条件更新成功同步对象、任一行版本不符抛并发冲突且不同步对象。 */
    @Test
    void test_matchBatchReadsAndWrites() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        PlatformTxn first = PlatformTxnBuilder.payment(account, "BAT-1", "10.00", "0.10").build();
        PlatformTxn second = PlatformTxnBuilder.refund(account, "BAT-2", "4.00").build();
        platformTxns.insert(first);
        platformTxns.insert(second);
        String type = first.getReferenceType();
        List<ExternalReference> references = List.of(new ExternalReference(type, "BAT-1"),
            new ExternalReference(second.getReferenceType(), "BAT-2"), new ExternalReference(type, "BAT-404"));
        assertEquals(List.of(first.getId(), second.getId()), platformTxns.listByReferences(account.getId(), references)
            .stream().map(PlatformTxn::getId).sorted().toList());
        assertEquals(2, platformTxns.findByIds(List.of(first.getId(), second.getId(), -1L)).size());

        platformTxns.updateProgresses(List.of(new PlatformTxnProgressChange(first, new BigDecimal("10.00"),
            new BigDecimal("0.10"), BigDecimal.ZERO, PlatformTxnStatus.MATCHED), new PlatformTxnProgressChange(second,
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, PlatformTxnStatus.PARTIALLY_MATCHED)));
        assertEquals(2L, first.getVersion());
        assertEquals(PlatformTxnStatus.PARTIALLY_MATCHED, platformTxns.findById(second.getId()).getStatus());
        PlatformTxn stale = platformTxns.findById(first.getId());
        stale.setVersion(1L);
        assertThrows(ConcurrentRequestException.class, () -> platformTxns.updateProgresses(List.of(
            new PlatformTxnProgressChange(stale, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, PlatformTxnStatus.OPEN))));
        assertEquals(PlatformTxnStatus.MATCHED, stale.getStatus());

        ChannelStatement statement = ChannelStatementBuilder.matching(account, "BATCH-BAT", "14.00", "0", "6.00",
            CashDirection.CREDIT, 2).build();
        ChannelStatementLine payment = ChannelStatementBuilder.line(statement, 1, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, "10.00", "0", type, "BAT-1");
        ChannelStatementLine refund = ChannelStatementBuilder.line(statement, 2, ChannelLineType.REFUND,
            SettlementDirection.DEBIT, "4.00", "0", second.getReferenceType(), "BAT-2");
        data.channelStatement(statement, List.of(payment, refund));
        assertEquals(List.of(payment.getId(), refund.getId()), channelStatementLines.listByReferences(account.getId(),
            references).stream().map(ChannelStatementLine::getId).sorted().toList());

        Instant matchedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        channelStatementLines.updateMatches(List.of(new ChannelLineMatchChange(payment, first.getId(), MatchMethod.AUTO,
            matchedTime, new BigDecimal("0.10"), null, LineMatchStatus.MATCHED), new ChannelLineMatchChange(refund, null,
                null, null, null, null, LineMatchStatus.EXCEPTION)));
        ChannelStatementLine matched = channelStatementLines.findById(payment.getId());
        assertEquals(first.getId(), matched.getPlatformTxnId());
        assertEquals(matchedTime, matched.getMatchedTime());
        assertEquals(0, new BigDecimal("0.10").compareTo(matched.getActualFee()));
        assertEquals(LineMatchStatus.EXCEPTION, channelStatementLines.findById(refund.getId()).getMatchStatus());
        assertEquals(matched.getVersion(), payment.getVersion());
        ChannelStatementLine staleLine = channelStatementLines.findById(refund.getId());
        staleLine.setVersion(staleLine.getVersion() - 1);
        assertThrows(ConcurrentRequestException.class, () -> channelStatementLines.updateMatches(List.of(
            new ChannelLineMatchChange(staleLine, null, null, null, null, null, LineMatchStatus.UNMATCHED))));
        assertEquals(LineMatchStatus.EXCEPTION, staleLine.getMatchStatus());

        ReconException onPayment = ReconExceptionBuilder.onChannelLine(payment, ReconExceptionType.DUPLICATE_SETTLEMENT,
            "10.00").build();
        ReconException onRefund = ReconExceptionBuilder.onChannelLine(refund, ReconExceptionType.DUPLICATE_SETTLEMENT,
            "-4.00").build();
        reconExceptions.insertAll(List.of(onPayment, onRefund));
        assertThrows(ConcurrentRequestException.class, () -> reconExceptions.insertAll(List.of(ReconExceptionBuilder
            .onChannelLine(payment, ReconExceptionType.DUPLICATE_SETTLEMENT, "10.00").build())));
        List<Long> lineIds = List.of(payment.getId(), refund.getId());
        assertEquals(2, reconExceptions.listActiveBySources(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, lineIds,
            ReconExceptionType.DUPLICATE_SETTLEMENT).size());

        Instant closed = Instant.now().truncatedTo(ChronoUnit.MICROS);
        reconExceptions.changeStatusAll(List.of(onPayment), ReconExceptionStatus.AUTO_CLOSED, closed);
        assertNull(onPayment.getActiveSlot());
        ReconException reopened = ReconExceptionBuilder.onChannelLine(payment, ReconExceptionType.DUPLICATE_SETTLEMENT,
            "10.00").build();
        reconExceptions.insertAll(List.of(reopened));
        List<ReconException> latest = reconExceptions.listLatestBySources(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            lineIds, ReconExceptionType.DUPLICATE_SETTLEMENT);
        assertEquals(2, latest.size());
        assertTrue(latest.stream().anyMatch(item -> item.getId().equals(reopened.getId())));
        assertTrue(latest.stream().noneMatch(item -> item.getId().equals(onPayment.getId())));
        ReconException staleException = reconExceptions.findById(onRefund.getId());
        staleException.setStatus(ReconExceptionStatus.UNDER_REVIEW);
        assertThrows(ConcurrentRequestException.class, () -> reconExceptions.changeStatusAll(List.of(staleException),
            ReconExceptionStatus.AUTO_CLOSED, closed));
        assertEquals(ReconExceptionStatus.OPEN, reconExceptions.findById(onRefund.getId()).getStatus());
    }

    /** 一条待确认的系统建议。 */
    private static BankMatch proposal(BankStatementLine line, ExpectedBankMovement movement, String amount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BankMatch match = new BankMatch();
        match.setId(ReconciliationTestConstants.nextId());
        match.setBankStatementLineId(line.getId());
        match.setExpectedMovementId(movement.getId());
        match.setMatchedAmount(new BigDecimal(amount));
        match.setMatchMethod(MatchMethod.AUTO);
        match.setStatus(BankMatchStatus.PROPOSED);
        match.setVersion(1L);
        match.setCreateTime(now);
        match.setUpdateTime(now);
        return match;
    }

    /** 一条待复核的归类处置。 */
    private static ReconResolution resolution(ReconException exception) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ReconResolution resolution = new ReconResolution();
        resolution.setId(ReconciliationTestConstants.nextId());
        resolution.setExceptionId(exception.getId());
        resolution.setResolutionNo(1);
        resolution.setResolutionType(ResolutionType.CLASSIFY);
        resolution.setClassificationCode(ReconClassification.RECON_DIFFERENCE);
        resolution.setAmount(exception.getDifferenceAmount());
        resolution.setNote("差异归类");
        resolution.setContentHash("c".repeat(64));
        resolution.setMakerId(ReconciliationTestConstants.MAKER_ID);
        resolution.setStatus(ResolutionStatus.PENDING_CHECK);
        resolution.setVersion(1L);
        resolution.setCreateTime(now);
        resolution.setUpdateTime(now);
        return resolution;
    }
}

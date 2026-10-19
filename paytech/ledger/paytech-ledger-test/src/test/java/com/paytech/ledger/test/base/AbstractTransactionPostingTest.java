package com.paytech.ledger.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.service.TransactionPostingService;
import com.paytech.ledger.ext.mq.AccountPostedPostingHandler;
import com.paytech.ledger.test.builder.AccountPostingBuilder;
import com.paytech.ledger.test.builder.EconomicComponentBuilder;
import com.paytech.ledger.test.builder.TransactionEconomicsBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;
import com.paytech.ledger.test.mocker.local.LedgerMocker;
import com.paytech.ledger.test.probe.AccountPostedPublisher;

/**
 * 控制科目过账入口测试的公共前置与调用
 * <li>每例准备交易 OE（AE001/USD）账簿与当月 OPEN 期间；不预建任何 t_ledger_account，验证引擎自动建户。</li>
 * <li>入口：经济事实由 {@link TransactionEconomicsBuilder} 构造并配置为 billing 详情 DTO，入账批次由 {@link AccountPostingBuilder} 构造，经
 * {@link #postBatch} 扮演 account 发布 AccountPosted 到真实本机 RabbitMQ，由正式消费链路（分区队列 → 幂等拦截器 →
 * AccountPostedPostingHandler）事务外读事实、事务内过账；拒绝矩阵经 {@link #consumeDirectly} 直接调用同一 Handler，断言拒绝原因。</li>
 * <li>断言落在指令、指令行（规则行 / account 流水行）、分录与按分录聚合的账户余额；Owner 明细余额由 account 的测试覆盖。</li>
 */
public abstract class AbstractTransactionPostingTest extends MockTestBase {
    /** 交易 Operating Entity。 */
    protected static final String ENTITY_NO = "AE001";

    /** 交易币种。 */
    protected static final String CURRENCY = "USD";

    /** 交易 Client。 */
    protected static final long CLIENT_ID = 1001L;

    /** billing 在经济事实头中给出的 Finance 渠道清算账户主键。 */
    protected static final long CHANNEL_ACCOUNT_ID = 620001L;

    /** 交易 Local Partner。 */
    protected static final long PARTNER_ID = 3001L;

    /** Partner Agreement OE 关联。 */
    protected static final long PARTNER_AGREEMENT_OE_ID = 3101L;

    /** Seller 税务机关。 */
    protected static final long TAX_AUTHORITY_ID = 4001L;

    /** 平台 Finance Entity 代码与法律主体。 */
    protected static final String PLATFORM_ENTITY_CODE = "PLATFORM_FE";

    protected static final long PLATFORM_LEGAL_PARTY_ID = 5501L;

    /** 初始化 DML 预置的 STANDARD_TRANSACTION 规则版本。 */
    protected static final long INITIAL_VERSION_ID = 5101L;

    /** 初始化 DML 预置的 STANDARD_ADJUSTMENT 规则版本。 */
    protected static final long ADJUSTMENT_VERSION_ID = 5102L;

    @Autowired
    protected TransactionPostingService postingService;

    @Autowired
    protected AccountPostedPostingHandler postingHandler;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected IdGenerator idGenerator;

    @Autowired
    protected AccountingTestMapper testMapper;

    protected LedgerFixture oeLedger;

    /** 两级初始化的类级部分：交易 OE 账簿与开放期间。 */
    @BeforeEach
    void setUpPostingMocks() {
        oeLedger = ledgerMocker.mockOperatingEntityLedger(ENTITY_NO, CURRENCY);
    }

    /** 无 Partner 的 PAYMENT 经济事实模板，渠道清算账户为默认主键。 */
    protected static TransactionEconomicsBuilder payment(String grossAmount) {
        return TransactionEconomicsBuilder.template(ENTITY_NO, CLIENT_ID, CURRENCY, grossAmount, CHANNEL_ACCOUNT_ID);
    }

    /** 分配事实主键并构造经济事实载荷。 */
    protected EconomicFactPayload fact(TransactionEconomicsBuilder builder, EconomicComponentBuilder... lines) {
        return builder.build(idGenerator.nextId(), lines);
    }

    /** 插入 ACTIVE 平台 Finance Entity 及其币种账簿、日历和当月 OPEN 期间。 */
    protected LedgerFixture mockPlatformFinanceEntityLedger() {
        long financeEntityId = ledgerMocker.mockFinanceEntity(PLATFORM_ENTITY_CODE, PLATFORM_LEGAL_PARTY_ID,
            FinanceEntityStatus.ACTIVE);
        return ledgerMocker.mockFinanceEntityLedger(financeEntityId, CURRENCY);
    }

    /** 只插入 ACTIVE 平台 Finance Entity，不建账簿，用于账簿缺失场景。 */
    protected long mockPlatformFinanceEntity() {
        return ledgerMocker.mockFinanceEntity(PLATFORM_ENTITY_CODE, PLATFORM_LEGAL_PARTY_ID, FinanceEntityStatus.ACTIVE);
    }

    /** 事实批次模板：OE 流、给定序号、APPLIED、无流水。 */
    protected static AccountPostingBuilder posting(EconomicFactPayload fact, long sequence) {
        return AccountPostingBuilder.fact(fact, sequence);
    }

    /** PAYMENT 批次：Client 本金进 CLIENT_PAYABLE / PENDING 贷，Partner 分成进 PARTNER_PAYABLE / PENDING 贷。 */
    protected static AccountPostingBuilder paymentPosting(EconomicFactPayload fact, long sequence, String principal,
        String partnerShare) {
        com.paytech.account.api.enums.EntrySide credit = com.paytech.account.api.enums.EntrySide.CREDIT;
        AccountPostingBuilder builder = posting(fact, sequence).entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT,
            String.valueOf(CLIENT_ID), BalanceBucket.PENDING, credit, principal, 1);
        if (partnerShare != null) {
            builder.entry(StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER, String.valueOf(PARTNER_ID),
                BalanceBucket.PENDING, credit, partnerShare, 3);
        }
        return builder;
    }

    /**
     * 配置 billing 事实详情，扮演 account 发布批次，等待 ledger 消费登记后返回该批次的已过账指令
     *
     * @return 按账簿主键、指令主键升序的指令与分录
     */
    protected List<PostedInstructionView> postBatch(EconomicFactPayload fact, AccountPostedPayload posting) {
        economicFacts.mockFact(fact);
        return publishAndAwait(posting);
    }

    /** 只发布批次（事实已配置或无需事实），等待 ledger 消费登记。 */
    protected List<PostedInstructionView> publishAndAwait(AccountPostedPayload posting) {
        String eventId = accountEvents.publish(posting);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return postedViews(posting);
    }

    /** 直接以 AccountPosted 信封调用消费 Handler（HANDLER_MANAGED：事务外调用，Handler 自管本地事务），用于拒绝矩阵。 */
    protected void consumeDirectly(AccountPostedPayload posting) {
        postingHandler.consume(AccountPostedPublisher.event(posting));
    }

    /** 在调用方事务内直接调用过账引擎，验证业务层幂等。 */
    protected List<PostedInstructionView> postDirectly(EconomicFactPayload fact, AccountPostedPayload posting) {
        return transactionTemplate.execute(status -> postingService.post(new AccountPostingCommand(
            AccountPostingBatch.of(posting), fact)));
    }

    /** 过账流已消费序号（未消费为 0）。 */
    protected long consumedSequence(AccountPostedPayload posting) {
        Long version = testMapper.selectConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE,
            AccountPostedPublisher.aggregateId(posting));
        return version == null ? 0 : version;
    }

    /** 从库中读取批次生成的全部指令及其分录，按账簿主键、指令主键升序。 */
    protected List<PostedInstructionView> postedViews(AccountPostedPayload posting) {
        return testMapper.selectSourceInstructions(AccountPostingBatch.SOURCE_SYSTEM, posting.getPostingId().toString())
            .stream().sorted(Comparator.comparing(AccountingInstruction::getLedgerBookId)
                .thenComparing(AccountingInstruction::getId))
            .map(instruction -> {
                JournalEntry entry = testMapper.selectJournalEntryByInstruction(instruction.getId());
                return new PostedInstructionView(instruction.getId(), instruction.getLedgerBookId(),
                    AutoInstructionType.valueOf(instruction.getInstructionType()), entry == null ? null : entry.getId(),
                    entry == null ? null : entry.getJournalSequence());
            }).toList();
    }

    /** 按账簿、科目、核算对象与余额桶读取账户主键，不存在返回空。 */
    protected Long account(long ledgerBookId, String accountCode, LedgerOwnerType ownerType, String ownerId) {
        return testMapper.selectBucketAccountId(ledgerBookId, accountCode, ownerType.getCode(), ownerId,
            BalanceBucket.NONE.getCode());
    }

    /** 读取并断言账户已存在（总账账户一律 NONE 桶）。 */
    protected long existingAccount(long ledgerBookId, String accountCode, LedgerOwnerType ownerType, String ownerId) {
        Long id = account(ledgerBookId, accountCode, ownerType, ownerId);
        assertNotNull(id, "账户应已自动创建: " + accountCode + "/" + ownerType.getCode() + "/" + ownerId);
        return id;
    }

    /** 交易 OE 账簿的 Owner 控制账户：Owner 为账簿主体本身。 */
    protected long controlAccount(String accountCode) {
        return existingAccount(oeLedger.getLedgerBookId(), accountCode, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO);
    }

    /** 读取批次唯一一条指定类型的指令。 */
    protected AccountingInstruction instruction(AccountPostedPayload posting, AutoInstructionType type) {
        List<AccountingInstruction> matched = testMapper.selectSourceInstructions(AccountPostingBatch.SOURCE_SYSTEM,
            posting.getPostingId().toString()).stream().filter(i -> type.getCode().equals(i.getInstructionType()))
            .toList();
        assertEquals(1, matched.size(), "指令类型 " + type.getCode() + " 数量");
        return matched.get(0);
    }

    /** 断言 ACCOUNT_POSTING 指令冻结批次来源、规则版本、Input Hash 与经济事实，并已过账生成一条 STANDARD Journal。 */
    protected JournalEntry assertPostingInstruction(AccountingInstruction instruction, EconomicFactPayload economics,
        AccountPostedPayload posting, long ledgerBookId, long ruleVersionId, LocalDate postingDate) {
        JournalEntry journal = testMapper.selectJournalEntryByInstruction(instruction.getId());
        assertNotNull(journal, "指令应已过账: " + instruction.getId());
        assertAll("指令 " + instruction.getInstructionType(),
            () -> assertEquals(ledgerBookId, instruction.getLedgerBookId()),
            () -> assertEquals(InstructionOrigin.ACCOUNT_POSTING, instruction.getOriginType()),
            () -> assertEquals(InstructionStatus.POSTED, instruction.getStatus()),
            () -> assertEquals(AccountPostingBatch.SOURCE_SYSTEM, instruction.getSourceSystem()),
            () -> assertEquals(posting.getPostingId().toString(), instruction.getSourceEventKey()),
            () -> assertEquals(posting.getPostingSequence(), instruction.getSourceVersion()),
            () -> assertEquals(economics.getFactId(), instruction.getEconomicFactId()),
            () -> assertEquals(ruleVersionId, instruction.getAccountingRuleBundleVerId()),
            () -> assertTrue(instruction.getInputHash() != null && instruction.getInputHash().matches("[0-9a-f]{64}"),
                "inputHash: " + instruction.getInputHash()),
            () -> assertEquals(JournalEntryType.STANDARD, journal.getEntryType()),
            () -> assertEquals(postingDate, journal.getPostingDate()));
        return journal;
    }

    /** PAYMENT 的业务日期：支付成功时间在账簿日历时区的日期。 */
    protected static LocalDate paymentDate(EconomicFactPayload economics) {
        return LocalDate.ofInstant(economics.getPaymentSucceededTime(), ZoneId.of(LedgerMocker.TIMEZONE));
    }

    /** 调整类事实的业务日期：批准（登记）时间在账簿日历时区的日期。 */
    protected static LocalDate recordedDate(EconomicFactPayload economics) {
        return LocalDate.ofInstant(economics.getRecordedTime(), ZoneId.of(LedgerMocker.TIMEZONE));
    }

    /** 断言指令行按行号顺序逐行引用规则行或 account 流水、账户、方向与金额。 */
    protected void assertInstructionLines(long instructionId, ExpectedLine... expected) {
        List<InstructionLine> lines = testMapper.selectInstructionLines(instructionId);
        assertEquals(expected.length, lines.size(), "指令行数: " + instructionId);
        for (int i = 0; i < expected.length; i++) {
            ExpectedLine want = expected[i];
            InstructionLine line = lines.get(i);
            int lineNo = i + 1;
            assertAll("指令行 " + lineNo,
                () -> assertEquals(lineNo, line.getLineNo()),
                () -> assertEquals(want.ruleLineId(), line.getAccountingRuleLineId()),
                () -> assertEquals(want.accountEntryId(), line.getAccountEntryId()),
                () -> assertEquals(want.accountId(), line.getAccountId()),
                () -> assertEquals(want.side(), line.getEntrySide()),
                () -> assertEquals(0, new BigDecimal(want.amount()).compareTo(line.getAmount()),
                    "amount " + line.getAmount()));
        }
    }

    /** 期望的指令行：规则行与 account 流水二选一。 */
    protected record ExpectedLine(Long ruleLineId, Long accountEntryId, long accountId, EntrySide side, String amount) {
    }

    /** 期望规则借方行。 */
    protected static ExpectedLine debit(long ruleLineId, long accountId, String amount) {
        return new ExpectedLine(ruleLineId, null, accountId, EntrySide.DEBIT, amount);
    }

    /** 期望规则贷方行。 */
    protected static ExpectedLine credit(long ruleLineId, long accountId, String amount) {
        return new ExpectedLine(ruleLineId, null, accountId, EntrySide.CREDIT, amount);
    }

    /** 期望 account 流水行（方向与金额照抄流水）。 */
    protected static ExpectedLine entry(AccountPostedEntryPayload entry, long accountId) {
        return new ExpectedLine(null, entry.getEntryId(), accountId, EntrySide.valueOf(entry.getEntrySide()),
            entry.getAmount().toPlainString());
    }
}

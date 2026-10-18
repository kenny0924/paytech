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

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.domain.TransactionPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.service.TransactionPostingService;
import com.paytech.ledger.ext.mq.EconomicFactRecordedPostingHandler;
import com.paytech.ledger.test.builder.EconomicComponentBuilder;
import com.paytech.ledger.test.builder.TransactionEconomicsBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;
import com.paytech.ledger.test.mocker.local.LedgerMocker;
import com.paytech.ledger.test.probe.BillingEventPublisher;

/**
 * 交易自动记账引擎入口测试的公共前置与调用
 * <li>每例准备交易 OE（AE001/USD）账簿与当月 OPEN 期间；不预建任何 t_account，验证引擎自动建户。</li>
 * <li>入口（第 11b 步）：经济事实由 {@link TransactionEconomicsBuilder} 构造 DTO，经 {@link #postFact} 扮演 billing 发布 EconomicFactRecorded
 * 到真实本机 RabbitMQ，由正式消费链路（分区队列 → 幂等拦截器 → EconomicFactRecordedPostingHandler）在 ledger 本地事务内过账；
 * 拒绝矩阵经 {@link #consumeDirectly} 在测试事务内调用同一 Handler，断言拒绝原因。</li>
 * <li>渠道清算账户主键由载荷头给出，不写 reconciliation 表；不写 billing 表。</li>
 * <li>ledger 不再写余额（design/04 §4）：断言落在指令、分录与按分录聚合的账户余额；Owner 余额断言在 account 的事实消费测试。</li>
 */
public abstract class AbstractTransactionPostingTest extends MockTestBase {
    /** 交易 Operating Entity。 */
    protected static final String ENTITY_NO = "AE001";

    /** 交易币种。 */
    protected static final String CURRENCY = "USD";

    /** 交易 Client。 */
    protected static final long CLIENT_ID = 1001L;

    /** billing 在经济事实头中给出的 Finance 渠道清算账户主键（reconciliation t_channel_account.id）。 */
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

    /** 初始化 DML 预置的规则版本 5101 及其规则行主键。 */
    protected static final long INITIAL_VERSION_ID = 5101L;

    @Autowired
    protected TransactionPostingService postingService;

    @Autowired
    protected EconomicFactRecordedPostingHandler postingHandler;

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

    /**
     * 扮演 billing 发布经济事实，等待 ledger 消费提交后返回该事实的已过账指令
     *
     * @return 按账簿主键、指令主键升序的指令与分录
     */
    protected List<PostedInstructionView> postFact(EconomicFactPayload fact) {
        String eventId = billing.publish(fact);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return postedViews(fact.getFactId());
    }

    /** 在测试事务内直接以 EconomicFactRecorded 信封调用消费 Handler（与拦截器的 SHARED 事务语义一致），用于拒绝矩阵。 */
    protected void consumeDirectly(EconomicFactPayload fact) {
        transactionTemplate.executeWithoutResult(status -> postingHandler.consume(event(fact)));
    }

    /** 在调用方事务内直接调用过账引擎，验证业务层幂等。 */
    protected List<PostedInstructionView> postDirectly(EconomicFactPayload fact) {
        return transactionTemplate.execute(status -> postingService.post(new TransactionPostingCommand(fact)));
    }

    /** 与 billing 发布端相同的 EconomicFactRecorded 信封（不含发布端计算的 payloadHash）。 */
    protected static DomainEvent event(EconomicFactPayload fact) {
        return DomainEvent.builder().eventType(EconomicFactPayload.EVENT_TYPE)
            .schemaVersion(EconomicFactPayload.SCHEMA_VERSION)
            .aggregate(EconomicFactPayload.AGGREGATE_TYPE, fact.getFactId().toString())
            .aggregateVersion(fact.getFactVersion()).occurredAt(fact.getRecordedTime()).sourceService("billing")
            .instanceId("main").payload(BillingEventPublisher.json(fact)).build();
    }

    /** 从库中读取经济事实的全部 AUTO 指令及其分录，按账簿主键、指令主键升序。 */
    protected List<PostedInstructionView> postedViews(long factId) {
        return testMapper.selectEconomicsInstructions(factId).stream()
            .sorted(Comparator.comparing(AccountingInstruction::getLedgerBookId)
                .thenComparing(AccountingInstruction::getId))
            .map(instruction -> {
                JournalEntry entry = testMapper.selectJournalEntryByInstruction(instruction.getId());
                return new PostedInstructionView(instruction.getId(), instruction.getLedgerBookId(),
                    AutoInstructionType.valueOf(instruction.getInstructionType()), entry == null ? null : entry.getId(),
                    entry == null ? null : entry.getJournalSequence());
            }).toList();
    }

    /** 按账簿、科目、核算对象与余额桶读取账户主键，不存在返回空。 */
    protected Long account(long ledgerBookId, String accountCode, OwnerType ownerType, String ownerId,
        BalanceBucket bucket) {
        return testMapper.selectBucketAccountId(ledgerBookId, accountCode, ownerType.getCode(), ownerId,
            bucket.getCode());
    }

    /** 读取并断言账户已存在。 */
    protected long existingAccount(long ledgerBookId, String accountCode, OwnerType ownerType, String ownerId,
        BalanceBucket bucket) {
        Long id = account(ledgerBookId, accountCode, ownerType, ownerId, bucket);
        assertNotNull(id, "账户应已自动创建: " + accountCode + "/" + ownerType.getCode() + "/" + ownerId);
        return id;
    }

    /** 读取经济事实唯一一条指定类型的指令。 */
    protected AccountingInstruction instruction(long factId, AutoInstructionType type) {
        List<AccountingInstruction> matched = testMapper.selectEconomicsInstructions(factId).stream()
            .filter(i -> type.getCode().equals(i.getInstructionType())).toList();
        assertEquals(1, matched.size(), "指令类型 " + type.getCode() + " 数量");
        return matched.get(0);
    }

    /** 断言 AUTO 指令冻结来源 Tuple、规则版本、Input Hash 与经济事实，并已过账生成一条 STANDARD Journal。 */
    protected JournalEntry assertAutoInstruction(AccountingInstruction instruction, EconomicFactPayload economics,
        long ledgerBookId, PostedInstructionView view) {
        JournalEntry journal = testMapper.selectJournalEntryByInstruction(instruction.getId());
        assertNotNull(journal, "指令应已过账: " + instruction.getId());
        assertAll("指令 " + instruction.getInstructionType(),
            () -> assertEquals(ledgerBookId, instruction.getLedgerBookId()),
            () -> assertEquals(InstructionOrigin.AUTO, instruction.getOriginType()),
            () -> assertEquals(InstructionStatus.POSTED, instruction.getStatus()),
            () -> assertEquals(economics.getSourceSystem(), instruction.getSourceSystem()),
            () -> assertEquals(economics.getSourceEventKey(), instruction.getSourceEventKey()),
            () -> assertEquals(economics.getSourceVersion(), instruction.getSourceVersion()),
            () -> assertEquals(economics.getSourcePayloadHash(), instruction.getSourcePayloadHash()),
            () -> assertEquals(economics.getFactId(), instruction.getEconomicFactId()),
            () -> assertEquals(INITIAL_VERSION_ID, instruction.getAccountingRuleBundleVerId()),
            () -> assertTrue(instruction.getInputHash() != null && instruction.getInputHash().matches("[0-9a-f]{64}"),
                "inputHash: " + instruction.getInputHash()),
            () -> assertEquals(JournalEntryType.STANDARD, journal.getEntryType()),
            () -> assertEquals(LocalDate.ofInstant(economics.getPaymentSucceededTime(),
                ZoneId.of(LedgerMocker.TIMEZONE)), journal.getPostingDate()),
            () -> assertEquals(instruction.getId(), view.getInstructionId()),
            () -> assertEquals(ledgerBookId, view.getLedgerBookId()),
            () -> assertEquals(instruction.getInstructionType(), view.getInstructionType().getCode()),
            () -> assertEquals(journal.getId(), view.getJournalEntryId()),
            () -> assertEquals(journal.getJournalSequence(), view.getJournalSequence()));
        return journal;
    }

    /** 断言指令行按行号顺序逐行引用规则行、账户、方向与金额。 */
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
                () -> assertEquals(want.accountId(), line.getAccountId()),
                () -> assertEquals(want.side(), line.getEntrySide()),
                () -> assertEquals(0, new BigDecimal(want.amount()).compareTo(line.getAmount()),
                    "amount " + line.getAmount()));
        }
    }

    /** 期望的指令行。 */
    protected record ExpectedLine(long ruleLineId, long accountId, EntrySide side, String amount) {
    }

    /** 期望借方行。 */
    protected static ExpectedLine debit(long ruleLineId, long accountId, String amount) {
        return new ExpectedLine(ruleLineId, accountId, EntrySide.DEBIT, amount);
    }

    /** 期望贷方行。 */
    protected static ExpectedLine credit(long ruleLineId, long accountId, String amount) {
        return new ExpectedLine(ruleLineId, accountId, EntrySide.CREDIT, amount);
    }
}

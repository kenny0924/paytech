package com.paytech.finance.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.InstructionOrigin;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import com.paytech.finance.accounting.core.enums.JournalEntryType;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.domain.TransactionPostingCommand;
import com.paytech.finance.accounting.domain.view.PostedInstructionView;
import com.paytech.finance.accounting.domainservice.service.TransactionPostingService;
import com.paytech.finance.test.builder.TransactionEconomicsBuilder;
import com.paytech.finance.test.dal.AccountingTestMapper;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;
import com.paytech.finance.test.mocker.local.LedgerFixture;
import com.paytech.finance.test.mocker.local.LedgerMocker;
import com.paytech.finance.test.mocker.local.TransactionEconomicsMocker;

/**
 * 交易自动记账引擎入口测试的公共前置与调用
 * <li>每例准备交易 OE（AE001/USD）账簿与当月 OPEN 期间，以及按 MID + 币种命中的 ACTIVE 渠道清算账户；不预建任何 t_account，验证引擎自动建户。</li>
 * <li>引擎要求调用方事务：每次调用以独立 TransactionTemplate 包裹，正常提交或异常整体回滚。</li>
 */
public abstract class AbstractTransactionPostingTest extends MockTestBase {
    /** 交易 Operating Entity。 */
    protected static final String ENTITY_NO = "AE001";

    /** 交易币种。 */
    protected static final String CURRENCY = "USD";

    /** 交易 Client。 */
    protected static final long CLIENT_ID = 1001L;

    /** Payment 冻结的 MID。 */
    protected static final long MID_ID = 620001L;

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
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected TransactionEconomicsMocker economicsMocker;

    @Autowired
    protected AccountingTestMapper testMapper;

    protected LedgerFixture oeLedger;

    protected long channelAccountId;

    /** 两级初始化的类级部分：交易 OE 账簿、开放期间与渠道清算账户。 */
    @BeforeEach
    void setUpPostingMocks() {
        oeLedger = ledgerMocker.mockOperatingEntityLedger(ENTITY_NO, CURRENCY);
        channelAccountId = economicsMocker.mockChannelAccount(MID_ID, ENTITY_NO, CURRENCY);
    }

    /** 无 Partner 的 PAYMENT 经济结果模板。 */
    protected static TransactionEconomicsBuilder payment(String grossAmount) {
        return TransactionEconomicsBuilder.template(ENTITY_NO, CLIENT_ID, CURRENCY, grossAmount);
    }

    /** 在调用方事务内以默认 MID 调用引擎。 */
    protected List<PostedInstructionView> postEconomics(long economicsId) {
        return postEconomics(economicsId, MID_ID);
    }

    /** 在调用方事务内以指定 MID 调用引擎。 */
    protected List<PostedInstructionView> postEconomics(long economicsId, long midId) {
        return transactionTemplate.execute(
            status -> postingService.post(new TransactionPostingCommand(economicsId, midId)));
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

    /** 读取经济结果唯一一条指定类型的指令。 */
    protected AccountingInstruction instruction(long economicsId, AutoInstructionType type) {
        List<AccountingInstruction> matched = testMapper.selectEconomicsInstructions(economicsId).stream()
            .filter(i -> type.getCode().equals(i.getInstructionType())).toList();
        assertEquals(1, matched.size(), "指令类型 " + type.getCode() + " 数量");
        return matched.get(0);
    }

    /** 断言 AUTO 指令冻结来源 Tuple、规则版本、Input Hash 与经济结果，并已过账生成一条 STANDARD Journal。 */
    protected JournalEntry assertAutoInstruction(AccountingInstruction instruction, TransactionEconomicsRow economics,
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
            () -> assertEquals(economics.getId(), instruction.getTransactionEconomicsId()),
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

package com.paytech.ledger.auto.ext.http.query;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.adjustment;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.response.JournalEntryDetailResponseBody;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.AccountPostingBuilder;

import tools.jackson.databind.JsonNode;

/**
 * ACCOUNT_POSTING 指令的查询下钻：分录详情与账户流水返回 account 批次主键与序号，控制科目行返回 account 流水主键，规则行为空
 *
 * @see LedgerBookQueryApi#getJournalEntry(String, String)
 * @see LedgerBookQueryApi#listAccountJournalLines(String, String, String, String, Integer, Integer)
 */
class AccountPostingJournalQueryTest extends AbstractTransactionPostingTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    @Test
    void test_journalEntryDetailCarriesPostingAndEntry() {
        Posted posted = postClientCharge();

        var detail = get(bookPath() + "/journal-entries/" + posted.view().getJournalEntryId()).assertSuccess(200)
            .as(JournalEntryDetailResponseBody.class);

        assertAll(() -> assertEquals(InstructionOrigin.ACCOUNT_POSTING.getCode(), detail.getOriginType()),
            () -> assertEquals(posted.posting().getPostingId().toString(), detail.getAccountPostingId()),
            () -> assertEquals("3", detail.getAccountPostingSequence()),
            () -> assertEquals(posted.posting().getPostingId().toString(), detail.getSourceEventKey()),
            () -> assertEquals(2, detail.getLines().size()));
        var ruleLine = detail.getLines().get(0);
        var controlLine = detail.getLines().get(1);
        assertAll(() -> assertNull(ruleLine.getAccountEntryId(), "规则行没有 account 流水"),
            () -> assertEquals(StandardAccountCodes.CLIENT_PAYABLE, controlLine.getAccountCode()),
            () -> assertEquals(LedgerOwnerType.OPERATING_ENTITY.getCode(), controlLine.getOwnerType()),
            () -> assertEquals(ENTITY_NO, controlLine.getOwnerId()),
            () -> assertEquals(BalanceBucket.NONE.getCode(), controlLine.getBalanceBucket()),
            () -> assertEquals(posted.posting().getEntries().get(0).getEntryId().toString(),
                controlLine.getAccountEntryId()));
    }

    @Test
    void test_accountJournalLinesCarryPosting() {
        Posted posted = postClientCharge();

        List<JsonNode> lines = get(bookPath() + "/accounts/" + controlAccount(StandardAccountCodes.CLIENT_PAYABLE)
            + "/journal-lines").assertSuccess(200).assertPage(1, 10, 1);

        JsonNode line = lines.getFirst();
        assertAll(() -> assertEquals(InstructionOrigin.ACCOUNT_POSTING.getCode(), line.path("originType").asString()),
            () -> assertEquals(posted.posting().getPostingId().toString(), line.path("accountPostingId").asString()),
            () -> assertEquals("3", line.path("accountPostingSequence").asString()),
            () -> assertEquals("9.00", line.path("balanceAfter").asString()));
    }

    /** 过账流已消费到 2，序号 3 的批次：OE 向 Client 付本金 9。 */
    Posted postClientCharge() {
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE,
            AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ENTITY_NO,
                CURRENCY, 0), 2);
        BillingFactPayload fact = fact(payment("0").factType(BillingFactType.MANUAL_ADJUSTMENT),
            adjustment(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT, FactLineDirection.CHARGE, "9"));
        AccountPostedPayload posting = AccountPostingBuilder.fact(fact, 3).entry(StandardAccountCodes.CLIENT_PAYABLE,
            OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.CREDIT, "9", 1).build();
        return new Posted(posting, postBatch(fact, posting).getFirst());
    }

    /** 本类账簿路径。 */
    String bookPath() {
        return "/ledger-books/v1/" + oeLedger.getLedgerBookId();
    }

    /** 一个已过账的批次及其指令。 */
    record Posted(AccountPostedPayload posting, PostedInstructionView view) {
    }
}

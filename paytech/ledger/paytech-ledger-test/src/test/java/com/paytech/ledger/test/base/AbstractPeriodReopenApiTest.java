package com.paytech.ledger.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.request.StartPeriodReopenRequestBody;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.JournalLine;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodReopenReason;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 期间重开与年末结转入口测试的公共前置与调用
 * <li>沿用关账基类的 AE001/USD 账簿、当月 OPEN 期间与收入/费用账户；关账前置通过真实关账入口完成。</li>
 * <li>年末判定只取决于日历财年起始月：把起始月设为下月即让当月期间成为财年最后期间，设为当月则为非年末期间。</li>
 */
public abstract class AbstractPeriodReopenApiTest extends AbstractPeriodCloseApiTest {
    /** 留存收益科目代码。 */
    protected static final String RETAINED_EARNINGS_CODE = "RETAINED_EARNINGS";

    /** 重开流程的 3 个固定步骤。 */
    protected static final List<String> REOPEN_STEP_TYPES = List.of(CloseStepType.LOCK_SEQUENCE.getCode(),
        CloseStepType.PLAN_REOPEN_REVERSAL.getCode(), CloseStepType.POST_REOPEN_REVERSAL.getCode());

    /** 模板重开申请编号。 */
    protected static final String REOPEN_REQUEST_REF = "REOPEN-REQ-001";

    /** 模板重开原因。 */
    protected static final PeriodReopenReason REOPEN_REASON = PeriodReopenReason.ACCOUNTING_CORRECTION;

    /** 重开资源路径。 */
    protected String reopensPath(long periodId) {
        return "/accounting-periods/v1/" + periodId + "/reopens";
    }

    /** 以模板 Maker、原因与申请编号发起重开。 */
    protected LedgerResponseChecker startReopen(long periodId) {
        return postAs(reopensPath(periodId), CLOSE_MAKER_ID, reopenBody(REOPEN_REASON.getCode(), REOPEN_REQUEST_REF));
    }

    /** 对当月期间发起重开。 */
    protected LedgerResponseChecker startReopen() {
        return startReopen(ledger.getOpenPeriodId());
    }

    /** 发起重开并断言进入待审批。 */
    protected LedgerResponseChecker startedReopen() {
        return startReopen().assertSuccess(201).assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());
    }

    /** 重开请求体；编制人以 Header 发送，不修复负例。 */
    protected StartPeriodReopenRequestBody reopenBody(String reasonCode, String requestRef) {
        StartPeriodReopenRequestBody body = new StartPeriodReopenRequestBody();
        body.setReasonCode(reasonCode);
        body.setRequestRef(requestRef);
        return body;
    }

    /** 对指定期间发起关账并由模板 Checker 批准，断言期间 CLOSED，返回发起响应。 */
    protected LedgerResponseChecker closeAndApprove(long periodId) {
        LedgerResponseChecker started = startClose(periodId).assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());
        approveClose(periodId, started.text("closeNo"), CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200)
            .assertText("status", PeriodCloseStatus.COMPLETED.getCode());
        assertEquals(PeriodStatus.CLOSED.getCode(), testMapper.selectPeriodStatus(periodId));
        return started;
    }

    /** 关闭并批准当月期间。 */
    protected LedgerResponseChecker closeAndApprove() {
        return closeAndApprove(ledger.getOpenPeriodId());
    }

    /** 批准重开流程。 */
    protected LedgerResponseChecker approveReopen(LedgerResponseChecker reopen) {
        return approveClose(ledger.getOpenPeriodId(), reopen.text("closeNo"), CLOSE_CHECKER_ID,
            reopen.text("closeHash"));
    }

    /** 驳回重开流程。 */
    protected LedgerResponseChecker rejectReopen(LedgerResponseChecker reopen) {
        return rejectClose(ledger.getOpenPeriodId(), reopen.text("closeNo"), CLOSE_CHECKER_ID,
            reopen.text("closeHash"));
    }

    /** 账簿主体的本期损益账户主键；不存在返回 null。 */
    protected Long currentResultAccountId() {
        return entityAccountId(CURRENT_RESULT_CODE);
    }

    /** 账簿主体的留存收益账户主键；不存在返回 null。 */
    protected Long retainedEarningsAccountId() {
        return entityAccountId(RETAINED_EARNINGS_CODE);
    }

    /** 按科目代码读取账簿主体账户主键。 */
    protected Long entityAccountId(String accountCode) {
        return testMapper.selectAccountId(ledger.getLedgerBookId(), accountCode, LedgerOwnerType.OPERATING_ENTITY.getCode(),
            ledger.getEntityNo());
    }

    /** 断言账户净余额（按正常方向）。 */
    protected void assertNetBalance(long accountId, String expected) {
        BigDecimal actual = testMapper.selectAccountNetBalance(accountId);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "账户 " + accountId + " 净余额，实际 " + actual);
    }

    /** 当月期间的期末日。 */
    protected LocalDate currentPeriodEnd() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(today.lengthOfMonth());
    }

    /** 流程详情中唯一账簿证据引用的指令所过账的分录头；断言账簿唯一且已有指令。 */
    protected JournalEntry postedEntry(long periodId, String closeNo) {
        JsonNode books = closeDetail(periodId, closeNo).assertSuccess(200).json().path("books");
        assertEquals(1, books.size(), "流程账簿证据数量");
        JsonNode instructionId = books.get(0).path("postingInstructionId");
        assertEquals(false, instructionId.isNull() || instructionId.isMissingNode(), "账簿应有过账指令");
        return testMapper.selectJournalEntryByInstruction(Long.parseLong(instructionId.asString()));
    }

    /** 流程过账分录的全部分录行。 */
    protected List<JournalLine> postedLines(long periodId, String closeNo) {
        return ledgerChecker.journalLines(postedEntry(periodId, closeNo).getId());
    }

    /** 重开流程恰有 3 个步骤：前两步成功并有结果摘要，第三步为给定状态。 */
    protected void assertReopenSteps(JsonNode detail, CloseStepStatus postStatus) {
        List<String> types = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        for (JsonNode step : detail.path("steps")) {
            types.add(step.path("stepType").asString());
            statuses.add(step.path("status").asString());
        }
        assertEquals(REOPEN_STEP_TYPES, types);
        assertEquals(List.of(CloseStepStatus.SUCCEEDED.getCode(), CloseStepStatus.SUCCEEDED.getCode(),
            postStatus.getCode()), statuses);
        for (int i = 0; i < 2; i++) {
            assertTrue(detail.path("steps").get(i).path("resultHash").asString().matches("[0-9a-f]{64}"));
        }
    }
}

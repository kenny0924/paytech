package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 创建统一账户：正常方向冻结、账户号格式与序号、账户身份矩阵、科目归属与启停、唯一性。
 *
 * @see LedgerBookCommandApi#createAccount(String, String, String, CreateAccountRequestBody)
 */
class CreateAccountTest extends MockTestBase {
    /** STANDARD 实收资本末级科目（30010101）。 */
    private static final String CAPITAL_CODE = "CAPITAL";

    @Autowired
    private AccountingTestMapper testMapper;

    private LedgerFixture ledger;

    @BeforeEach
    void setUpClassMocks() {
        ledger = ledgerMocker.mockOperatingEntityLedger("AE001", "USD");
    }

    /** 控制科目账户：Owner 为账簿主体、桶 NONE，正常方向从科目复制。 */
    @Test
    void test_normalFlowCopiesNormalSide() {
        var response = postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode())).assertSuccess(201);

        LedgerAccount account = testMapper.selectAccount(response.id("accountId"));
        assertAll(() -> assertEquals(ledger.getLedgerBookId(), account.getLedgerBookId()),
            () -> assertEquals(LedgerOwnerType.OPERATING_ENTITY, account.getOwnerType()),
            () -> assertEquals("AE001", account.getOwnerId()),
            () -> assertEquals(BalanceBucket.NONE, account.getBalanceBucket()),
            () -> assertEquals(NormalSide.CREDIT, account.getNormalSide()),
            () -> assertEquals(AccountStatus.ACTIVE, account.getAccountStatus()));
        ledgerChecker.assertAccount(account.getId(), "0", "0", "0", 0L);
    }

    /** 账户号 {entityNo}-{币种}-{科目编号}-{序号}：同账簿同科目序号递增，不同科目各自从 1 开始。 */
    @Test
    void test_accountNoFormatAndSequence() {
        long clientPayable = postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode())).assertSuccess(201)
            .id("accountId");
        long bank1 = postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.BANK_CASH,
            LedgerOwnerType.CASH_ACCOUNT.getCode(), "5001", BalanceBucket.NONE.getCode())).assertSuccess(201).id("accountId");
        long bank2 = postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.BANK_CASH,
            LedgerOwnerType.CASH_ACCOUNT.getCode(), "5002", BalanceBucket.NONE.getCode())).assertSuccess(201).id("accountId");

        assertAll(() -> assertEquals("AE001-USD-22010101-0001", testMapper.selectAccount(clientPayable).getAccountNo()),
            () -> assertEquals("AE001-USD-10010101-0001", testMapper.selectAccount(bank1).getAccountNo()),
            () -> assertEquals("AE001-USD-10010101-0002", testMapper.selectAccount(bank2).getAccountNo()));
    }

    /** Finance Entity 账簿的主体段为财务主体代码。 */
    @Test
    void test_financeEntityAccountNoUsesEntityCode() {
        long financeEntityId = ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);
        long bookId = ledgerMocker.mockBook(LedgerEntityType.FINANCE_ENTITY, String.valueOf(financeEntityId), "USD",
            LedgerMocker.STANDARD_CHART_ID);

        long id = postAsOperator("/ledger-books/v1/" + bookId + "/accounts", AccountingSetupRequestBuilder.account(
            StandardAccountCodes.BANK_CASH, LedgerOwnerType.CASH_ACCOUNT.getCode(), "5001", BalanceBucket.NONE.getCode()))
            .assertSuccess(201).id("accountId");

        assertEquals("FE-PLATFORM-USD-10010101-0001", testMapper.selectAccount(id).getAccountNo());
    }

    /** 停用的科目不能新建账户；汇总科目不可过账。 */
    @Test
    void test_inactiveOrSummaryChartAccountRejected() {
        testMapper.updateStandardChartAccountStatus(CAPITAL_CODE, ChartAccountStatus.INACTIVE.getCode());

        postAsOperator(path(), AccountingSetupRequestBuilder.account(CAPITAL_CODE, LedgerOwnerType.OPERATING_ENTITY.getCode(),
            "AE001", BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_INACTIVE);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("SHARE_CAPITAL", LedgerOwnerType.OPERATING_ENTITY.getCode(),
            "AE001", BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_POSTABLE);
        assertEquals(0, testMapper.countAllAccounts());
    }

    @Test
    void test_platformOwnedAccountUsesNoneBucket() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("BANK_CASH", LedgerOwnerType.CASH_ACCOUNT.getCode(), "5001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("CAPITAL", LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
    }

    /** 身份矩阵：控制科目只能以账簿主体为 Owner；任何科目的桶只能为 NONE；Client / Local Partner 不是总账核算对象类型。 */
    @Test
    void test_identityMatrixViolationRejected() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE002", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.PARTNER_RECOVERY_RECEIVABLE,
            LedgerOwnerType.CHANNEL_ACCOUNT.getCode(), "2001", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.PENDING.getCode()))
            .assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.BANK_CASH,
            LedgerOwnerType.CASH_ACCOUNT.getCode(), "5001", BalanceBucket.AVAILABLE.getCode()))
            .assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            OwnerType.CLIENT.getCode(), "1001", BalanceBucket.PENDING.getCode())).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("ownerType");
    }

    @Test
    void test_unknownChartAccountOrBookRejected() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("NOT_EXISTS", LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_POSTABLE);
        postAsOperator("/ledger-books/v1/123456789/accounts", AccountingSetupRequestBuilder.account("CAPITAL",
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    @Test
    void test_nonPostableChartAccountRejected() {
        long chartId = ledgerMocker.mockChart("LOCAL", ReferenceDataStatus.ACTIVE);
        ledgerMocker.mockChartAccount(chartId, "1001", "LOCAL_PARENT", false);
        long bookId = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD", chartId);

        postAsOperator("/ledger-books/v1/" + bookId + "/accounts", AccountingSetupRequestBuilder.account("LOCAL_PARENT",
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE002", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_POSTABLE);
    }

    @Test
    void test_duplicateAccountRejected() {
        var body = AccountingSetupRequestBuilder.account(StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode());
        postAsOperator(path(), body).assertSuccess(201);

        postAsOperator(path(), body).assertRejected(LedgerSetupErrorCode.ACCOUNT_ALREADY_EXISTS);
        long chartAccountId = testMapper.selectStandardChartAccountId(StandardAccountCodes.CLIENT_PAYABLE);
        assertEquals(1L, testMapper.selectAccountNoSequence(ledger.getLedgerBookId(), chartAccountId),
            "重复建户整体回滚，序号随之回滚");
    }

    @Test
    void test_invalidOwnerIdFormat() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.BANK_CASH,
            LedgerOwnerType.CASH_ACCOUNT.getCode(), "C-1001", BalanceBucket.NONE.getCode())).assertInvalidRequest()
            .assertDetailContains("Owner 标识格式非法");
    }

    /** 本类账簿的账户路径。 */
    String path() {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/accounts";
    }
}

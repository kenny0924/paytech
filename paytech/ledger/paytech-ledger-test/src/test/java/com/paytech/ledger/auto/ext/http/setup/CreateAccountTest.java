package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;

/**
 * 创建统一账户：正常方向冻结、账户身份矩阵、科目归属与唯一性。
 *
 * @see LedgerBookCommandApi#createAccount(String, String, String, CreateAccountRequestBody)
 */
class CreateAccountTest extends MockTestBase {

    @Autowired
    private AccountingTestMapper testMapper;

    private LedgerFixture ledger;

    @BeforeEach
    void setUpClassMocks() {
        ledger = ledgerMocker.mockOperatingEntityLedger("AE001", "USD");
    }

    @Test
    void test_normalFlowCopiesNormalSide() {
        var response = postAsOperator(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE",
            OwnerType.CLIENT.getCode(), "1001", BalanceBucket.PENDING.getCode())).assertSuccess(201);

        LedgerAccount account = testMapper.selectAccount(response.id("accountId"));
        assertAll(() -> assertEquals(ledger.getLedgerBookId(), account.getLedgerBookId()),
            () -> assertEquals(OwnerType.CLIENT, account.getOwnerType()), () -> assertEquals("1001", account.getOwnerId()),
            () -> assertEquals(BalanceBucket.PENDING, account.getBalanceBucket()),
            () -> assertEquals(NormalSide.CREDIT, account.getNormalSide()),
            () -> assertEquals(AccountStatus.ACTIVE, account.getAccountStatus()));
        ledgerChecker.assertAccount(account.getId(), "0", "0", "0", 0L);
    }

    @Test
    void test_platformOwnedAccountUsesNoneBucket() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("BANK_CASH", OwnerType.CASH_ACCOUNT.getCode(), "5001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("CAPITAL", OwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
    }

    @Test
    void test_identityMatrixViolationRejected() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("PARTNER_RECOVERY_RECEIVABLE",
            OwnerType.LOCAL_PARTNER.getCode(), "2001", BalanceBucket.AVAILABLE.getCode()))
            .assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.LOCAL_PARTNER.getCode(), "2001",
            BalanceBucket.PENDING.getCode())).assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
        postAsOperator(path(), AccountingSetupRequestBuilder.account("BANK_CASH", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.ACCOUNT_IDENTITY_INVALID);
    }

    @Test
    void test_unknownChartAccountOrBookRejected() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("NOT_EXISTS", OwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_POSTABLE);
        postAsOperator("/ledger-books/v1/123456789/accounts", AccountingSetupRequestBuilder.account("CAPITAL",
            OwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    @Test
    void test_nonPostableChartAccountRejected() {
        long chartId = ledgerMocker.mockChart("LOCAL", ReferenceDataStatus.ACTIVE);
        ledgerMocker.mockChartAccount(chartId, "LOCAL_PARENT", false);
        long bookId = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD", chartId);

        postAsOperator("/ledger-books/v1/" + bookId + "/accounts", AccountingSetupRequestBuilder.account("LOCAL_PARENT",
            OwnerType.OPERATING_ENTITY.getCode(), "AE002", BalanceBucket.NONE.getCode()))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_POSTABLE);
    }

    @Test
    void test_duplicateAccountRejected() {
        var body = AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.AVAILABLE.getCode());
        postAsOperator(path(), body).assertSuccess(201);

        postAsOperator(path(), body).assertRejected(LedgerSetupErrorCode.ACCOUNT_ALREADY_EXISTS);
    }

    @Test
    void test_invalidOwnerIdFormat() {
        postAsOperator(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "C-1001",
            BalanceBucket.PENDING.getCode())).assertInvalidRequest().assertDetailContains("Owner 标识格式非法");
    }

    /** 本类账簿的账户路径。 */
    String path() {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/accounts";
    }
}

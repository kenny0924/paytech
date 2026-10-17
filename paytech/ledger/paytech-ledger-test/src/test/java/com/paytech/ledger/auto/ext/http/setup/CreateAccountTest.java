package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.core.dal.dataobject.Account;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 创建统一账户：正常方向冻结、账户身份矩阵、科目归属与唯一性。
 *
 * @see LedgerBookCommandApi#createAccount(String, CreateAccountRequestBody)
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
        var response = post(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE",
            OwnerType.CLIENT.getCode(), "1001", BalanceBucket.PENDING.getCode())).assertSuccess(201);

        Account account = testMapper.selectAccount(response.id("accountId"));
        assertAll(() -> assertEquals(ledger.getLedgerBookId(), account.getLedgerBookId()),
            () -> assertEquals(OwnerType.CLIENT, account.getOwnerType()), () -> assertEquals("1001", account.getOwnerId()),
            () -> assertEquals(BalanceBucket.PENDING, account.getBalanceBucket()),
            () -> assertEquals(NormalSide.CREDIT, account.getNormalSide()),
            () -> assertEquals(AccountStatus.ACTIVE, account.getAccountStatus()),
            () -> assertEquals(0L, account.getBalanceVersion()));
        ledgerChecker.assertAccount(account.getId(), "0", "0", "0", 0L);
    }

    @Test
    void test_platformOwnedAccountUsesNoneBucket() {
        post(path(), AccountingSetupRequestBuilder.account("BANK_CASH", OwnerType.CASH_ACCOUNT.getCode(), "5001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
        post(path(), AccountingSetupRequestBuilder.account("CAPITAL", OwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertSuccess(201);
    }

    @Test
    void test_identityMatrixViolationRejected() {
        post(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.NONE.getCode())).assertBusinessRejected("科目不允许该余额桶");
        post(path(), AccountingSetupRequestBuilder.account("PARTNER_RECOVERY_RECEIVABLE",
            OwnerType.LOCAL_PARTNER.getCode(), "2001", BalanceBucket.AVAILABLE.getCode()))
            .assertBusinessRejected("科目不允许该余额桶");
        post(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.LOCAL_PARTNER.getCode(), "2001",
            BalanceBucket.PENDING.getCode())).assertBusinessRejected("科目不允许该核算对象");
        post(path(), AccountingSetupRequestBuilder.account("BANK_CASH", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.NONE.getCode())).assertBusinessRejected("科目不允许该核算对象");
    }

    @Test
    void test_unknownChartAccountOrBookRejected() {
        post(path(), AccountingSetupRequestBuilder.account("NOT_EXISTS", OwnerType.OPERATING_ENTITY.getCode(), "AE001",
            BalanceBucket.NONE.getCode())).assertBusinessRejected("科目不存在或不可过账");
        post("/ledger-books/v1/123456789/accounts", AccountingSetupRequestBuilder.account("CAPITAL",
            OwnerType.OPERATING_ENTITY.getCode(), "AE001", BalanceBucket.NONE.getCode()))
            .assertBusinessRejected("账簿不存在");
    }

    @Test
    void test_duplicateAccountRejected() {
        var body = AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "1001",
            BalanceBucket.AVAILABLE.getCode());
        post(path(), body).assertSuccess(201);

        post(path(), body).assertBusinessRejected("账户已存在");
    }

    @Test
    void test_invalidOwnerIdFormat() {
        post(path(), AccountingSetupRequestBuilder.account("CLIENT_PAYABLE", OwnerType.CLIENT.getCode(), "C-1001",
            BalanceBucket.PENDING.getCode())).assertInvalidRequest().assertDetailContains("Owner 标识格式非法");
    }

    /** 本类账簿的账户路径。 */
    String path() {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/accounts";
    }
}

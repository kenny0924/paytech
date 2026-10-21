package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.reconciliation.api.cash.request.CreateCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.mocker.external.VaultMocker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;
import com.paytech.vault.client.TokenMode;

/**
 * 银行账户新建、关闭与复核、当前账户查询（AC-03，UC-R02、R04，technique D-03、D-04、I-05）
 * <li>账号只经 Vault Protect 后以令牌与末四位落库；复核人不能是提交人，确认的摘要必须一致；同主体 + 币种 + 用途只能一个 ACTIVE。</li>
 *
 * @see com.paytech.reconciliation.api.cash.CashAccountApi#createCashAccount(String, String, CreateCashAccountRequestBody)
 * @see com.paytech.reconciliation.api.cash.CashAccountApi#requestCashAccountClosure(String, String, String)
 * @see com.paytech.reconciliation.api.cash.CashAccountApi#reviewCashAccount(String, String, String, ReviewCashAccountRequestBody)
 * @see com.paytech.reconciliation.api.cash.CashAccountApi#getCurrentCashAccount(String, String, String, String)
 */
@ExtendWith(OutputCaptureExtension.class)
class CashAccountApiTest extends AbstractBankReconciliationTest {
    /** 合成银行账号（IBAN 格式），只出现在请求体。 */
    static final String ACCOUNT_NUMBER = "AE070331234567890123456";

    /** Vault 数据类型。 */
    static final String DATA_TYPE = "CASH_ACCOUNT_NUMBER_V1";

    /** Vault 用途。 */
    static final String PURPOSE = "RECON_CASH_ACCOUNT";

    /** 合成 Vault Token（43 位 Base64URL）。 */
    static final String TOKEN = "v".repeat(42) + "1";

    /** 新建只存令牌与末四位，复核通过后 ACTIVE，当前账户只返回主键与末四位；账号不进响应与日志。 */
    @Test
    void test_createAndApprove(CapturedOutput output) {
        vault.expectProtect(DATA_TYPE, PURPOSE, TOKEN);

        ReconciliationResponseChecker created = postAsMaker(CASH_ACCOUNTS, createBody()).assertSuccess(201)
            .assertText("status", CashAccountStatus.PENDING_VERIFICATION.getCode()).assertText("accountLast4", "3456");
        long id = created.id("cashAccountId");
        CashAccount stored = cashAccounts.findById(id);
        VaultMocker.ProtectCall call = vault.protectCalls().getFirst();
        assertAll(() -> assertEquals(TOKEN, stored.getAccountVaultToken()), () -> assertEquals("3456", stored.getAccountLast4()),
            () -> assertEquals(TokenMode.REFERENCE.getCode(), call.tokenMode()), () -> assertEquals(DATA_TYPE, call.dataType()),
            () -> assertTrue(call.idempotencyKey().startsWith("recon-cash-account.")),
            () -> assertFalse(created.json().toString().contains(ACCOUNT_NUMBER)));
        get(CASH_ACCOUNTS + "/current?entityType=OPERATING_ENTITY&entityId=" + ReconciliationTestConstants.ENTITY_NO
            + "&currency=USD&purpose=CHANNEL_SETTLEMENT").assertBusinessRejected("没有有效的银行账户");

        reviewAccount(id, CheckerDecision.APPROVED, created.text("contentHash")).assertSuccess(200)
            .assertText("status", CashAccountStatus.ACTIVE.getCode())
            .assertText("checkerId", String.valueOf(ReconciliationTestConstants.CHECKER_ID));
        ReconciliationResponseChecker current = get(CASH_ACCOUNTS + "/current?entityType=OPERATING_ENTITY&entityId="
            + ReconciliationTestConstants.ENTITY_NO + "&currency=USD&purpose=CHANNEL_SETTLEMENT").assertSuccess(200);
        assertAll(() -> assertEquals(String.valueOf(id), current.text("cashAccountId")),
            () -> assertEquals("3456", current.text("accountLast4")), () -> assertEquals(2, current.json().size()),
            () -> assertFalse(output.getAll().contains(ACCOUNT_NUMBER), "银行账号明文不得进入日志"));
    }

    /** 提交人不能复核；摘要不符拒绝；拒绝新建 → CLOSED。 */
    @Test
    void test_makerCheckerAndRejectCreate() {
        vault.expectProtect(DATA_TYPE, PURPOSE, TOKEN);
        ReconciliationResponseChecker created = postAsMaker(CASH_ACCOUNTS, createBody()).assertSuccess(201);
        long id = created.id("cashAccountId");

        post(CASH_ACCOUNTS + "/" + id + "/approval", operator(ReconciliationTestConstants.MAKER_ID),
            reviewBody(CheckerDecision.APPROVED, created.text("contentHash"))).assertBusinessRejected("提交人不能复核");
        reviewAccount(id, CheckerDecision.APPROVED, "0".repeat(64)).assertBusinessRejected("内容摘要与提交不一致");
        assertEquals(CashAccountStatus.PENDING_VERIFICATION, cashAccounts.findById(id).getStatus());

        reviewAccount(id, CheckerDecision.REJECTED, created.text("contentHash")).assertSuccess(200)
            .assertText("status", CashAccountStatus.CLOSED.getCode());
        reviewAccount(id, CheckerDecision.APPROVED, created.text("contentHash")).assertBusinessRejected("没有待复核的内容");
    }

    /** 同主体 + 币种 + 用途已有 ACTIVE 时批准新建被拒，仍 PENDING_VERIFICATION。 */
    @Test
    void test_secondActiveRejected() {
        CashAccount existing = activeCashAccount();
        vault.expectProtect(DATA_TYPE, PURPOSE, TOKEN);
        ReconciliationResponseChecker created = postAsMaker(CASH_ACCOUNTS, createBody()).assertSuccess(201);

        reviewAccount(created.id("cashAccountId"), CheckerDecision.APPROVED, created.text("contentHash"))
            .assertBusinessRejected("已有有效银行账户");
        assertAll(() -> assertEquals(CashAccountStatus.PENDING_VERIFICATION,
            cashAccounts.findById(created.id("cashAccountId")).getStatus()),
            () -> assertEquals(CashAccountStatus.ACTIVE, cashAccounts.findById(existing.getId()).getStatus()));
    }

    /** 申请关闭 → 拒绝清空申请 → 再申请 → 批准 CLOSED；重复申请与非 ACTIVE 拒绝。 */
    @Test
    void test_closure() {
        CashAccount account = activeCashAccount();
        String path = CASH_ACCOUNTS + "/" + account.getId();

        ReconciliationResponseChecker requested = postAsMaker(path + "/closure", null).assertSuccess(200)
            .assertText("pendingAction", CashAccountPendingAction.CLOSE.getCode());
        postAsMaker(path + "/closure", null).assertBusinessRejected("已有待复核的关闭申请");
        reviewAccount(account.getId(), CheckerDecision.REJECTED, requested.text("contentHash")).assertSuccess(200)
            .assertText("status", CashAccountStatus.ACTIVE.getCode()).assertText("pendingAction", null);

        ReconciliationResponseChecker again = postAsMaker(path + "/closure", null).assertSuccess(200);
        reviewAccount(account.getId(), CheckerDecision.APPROVED, again.text("contentHash")).assertSuccess(200)
            .assertText("status", CashAccountStatus.CLOSED.getCode());
        postAsMaker(path + "/closure", null).assertBusinessRejected("不是 ACTIVE");
    }

    /** 主体标识与类型不符拒绝且不调 Vault；Vault 不可用返回 503 且不落库；列表只含末四位。 */
    @Test
    void test_invalidEntityAndVaultUnavailable() {
        CreateCashAccountRequestBody wrongEntity = createBody();
        wrongEntity.setEntityType(ReconEntityType.FINANCE_ENTITY.getCode());
        postAsMaker(CASH_ACCOUNTS, wrongEntity).assertBusinessRejected("主体标识与主体类型不符");
        vault.assertNoCalls();

        vault.expectProtect(DATA_TYPE, PURPOSE, TOKEN);
        vault.failProtect(MockHttpFault.disconnect());
        postAsMaker(CASH_ACCOUNTS, createBody()).assertStatus(503);
        assertEquals(List.of(), get(CASH_ACCOUNTS).assertSuccess(200).assertPage(1, 10, 0));
        assertNull(cashAccounts.listByScope(ReconEntityType.OPERATING_ENTITY, ReconciliationTestConstants.ENTITY_NO,
            ReconciliationTestConstants.CURRENCY, AccountPurpose.CHANNEL_SETTLEMENT, CashAccountStatus.PENDING_VERIFICATION)
            .stream().findFirst().orElse(null));
    }

    /** 新建请求。 */
    static CreateCashAccountRequestBody createBody() {
        CreateCashAccountRequestBody body = new CreateCashAccountRequestBody();
        body.setEntityType(ReconEntityType.OPERATING_ENTITY.getCode());
        body.setEntityId(ReconciliationTestConstants.ENTITY_NO);
        body.setAccountPurpose(AccountPurpose.CHANNEL_SETTLEMENT.getCode());
        body.setBankCode("ENBD");
        body.setAccountName("AE001 settlement");
        body.setCurrency(ReconciliationTestConstants.CURRENCY);
        body.setAccountNumber(ACCOUNT_NUMBER);
        return body;
    }

    /** 复核请求。 */
    static ReviewCashAccountRequestBody reviewBody(CheckerDecision decision, String hash) {
        ReviewCashAccountRequestBody body = new ReviewCashAccountRequestBody();
        body.setDecision(decision.getCode());
        body.setApprovedContentHash(hash);
        return body;
    }

    /** 以复核人身份复核银行账户。 */
    ReconciliationResponseChecker reviewAccount(long id, CheckerDecision decision, String hash) {
        return postAsChecker(CASH_ACCOUNTS + "/" + id + "/approval", reviewBody(decision, hash));
    }
}

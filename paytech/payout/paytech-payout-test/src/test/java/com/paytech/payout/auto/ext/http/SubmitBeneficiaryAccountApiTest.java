package com.paytech.payout.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.test.base.AbstractBeneficiaryApiTest;
import com.paytech.payout.test.builder.SubmitBeneficiaryAccountBuilder;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.mocker.external.VaultMocker;
import com.paytech.payout.test.support.PayoutTestConstants;
import com.paytech.vault.client.TokenMode;

/**
 * 提交收款账户与账户查询（AC-01、AC-02、AC-10、B6—B8）
 * <li>按国家 × 币种 × 收款方式校验字段，原值只经 Vault Protect；同 Owner 同账号同内容返回原账户、不同内容 409；不同 Owner 共用可提交并在运营视图提示。</li>
 *
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#submitBeneficiaryAccount(SubmitBeneficiaryAccountRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#listBeneficiaryAccounts(String, String, String, String, String)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#getBeneficiaryAccount(String, String, String, String)
 */
@ExtendWith(OutputCaptureExtension.class)
class SubmitBeneficiaryAccountApiTest extends AbstractBeneficiaryApiTest {

    /** AE 本地 USD：PENDING_REVIEW，库内只有 Token、脱敏户名与末四位；原值不进库、事件、补偿载荷、响应与日志；Vault 在事务外调用。 */
    @Test
    void test_submitStoresTokensOnly(CapturedOutput output) {
        PayoutResponseChecker created = submitTemplate().assertCode("status", BeneficiaryAccountStatus.PENDING_REVIEW)
            .assertText("accountLast4", "3456").assertText("holderNameMasked", PayoutTestConstants.HOLDER_NAME_MASKED)
            .assertText("accountCode", "BA-0001").assertText("bankName", PayoutTestConstants.BANK_NAME)
            .assertCode("submittedByType", BeneficiaryActorType.CLIENT_MEMBER);
        List<VaultMocker.ProtectCall> calls = vault.protectCalls();
        assertAll(() -> assertEquals(2, calls.size()),
            () -> assertEquals(TokenMode.REFERENCE.getCode(), calls.get(0).tokenMode()),
            () -> assertEquals(VaultMocker.ACCOUNT_KEY_TYPE, calls.get(1).dataType()),
            () -> assertEquals(VaultMocker.sha256(PayoutTestConstants.AE_IBAN), calls.get(1).valueSha256()),
            () -> assertFalse(created.json().get("usable").asBoolean()),
            () -> assertNull(created.text("bankCode")),
            () -> assertNull(created.text("sharedOwners")),
            () -> assertNull(created.text("reviewMakerNote")),
            () -> assertEquals(List.of(BeneficiaryEventAction.SUBMIT.getCode()), testMapper.selectEventActions(
                created.id("beneficiaryAccountId"))),
            () -> assertTrue(transactions.commits() > 0, "事务观察器未挂到事务管理器"));
        assertNoRawValue(created, output, PayoutTestConstants.AE_IBAN, PayoutTestConstants.HOLDER_NAME);
    }

    /** SA 本地、EG 本地（账号 + 银行代码）、SWIFT（账号 + BIC）都可提交；bank_code 列取本地银行代码或 BIC。 */
    @Test
    void test_submitSupportedRoutes() {
        vault.acceptProtect();
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.LOCAL, "SA", "SAR")
            .iban(PayoutTestConstants.SA_IBAN).build()).assertSuccess(201).assertText("accountLast4", "7519");
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.LOCAL, "EG", "EGP").iban(null)
            .accountNumber(PayoutTestConstants.EG_ACCOUNT_NUMBER).bankCode(PayoutTestConstants.EG_BANK_CODE).build())
            .assertSuccess(201).assertText("bankCode", PayoutTestConstants.EG_BANK_CODE).assertText("accountLast4", "0400");
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.SWIFT, "US", "USD").iban(null)
            .accountNumber(PayoutTestConstants.SWIFT_ACCOUNT_NUMBER).swiftBic(PayoutTestConstants.SWIFT_BIC).build())
            .assertSuccess(201).assertText("bankCode", PayoutTestConstants.SWIFT_BIC).assertText("accountCode", "BA-0003");
        assertEquals(3, testMapper.countAccounts());
    }

    /** 清单外的国家 / 币种组合 422，不调用 Vault、不落库。 */
    @Test
    void test_unsupportedRoute() {
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.LOCAL, "AE", "EUR").build())
            .assertError(422, PayoutErrorCode.BENEFICIARY_UNSUPPORTED_ROUTE).assertDetailContains("AE EUR");
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.LOCAL, "FR", "USD").build())
            .assertError(422, PayoutErrorCode.BENEFICIARY_UNSUPPORTED_ROUTE);
        assertAll(vault::assertNoCalls, () -> assertEquals(0, testMapper.countAccounts()));
    }

    /** 必填缺失、格式错误、字段不适用、二选一不满足为 400；请求体字段约束不符为 422；都不调用 Vault。 */
    @Test
    void test_invalidFields() {
        submit(SubmitBeneficiaryAccountBuilder.template().holderName(" ").build()).assertInvalidRequest();
        submit(SubmitBeneficiaryAccountBuilder.template().iban("AE12").build()).assertInvalidRequest();
        submit(SubmitBeneficiaryAccountBuilder.template().swiftBic(PayoutTestConstants.SWIFT_BIC).build())
            .assertInvalidRequest();
        submit(SubmitBeneficiaryAccountBuilder.template().route(BeneficiaryRailType.SWIFT, "US", "USD")
            .accountNumber(PayoutTestConstants.SWIFT_ACCOUNT_NUMBER).swiftBic(PayoutTestConstants.SWIFT_BIC).build())
            .assertInvalidRequest();
        SubmitBeneficiaryAccountRequestBody badOwner = SubmitBeneficiaryAccountBuilder.template().build();
        badOwner.setOwnerId("0");
        submit(badOwner).assertValidationError();
        assertAll(vault::assertNoCalls, () -> assertEquals(0, testMapper.countAccounts()));
    }

    /** IBAN 含空格与小写时规范化后同一账号：同内容重复提交 200 返回原账户；改户名 409 并给出已有账户（冻结字段修改须新建）。 */
    @Test
    void test_duplicateSameOwner() {
        PayoutResponseChecker created = submitTemplate();
        String id = created.text("beneficiaryAccountId");

        submit(SubmitBeneficiaryAccountBuilder.template().iban("ae07 0331 2345 6789 0123 456").build()).assertSuccess(200)
            .assertText("beneficiaryAccountId", id).assertText("contentHash", created.text("contentHash"));
        submit(SubmitBeneficiaryAccountBuilder.template().holderName("Other Holder").build())
            .assertError(409, PayoutErrorCode.BENEFICIARY_DUPLICATE).assertDetailContains(id)
            .assertDetailContains("状态 " + BeneficiaryAccountStatus.PENDING_REVIEW.getCode());
        assertEquals(1, testMapper.countAccounts());
    }

    /** 不同 Owner 共用同一账号：都可提交；运营列表与详情列出对方 Owner、编号与状态，Client 视图不返回该字段。 */
    @Test
    void test_sharedAcrossOwners() {
        submitTemplate();
        submit(SubmitBeneficiaryAccountBuilder.template().owner(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.OTHER_CLIENT_ID)
            .build()).assertSuccess(201);
        String query = ACCOUNTS + "?ownerType=" + BeneficiaryOwnerType.CLIENT.getCode() + "&ownerId="
            + PayoutTestConstants.CLIENT_ID;

        PayoutResponseChecker operations = get(query, caller(PayoutTestConstants.OPERATIONS_BFF)).assertSuccess(200);
        PayoutResponseChecker client = get(query).assertSuccess(200);
        JsonNode shared = operations.json().get("items").get(0).get("sharedOwners");
        PayoutResponseChecker detail = get(ACCOUNTS + "/" + operations.json().get("items").get(0).get("beneficiaryAccountId")
            .asString(), caller(PayoutTestConstants.OPERATIONS_BFF)).assertSuccess(200);
        assertAll(() -> assertEquals(1, operations.json().get("items").size()),
            () -> assertEquals(1, shared.size()),
            () -> assertEquals(BeneficiaryOwnerType.CLIENT.getCode(), shared.get(0).get("ownerType").asString()),
            () -> assertEquals(String.valueOf(PayoutTestConstants.OTHER_CLIENT_ID), shared.get(0).get("ownerId").asString()),
            () -> assertEquals("BA-0001", shared.get(0).get("accountCode").asString()),
            () -> assertEquals(BeneficiaryAccountStatus.PENDING_REVIEW.getCode(), shared.get(0).get("status").asString()),
            () -> assertEquals(shared, detail.json().get("sharedOwners")),
            () -> assertTrue(client.json().get("items").get(0).get("sharedOwners").isNull()));
    }

    /** Local Partner 的账户只能由运营代提交。 */
    @Test
    void test_localPartnerByOperationsOnly() {
        submit(SubmitBeneficiaryAccountBuilder.template().owner(BeneficiaryOwnerType.LOCAL_PARTNER,
            PayoutTestConstants.PARTNER_ID).build()).assertBusinessRejected().assertDetailContains("运营代提交");
        vault.acceptProtect();
        submit(SubmitBeneficiaryAccountBuilder.template().owner(BeneficiaryOwnerType.LOCAL_PARTNER,
            PayoutTestConstants.PARTNER_ID).byOperations(PayoutTestConstants.OPS_SUBMITTER_ID).build()).assertSuccess(201)
            .assertCode("ownerType", BeneficiaryOwnerType.LOCAL_PARTNER)
            .assertCode("submittedByType", BeneficiaryActorType.OPERATIONS);
    }

    /** Vault 不可用：503，不落库。 */
    @Test
    void test_vaultUnavailable() {
        vault.failProtect(MockHttpFault.disconnect());

        submit(SubmitBeneficiaryAccountBuilder.template().build()).assertStatus(503);
        assertEquals(0, testMapper.countAccounts());
    }

    /** 详情带事件时间线；Owner 不符或不存在时 404。 */
    @Test
    void test_detailAndOwnership() {
        PayoutResponseChecker created = submitTemplate();
        String path = ACCOUNTS + "/" + created.text("beneficiaryAccountId");

        PayoutResponseChecker detail = get(path + "?ownerType=CLIENT&ownerId=" + PayoutTestConstants.CLIENT_ID)
            .assertSuccess(200);
        assertAll(() -> assertEquals(1, detail.json().get("events").size()),
            () -> assertEquals(BeneficiaryEventAction.SUBMIT.getCode(),
                detail.json().get("events").get(0).get("action").asString()));
        get(path + "?ownerType=CLIENT&ownerId=" + PayoutTestConstants.OTHER_CLIENT_ID)
            .assertError(404, PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND);
        get(ACCOUNTS + "/123456789").assertError(404, PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND);
        get(path + "?ownerType=CLIENT").assertInvalidRequest();
    }

    /** 原值不出现在账户行、事件行、补偿任务、响应与日志中。 */
    void assertNoRawValue(PayoutResponseChecker response, CapturedOutput output, String... rawValues) {
        String stored = testMapper.selectAccountRows().toString() + testMapper.selectEventRows()
            + testMapper.selectCompensationEvents();
        for (String raw : rawValues) {
            assertAll(() -> assertFalse(stored.contains(raw), "原值进入数据库"),
                () -> assertFalse(response.raw().contains(raw), "原值进入响应"),
                () -> assertFalse(output.getAll().contains(raw), "原值进入日志"));
        }
        Map<String, Object> row = testMapper.selectAccountRows().getFirst();
        assertEquals(43, row.get("beneficiary_vault_token").toString().length());
    }
}

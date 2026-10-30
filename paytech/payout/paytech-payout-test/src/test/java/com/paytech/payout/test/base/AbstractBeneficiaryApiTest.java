package com.paytech.payout.test.base;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.test.builder.SubmitBeneficiaryAccountBuilder;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.support.PayoutTestConstants;

/** 收款账户接口测试的公共入口调用：路径常量、提交与核验流程的 HTTP 调用；不含 @Test。 */
public abstract class AbstractBeneficiaryApiTest extends MockTestBase {
    /** 收款账户接口根路径。 */
    protected static final String ACCOUNTS = "/payout/v1/beneficiary-accounts";

    /** 核验队列路径。 */
    protected static final String REVIEWS = "/payout/v1/beneficiary-reviews";

    /** 提交（Vault Protect 已接受）。 */
    protected PayoutResponseChecker submit(SubmitBeneficiaryAccountRequestBody body) {
        return post(ACCOUNTS, body);
    }

    /** 以模板提交并返回 201 的账户。 */
    protected PayoutResponseChecker submitTemplate() {
        vault.acceptProtect();
        return submit(SubmitBeneficiaryAccountBuilder.template().build()).assertSuccess(201);
    }

    /** Maker 意见。 */
    protected PayoutResponseChecker makerReview(PayoutResponseChecker account, MakerReviewDecision decision,
        String reasonCode, long makerId) {
        return makerReview(account, decision, reasonCode, makerId, null, null);
    }

    /** Maker 意见（带内部备注与给 Client 的说明）。 */
    protected PayoutResponseChecker makerReview(PayoutResponseChecker account, MakerReviewDecision decision,
        String reasonCode, long makerId, String note, String clientNote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision.getCode());
        body.put("reasonCode", reasonCode);
        body.put("note", note);
        body.put("clientNote", clientNote);
        body.put("contentHash", account.text("contentHash"));
        body.put("expectedVersion", account.text("version"));
        body.put("actorId", String.valueOf(makerId));
        return post(ACCOUNTS + "/" + account.text("beneficiaryAccountId") + "/maker-reviews",
            caller(PayoutTestConstants.OPERATIONS_BFF), body);
    }

    /** Checker 决定。 */
    protected PayoutResponseChecker checkerDecision(PayoutResponseChecker account, CheckerDecision decision,
        String reasonCode, long checkerId) {
        return checkerDecision(account, decision, reasonCode, checkerId, null);
    }

    /** Checker 决定（带给 Client 的说明）。 */
    protected PayoutResponseChecker checkerDecision(PayoutResponseChecker account, CheckerDecision decision,
        String reasonCode, long checkerId, String clientNote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision.getCode());
        body.put("reasonCode", reasonCode);
        body.put("clientNote", clientNote);
        body.put("contentHash", account.text("contentHash"));
        body.put("expectedVersion", account.text("version"));
        body.put("actorId", String.valueOf(checkerId));
        return post(ACCOUNTS + "/" + account.text("beneficiaryAccountId") + "/checker-decisions",
            caller(PayoutTestConstants.OPERATIONS_BFF), body);
    }

    /** 状态命令请求体。 */
    protected static Map<String, Object> command(BeneficiaryOwnerType ownerType, Long ownerId, String reasonCode,
        String expectedVersion, BeneficiaryActorType actorType, long actorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ownerType", ownerType == null ? null : ownerType.getCode());
        body.put("ownerId", ownerId == null ? null : String.valueOf(ownerId));
        body.put("reasonCode", reasonCode);
        body.put("expectedVersion", expectedVersion);
        body.put("actorType", actorType.getCode());
        body.put("actorId", String.valueOf(actorId));
        return body;
    }

    /** 运营执行状态命令。 */
    protected PayoutResponseChecker operationsCommand(String action, PayoutResponseChecker account, String reasonCode) {
        return post(ACCOUNTS + "/" + account.text("beneficiaryAccountId") + "/" + action,
            caller(PayoutTestConstants.OPERATIONS_BFF), command(null, null, reasonCode, account.text("version"),
                BeneficiaryActorType.OPERATIONS, PayoutTestConstants.MAKER_ID));
    }

    /** 提交并完成 Maker 通过、Checker 批准，返回 VERIFIED 账户（需已配置 VERIFIED 通知结果）。 */
    protected PayoutResponseChecker verifiedAccount() {
        PayoutResponseChecker submitted = submitTemplate();
        PayoutResponseChecker reviewed = makerReview(submitted, MakerReviewDecision.APPROVE, null,
            PayoutTestConstants.MAKER_ID).assertSuccess(200);
        return checkerDecision(reviewed, CheckerDecision.APPROVED, null, PayoutTestConstants.CHECKER_ID).assertSuccess(200);
    }
}

package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.reconciliation.api.clearing.request.AddClearingAccountMidRequestBody;
import com.paytech.reconciliation.api.clearing.request.ChangeClearingAccountStatusRequestBody;
import com.paytech.reconciliation.api.clearing.request.CreateClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.request.ResolveClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.request.UpdateClearingAccountSettingsRequestBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.CashAccountBuilder;
import com.paytech.reconciliation.test.builder.ChannelMidBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 清算账户维护、未归属 MID 与解析（AC-02）：挂 MID 前经真实出站客户端向 channel-connector Mock 核对
 *
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#createClearingAccount(String, String, CreateClearingAccountRequestBody)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#updateClearingAccountSettings(String, String, String, UpdateClearingAccountSettingsRequestBody)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#addClearingAccountMid(String, String, String, AddClearingAccountMidRequestBody)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#removeClearingAccountMid(String, String, String, String)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#changeClearingAccountStatus(String, String, String, ChangeClearingAccountStatusRequestBody)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#listClearingAccounts(String, String, String, String, Integer, Integer)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#getClearingAccount(String)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountApi#listUnassignedMids(String, String, String)
 * @see com.paytech.reconciliation.api.clearing.ClearingAccountResolutionApi#resolveClearingAccount(ResolveClearingAccountRequestBody)
 */
class ClearingAccountApiTest extends AbstractReconciliationFlowTest {

    /** 新建：ACTIVE、无 MID、到账银行账户与容差按币种精度输出；编码重复拒绝。 */
    @Test
    void test_createClearingAccount() {
        CashAccount cash = data.cashAccount(CashAccountBuilder.template().build());
        CreateClearingAccountRequestBody body = createBody("CA-NI-USD", String.valueOf(cash.getId()));

        var created = postAsMaker(CLEARING_ACCOUNTS, body).assertSuccess(201);

        created.assertText("status", ClearingAccountStatus.ACTIVE.getCode()).assertText("costToleranceAmount", "0.10")
            .assertText("settlementCashAccountId", String.valueOf(cash.getId()));
        assertEquals(0, created.json().path("mids").size());
        postAsMaker(CLEARING_ACCOUNTS, body).assertBusinessRejected("清算账户编码已存在");
        channelConnector.assertNoCalls();
    }

    /** 到账银行账户必须同主体、同币种、渠道结算用途且 ACTIVE；容差不能超过币种精度。 */
    @Test
    void test_createClearingAccountInvalidSettings() {
        CashAccount payout = data.cashAccount(CashAccountBuilder.template()
            .with(account -> account.setAccountPurpose(AccountPurpose.PAYOUT)).build());

        postAsMaker(CLEARING_ACCOUNTS, createBody("CA-BAD-CASH", String.valueOf(payout.getId())))
            .assertBusinessRejected("渠道结算用途");
        postAsMaker(CLEARING_ACCOUNTS, createBody("CA-NO-CASH", "999"))
            .assertBusinessRejected("到账银行账户不存在或未生效");
        CreateClearingAccountRequestBody precision = createBody("CA-PRECISION", null);
        precision.setCostToleranceAmount("0.001");
        postAsMaker(CLEARING_ACCOUNTS, precision).assertInvalidRequest();
    }

    /** 修改配置整体替换；清算账户不存在拒绝。 */
    @Test
    void test_updateSettings() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        CashAccount cash = data.cashAccount(CashAccountBuilder.template().build());
        UpdateClearingAccountSettingsRequestBody body = new UpdateClearingAccountSettingsRequestBody();
        body.setSettlementCashAccountId(String.valueOf(cash.getId()));
        body.setUnmatchedWaitDays(5);
        body.setCostToleranceAmount("1.5");
        body.setCashGraceDays(4);

        exchange(HttpMethod.PUT, CLEARING_ACCOUNTS + "/" + account.getId() + "/settings", body).assertSuccess(200)
            .assertText("costToleranceAmount", "1.50").assertText("settlementCashAccountId", String.valueOf(cash.getId()))
            .assertText("version", "2");
        exchange(HttpMethod.PUT, CLEARING_ACCOUNTS + "/99/settings", body).assertBusinessRejected("清算账户不存在");
    }

    /** 挂 MID：核对通过后归属；重复挂同一 MID 返回当前结果；摘除后可解析不到。 */
    @Test
    void test_addAndRemoveMid() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        long midId = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(midId));

        addMid(account, midId).assertSuccess(200).assertText("version", "2");
        addMid(account, midId).assertSuccess(200).assertText("version", "2");
        assertEquals(List.of("detail:" + midId, "list:" + ReconciliationTestConstants.ENTITY_NO + ":ACTIVE"),
            channelConnector.calls());
        resolve(midId).assertSuccess(200).assertText("clearingAccountId", String.valueOf(account.getId()))
            .assertText("entityNo", ReconciliationTestConstants.ENTITY_NO);

        exchange(HttpMethod.DELETE, CLEARING_ACCOUNTS + "/" + account.getId() + "/mids/" + midId, null)
            .assertSuccess(200).assertText("version", "3");
        assertEquals(0, get(CLEARING_ACCOUNTS + "/" + account.getId()).assertSuccess(200).json().path("mids").size());
        resolve(midId).assertProblem(400, ReconciliationErrorCode.CLEARING_ACCOUNT_NOT_RESOLVED.getCode());
        exchange(HttpMethod.DELETE, CLEARING_ACCOUNTS + "/" + account.getId() + "/mids/" + midId, null)
            .assertBusinessRejected("MID 不在本清算账户");
    }

    /** 同一 MID + 币种已归属其他清算账户：业务拒绝，不调用 channel-connector。 */
    @Test
    void test_midAlreadyAssigned() {
        ClearingAccount owner = data.clearingAccount(ClearingAccountBuilder.template().build());
        ClearingAccount other = data.clearingAccount(ClearingAccountBuilder.template().build());
        long midId = ReconciliationTestConstants.nextId();
        data.clearingAccountMid(ClearingAccountBuilder.mid(owner, midId));

        addMid(other, midId).assertBusinessRejected("MID + 币种已归属其他清算账户");
        channelConnector.assertNoCalls();
    }

    /** MID 不存在、未激活、渠道不一致、经营主体不一致、能力组无清算币种：逐一拒绝。 */
    @Test
    void test_midMismatch() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        long missing = ReconciliationTestConstants.nextId();
        channelConnector.rejectMid(String.valueOf(missing), 400, new ApiProblemResponse("about:blank",
            "Bad Request", "MID 不存在", CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), REQUEST_ID,
            "2026-10-20T00:00:00.000000Z", null));
        addMid(account, missing).assertBusinessRejected("MID 不存在");

        long suspended = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(suspended).status("SUSPENDED"));
        addMid(account, suspended).assertBusinessRejected("MID 未激活");

        long otherChannel = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(otherChannel).channel("78", "GEIDEA"));
        addMid(account, otherChannel).assertBusinessRejected("MID 渠道与清算账户不一致");

        long otherEntity = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(otherEntity).entityNo("AE002"));
        addMid(account, otherEntity).assertBusinessRejected("MID 经营主体与清算账户不一致");

        ClearingAccount aed = data.clearingAccount(ClearingAccountBuilder.template()
            .with(item -> item.setCurrency("AED")).build());
        long usdOnly = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(usdOnly));
        addMid(aed, usdOnly).assertBusinessRejected("MID 能力组不含清算币种");
        assertEquals(0, get(CLEARING_ACCOUNTS + "/" + account.getId()).assertSuccess(200).json().path("mids").size());
    }

    /** 停用后挂 MID 被拒、解析不到；已是目标状态直接返回。 */
    @Test
    void test_changeStatus() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        long midId = ReconciliationTestConstants.nextId();
        data.clearingAccountMid(ClearingAccountBuilder.mid(account, midId));

        changeStatus(account, ClearingAccountStatus.INACTIVE).assertSuccess(200)
            .assertText("status", ClearingAccountStatus.INACTIVE.getCode()).assertText("version", "2");
        changeStatus(account, ClearingAccountStatus.INACTIVE).assertSuccess(200).assertText("version", "2");
        resolve(midId).assertProblem(400, ReconciliationErrorCode.CLEARING_ACCOUNT_NOT_RESOLVED.getCode());
        addMid(account, ReconciliationTestConstants.nextId()).assertBusinessRejected("清算账户不存在或已停用");
    }

    /** 未归属 MID = ACTIVE MID × 能力组币种 − 已归属；列表与详情。 */
    @Test
    void test_unassignedMidsAndQueries() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        long assigned = ReconciliationTestConstants.nextId();
        long free = ReconciliationTestConstants.nextId();
        mockMid(ChannelMidBuilder.active(assigned).currency("AED"));
        mockMid(ChannelMidBuilder.active(free));
        data.clearingAccountMid(ClearingAccountBuilder.mid(account, assigned));

        JsonNode items = get(CLEARING_ACCOUNTS + "/unassigned-mids?entityNo=" + ReconciliationTestConstants.ENTITY_NO)
            .assertSuccess(200).json().path("items");

        assertEquals(List.of(Map.of(assigned, "AED"), Map.of(free, ReconciliationTestConstants.CURRENCY)),
            List.of(Map.of(items.get(0).path("midId").asLong(), items.get(0).path("currency").asString()),
                Map.of(items.get(1).path("midId").asLong(), items.get(1).path("currency").asString())));
        assertEquals(2, items.size());
        var page = get(CLEARING_ACCOUNTS + "?status=" + ClearingAccountStatus.ACTIVE.getCode()).assertSuccess(200)
            .assertPage(1, 10, 1);
        assertEquals(1, page.getFirst().path("midCount").asInt());
        get(CLEARING_ACCOUNTS + "/" + account.getId()).assertSuccess(200)
            .assertText("clearingAccountCode", account.getClearingAccountCode());
        get(CLEARING_ACCOUNTS + "/99").assertBusinessRejected("清算账户不存在");
        assertNull(get(CLEARING_ACCOUNTS + "/" + account.getId()).text("settlementCashAccountId"));
    }

    /** 新建请求体。 */
    static CreateClearingAccountRequestBody createBody(String code, String cashAccountId) {
        CreateClearingAccountRequestBody body = new CreateClearingAccountRequestBody();
        body.setClearingAccountCode(code);
        body.setChannelCode(ReconciliationTestConstants.CHANNEL_CODE);
        body.setEntityNo(ReconciliationTestConstants.ENTITY_NO);
        body.setCurrency(ReconciliationTestConstants.CURRENCY);
        body.setSettlementCashAccountId(cashAccountId);
        body.setUnmatchedWaitDays(3);
        body.setCostToleranceAmount("0.1");
        body.setCashGraceDays(2);
        return body;
    }

    /** 配置 channel-connector 中的一个 MID。 */
    void mockMid(ChannelMidBuilder mid) {
        channelConnector.mockMid(mid.summary(), mid.detail());
    }

    /** 挂 MID。 */
    com.paytech.reconciliation.test.checker.ReconciliationResponseChecker addMid(ClearingAccount account, long midId) {
        AddClearingAccountMidRequestBody body = new AddClearingAccountMidRequestBody();
        body.setMidId(String.valueOf(midId));
        return postAsMaker(CLEARING_ACCOUNTS + "/" + account.getId() + "/mids", body);
    }

    /** 启停。 */
    com.paytech.reconciliation.test.checker.ReconciliationResponseChecker changeStatus(ClearingAccount account,
        ClearingAccountStatus status) {
        ChangeClearingAccountStatusRequestBody body = new ChangeClearingAccountStatusRequestBody();
        body.setStatus(status.getCode());
        return postAsMaker(CLEARING_ACCOUNTS + "/" + account.getId() + "/status", body);
    }

    /** 按 MID + USD 解析（内部接口，无操作人头）。 */
    com.paytech.reconciliation.test.checker.ReconciliationResponseChecker resolve(long midId) {
        ResolveClearingAccountRequestBody body = new ResolveClearingAccountRequestBody();
        body.setMidId(String.valueOf(midId));
        body.setCurrency(ReconciliationTestConstants.CURRENCY);
        return post(CLEARING_ACCOUNTS + "/resolutions", body);
    }
}

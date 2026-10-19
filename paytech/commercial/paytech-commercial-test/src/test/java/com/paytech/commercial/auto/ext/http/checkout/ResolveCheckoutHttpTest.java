package com.paytech.commercial.auto.ext.http.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.test.base.checkout.AbstractCheckoutApiTest;

/**
 * 两段式完整解析的真实 HTTP、业务拒绝、配置变化和只读行为（design/06 §2.2）。
 * <li>原用例对客户主档表的负例 UPDATE 改为 client-master DTO 变体或错误；本库商务事实仍由合成夹具插入真实 mor_commercial。</li>
 * @see CheckoutContextApi#resolve(String, ResolveCheckoutContextRequestBody)
 */
class ResolveCheckoutHttpTest extends AbstractCheckoutApiTest {
    /** 每例独立准备完整商务事实与默认主档候选，具体差异由用例声明。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
        clientMaster.mockCandidates();
    }

    /** 正式不可用资格端口必须通过共享 Problem 返回，不保存任何查询结果。 */
    @Test
    void test_unconnectedRiskFailsClosedAndLeavesDatabaseUnchanged() {
        fixture.assertReadOnly(
                () -> invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE").assertNoCommercialFacts());
    }

    /** 支付方式只接受规范 Code；合法值参与开通项匹配：无候选开通时业务拒绝，有开通时停在尚未接入的 Risk 资格。 */
    @Test
    void test_paymentMethodCodeSelectsEnabledCapability() {
        var request = request();
        request.setPaymentMethodCode("card");
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY"));
        assertEquals(0, clientMaster.count("candidates"));
        request.setPaymentMethodCode(CommercialCodes.PaymentMethod.GOOGLE_PAY);
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY"));
        request.setPaymentMethodCode(CommercialCodes.PaymentMethod.CARD);
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(503, "OWNER_UNAVAILABLE"));
    }

    /** class DTO 缺失必填字段时由 HTTP Bean Validation 拒绝。 */
    @Test
    void test_missingLocaleFailsBeforeResolution() {
        var request = request();
        request.setLocale(null);
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "VALIDATION_ERROR")
                .assertFieldError("locale").assertNoCommercialFacts());
        assertEquals(0, clientMaster.count("candidates"));
    }

    /** 调用方不能注入 OE 来绕过解析选择。 */
    @Test
    void test_callerCannotSelectEntity() {
        var body = json.valueToTree(request()).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) body).put("entityNo", "AE991");
        fixture.assertReadOnly(
                () -> invoke(body, headers()).assertProblem(400, "INVALID_REQUEST").assertNoCommercialFacts());
    }

    /** 非 Commerce 私有调用与不可访问的 Client 使用同一拒绝合同。 */
    @Test
    void test_invalidCallerCannotReadCommercialFacts() {
        var headers = headers();
        headers.set("X-Caller-Service", "partner-console-bff");
        fixture.assertReadOnly(
                () -> invoke(request(), headers).assertProblem(404, "CLIENT_NOT_FOUND").assertNoCommercialFacts());
        assertEquals(0, clientMaster.count("candidates"));
    }

    /** 没有批准法律内容的 Locale 不得隐式回退：client-master 按 Locale 不返回法律记录，全部候选落选。 */
    @Test
    void test_unsupportedLocaleHasNoEligibleCandidate() {
        var candidates = clientMaster.sample();
        candidates.getCandidates().forEach(candidate -> {
            candidate.setLegalDocuments(java.util.List.of());
            candidate.setSellerDisclosures(java.util.List.of());
        });
        clientMaster.mockCandidates(candidates);
        var request = request();
        request.setLocale("fr-FR");
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY"));
    }

    /** 批准业务数据的支持范围不受部署目录限制：client-master 按新范围返回候选后仍必须执行真实 Risk 资格检查。 */
    @Test
    void test_approvedScopeAndLegalDataDefineSupportedLocation() {
        var request = request();
        request.setConsumerCountryCode("GB");
        request.setCurrency("GBP");
        request.setLocale("en-GB");
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(503, "OWNER_UNAVAILABLE"));
    }

    /** 当前主体和分类必须已批准：client-master 的整体拒绝原样映射为 CLIENT_NOT_ELIGIBLE。 */
    @Test
    void resolutionObservesPersistedApprovalAndClassificationReadiness() {
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        clientMaster.mockCandidatesProblem(422, CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode());
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE"));
        clientMaster.mockCandidates();
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        clientMaster.mockCandidatesProblem(422, CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode());
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE"));
    }

    /** 本地完整候选不能绕过尚未接入的真实 Risk 资格。 */
    @Test
    void resolverRemainsClosedUntilRiskContractIsConnected() {
        assertEquals("AE991", context(query()).candidates().iterator().next().entityNo());
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE")
                .assertDoesNotExpose("910000000000113"));
    }

    /** 不向越权调用者泄露 Client 是否存在。 */
    @Test
    void unknownClientAndInvalidCallerAreIndistinguishable() {
        clientMaster.mockCandidatesProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
        var missing = request();
        missing.setClientId("9223372036854775806");
        invoke(missing, headers()).assertProblem(404, "CLIENT_NOT_FOUND");
        var caller = headers();
        caller.set("X-Caller-Service", "partner-console-bff");
        invoke(request(), caller).assertProblem(404, "CLIENT_NOT_FOUND");
    }

    /** 额外的路由或重复 Query 参数不能进入数据查询。 */
    @Test
    void forbiddenRoutingInputAndDuplicateQueryParametersAreRejected() {
        invoke(request(), "?entityNo=AE991", headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        invoke(request(), "?currency=USD&currency=USD", headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(request());
        body.put("entityNo", "AE991");
        invoke(body, headers()).assertProblem(400, "INVALID_REQUEST");
        assertEquals(0, clientMaster.count("candidates"));
    }

    /** 新调用观察 client-master 当前候选，不把上次候选作为准入凭证。 */
    @Test
    void changedScopeIsObservedByNextResolution() {
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        clientMaster.mockNoCandidates();
        invoke(request(), headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY");
        assertFalse(context(query()).candidates().iterator().hasNext());
    }

    /** 分类未就绪与主档配置冲突各自映射为原合同的业务错误。 */
    @Test
    void dataConflictReturnsProblemWithoutSelectingAnotherEntity() {
        clientMaster.mockCandidatesProblem(422, CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode());
        invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE");
        clientMaster.mockCandidatesProblem(409, CheckoutCandidateErrorCode.CANDIDATE_CONTEXT_AMBIGUOUS.getCode());
        invoke(request(), headers()).assertProblem(409, "CCT_CONTEXT_AMBIGUOUS");
    }

    /** client-master 读取失败（如未知持久化枚举）失败关闭为 503，不泄露数据库值或 Java 类型。 */
    @Test
    void unknownPersistedStatusFailsClosedWithoutLeakingDatabaseDetails() {
        clientMaster.mockCandidatesProblem(503, CheckoutCandidateErrorCode.CANDIDATE_SOURCE_UNAVAILABLE.getCode());
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE").assertDoesNotExpose("UNKNOWN_STATUS")
                .assertDoesNotExpose("ClientStatus").assertDoesNotExpose("CANDIDATE_SOURCE_UNAVAILABLE");
    }

    /** client-master 不可用时失败关闭为 503，不降级、不读取或修改本库事实（design/06 §4）。 */
    @Test
    void test_clientMasterUnavailableFailsClosedAndLeavesDatabaseUnchanged() {
        clientMaster.mockCandidatesUnavailable();
        fixture.assertReadOnly(
                () -> invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE").assertNoCommercialFacts());
        assertEquals(1, clientMaster.count("candidates"));
        assertEquals(0, clientMaster.count("profiles"));
    }
}

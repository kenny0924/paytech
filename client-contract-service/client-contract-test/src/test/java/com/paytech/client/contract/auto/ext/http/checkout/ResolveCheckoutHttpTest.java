package com.paytech.client.contract.auto.ext.http.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.client.contract.api.checkout.CheckoutContextApi;
import com.paytech.client.contract.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.client.contract.test.base.checkout.AbstractCheckoutApiTest;
import com.paytech.client.contract.test.dal.checkout.CheckoutTestMapper;

/**
 * 完整解析的真实 HTTP、业务拒绝、配置变化和只读行为。
 * @see CheckoutContextApi#resolve(String, ResolveCheckoutContextRequestBody)
 */
class ResolveCheckoutHttpTest extends AbstractCheckoutApiTest {
    @Autowired
    CheckoutTestMapper data;
    @Autowired
    com.paytech.client.contract.domainservice.service.CheckoutQueryService service;

    /** 每例独立准备完整商业事实，具体差异由用例声明。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
    }

    /** 正式不可用资格端口必须通过共享 Problem 返回，不保存任何查询结果。 */
    @Test
    void test_unconnectedRiskFailsClosedAndLeavesDatabaseUnchanged() {
        fixture.assertReadOnly(
                () -> invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE").assertNoCommercialFacts());
    }

    /** class DTO 缺失必填字段时由 HTTP Bean Validation 拒绝。 */
    @Test
    void test_missingLocaleFailsBeforeResolution() {
        var request = request();
        request.setLocale(null);
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "VALIDATION_ERROR")
                .assertFieldError("locale").assertNoCommercialFacts());
    }

    /** 调用方不能注入 OE 来绕过 CCT 选择。 */
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
    }

    /** 格式合法但没有批准法律内容的 Locale 不得隐式回退。 */
    @Test
    void test_unsupportedLocaleHasNoEligibleCandidate() {
        var request = request();
        request.setLocale("fr-FR");
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY"));
    }

    /** 批准业务数据的支持范围不再受部署目录限制，仍必须执行真实 Risk 资格检查。 */
    @Test
    void test_approvedScopeAndLegalDataDefineSupportedLocation() {
        fixture.useCommercialLocation("GB", "GBP", "en-GB");
        var request = request();
        request.setConsumerCountryCode("GB");
        request.setCurrency("GBP");
        request.setLocale("en-GB");
        fixture.assertReadOnly(() -> invoke(request, headers()).assertProblem(503, "OWNER_UNAVAILABLE"));
    }

    /** 当前主体和分类必须已批准，且合同签署时引用的资料仍成立。 */
    @Test
    void resolutionObservesPersistedApprovalAndClassificationReadiness() {
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        data.clientApproval(null);
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE"));
        data.clientApproval(java.time.Instant.parse("2026-09-01T00:00:00Z"));
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        data.clearClassificationConfirmation();
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE"));
    }

    /** 本地完整候选不能绕过尚未接入的真实 Risk 资格。 */
    @Test
    void resolverRemainsClosedUntilRiskContractIsConnected() {
        assertEquals("AE991", service.load(query()).candidates().iterator().next().entityNo());
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE")
                .assertDoesNotExpose("910000000000113"));
    }

    /** 不向越权调用者泄露 Client 是否存在。 */
    @Test
    void unknownClientAndInvalidCallerAreIndistinguishable() {
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
    }

    /** 新调用观察当前 Scope，不把上次候选作为准入凭证。 */
    @Test
    void changedScopeIsObservedByNextResolution() {
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE");
        data.endScopes();
        invoke(request(), headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY");
        assertFalse(service.load(query()).candidates().iterator().hasNext());
    }

    /** 分类未就绪和批准事实冲突使用各自的业务错误。 */
    @Test
    void dataConflictReturnsProblemWithoutSelectingAnotherEntity() {
        data.primaryClassification(null);
        invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE");
        data.primaryClassification(1);
        data.rejectScopeApplication();
        invoke(request(), headers()).assertProblem(409, "CCT_CONTEXT_AMBIGUOUS");
    }

    /** 未知持久化枚举保持不可用，不泄露数据库值或 Java 类型。 */
    @Test
    void unknownPersistedStatusFailsClosedWithoutLeakingDatabaseDetails() {
        data.unknownClientStatus();
        invoke(request(), headers()).assertProblem(503, "OWNER_UNAVAILABLE").assertDoesNotExpose("UNKNOWN_STATUS")
                .assertDoesNotExpose("ClientStatus");
    }
}

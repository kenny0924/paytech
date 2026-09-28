package com.paytech.client.contract.auto.ext.http.checkout;

import org.junit.jupiter.api.Test;

import com.paytech.client.contract.api.checkout.CheckoutContextApi;
import com.paytech.client.contract.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.client.contract.test.base.checkout.AbstractCheckoutApiTest;

import tools.jackson.databind.node.ObjectNode;

/**
 * 不准备数据库业务行，通过真实 HTTP 在读取前验证输入边界。
 * @see CheckoutContextApi#resolve(String, ResolveCheckoutContextRequestBody)
 */
class ResolveCheckoutValidationTest extends AbstractCheckoutApiTest {
    /** JSON 未知字段、隐式类型转换和缺少必填字段均拒绝。 */
    @Test
    void malformedUnknownAndCoercedBodyFieldsFailAtHttpBoundary() {
        var request = (ObjectNode) json.valueToTree(request());
        request.put("entityNo", "AE991");
        invoke(request, headers()).assertProblem(400, "INVALID_REQUEST");
        request = (ObjectNode) json.valueToTree(request());
        request.put("clientId", 1);
        invoke(request, headers()).assertProblem(400, "INVALID_REQUEST");
        request = (ObjectNode) json.valueToTree(request());
        request.remove("orderNo");
        invoke(request, headers()).assertProblem(422, "VALIDATION_ERROR");
    }

    /** 调用者和封闭 Query 参数都先于数据库访问检查。 */
    @Test
    void callerAndClosedQueryParametersAreEnforced() {
        invoke(request(), "?entityNo=AE991", headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        invoke(request(), "?clientId=1&clientId=1", headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        var caller = headers();
        caller.set("X-Caller-Service", "partner-console-bff");
        invoke(request(), caller).assertProblem(404, "CLIENT_NOT_FOUND");
    }

    /** 格式、规范身份和未来时间在读取前拒绝。 */
    @Test
    void malformedCodesAndCanonicalIdsAreValidatedBeforeReadingFacts() {
        var request = request();
        request.setCurrency("usd");
        invoke(request, headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        request = request();
        request.setClientId("01");
        invoke(request, headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
        request = request();
        request.setContextAsOfTime("2099-01-01T00:00:00Z");
        invoke(request, headers()).assertProblem(422, "INVALID_CHECKOUT_QUERY");
    }
}

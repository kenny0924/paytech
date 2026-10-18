package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.externalapi.StandardApiManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.response.ApiFieldDetail;
import com.paytech.platform.support.api.externalapi.response.StandardApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.StandardApiSummary;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiRequests;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.core.type.TypeReference;

/**
 * 标准 API 管理：创建与详情、列表筛选、字段整组替换、版本冲突、静态校验、实现引用保护与权限。
 *
 * @see StandardApiManagementApi#standardApis(String, String, Integer, Integer, String)
 * @see StandardApiManagementApi#standardApi(String, String, String)
 * @see StandardApiManagementApi#createStandardApi(String, String, CreateStandardApiRequestBody)
 * @see StandardApiManagementApi#updateStandardApi(String, String, String, UpdateStandardApiRequestBody)
 * @see StandardApiManagementApi#deleteStandardApi(String, String, String, DeleteExternalApiConfigRequestBody)
 */
class StandardApiManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/standard-apis";

    private Operator admin;

    /** 本类公共：一名平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
    }

    /** 创建后版本为 1，详情按数组顺序返回字段，并写入成功审计。 */
    @Test
    void test_createAndGet() {
        assertMutation(post(PATH, admin, ExternalApiRequests.createStandardApi("PAY")), "STANDARD_API", "PAY", 1);
        auditChecker.assertEvent(requestId, "OPERATIONS_STANDARD_API_CREATE", AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), "STANDARD_API", "PAY");

        var detail = get(PATH + "/PAY", admin).assertOk().as(StandardApiResponseBody.class);

        assertAll(() -> assertEquals("PAY 名称", detail.getStandardApiName()), () -> assertEquals(1, detail.getVersion()),
            () -> assertEquals(List.of("paymentContext", "paymentContext.attemptId", "paymentContext.amount",
                "paymentContext.vaultToken"), paths(detail.getRequestFields())),
            () -> assertEquals(List.of("paymentStatus", "transactionRef"), paths(detail.getResponseFields())),
            () -> assertEquals(ApiFieldDataType.OBJECT.getCode(), detail.getRequestFields().getFirst().getDataType()),
            () -> assertFalse(detail.getResponseFields().get(1).isRequired()),
            () -> assertTrue(detail.getImplementations().isEmpty()),
            () -> assertEquals(admin.actor(), detail.getUpdatedBy()));
    }

    /** 列表按编码或名称包含筛选，统计字段数与已实现接入方。 */
    @Test
    void test_listWithKeyword() {
        var pay = externalApi.standardApi("PAY");
        externalApi.standardApi("REFUND");
        var integration = externalApi.integration("DEMO");
        externalApi.createOrderApi(integration);
        externalApi.implementation(integration, pay, ExternalApiSamples.payFlow());

        var all = list("?page=1&size=10");
        var filtered = list("?keyword=PA");

        assertAll(() -> assertEquals(2, all.total()),
            () -> assertEquals(List.of("PAY"),
                filtered.items().stream().map(StandardApiSummary::getStandardApiCode).toList()),
            () -> assertEquals(4, filtered.items().getFirst().getRequestFieldCount()),
            () -> assertEquals(2, filtered.items().getFirst().getResponseFieldCount()),
            () -> assertEquals("DEMO", filtered.items().getFirst().getImplementedBy().getFirst().getIntegrationCode()));
    }

    /** 修改整组替换字段并递增版本；旧版本再次提交返回 409，数据保持第一次修改的结果。 */
    @Test
    void test_updateReplacesFieldsAndRejectsStaleVersion() {
        externalApi.standardApi("PAY");
        var fields = List.of(ExternalApiSamples.field(ApiFieldLocation.BODY, "orderId", ApiFieldDataType.STRING, true));

        assertMutation(put(PATH + "/PAY", admin, ExternalApiRequests.updateStandardApi("支付 2", fields, List.of(), 1)),
            "STANDARD_API", "PAY", 2);
        put(PATH + "/PAY", admin, ExternalApiRequests.updateStandardApi("旧版本", List.of(), List.of(), 1))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        var detail = get(PATH + "/PAY", admin).assertOk().as(StandardApiResponseBody.class);
        assertAll(() -> assertEquals("支付 2", detail.getStandardApiName()), () -> assertEquals(2, detail.getVersion()),
            () -> assertEquals(List.of("orderId"), paths(detail.getRequestFields())),
            () -> assertTrue(detail.getResponseFields().isEmpty()));
    }

    /** 编码重复返回业务校验失败，原记录不变。 */
    @Test
    void test_duplicateCode() {
        externalApi.standardApi("PAY");

        post(PATH, admin, ExternalApiRequests.createStandardApi("PAY", List.of(), List.of())).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertEquals(4,
            get(PATH + "/PAY", admin).assertOk().as(StandardApiResponseBody.class).getRequestFields().size());
    }

    /** 字段静态校验一次返回全部问题位置（标准 API 只用 BODY 字段，父对象须先定义），不落库。 */
    @Test
    void test_invalidFields() {
        var request = ExternalApiRequests.createStandardApi("PAY",
            List.of(ExternalApiSamples.field(ApiFieldLocation.BODY, "payment.amount", ApiFieldDataType.STRING, true),
                ExternalApiSamples.field(ApiFieldLocation.HEADER, "X-Trace", ApiFieldDataType.OBJECT, false),
                ExternalApiSamples.field(ApiFieldLocation.BODY, "bad path", ApiFieldDataType.STRING, false)),
            List.of(ExternalApiSamples.field(ApiFieldLocation.QUERY, "status", ApiFieldDataType.STRING, true)));

        post(PATH, admin, request).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("requestFields[0].fieldPath").assertFieldError("requestFields[1].fieldLocation")
            .assertFieldError("requestFields[2].fieldPath").assertFieldError("responseFields[0].fieldLocation");

        get(PATH + "/PAY", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 修改会使已有实现失效时拒绝并列出接入方；改为兼容修改后可以保存。 */
    @Test
    void test_updateBreakingImplementation() {
        var pay = externalApi.standardApi("PAY");
        var integration = externalApi.integration("DEMO");
        externalApi.createOrderApi(integration);
        externalApi.implementation(integration, pay, ExternalApiSamples.payFlow());
        var requiredRef =
            List.of(ExternalApiSamples.field(ApiFieldLocation.BODY, "paymentStatus", ApiFieldDataType.STRING, true),
                ExternalApiSamples.field(ApiFieldLocation.BODY, "transactionRef", ApiFieldDataType.STRING, true));

        var rejected = put(PATH + "/PAY", admin,
            ExternalApiRequests.updateStandardApi("支付", ExternalApiSamples.standardRequestFields(), requiredRef, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertTrue(rejected.json().path("detail").asString().contains("DEMO"));
        assertMutation(
            put(PATH + "/PAY", admin, ExternalApiRequests.updateStandardApi("支付",
                ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields(), 1)),
            "STANDARD_API", "PAY", 2);
    }

    /** 被实现引用时不能删除；未被引用时按版本删除，之后不可读取。 */
    @Test
    void test_delete() {
        var pay = externalApi.standardApi("PAY");
        externalApi.standardApi("REFUND");
        var integration = externalApi.integration("DEMO");
        externalApi.createOrderApi(integration);
        externalApi.implementation(integration, pay, ExternalApiSamples.payFlow());

        delete(PATH + "/PAY", admin, ExternalApiRequests.delete(1)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        delete(PATH + "/REFUND", admin, ExternalApiRequests.delete(2)).assertProblem(409,
            CommonErrorCode.CONCURRENT_REQUEST);
        assertMutation(delete(PATH + "/REFUND", admin, ExternalApiRequests.delete(1)), "STANDARD_API", "REFUND", 1);

        get(PATH + "/REFUND", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(PATH + "/PAY", admin).assertOk();
    }

    /** 只有查看权限可读不可写；没有外部 API 权限时读也拒绝。 */
    @Test
    void test_permissions() {
        externalApi.standardApi("PAY");
        Operator reader = reader();
        long otherRole = operationsMocker.customRole("NO_EXTERNAL_API", "OPERATIONS_ROLE_READ");
        Operator other = operationsMocker.operator(OperatorBuilder.administrator().roles(otherRole));

        get(PATH + "/PAY", reader).assertOk();
        post(PATH, reader, ExternalApiRequests.createStandardApi("REFUND")).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(PATH, other).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(PATH + "/REFUND", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    private PagingResponseBody<StandardApiSummary> list(String query) {
        return get(PATH + query, admin).assertOk().as(new TypeReference<PagingResponseBody<StandardApiSummary>>() {});
    }

    private static List<String> paths(List<ApiFieldDetail> fields) {
        return fields.stream().map(ApiFieldDetail::getFieldPath).toList();
    }
}

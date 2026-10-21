package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.referencedata.CurrencySpecManagementApi;
import com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody;
import com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody;
import com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;
import com.paytech.platform.support.test.mocker.local.CurrencySpecMocker;

/**
 * 币种规则维护：新建草稿、修改草稿（启用后冻结）、启停转换、版本冲突、权限复核与每次成功变更一条审计。
 *
 * @see CurrencySpecManagementApi#createCurrencySpec(String, String, CreateCurrencySpecRequestBody)
 * @see CurrencySpecManagementApi#updateCurrencySpec(String, String, String, UpdateCurrencySpecRequestBody)
 * @see CurrencySpecManagementApi#changeCurrencySpecStatus(String, String, String, ChangeCurrencySpecStatusRequestBody)
 */
class CurrencySpecManagementApiTest extends OperationsApiTestBase {

    private static final String PATH = "/currency-admin/v1/currency-specs";

    private static final Instant SEEDED_AT = Instant.parse("2026-10-20T00:00:00.123456Z");

    private static final String REASON = "新增结算币种";

    private static final String CREATE = "OPERATIONS_CURRENCY_SPEC_CREATE";

    private static final String UPDATE = "OPERATIONS_CURRENCY_SPEC_UPDATE";

    private static final String STATUS = "OPERATIONS_CURRENCY_SPEC_STATUS";

    /** 只读权限编码；Platform 不检查读取权限，生产代码没有对应常量。 */
    private static final String READ_PERMISSION = "CURRENCY_SPEC_READ";

    @Autowired
    CurrencySpecMocker currencies;

    @Autowired
    ReferenceDataTestMapper referenceData;

    @Autowired
    AuditTestMapper auditData;

    private Operator admin;

    /** 本类公共：表与 version 列就绪且只有 USD 种子，一名持有全部目录权限的平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        currencies.assertReady();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 新建为 DRAFT、版本 1，查询接口返回同一版本，并写入一条带前后值的审计。 */
    @Test
    void test_createDraft() {
        var mutation = assertMutation(post(PATH, admin, create("EUR", 2)), "EUR", CurrencySpecStatus.DRAFT, 1);

        var event = auditChecker.assertEvent(requestId, CREATE, AuditModels.Result.SUCCEEDED.getCode(), admin.actor(),
            CurrencySpecAuditFacts.TARGET_TYPE, "EUR");
        var attributes = attributes(event);
        assertAll(() -> assertEquals("", attributes.get("before.STATUS")),
            () -> assertEquals(CurrencySpecStatus.DRAFT.getCode(), attributes.get("after.STATUS")),
            () -> assertEquals("2", attributes.get("after.MINOR_UNIT")),
            () -> assertEquals(CurrencyRoundingMode.HALF_UP.getCode(), attributes.get("after.ROUNDING_MODE")),
            () -> assertEquals("1", attributes.get("after.version")),
            () -> assertEquals(REASON, attributes.get("reason")));
        assertRow("EUR", 2, CurrencySpecStatus.DRAFT, 1);
        assertEquals(Long.toString(((Number) referenceData.currencySpec("EUR").get("id")).longValue()),
            mutation.getObjectId());
    }

    /** 币种代码已存在返回 400 业务拒绝且提示带币种，原行不变；同一代码第二次新建同样拒绝。 */
    @Test
    void test_createDuplicate() {
        var seed = post(PATH, admin, create(CurrencySpecMocker.SEED_CURRENCY, 3)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertMutation(post(PATH, admin, create("EUR", 2)), "EUR", CurrencySpecStatus.DRAFT, 1);
        var duplicate = post(PATH, admin, create("EUR", 3)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals("币种规则已存在: " + CurrencySpecMocker.SEED_CURRENCY, detail(seed)),
            () -> assertEquals("币种规则已存在: EUR", detail(duplicate)));

        assertRow(CurrencySpecMocker.SEED_CURRENCY, 2, CurrencySpecStatus.ACTIVE, 1);
        assertRow("EUR", 2, CurrencySpecStatus.DRAFT, 1);
    }

    /** 代码格式、小数位范围、舍入方式与原因不合法时返回字段错误，不写库。 */
    @Test
    void test_invalidParameter() {
        post(PATH, admin, create("eur", 2)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("currency");
        post(PATH, admin, create("EURO", 2)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("currency");
        post(PATH, admin, create("EUR", 9)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("minorUnit");
        post(PATH, admin, create("EUR", -1)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("minorUnit");
        // 负例：不存在的舍入方式，没有对应枚举。
        post(PATH, admin, new CreateCurrencySpecRequestBody("EUR", 2, "HALF_EVEN", REASON))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("roundingMode");
        post(PATH, admin, new CreateCurrencySpecRequestBody("EUR", 2, CurrencyRoundingMode.HALF_UP.getCode(), " "))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("reason");
        put(PATH + "/eur", admin, update(2, 1)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertNull(referenceData.currencySpec("EUR"));
        assertEquals(1, referenceData.currencySpecCount());
    }

    /** DRAFT 可修改小数位，版本递增，审计记录前后小数位。 */
    @Test
    void test_updateDraft() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, SEEDED_AT);

        assertMutation(put(PATH + "/XTS", admin, update(3, 1)), "XTS", CurrencySpecStatus.DRAFT, 2);

        var attributes = attributes(auditChecker.assertEvent(requestId, UPDATE, AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), CurrencySpecAuditFacts.TARGET_TYPE, "XTS"));
        assertAll(() -> assertEquals("2", attributes.get("before.MINOR_UNIT")),
            () -> assertEquals("3", attributes.get("after.MINOR_UNIT")),
            () -> assertEquals("1", attributes.get("before.version")),
            () -> assertEquals("2", attributes.get("after.version")));
        assertRow("XTS", 3, CurrencySpecStatus.DRAFT, 2);
    }

    /** 无变化的修改不递增版本、不写审计。 */
    @Test
    void test_updateUnchanged() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, SEEDED_AT);

        assertMutation(put(PATH + "/XTS", admin, update(2, 1)), "XTS", CurrencySpecStatus.DRAFT, 1);

        awaitAudit();
        auditChecker.assertNoEvent(requestId);
        assertRow("XTS", 2, CurrencySpecStatus.DRAFT, 1);
    }

    /** 启用过的规则（ACTIVE、INACTIVE）冻结小数位与舍入方式，拒绝信息带币种与状态。 */
    @Test
    void test_updateFrozenAfterActivation() {
        currencies.currency("JPY", 0, CurrencySpecStatus.ACTIVE, SEEDED_AT);
        currencies.currency("XTS", 2, CurrencySpecStatus.INACTIVE, SEEDED_AT);

        var active = put(PATH + "/JPY", admin, update(2, 1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var inactive = put(PATH + "/XTS", admin, update(3, 1)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertTrue(detail(active).contains("JPY")),
            () -> assertTrue(detail(active).contains(CurrencySpecStatus.ACTIVE.getCode())),
            () -> assertTrue(detail(inactive).contains(CurrencySpecStatus.INACTIVE.getCode())));
        assertRow("JPY", 0, CurrencySpecStatus.ACTIVE, 1);
        assertRow("XTS", 2, CurrencySpecStatus.INACTIVE, 1);
    }

    /** 修改与启停的版本不符返回 409，数据不变。 */
    @Test
    void test_versionConflict() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, 3, SEEDED_AT);

        put(PATH + "/XTS", admin, update(4, 2)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        patch("/XTS", status(CurrencySpecStatus.ACTIVE, 2)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        assertRow("XTS", 2, CurrencySpecStatus.DRAFT, 3);
    }

    /** 不存在的币种：修改与启停均返回 404。 */
    @Test
    void test_noDataFound() {
        put(PATH + "/EUR", admin, update(2, 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        patch("/EUR", status(CurrencySpecStatus.ACTIVE, 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);

        assertNull(referenceData.currencySpec("EUR"));
    }

    /** 启用、停用、再启用依次成功，版本逐次递增，每次各一条审计。 */
    @Test
    void test_statusLifecycle() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, SEEDED_AT);

        assertStatusChanged(CurrencySpecStatus.DRAFT, CurrencySpecStatus.ACTIVE, 1);
        assertStatusChanged(CurrencySpecStatus.ACTIVE, CurrencySpecStatus.INACTIVE, 2);
        assertStatusChanged(CurrencySpecStatus.INACTIVE, CurrencySpecStatus.ACTIVE, 3);

        assertRow("XTS", 2, CurrencySpecStatus.ACTIVE, 4);
    }

    /** DRAFT 不能直接停用，同状态转换拒绝，DRAFT 不是可请求的目标状态。 */
    @Test
    void test_illegalTransitions() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, SEEDED_AT);
        currencies.currency("JPY", 0, CurrencySpecStatus.ACTIVE, SEEDED_AT);
        currencies.currency("XAU", 2, CurrencySpecStatus.INACTIVE, SEEDED_AT);

        var draft = patch("/XTS", status(CurrencySpecStatus.INACTIVE, 1)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch("/JPY", status(CurrencySpecStatus.ACTIVE, 1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch("/XAU", status(CurrencySpecStatus.INACTIVE, 1)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch("/XTS", status(CurrencySpecStatus.DRAFT, 1)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("status");

        assertTrue(detail(draft).contains("XTS"));
        assertRow("XTS", 2, CurrencySpecStatus.DRAFT, 1);
        assertRow("JPY", 0, CurrencySpecStatus.ACTIVE, 1);
        assertRow("XAU", 2, CurrencySpecStatus.INACTIVE, 1);
    }

    /** 只有读取权限的人员不能新建、修改或启停，数据不变。 */
    @Test
    void test_permissionDenied() {
        currencies.currency("XTS", 2, CurrencySpecStatus.DRAFT, SEEDED_AT);
        long role = operationsMocker.customRole("CURRENCY_SPEC_READER", READ_PERMISSION);
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        post(PATH, reader, create("EUR", 2)).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        put(PATH + "/XTS", reader, update(3, 1)).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        call(HttpMethod.PATCH, PATH + "/XTS/status", headers(reader), status(CurrencySpecStatus.ACTIVE, 1))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);

        assertNull(referenceData.currencySpec("EUR"));
        assertRow("XTS", 2, CurrencySpecStatus.DRAFT, 1);
    }

    /** 以管理员身份切换 XTS 状态，检查回执与本次审计。 */
    private void assertStatusChanged(CurrencySpecStatus from, CurrencySpecStatus to, int expectedVersion) {
        assertMutation(patch("/XTS", status(to, expectedVersion)), "XTS", to, expectedVersion + 1);
        var attributes = attributes(auditChecker.assertEvent(requestId, STATUS, AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), CurrencySpecAuditFacts.TARGET_TYPE, "XTS"));
        assertAll(() -> assertEquals(from.getCode(), attributes.get("before.STATUS")),
            () -> assertEquals(to.getCode(), attributes.get("after.STATUS")),
            () -> assertEquals(Integer.toString(expectedVersion + 1), attributes.get("after.version")));
    }

    /** 写成功并返回回执，检查对象类型、币种、状态与版本。 */
    private static ConfigurationMutationResponseBody assertMutation(PlatformResponseChecker checker, String currency,
        CurrencySpecStatus status, int version) {
        var mutation = checker.assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(CurrencySpecAuditFacts.TARGET_TYPE, mutation.getObjectType()),
            () -> assertEquals(currency, mutation.getObjectCode()),
            () -> assertEquals(status.getCode(), mutation.getStatus()),
            () -> assertEquals(version, mutation.getVersion()));
        return mutation;
    }

    /** 从数据库读取规则行，核对小数位、舍入方式、状态与版本。 */
    private void assertRow(String currency, int minorUnit, CurrencySpecStatus status, int version) {
        Map<String, Object> row = referenceData.currencySpec(currency);
        assertAll(() -> assertEquals(minorUnit, ((Number) row.get("minor_unit")).intValue(), currency + " 小数位"),
            () -> assertEquals(CurrencyRoundingMode.HALF_UP.getCode(), row.get("rounding_mode"), currency + " 舍入方式"),
            () -> assertEquals(status.getCode(), row.get("status"), currency + " 状态"),
            () -> assertEquals(version, ((Number) row.get("version")).intValue(), currency + " 版本"));
    }

    /** 审计属性键值。 */
    private Map<String, String> attributes(Map<String, Object> event) {
        var result = new HashMap<String, String>();
        for (var attribute : auditData.attributes(((Number) event.get("audit_event_id")).longValue())) {
            result.put((String) attribute.get("attr_key"), (String) attribute.get("value_text"));
        }
        return result;
    }

    /** 等待异步审计写完，再判断没有事件。 */
    private void awaitAudit() {
        await().atMost(Duration.ofSeconds(5)).until(() -> auditDispatcher.pendingCount() == 0);
    }

    /** Problem 的人类提示。 */
    private static String detail(PlatformResponseChecker checker) {
        return checker.json().path("detail").asString();
    }

    /** 以管理员身份 PATCH 状态子资源。 */
    private PlatformResponseChecker patch(String currencyPath, ChangeCurrencySpecStatusRequestBody body) {
        return call(HttpMethod.PATCH, PATH + currencyPath + "/status", headers(admin), body);
    }

    /** 新建请求，舍入方式 HALF_UP。 */
    private static CreateCurrencySpecRequestBody create(String currency, int minorUnit) {
        return new CreateCurrencySpecRequestBody(currency, minorUnit, CurrencyRoundingMode.HALF_UP.getCode(), REASON);
    }

    /** 修改请求，舍入方式 HALF_UP。 */
    private static UpdateCurrencySpecRequestBody update(int minorUnit, int expectedVersion) {
        return new UpdateCurrencySpecRequestBody(minorUnit, CurrencyRoundingMode.HALF_UP.getCode(), expectedVersion,
            REASON);
    }

    /** 状态切换请求。 */
    private static ChangeCurrencySpecStatusRequestBody status(CurrencySpecStatus status, int expectedVersion) {
        return new ChangeCurrencySpecStatusRequestBody(status.getCode(), expectedVersion, REASON);
    }
}

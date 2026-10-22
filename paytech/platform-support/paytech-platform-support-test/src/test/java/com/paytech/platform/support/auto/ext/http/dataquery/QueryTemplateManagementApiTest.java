package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;
import com.paytech.platform.support.api.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.platform.support.api.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryRequestBuilder;

/**
 * 模板维护：保存即生效并生成新版本、版本列表与对比、恢复；保存校验（B2）的每项拒绝带行号；警告不阻止保存；版本冲突 409。
 *
 * @see QueryTemplateManagementApi#createQueryTemplate(String, String, CreateQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#updateQueryTemplate(String, String, String, UpdateQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#restoreQueryTemplate(String, String, String, RestoreQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#getQueryTemplate(String, String, String)
 * @see QueryTemplateManagementApi#listQueryTemplateVersions(String, String, String, Integer, Integer)
 * @see QueryTemplateManagementApi#getQueryTemplateVersion(String, String, String, Integer)
 * @see QueryTemplateManagementApi#validateQueryTemplate(String, String, ValidateQueryTemplateRequestBody)
 */
class QueryTemplateManagementApiTest extends DataQueryApiTestBase {

    private static final String TEMPLATES = ADMIN + "/templates";

    private Operator admin;

    private long dataSourceId;

    /** 本类公共：平台管理员与一个 ACTIVE 只读数据源（默认库为被查库）。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_TEMPLATES");
    }

    /** 新建 v1 → 修改 v2 立即生效 → 版本列表与 v1 内容 → 以 v1 恢复为 v3 并记录来源。 */
    @Test
    void test_saveRestoreAndCompare() {
        var created = post(TEMPLATES, admin, DataQueryRequestBuilder.createTemplate("REFUNDS", dataSourceId,
            DataQueryRequestBuilder.REFUND_BODY, DataQueryRequestBuilder.REFUND_PARAMETERS,
            DataQueryRequestBuilder.none(100))).assertOk().as(SaveQueryTemplateResponseBody.class);
        long id = Long.parseLong(created.getTemplateId());
        assertAll(() -> assertEquals(1, created.getCurrentVersionNo()), () -> assertEquals(1, created.getVersion()),
            () -> assertEquals(QueryTemplateStatus.ACTIVE.getCode(), created.getStatus()));

        String v2Body = "SELECT refund_no FROM t_refund_fixture ORDER BY refund_id";
        var updated = put(TEMPLATES + "/" + id, admin, DataQueryRequestBuilder.updateTemplate(dataSourceId, v2Body,
            "[]", DataQueryRequestBuilder.none(100), "只返回退款号", 1)).assertOk().as(SaveQueryTemplateResponseBody.class);
        assertAll(() -> assertEquals(2, updated.getCurrentVersionNo()), () -> assertEquals(2, updated.getVersion()));
        var detail = get(TEMPLATES + "/" + id, admin).assertOk().as(QueryTemplateDetailResponseBody.class);
        assertEquals(v2Body, detail.getCurrentVersion().getBody());

        var versions = get(TEMPLATES + "/" + id + "/versions", admin).assertOk().json();
        assertAll(() -> assertEquals(2, versions.path("total").asLong()),
            () -> assertEquals(2, versions.path("items").get(0).path("versionNo").asInt()),
            () -> assertEquals("只返回退款号", versions.path("items").get(0).path("changeNote").asString()));
        var v1 = get(TEMPLATES + "/" + id + "/versions/1", admin).assertOk().as(QueryTemplateVersionResponseBody.class);
        assertEquals(DataQueryRequestBuilder.REFUND_BODY, v1.getBody());

        RestoreQueryTemplateRequestBody restore = new RestoreQueryTemplateRequestBody();
        restore.setVersionNo(1);
        restore.setExpectedVersion(2);
        var restored = post(TEMPLATES + "/" + id + "/restoration", admin, restore).assertOk()
            .as(SaveQueryTemplateResponseBody.class);
        Map<String, Object> v3 = dataQueryRows.version(id, 3);
        assertAll(() -> assertEquals(3, restored.getCurrentVersionNo()),
            () -> assertEquals(DataQueryRequestBuilder.REFUND_BODY, v3.get("body")),
            () -> assertEquals(1, ((Number) v3.get("restoredFromVersionNo")).intValue()),
            () -> assertEquals("恢复自版本 1", v3.get("changeNote")),
            () -> assertEquals(3L, dataQueryRows.versionCount(id)));
    }

    /** 每一项 B2 拒绝都返回 422，正文问题的 field 为 body 且 message 以“第 N 行第 M 列：”开头；不写库。 */
    @ParameterizedTest
    @MethodSource("rejectedBodies")
    void test_saveRejectionCarriesLine(String code, String body, String parameters) {
        var rejected = post(TEMPLATES, admin, DataQueryRequestBuilder.createTemplate(code, dataSourceId, body,
            parameters, DataQueryRequestBuilder.none(100))).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("body");
        String message = rejected.json().path("errors").get(0).path("message").asString();
        assertAll(() -> assertTrue(message.matches("第 \\d+ 行第 \\d+ 列：.+"), message),
            () -> assertNull(dataQueryRows.templateId(code)));
    }

    /** B2 的拒绝样例：标签不成对、未知标签、表达式非法、未声明参数、多条语句、非 SELECT、注释符号、系统库。 */
    static Stream<Arguments> rejectedBodies() {
        return Stream.of(
            Arguments.of("UNCLOSED_IF", "SELECT 1\nFROM t_refund_fixture\n{{#if clientId}} WHERE client_id = 1",
                DataQueryRequestBuilder.REFUND_PARAMETERS),
            Arguments.of("UNKNOWN_TAG", "SELECT 1\n{{#loop x}}{{/loop}}", "[]"),
            Arguments.of("BAD_EXPRESSION", "SELECT 1\nFROM t_refund_fixture\n{{#if clientId ==}}x{{/if}}",
                DataQueryRequestBuilder.REFUND_PARAMETERS),
            Arguments.of("UNDECLARED", "SELECT refund_id\nFROM t_refund_fixture\nWHERE client_id = #{clientNo}", "[]"),
            Arguments.of("MULTI_STATEMENT", "SELECT 1;\nDELETE FROM t_refund_fixture", "[]"),
            Arguments.of("NOT_SELECT", "UPDATE t_refund_fixture\nSET refund_status = 'FAILED'", "[]"),
            Arguments.of("SQL_COMMENT", "SELECT 1\n/*! DELETE */ FROM t_refund_fixture", "[]"),
            Arguments.of("SYSTEM_SCHEMA", "SELECT user\nFROM mysql.user", "[]"));
    }

    /** 声明了但未使用的参数只给警告，保存成功并返回警告。 */
    @Test
    void test_warningDoesNotBlockSave() {
        var saved = post(TEMPLATES, admin, DataQueryRequestBuilder.createTemplate("WITH_WARNING", dataSourceId,
            "SELECT refund_id FROM t_refund_fixture ORDER BY refund_id", DataQueryRequestBuilder.REFUND_PARAMETERS,
            DataQueryRequestBuilder.none(100))).assertOk().as(SaveQueryTemplateResponseBody.class);
        assertAll(() -> assertEquals(1, saved.getCurrentVersionNo()),
            () -> assertFalse(saved.getWarnings().isEmpty()),
            () -> assertTrue(saved.getWarnings().stream()
                .allMatch(problem -> TemplateProblemLevel.WARNING.getCode().equals(problem.getLevel()))));
    }

    /** 编辑器校验不保存：返回带行列的问题列表，valid=false；约束超过硬上限给出字段问题。 */
    @Test
    void test_validateReturnsProblems() {
        ValidateQueryTemplateRequestBody request = new ValidateQueryTemplateRequestBody();
        request.setDataSourceId(Long.toString(dataSourceId));
        request.setBody("SELECT 1\nFROM t_refund_fixture\nWHERE client_id = #{clientNo}");
        request.setParameters(DataQueryRequestBuilder.json("[]"));
        var constraints = DataQueryRequestBuilder.none(20000);
        request.setConstraints(constraints);

        var result = post(ADMIN + "/template-validations", admin, request).assertOk()
            .as(QueryTemplateValidationResponseBody.class);
        assertAll(() -> assertFalse(result.isValid()),
            () -> assertTrue(result.getProblems().stream().anyMatch(problem -> Integer.valueOf(3)
                .equals(problem.getLine()) && problem.getColumn() != null)),
            () -> assertTrue(result.getProblems().stream()
                .anyMatch(problem -> "constraints.maxRows".equals(problem.getField()))));
    }

    /** 数据源未启用时不能保存，问题落在 dataSourceId 字段。 */
    @Test
    void test_inactiveDataSourceRejected() {
        long inactive = dataQuery.source("FIXTURE_TEMPLATES_OFF", fixture.readerUsername(), fixture.readerPassword(),
            "mor_data_query_fixture", DataSourceStatus.INACTIVE);

        post(TEMPLATES, admin, DataQueryRequestBuilder.createTemplate("ON_INACTIVE", inactive,
            "SELECT 1", "[]", DataQueryRequestBuilder.none(10))).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("dataSourceId");
        assertNull(dataQueryRows.templateId("ON_INACTIVE"));
    }

    /** 原版本不符返回 409，不生成新版本。 */
    @Test
    void test_versionConflict() {
        var created = post(TEMPLATES, admin, DataQueryRequestBuilder.createTemplate("CONFLICT", dataSourceId,
            "SELECT 1", "[]", DataQueryRequestBuilder.none(10))).assertOk().as(SaveQueryTemplateResponseBody.class);
        long id = Long.parseLong(created.getTemplateId());

        put(TEMPLATES + "/" + id, admin, DataQueryRequestBuilder.updateTemplate(dataSourceId, "SELECT 2", "[]",
            DataQueryRequestBuilder.none(10), null, 9)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals(1L, dataQueryRows.versionCount(id));
    }
}

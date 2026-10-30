package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.QueryKeyTypeManagementApi;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryTemplateStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointColumn;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointConfig;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateJointKeyParameter;
import com.paytech.platform.support.api.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateProblem;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.platform.support.api.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.core.enums.JointQueryExclusionReason;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryRequestBuilder;
import com.paytech.platform.support.test.dataquery.JointQueryMocker;

/**
 * 模板联合查询配置：保存与回显、参与提示（INFO / WARNING 与原因码）、B3 校验错误定位到 joint.* 字段、维度键标为链路的提醒、
 * 版本恢复连同配置、编辑器校验，以及引用的键类型在模板保存 / 启用与键类型停用之间互斥（AC-02）。
 *
 * @see QueryTemplateManagementApi#createQueryTemplate(String, String, CreateQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#updateQueryTemplate(String, String, String, UpdateQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#restoreQueryTemplate(String, String, String, RestoreQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#getQueryTemplate(String, String, String)
 * @see QueryTemplateManagementApi#getQueryTemplateVersion(String, String, String, Integer)
 * @see QueryTemplateManagementApi#validateQueryTemplate(String, String, ValidateQueryTemplateRequestBody)
 * @see QueryTemplateManagementApi#changeQueryTemplateStatus(String, String, String, ChangeQueryTemplateStatusRequestBody)
 * @see QueryKeyTypeManagementApi#changeQueryKeyTypeStatus(String, String, String, ChangeQueryKeyTypeStatusRequestBody)
 */
class QueryTemplateJointConfigApiTest extends DataQueryApiTestBase {

    private static final String TEMPLATES = ADMIN + "/templates";

    private static final String ORDER_BODY = "SELECT order_id, client_id, buyer_email FROM t_jq_order"
        + " {{#if orderIds}}WHERE order_id IN (#{orderIds}){{/if}} ORDER BY order_id";

    private static final String ORDER_PARAMETERS = JointQueryMocker.list("orderIds", "INTEGER");

    private Operator admin;

    private long dataSourceId;

    private long orderKeyTypeId;

    /** 本类公共：平台管理员、ACTIVE 只读数据源与链路键类型。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_JOINT_TEMPLATES");
        jointQuery.chainKeyTypes();
        jointQuery.keyType("DISABLED_KEY", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 15,
            QueryKeyTypeStatus.INACTIVE);
        orderKeyTypeId = dataQueryRows.keyTypeId("ORDER_ID");
    }

    /** 参与的配置保存为 joint_json 并提示 INFO；详情与版本回显配置与参与情况；保存为不参与后再恢复版本 1，配置随版本恢复。 */
    @Test
    void test_saveEchoAndRestoreJoint() {
        var saved = post(TEMPLATES, admin, create("JQ_ORDER", ORDER_BODY, ORDER_PARAMETERS, orderJoint())).assertOk()
            .as(SaveQueryTemplateResponseBody.class);
        long id = Long.parseLong(saved.getTemplateId());
        var detail = get(TEMPLATES + "/" + id, admin).assertOk().as(QueryTemplateDetailResponseBody.class);

        UpdateQueryTemplateRequestBody plain = DataQueryRequestBuilder.updateTemplate(dataSourceId, ORDER_BODY,
            ORDER_PARAMETERS, DataQueryRequestBuilder.none(100), "去掉联合查询", 1);
        var cleared = put(TEMPLATES + "/" + id, admin, plain).assertOk().as(SaveQueryTemplateResponseBody.class);
        var v2 = get(TEMPLATES + "/" + id + "/versions/2", admin).assertOk().as(QueryTemplateVersionResponseBody.class);
        RestoreQueryTemplateRequestBody restore = new RestoreQueryTemplateRequestBody();
        restore.setVersionNo(1);
        restore.setExpectedVersion(2);
        post(TEMPLATES + "/" + id + "/restoration", admin, restore).assertOk();
        var v3 = get(TEMPLATES + "/" + id + "/versions/3", admin).assertOk().as(QueryTemplateVersionResponseBody.class);

        assertAll(() -> assertEquals(List.of(problem(TemplateProblemLevel.INFO, "joint", "参与联合查询，触发键 ORDER_ID")),
                problems(saved.getWarnings())),
            () -> assertTrue(dataQueryRows.jointJson(id, 1).contains("\"subjectKeyType\": \"ORDER_ID\"")
                || dataQueryRows.jointJson(id, 1).contains("\"subjectKeyType\":\"ORDER_ID\"")),
            () -> assertEquals("orderIds", detail.getJoint().getKeyParameter().getName()),
            () -> assertEquals(3, detail.getJoint().getColumns().size()),
            () -> assertEquals(QueryKeyRole.DIMENSION.getCode(), detail.getJoint().getColumns().get(1).getKeyRole()),
            () -> assertTrue(detail.getJointQuery().isParticipating()), () -> assertNull(detail.getJointQuery()
                .getReasonCode()),
            () -> assertEquals("ORDER_ID", detail.getCurrentVersion().getJoint().getSubjectKeyType()),
            () -> assertTrue(cleared.getWarnings().isEmpty()), () -> assertNull(v2.getJoint()),
            () -> assertNull(v2.getJointQuery()), () -> assertNull(dataQueryRows.jointJson(id, 2)),
            () -> assertEquals("ORDER_ID", v3.getJoint().getSubjectKeyType()),
            () -> assertTrue(v3.getJointQuery().isParticipating()));
    }

    /** 违反 B3 时 422 定位到 joint.* 字段且不写库。 */
    @ParameterizedTest
    @MethodSource("invalidJoints")
    void test_saveRejectsInvalidJoint(String code, String parameters, Consumer<QueryTemplateJointConfig> change,
        String field) {
        QueryTemplateJointConfig joint = orderJoint();
        change.accept(joint);
        CreateQueryTemplateRequestBody request = create(code, ORDER_BODY.replace("orderIds", firstName(parameters)), parameters, joint);
        request.setColumns(DataQueryRequestBuilder.columns(DataQueryRequestBuilder.column("order_id", null,
            ColumnMaskType.KEEP_EDGES.getCode(), 1, 1)));

        post(TEMPLATES, admin, request).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError(field);
        assertNull(dataQueryRows.templateId(code));
    }

    /** 每项拒绝：未知行主体、键参数未声明、参数类型不匹配、停用键类型、列重复、链路列屏蔽、未知列键类型、非法角色。 */
    static Stream<Arguments> invalidJoints() {
        Consumer<QueryTemplateJointConfig> unmasked = joint -> joint.getColumns().removeFirst();
        return Stream.of(
            Arguments.of("JQ_BAD_SUBJECT", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.setSubjectKeyType(
                "NO_SUCH_KEY")), "joint.subjectKeyType"),
            Arguments.of("JQ_BAD_PARAMETER", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getKeyParameter()
                .setName("missing")), "joint.keyParameter.name"),
            Arguments.of("JQ_DECIMAL_PARAMETER", JointQueryMocker.list("orderIds", "DECIMAL"), unmasked,
                "joint.keyParameter.keyType"),
            Arguments.of("JQ_INTEGER_ON_STRING", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getKeyParameter()
                .setKeyType("CLIENT_ORDER_ID")), "joint.keyParameter.keyType"),
            Arguments.of("JQ_INACTIVE_KEY", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getKeyParameter()
                .setKeyType("DISABLED_KEY")), "joint.keyParameter.keyType"),
            Arguments.of("JQ_DUPLICATE_COLUMN", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getColumns()
                .add(column("CLIENT_ID", "CLIENT_ID", QueryKeyRole.DIMENSION))), "joint.columns[2].name"),
            Arguments.of("JQ_MASKED_LINK", ORDER_PARAMETERS, (Consumer<QueryTemplateJointConfig>) joint -> { },
                "joint.columns[0].keyRole"),
            Arguments.of("JQ_UNKNOWN_COLUMN_KEY", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getColumns().getFirst()
                .setKeyType("NO_SUCH_KEY")), "joint.columns[0].keyType"),
            Arguments.of("JQ_BAD_ROLE", ORDER_PARAMETERS, unmasked.andThen(joint -> joint.getColumns().getFirst()
                .setKeyRole("PRIMARY")), "joint.columns[0].keyRole"));
    }

    /** 不参与的配置可以保存并给出 WARNING 与原因码；维度键标为链路给出提醒；INTEGER 键可绑 STRING 参数。 */
    @Test
    void test_participationWarnings() {
        var twoRequired = post(TEMPLATES, admin, create("JQ_TWO_REQUIRED", ORDER_BODY.replace("{{/if}}", " AND client_id = #{clientId}{{/if}}"),
            "[{\"name\":\"orderIds\",\"type\":\"LIST\",\"elementType\":\"INTEGER\",\"required\":true},"
                + "{\"name\":\"clientId\",\"type\":\"INTEGER\",\"required\":true}]", orderJoint())).assertOk()
            .as(SaveQueryTemplateResponseBody.class);
        var optionalKey = post(TEMPLATES, admin, create("JQ_OPTIONAL_KEY", "SELECT order_id, client_id, buyer_email"
            + " FROM t_jq_order {{#if orderIds}}WHERE order_id IN (#{orderIds}){{/if}}",
            "[{\"name\":\"orderIds\",\"type\":\"LIST\",\"elementType\":\"INTEGER\"}]", orderJoint())).assertOk()
            .as(SaveQueryTemplateResponseBody.class);
        QueryTemplateJointConfig dimensionAsLink = orderJoint();
        dimensionAsLink.getColumns().get(1).setKeyRole(QueryKeyRole.LINK.getCode());
        var linkWarning = post(TEMPLATES, admin, create("JQ_STRING_PARAMETER", ORDER_BODY,
            JointQueryMocker.list("orderIds", "STRING"), dimensionAsLink)).assertOk()
            .as(SaveQueryTemplateResponseBody.class);
        var detail = get(TEMPLATES + "/" + twoRequired.getTemplateId(), admin).assertOk()
            .as(QueryTemplateDetailResponseBody.class);

        assertAll(() -> assertEquals(List.of(problem(TemplateProblemLevel.WARNING, "joint",
                "不参与联合查询：必填键参数不唯一，参数 clientId 必填且没有默认值")), problems(twoRequired.getWarnings())),
            () -> assertFalse(detail.getJointQuery().isParticipating()),
            () -> assertEquals(JointQueryExclusionReason.OTHER_REQUIRED_PARAMETER.getCode(),
                detail.getJointQuery().getReasonCode()),
            () -> assertTrue(problems(optionalKey.getWarnings()).contains(problem(TemplateProblemLevel.WARNING, "joint",
                "不参与联合查询：键参数 orderIds 不是必填"))),
            () -> assertEquals(List.of(TemplateProblemLevel.WARNING.getCode() + "|joint.columns[1].keyRole",
                TemplateProblemLevel.INFO.getCode() + "|joint"), linkWarning.getWarnings().stream()
                .map(problem -> problem.getLevel() + "|" + problem.getField()).toList()));
    }

    /** 编辑器校验返回联合查询配置的错误；配置有效时附参与提示。 */
    @Test
    void test_validateReturnsJointProblems() {
        QueryTemplateJointConfig broken = orderJoint();
        broken.getKeyParameter().setName("missing");

        var invalid = post(ADMIN + "/template-validations", admin, validation(broken)).assertOk()
            .as(QueryTemplateValidationResponseBody.class);
        var valid = post(ADMIN + "/template-validations", admin, validation(orderJoint())).assertOk()
            .as(QueryTemplateValidationResponseBody.class);

        assertAll(() -> assertFalse(invalid.isValid()),
            () -> assertTrue(invalid.getProblems().stream().anyMatch(problem -> "joint.keyParameter.name"
                .equals(problem.getField()) && TemplateProblemLevel.ERROR.getCode().equals(problem.getLevel()))),
            () -> assertTrue(invalid.getProblems().stream().noneMatch(problem -> "joint".equals(problem.getField()))),
            () -> assertTrue(valid.isValid()),
            () -> assertTrue(problems(valid.getProblems()).contains(problem(TemplateProblemLevel.INFO, "joint",
                "参与联合查询，触发键 ORDER_ID"))));
    }

    /** 引用的键类型被 ACTIVE 模板使用时不能停用；模板停用后可以停用键类型，此后模板不能再启用，保存引用停用键类型的配置被拒。 */
    @Test
    void test_keyTypeLockedWhileReferenced() {
        long id = Long.parseLong(post(TEMPLATES, admin, create("JQ_ORDER", ORDER_BODY, ORDER_PARAMETERS, orderJoint()))
            .assertOk().as(SaveQueryTemplateResponseBody.class).getTemplateId());

        post(ADMIN + "/key-types/" + orderKeyTypeId + "/status", admin, keyTypeStatus(QueryKeyTypeStatus.INACTIVE, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(TEMPLATES + "/" + id + "/status", admin, templateStatus(QueryTemplateStatus.INACTIVE, 1)).assertOk();
        post(ADMIN + "/key-types/" + orderKeyTypeId + "/status", admin, keyTypeStatus(QueryKeyTypeStatus.INACTIVE, 1))
            .assertOk();
        String message = post(TEMPLATES + "/" + id + "/status", admin, templateStatus(QueryTemplateStatus.ACTIVE, 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
        UpdateQueryTemplateRequestBody update = DataQueryRequestBuilder.updateTemplate(dataSourceId, ORDER_BODY,
            ORDER_PARAMETERS, DataQueryRequestBuilder.none(100), null, 2);
        update.setJoint(orderJoint());
        put(TEMPLATES + "/" + id, admin, update).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("joint.subjectKeyType");

        assertAll(() -> assertTrue(message.contains("ORDER_ID"), message),
            () -> assertEquals(QueryTemplateStatus.INACTIVE.getCode(), dataQueryRows.template(id).get("status")),
            () -> assertEquals(1L, dataQueryRows.versionCount(id)));
    }

    /** 新建请求：只读数据源、不分页 100 行，buyer_email 全部屏蔽。 */
    CreateQueryTemplateRequestBody create(String code, String body, String parameters, QueryTemplateJointConfig joint) {
        CreateQueryTemplateRequestBody request = DataQueryRequestBuilder.createTemplate(code, dataSourceId, body,
            parameters, DataQueryRequestBuilder.none(100));
        request.setColumns(DataQueryRequestBuilder.columns(DataQueryRequestBuilder.column("buyer_email", null,
            ColumnMaskType.FULL.getCode(), 0, 0)));
        request.setJoint(joint);
        return request;
    }

    /** 编辑器校验请求。 */
    ValidateQueryTemplateRequestBody validation(QueryTemplateJointConfig joint) {
        ValidateQueryTemplateRequestBody request = new ValidateQueryTemplateRequestBody();
        request.setDataSourceId(Long.toString(dataSourceId));
        request.setBody(ORDER_BODY);
        request.setParameters(DataQueryRequestBuilder.json(ORDER_PARAMETERS));
        request.setConstraints(DataQueryRequestBuilder.none(100));
        request.setJoint(joint);
        return request;
    }

    /** 订单模板的联合查询配置：行主体 ORDER_ID，按 orderIds 查，订单链路、Client 维度、邮箱只展示。 */
    static QueryTemplateJointConfig orderJoint() {
        QueryTemplateJointConfig joint = new QueryTemplateJointConfig();
        joint.setSubjectKeyType("ORDER_ID");
        QueryTemplateJointKeyParameter key = new QueryTemplateJointKeyParameter();
        key.setName("orderIds");
        key.setKeyType("ORDER_ID");
        joint.setKeyParameter(key);
        joint.setColumns(new ArrayList<>(List.of(column("order_id", "ORDER_ID", QueryKeyRole.LINK),
            column("client_id", "CLIENT_ID", QueryKeyRole.DIMENSION),
            column("buyer_email", "CLIENT_ORDER_ID", QueryKeyRole.DIMENSION))));
        return joint;
    }

    /** 列标注。 */
    static QueryTemplateJointColumn column(String name, String keyType, QueryKeyRole role) {
        QueryTemplateJointColumn column = new QueryTemplateJointColumn();
        column.setName(name);
        column.setKeyType(keyType);
        column.setKeyRole(role.getCode());
        return column;
    }

    /** 参数声明中的第一个参数名。 */
    static String firstName(String parameters) {
        return parameters.replaceFirst("^\\[\\{\"name\":\"([^\"]+)\".*$", "$1");
    }

    /** 模板启停请求。 */
    static ChangeQueryTemplateStatusRequestBody templateStatus(QueryTemplateStatus status, int expectedVersion) {
        ChangeQueryTemplateStatusRequestBody body = new ChangeQueryTemplateStatusRequestBody();
        body.setStatus(status.getCode());
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 键类型启停请求。 */
    static ChangeQueryKeyTypeStatusRequestBody keyTypeStatus(QueryKeyTypeStatus status, int expectedVersion) {
        ChangeQueryKeyTypeStatusRequestBody body = new ChangeQueryKeyTypeStatusRequestBody();
        body.setStatus(status.getCode());
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 问题的可比较形式。 */
    static List<String> problems(List<QueryTemplateProblem> problems) {
        return problems.stream().map(item -> item.getLevel() + "|" + item.getField() + "|" + item.getMessage()).toList();
    }

    /** 期望问题的可比较形式。 */
    static String problem(TemplateProblemLevel level, String field, String message) {
        return level.getCode() + "|" + field + "|" + message;
    }
}

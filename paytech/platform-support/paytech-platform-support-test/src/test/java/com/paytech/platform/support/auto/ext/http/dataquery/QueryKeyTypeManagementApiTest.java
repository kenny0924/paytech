package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.QueryKeyTypeManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.request.QueryKeyTypeOrderItem;
import com.paytech.platform.support.api.dataquery.request.ReorderQueryKeyTypesRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.platform.support.api.dataquery.response.ReorderQueryKeyTypesResponseBody;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.JointQueryMocker;

import tools.jackson.core.type.TypeReference;

/**
 * 键类型维护：新建与审计、列表顺序与引用数、编码重复与正则子集、修改版本冲突与被引用时禁止改值类型、
 * 被 ACTIVE 模板引用时禁止停用（列前 5 个模板）、同状态不加版本、整体重排（AC-01）。
 *
 * @see QueryKeyTypeManagementApi#listQueryKeyTypes(String, String, String, String, Integer, Integer)
 * @see QueryKeyTypeManagementApi#createQueryKeyType(String, String, CreateQueryKeyTypeRequestBody)
 * @see QueryKeyTypeManagementApi#updateQueryKeyType(String, String, String, UpdateQueryKeyTypeRequestBody)
 * @see QueryKeyTypeManagementApi#changeQueryKeyTypeStatus(String, String, String, ChangeQueryKeyTypeStatusRequestBody)
 * @see QueryKeyTypeManagementApi#reorderQueryKeyTypes(String, String, ReorderQueryKeyTypesRequestBody)
 */
class QueryKeyTypeManagementApiTest extends DataQueryApiTestBase {

    private static final String KEY_TYPES = ADMIN + "/key-types";

    private Operator admin;

    private long dataSourceId;

    /** 本类公共：平台管理员与一个 ACTIVE 只读数据源（供引用键类型的模板使用）。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_KEY_TYPES");
    }

    /** 新建为 ACTIVE 版本 1 并登记审计；列表按 sortNo、编码升序，引用数只计 ACTIVE 模板当前版本，可按状态与关键字筛选。 */
    @Test
    void test_createListAndAudit() {
        var created = post(KEY_TYPES, admin, create("PAYOUT_ID", "出款", 130)).assertOk()
            .as(ConfigurationMutationResponseBody.class);
        long id = Long.parseLong(created.getObjectId());
        auditChecker.assertEvent(requestId, "OPERATIONS_QUERY_KEY_TYPE_CREATE", AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), "QUERY_KEY_TYPE", created.getObjectId());
        long orderId = jointQuery.keyType("ORDER_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true,
            JointQueryMocker.ID_PATTERN, 20);
        jointQuery.keyType("OLD_KEY", "收单", QueryKeyValueType.STRING, QueryKeyRole.LINK, false, null, 20,
            QueryKeyTypeStatus.INACTIVE);
        orderTemplate("JQ_ACTIVE_REF", QueryTemplateStatus.ACTIVE);
        orderTemplate("JQ_INACTIVE_REF", QueryTemplateStatus.INACTIVE);

        List<QueryKeyTypeResponseBody> items = list("").items();
        Map<String, Object> row = dataQueryRows.keyType(id);
        assertAll(() -> assertEquals("QUERY_KEY_TYPE", created.getObjectType()),
            () -> assertEquals(QueryKeyTypeStatus.ACTIVE.getCode(), created.getStatus()),
            () -> assertEquals(1, created.getVersion()), () -> assertEquals("出款", row.get("groupName")),
            () -> assertEquals("[0-9]{1,19}", row.get("valuePattern")),
            () -> assertEquals(List.of("OLD_KEY", "ORDER_ID", "PAYOUT_ID"), items.stream()
                .map(QueryKeyTypeResponseBody::getKeyTypeCode).toList()),
            () -> assertEquals(1L, items.get(1).getReferencedTemplateCount()),
            () -> assertEquals(Long.toString(orderId), items.get(1).getKeyTypeId()),
            () -> assertEquals(0L, items.get(2).getReferencedTemplateCount()),
            () -> assertEquals(List.of("ORDER_ID", "PAYOUT_ID"), list("?status=ACTIVE").items().stream()
                .map(QueryKeyTypeResponseBody::getKeyTypeCode).toList()),
            () -> assertEquals(List.of("PAYOUT_ID"), list("?keyword=PAYOUT").items().stream()
                .map(QueryKeyTypeResponseBody::getKeyTypeCode).toList()));
    }

    /** 编码重复 400；正则不在 Java / JavaScript 共同子集内返回 422（field valuePattern）；缺少维护权限 403。 */
    @ParameterizedTest
    @MethodSource("invalidPatterns")
    void test_createRejectedPattern(String pattern) {
        CreateQueryKeyTypeRequestBody body = create("BAD_PATTERN", "收单", 10);
        body.setValuePattern(pattern);

        post(KEY_TYPES, admin, body).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("valuePattern");
        assertEquals(null, dataQueryRows.keyTypeId("BAD_PATTERN"));
    }

    /** 反向引用、命名引用、前后断言、内联标志、原子组、所有格量词、Unicode 属性、字符类交集与无法编译。 */
    static Stream<Arguments> invalidPatterns() {
        return Stream.of(Arguments.of("(a)\\1"), Arguments.of("(?<x>a)\\k<x>"), Arguments.of("a(?=b)"),
            Arguments.of("(?<!a)b"), Arguments.of("(?i)abc"), Arguments.of("(?>a)"), Arguments.of("a++"),
            Arguments.of("\\p{L}+"), Arguments.of("[a-z&&[^b]]"), Arguments.of("[a"), Arguments.of("\\Aabc"));
    }

    /** 编码重复与缺少维护权限。 */
    @Test
    void test_createDuplicateAndPermission() {
        post(KEY_TYPES, admin, create("PAYOUT_ID", "出款", 130)).assertOk();
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("DATA_QUERY_READER", "DATA_QUERY_READ")));
        CreateQueryKeyTypeRequestBody named = create("NAMED_GROUP", "收单", 10);
        named.setValuePattern("(?<prefix>ord_)[0-9]{3}");

        post(KEY_TYPES, admin, create("PAYOUT_ID", "出款", 130)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(KEY_TYPES, reader, create("READER_KEY", "收单", 10))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(KEY_TYPES, reader).assertOk();
        post(KEY_TYPES, admin, named).assertOk();
    }

    /** 修改名称、分组、默认角色与顺序加版本；版本不符 409；不存在 404；被 ACTIVE 模板引用时改值类型 400，只被停用模板引用时可改。 */
    @Test
    void test_updateVersionAndReferencedValueType() {
        long orderId = jointQuery.keyType("ORDER_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true,
            JointQueryMocker.ID_PATTERN, 20);
        long templateId = orderTemplate("JQ_ORDER_REF", QueryTemplateStatus.ACTIVE);

        var updated = put(KEY_TYPES + "/" + orderId, admin, update("交易订单", "订单", QueryKeyValueType.INTEGER,
            QueryKeyRole.DIMENSION, 25, 1)).assertOk().as(ConfigurationMutationResponseBody.class);
        put(KEY_TYPES + "/" + orderId, admin, update("交易订单", "订单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, 25,
            1)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        put(KEY_TYPES + "/999999999", admin, update("x", "x", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, 1, 1))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        String message = put(KEY_TYPES + "/" + orderId, admin, update("交易订单", "订单", QueryKeyValueType.STRING,
            QueryKeyRole.LINK, 25, 2)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail")
            .asString();
        dataQueryRows.changeTemplateStatus(templateId, QueryTemplateStatus.INACTIVE.getCode());
        put(KEY_TYPES + "/" + orderId, admin, update("交易订单", "订单", QueryKeyValueType.STRING, QueryKeyRole.LINK, 25,
            2)).assertOk();

        Map<String, Object> row = dataQueryRows.keyType(orderId);
        assertAll(() -> assertEquals(2, updated.getVersion()), () -> assertTrue(message.contains("JQ_ORDER_REF"), message),
            () -> assertEquals(3, ((Number) row.get("version")).intValue()),
            () -> assertEquals(QueryKeyValueType.STRING.getCode(), row.get("valueType")),
            () -> assertEquals("订单", row.get("groupName")), () -> assertEquals(25, ((Number) row.get("sortNo")).intValue()));
    }

    /** 被 ACTIVE 模板引用时停用 400 并列出前 5 个模板；未引用时停用加版本；同状态不加版本、不登记审计；可重新启用。 */
    @Test
    void test_statusChangeBlockedWhenReferenced() {
        long orderId = jointQuery.keyType("ORDER_ID", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true,
            JointQueryMocker.ID_PATTERN, 20);
        List<Long> templates = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            templates.add(orderTemplate("JQ_ORDER_REF_" + index, QueryTemplateStatus.ACTIVE));
        }

        String message = post(KEY_TYPES + "/" + orderId + "/status", admin, status(QueryKeyTypeStatus.INACTIVE, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
        templates.forEach(id -> dataQueryRows.changeTemplateStatus(id, QueryTemplateStatus.INACTIVE.getCode()));
        var disabled = post(KEY_TYPES + "/" + orderId + "/status", admin, status(QueryKeyTypeStatus.INACTIVE, 1))
            .assertOk().as(ConfigurationMutationResponseBody.class);
        var same = post(KEY_TYPES + "/" + orderId + "/status", admin, status(QueryKeyTypeStatus.INACTIVE, 2))
            .assertOk().as(ConfigurationMutationResponseBody.class);
        auditChecker.assertNoEvent(requestId);
        var enabled = post(KEY_TYPES + "/" + orderId + "/status", admin, status(QueryKeyTypeStatus.ACTIVE, 2))
            .assertOk().as(ConfigurationMutationResponseBody.class);

        assertAll(() -> assertTrue(message.contains("JQ_ORDER_REF_1, JQ_ORDER_REF_2, JQ_ORDER_REF_3, JQ_ORDER_REF_4,"
                + " JQ_ORDER_REF_5 等 6 个"), message),
            () -> assertEquals(QueryKeyTypeStatus.INACTIVE.getCode(), disabled.getStatus()),
            () -> assertEquals(2, disabled.getVersion()), () -> assertEquals(2, same.getVersion()),
            () -> assertEquals(3, enabled.getVersion()),
            () -> assertEquals(QueryKeyTypeStatus.ACTIVE.getCode(), dataQueryRows.keyType(orderId).get("status")));
    }

    /** 整体重排按位置写 sortNo = 序号 × 10、版本加一；未覆盖全部或重复 400；任一版本不符 409 且整体不生效。 */
    @Test
    void test_reorder() {
        long first = jointQuery.keyType("KEY_A", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 10);
        long second = jointQuery.keyType("KEY_B", "支付", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 20);
        long third = jointQuery.keyType("KEY_C", "计费", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 30);

        put(ADMIN + "/key-type-order", admin, order(item(third, 1), item(first, 1)))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(ADMIN + "/key-type-order", admin, order(item(third, 1), item(first, 1), item(first, 1)))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(ADMIN + "/key-type-order", admin, order(item(third, 1), item(first, 1), item(second, 9)))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals(30, ((Number) dataQueryRows.keyType(third).get("sortNo")).intValue());
        var reordered = put(ADMIN + "/key-type-order", admin, order(item(third, 1), item(first, 1), item(second, 1)))
            .assertOk().as(ReorderQueryKeyTypesResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_QUERY_KEY_TYPE_ORDER", AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), "QUERY_KEY_TYPE", "QUERY_KEY_TYPE_ORDER");

        assertAll(() -> assertEquals(List.of(Long.toString(third), Long.toString(first), Long.toString(second)),
                reordered.getItems().stream().map(item -> item.getKeyTypeId()).toList()),
            () -> assertEquals(List.of(10, 20, 30), reordered.getItems().stream().map(item -> item.getSortNo()).toList()),
            () -> assertEquals(List.of(2, 2, 2), reordered.getItems().stream().map(item -> item.getVersion()).toList()),
            () -> assertEquals(List.of("KEY_C", "KEY_A", "KEY_B"), list("").items().stream()
                .map(QueryKeyTypeResponseBody::getKeyTypeCode).toList()));
    }

    /** 引用 ORDER_ID 的模板。 */
    long orderTemplate(String code, QueryTemplateStatus status) {
        return jointQuery.template(code, dataSourceId, "SELECT order_id FROM t_jq_order WHERE order_id IN (#{orderIds})",
            JointQueryMocker.list("orderIds", "INTEGER"), null, JointQueryMocker.none(10),
            JointQueryMocker.joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK"), status);
    }

    /** 列表。 */
    PagingResponseBody<QueryKeyTypeResponseBody> list(String query) {
        return get(KEY_TYPES + query, admin).assertOk().as(
            new TypeReference<PagingResponseBody<QueryKeyTypeResponseBody>>() {
            });
    }

    /** 新建请求：INTEGER、LINK、可作入口、数字正则。 */
    static CreateQueryKeyTypeRequestBody create(String code, String group, int sortNo) {
        CreateQueryKeyTypeRequestBody body = new CreateQueryKeyTypeRequestBody();
        body.setKeyTypeCode(code);
        body.setKeyTypeName(code + " 名称");
        body.setGroupName(group);
        body.setValueType(QueryKeyValueType.INTEGER.getCode());
        body.setDefaultRole(QueryKeyRole.LINK.getCode());
        body.setEntryAllowed(true);
        body.setValuePattern("[0-9]{1,19}");
        body.setInputHint("1—19 位数字");
        body.setSortNo(sortNo);
        return body;
    }

    /** 修改请求。 */
    static UpdateQueryKeyTypeRequestBody update(String name, String group, QueryKeyValueType valueType,
        QueryKeyRole role, int sortNo, int expectedVersion) {
        UpdateQueryKeyTypeRequestBody body = new UpdateQueryKeyTypeRequestBody();
        body.setKeyTypeName(name);
        body.setGroupName(group);
        body.setValueType(valueType.getCode());
        body.setDefaultRole(role.getCode());
        body.setEntryAllowed(true);
        body.setSortNo(sortNo);
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 启停请求。 */
    static ChangeQueryKeyTypeStatusRequestBody status(QueryKeyTypeStatus status, int expectedVersion) {
        ChangeQueryKeyTypeStatusRequestBody body = new ChangeQueryKeyTypeStatusRequestBody();
        body.setStatus(status.getCode());
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 重排请求。 */
    static ReorderQueryKeyTypesRequestBody order(QueryKeyTypeOrderItem... items) {
        ReorderQueryKeyTypesRequestBody body = new ReorderQueryKeyTypesRequestBody();
        body.setItems(List.of(items));
        return body;
    }

    /** 重排项。 */
    static QueryKeyTypeOrderItem item(long keyTypeId, int expectedVersion) {
        QueryKeyTypeOrderItem item = new QueryKeyTypeOrderItem();
        item.setKeyTypeId(Long.toString(keyTypeId));
        item.setExpectedVersion(expectedVersion);
        return item;
    }
}

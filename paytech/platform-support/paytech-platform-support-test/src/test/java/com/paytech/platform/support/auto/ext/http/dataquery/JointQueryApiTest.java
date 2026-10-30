package com.paytech.platform.support.auto.ext.http.dataquery;

import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ACTION_BY_ATTEMPT;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ATTEMPT_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ATTEMPT_BY_INTENT;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.BILLING_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.INTENT_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.INTENT_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ORDER_BY_CLIENT;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ORDER_BY_CLIENT_ORDER_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ORDER_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_BY_NO;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_NO;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.SESSION_BY_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.JointQueryApi;
import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphTemplate;
import com.paytech.platform.support.api.dataquery.response.JointQueryNode;
import com.paytech.platform.support.api.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.dataquery.core.enums.JointQueryExclusionReason;
import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.JointQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;
import com.paytech.platform.support.test.dataquery.JointQueryMocker;

/**
 * 联合查询：设置（只列有参与模板的泳道分组）、可达预览（泳道、最少步数、断点、不参与；入口不可用 400）、按层展开的完整退款链路与上游入口、LIST 合并与标量逐值、
 * 维度键不展开、CLIENT_ORDER_ID 命中多个 Client、屏蔽与截断标识、重试指定模板、访问控制与入口校验（AC-03—AC-07、
 * AC-10—AC-12）。
 *
 * @see JointQueryApi#getJointQuerySettings(String, String, String)
 * @see JointQueryApi#getJointQueryGraph(String, String, String, String)
 * @see JointQueryApi#executeJointQueryStep(String, String, String, JointQueryStepRequestBody)
 */
class JointQueryApiTest extends JointQueryApiTestBase {

    /** 退款单号入口键类型。 */
    private static final String REFUND_NO_ENTRY = "REFUND_REQUEST_NO";

    private Operator admin;

    private long dataSourceId;

    /** 本类公共：平台管理员、链路键类型、一个 ACTIVE 只读数据源与退款链路的 12 个模板。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_JOINT");
        jointQuery.chainKeyTypes();
        jointQuery.chainTemplates(dataSourceId);
    }

    /**
     * 入口只列 ACTIVE 且允许作为入口的键类型；分组只含有参与模板以组内键类型为行主体的泳道（渠道、归属维度没有行主体模板，
     * 计费由计费事实模板形成），按组内 ACTIVE 键类型最小顺序号排序；护栏取配置值。
     */
    @Test
    void test_settingsListsEntryKeyTypesGroupsAndLimits() {
        jointQuery.keyType("PAYOUT_ID", "出款", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 130,
            QueryKeyTypeStatus.INACTIVE);

        var settings = joint(CALLER, admin, HttpMethod.GET, "/settings", null).assertOk()
            .as(JointQuerySettingsResponseBody.class);

        List<String> codes = settings.getEntryKeyTypes().stream().map(entry -> entry.getKeyTypeCode()).toList();
        assertAll(() -> assertEquals(List.of("CHECKOUT_SESSION_ID", "ORDER_ID", "CLIENT_ORDER_ID", "REFUND_REQUEST_ID",
                "REFUND_REQUEST_NO", "PAYMENT_INTENT_ID", "PAYMENT_ATTEMPT_ID", "PAYMENT_ACTION_ID",
                "EXTERNAL_TRANSACTION_ID", "BILLING_FACT_ID", "CLIENT_ID"), codes),
            () -> assertEquals(JointQueryMocker.REFUND_NO_PATTERN, settings.getEntryKeyTypes().get(4).getValuePattern()),
            () -> assertEquals(Map.of("收单", 10, "支付", 40, "计费", 100),
                settings.getGroups().stream().collect(Collectors.toMap(group -> group.getGroupName(),
                    group -> group.getRank()))),
            () -> assertEquals(List.of("收单", "支付", "计费"),
                settings.getGroups().stream().map(group -> group.getGroupName()).toList()),
            () -> assertEquals(20, settings.getLimits().getMaxEntryValues()),
            () -> assertEquals(50, settings.getLimits().getMaxValuesPerKey()),
            () -> assertEquals(100, settings.getLimits().getMaxRowsPerTemplate()),
            () -> assertEquals(20, settings.getLimits().getMaxExecutionsPerStep()),
            () -> assertEquals(6, settings.getLimits().getMaxDepth()),
            () -> assertEquals(60, settings.getLimits().getMaxTemplateExecutions()),
            () -> assertEquals(60, settings.getLimits().getMaxTotalSeconds()));
    }

    /** 可达预览按泳道分组并给出最少步数与断点；不参与的模板带原因；停用模板后预览随之变化；入口缺失或不可用时 400。 */
    @Test
    void test_previewGroupsBreaksAndExcluded() {
        jointQuery.template("JQ_TWO_REQUIRED", dataSourceId, "SELECT order_id FROM t_jq_order WHERE order_id"
            + " IN (#{orderIds}) AND client_id = #{clientId}", "[{\"name\":\"orderIds\",\"type\":\"LIST\","
            + "\"elementType\":\"INTEGER\",\"required\":true},{\"name\":\"clientId\",\"type\":\"INTEGER\","
            + "\"required\":true}]", null, JointQueryMocker.none(10),
            JointQueryMocker.joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK"));

        var graph = graph(REFUND_NO_ENTRY);
        Map<String, Integer> steps = graph.getGroups().stream().flatMap(group -> group.getTemplates().stream())
            .collect(Collectors.toMap(JointQueryGraphTemplate::getTemplateCode, JointQueryGraphTemplate::getMinSteps));
        assertAll(() -> assertEquals(List.of("收单", "支付", "计费"),
                graph.getGroups().stream().map(group -> group.getGroupName()).toList()),
            () -> assertEquals(Map.of(REFUND_BY_NO, 0, REFUND_BY_ORDER, 1, ORDER_BY_ID, 1, INTENT_BY_ORDER, 1,
                BILLING_BY_ORDER, 1, SESSION_BY_ID, 2, INTENT_BY_ID, 2, ATTEMPT_BY_INTENT, 2, ATTEMPT_BY_ID, 3,
                ACTION_BY_ATTEMPT, 3), steps),
            () -> assertEquals(List.of(SESSION_BY_ID, ORDER_BY_ID, REFUND_BY_NO, REFUND_BY_ORDER),
                graph.getGroups().getFirst().getTemplates().stream().map(JointQueryGraphTemplate::getTemplateCode)
                    .toList()),
            () -> assertEquals(Set.of("REFUND_REQUEST_ID", "BILLING_FACT_ID", "PAYMENT_ACTION_ID",
                "EXTERNAL_TRANSACTION_ID"), graph.getBreaks().stream().map(item -> item.getKeyType())
                .collect(Collectors.toSet())),
            () -> assertEquals(List.of("JQ_TWO_REQUIRED"), graph.getExcluded().stream()
                .map(item -> item.getTemplateCode()).toList()),
            () -> assertEquals(JointQueryExclusionReason.OTHER_REQUIRED_PARAMETER.getCode(),
                graph.getExcluded().getFirst().getReasonCode()));

        dataQueryRows.changeTemplateStatus(dataQueryRows.templateId(ORDER_BY_ID), QueryTemplateStatus.INACTIVE.getCode());
        var changed = graph(REFUND_NO_ENTRY);
        assertAll(() -> assertFalse(changed.getGroups().stream().flatMap(group -> group.getTemplates().stream())
                .anyMatch(template -> template.getTemplateCode().equals(ORDER_BY_ID))),
            () -> assertTrue(changed.getExcluded().stream().anyMatch(item -> item.getTemplateCode().equals(ORDER_BY_ID)
                && item.getReasonCode().equals(JointQueryExclusionReason.TEMPLATE_INACTIVE.getCode()))));

        jointQuery.keyType("DISABLED_ENTRY", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 15,
            QueryKeyTypeStatus.INACTIVE);
        for (String query : List.of("?entryKeyType=CHANNEL_INTERACTION_ID", "?entryKeyType=NO_SUCH_KEY",
            "?entryKeyType=DISABLED_ENTRY", "")) {
            String detail = joint(CALLER, admin, HttpMethod.GET, "/graph" + query, null)
                .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
            assertTrue(detail.startsWith("入口键类型不存在、未启用或不允许作为入口"), detail);
        }
    }

    /** 退款单号入口逐层展开全部 10 个链路模板；每层只执行未访问的键，订单与退款不重复查询；边能追溯到来源行。 */
    @Test
    void test_refundChainExpandsAllLayers() {
        List<JointQueryStepResponseBody> steps = runChain(admin, "REFUND_REQUEST_NO", REFUND_NO);

        assertAll(() -> assertEquals(List.of(Set.of(REFUND_BY_NO),
                Set.of(REFUND_BY_ORDER, ORDER_BY_ID, INTENT_BY_ORDER, BILLING_BY_ORDER),
                Set.of(SESSION_BY_ID, INTENT_BY_ID, ATTEMPT_BY_INTENT), Set.of(ATTEMPT_BY_ID, ACTION_BY_ATTEMPT), Set.of()),
                steps.stream().map(step -> step.getNodes().stream().map(JointQueryNode::getTemplateCode)
                    .collect(Collectors.toSet())).toList()),
            () -> assertEquals(10, nodes(steps).size()),
            () -> assertTrue(nodes(steps).stream().allMatch(node -> JointQueryNodeStatus.SUCCEEDED.getCode()
                .equals(node.getStatus()))));
        JointQueryStepResponseBody first = steps.getFirst();
        assertAll(() -> assertEquals(List.of("5001"), discovered(first, "ORDER_ID")),
            () -> assertTrue(first.getLinks().stream().anyMatch(link -> link.getNodeId().equals("0:" + REFUND_BY_NO
                + ":REFUND_REQUEST_NO") && link.getRowIndex() == 0 && link.getColumn().equals("order_id")
                && link.getValue().equals("5001"))),
            () -> assertEquals(List.of("5001"), node(nodes(steps), ORDER_BY_ID).getTriggerValues()),
            () -> assertEquals(List.of("7001", "7002"), discovered(steps.get(1), "REFUND_REQUEST_ID")),
            () -> assertEquals(List.of("9001", "9002"), node(nodes(steps), ACTION_BY_ATTEMPT).getTriggerValues()),
            () -> assertEquals(List.of("EXT-AAA", "EXT-BBB"), discovered(steps.get(3), "EXTERNAL_TRANSACTION_ID")),
            () -> assertEquals(List.of("5001"), discovered(steps.get(1), "ORDER_ID")));
    }

    /** 从下游支付尝试进入仍能查回订单与收银台会话，同一节点的泳道位置与退款单号入口相同（展示与跳数无关）。 */
    @Test
    void test_upstreamEntryKeepsLanePlacement() {
        List<JointQueryNode> fromRefund = nodes(runChain(admin, "REFUND_REQUEST_NO", REFUND_NO));
        List<JointQueryNode> fromAttempt = nodes(runChain(admin, "PAYMENT_ATTEMPT_ID", "9001"));

        for (String code : List.of(ORDER_BY_ID, SESSION_BY_ID, INTENT_BY_ID, INTENT_BY_ORDER)) {
            JointQueryNode expected = node(fromRefund, code);
            JointQueryNode actual = node(fromAttempt, code);
            assertAll(code, () -> assertEquals(expected.getGroupName(), actual.getGroupName()),
                () -> assertEquals(expected.getLaneRank(), actual.getLaneRank()),
                () -> assertEquals(expected.getSubjectSortNo(), actual.getSubjectSortNo()),
                () -> assertEquals(expected.getSubjectKeyType(), actual.getSubjectKeyType()));
        }
        JointQueryNode order = node(fromAttempt, ORDER_BY_ID);
        assertAll(() -> assertEquals("收单", order.getGroupName()), () -> assertEquals(10, order.getLaneRank()),
            () -> assertEquals(20, order.getSubjectSortNo()),
            () -> assertEquals(List.of("6001"), node(fromAttempt, SESSION_BY_ID).getTriggerValues()),
            () -> assertTrue(node(fromAttempt, SESSION_BY_ID).getNodeId().startsWith("3:")));
    }

    /** 同层同键类型的值规范化去重后合并为一次 LIST 执行；标量键参数逐值执行并追加节点序号。 */
    @Test
    void test_listMergedAndScalarPerValue() {
        JointQueryStepRequestBody body = new JointQueryStepRequestBody();
        body.setDepth(1);
        body.setKeys(List.of(key("ORDER_ID", "5001", "05001", "5002"), key("CHECKOUT_SESSION_ID", "6001", "6002")));
        body.setTemplateCodes(List.of(ORDER_BY_ID, SESSION_BY_ID));

        var step = stepOk(admin, body);

        JointQueryNode order = node(step.getNodes(), ORDER_BY_ID);
        List<JointQueryNode> sessions = step.getNodes().stream().filter(node -> node.getTemplateCode()
            .equals(SESSION_BY_ID)).toList();
        assertAll(() -> assertEquals(3, step.getNodes().size()),
            () -> assertEquals(List.of("5001", "5002"), order.getTriggerValues()),
            () -> assertTrue(order.isKeyParameterList()), () -> assertEquals("orderIds", order.getKeyParameter()),
            () -> assertEquals(2, order.getRows().size()), () -> assertEquals("1:" + ORDER_BY_ID + ":ORDER_ID",
                order.getNodeId()),
            () -> assertEquals(List.of("1:" + SESSION_BY_ID + ":CHECKOUT_SESSION_ID:1",
                "1:" + SESSION_BY_ID + ":CHECKOUT_SESSION_ID:2"), sessions.stream().map(JointQueryNode::getNodeId)
                .toList()),
            () -> assertEquals(List.of(List.of("6001"), List.of("6002")), sessions.stream()
                .map(JointQueryNode::getTriggerValues).toList()),
            () -> assertFalse(sessions.getFirst().isKeyParameterList()));
    }

    /** 维度列不进入发现键；以维度键为入口另起查询时按其模板正常展开。 */
    @Test
    void test_dimensionKeyNotExpanded() {
        var step = stepOk(admin, withTemplates(stepBody(1, "ORDER_ID", "5001"), ORDER_BY_ID));

        JointQueryNode order = node(step.getNodes(), ORDER_BY_ID);
        assertAll(() -> assertTrue(discovered(step, "CLIENT_ID").isEmpty()),
            () -> assertTrue(discovered(step, "CLIENT_ORDER_ID").isEmpty()),
            () -> assertEquals(QueryKeyRole.DIMENSION.getCode(), order.getColumns().stream()
                .filter(column -> column.getName().equals("client_id")).findFirst().orElseThrow().getKeyRole()),
            () -> assertEquals(List.of("6001"), discovered(step, "CHECKOUT_SESSION_ID")));

        var byClient = stepOk(admin, stepBody(0, "CLIENT_ID", "3101"));
        assertAll(() -> assertEquals(List.of(ORDER_BY_CLIENT), byClient.getNodes().stream()
                .map(JointQueryNode::getTemplateCode).toList()),
            () -> assertEquals(List.of("5001", "5003"), discovered(byClient, "ORDER_ID")),
            () -> assertTrue(discovered(byClient, "CLIENT_ID").isEmpty()));
    }

    /** CLIENT_ORDER_ID 在两个 Client 下重复时全部展开，结果行保留各自的 Client 维度供页面提示。 */
    @Test
    void test_clientOrderIdHitsMultipleClients() {
        var step = stepOk(admin, stepBody(0, "CLIENT_ORDER_ID", "CO-1"));

        JointQueryNode node = node(step.getNodes(), ORDER_BY_CLIENT_ORDER_ID);
        assertAll(() -> assertEquals(List.of("5001", "5002"), discovered(step, "ORDER_ID")),
            () -> assertEquals(List.of(List.of("5001", "CO-1", "3101"), List.of("5002", "CO-1", "3102")),
                node.getRows()));
    }

    /** 结果沿用列屏蔽；游标分页还有更多行时给出 nextCursor，不分页只标截断；结果缺少链路列时给出提示。 */
    @Test
    void test_maskingTruncationAndMissingLinkColumn() {
        jointQuery.template("JQ_ATTEMPT_PAGED", dataSourceId, "SELECT payment_attempt_id FROM t_jq_payment_attempt"
            + " WHERE payment_intent_id IN (#{intentIds})"
            + " {{#if __cursor}}AND payment_attempt_id > #{__cursor.payment_attempt_id}{{/if}} ORDER BY payment_attempt_id",
            JointQueryMocker.list("intentIds", "INTEGER"), null,
            DataQueryMocker.constraints(100, 5, QueryPagingMode.CURSOR, 1, false, "payment_attempt_id"),
            JointQueryMocker.joint("PAYMENT_ATTEMPT_ID", "intentIds", "PAYMENT_INTENT_ID",
                "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK"));
        jointQuery.template("JQ_ATTEMPT_ONE_ROW", dataSourceId, "SELECT payment_attempt_id AS attempt FROM"
            + " t_jq_payment_attempt WHERE payment_intent_id IN (#{intentIds}) ORDER BY payment_attempt_id",
            JointQueryMocker.list("intentIds", "INTEGER"), null, JointQueryMocker.none(1),
            JointQueryMocker.joint("PAYMENT_ATTEMPT_ID", "intentIds", "PAYMENT_INTENT_ID",
                "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK"));

        var orders = stepOk(admin, withTemplates(stepBody(1, "ORDER_ID", "5001"), ORDER_BY_ID));
        var attempts = stepOk(admin, withTemplates(stepBody(1, "PAYMENT_INTENT_ID", "8001"), "JQ_ATTEMPT_PAGED",
            "JQ_ATTEMPT_ONE_ROW", ATTEMPT_BY_INTENT));

        JointQueryNode order = node(orders.getNodes(), ORDER_BY_ID);
        JointQueryNode paged = node(attempts.getNodes(), "JQ_ATTEMPT_PAGED");
        JointQueryNode oneRow = node(attempts.getNodes(), "JQ_ATTEMPT_ONE_ROW");
        JointQueryNode complete = node(attempts.getNodes(), ATTEMPT_BY_INTENT);
        assertAll(() -> assertEquals("*".repeat("buyer.one@example.test".length()), order.getRows().getFirst().get(5)),
            () -> assertEquals(QueryPagingMode.OFFSET.getCode(), order.getPagingMode()),
            () -> assertTrue(paged.isTruncated()), () -> assertNotNull(paged.getNextCursor()),
            () -> assertEquals(QueryPagingMode.CURSOR.getCode(), paged.getPagingMode()),
            () -> assertTrue(oneRow.isTruncated()), () -> assertNull(oneRow.getNextCursor()),
            () -> assertEquals(List.of("LINK_COLUMN_MISSING:payment_attempt_id"), oneRow.getWarnings()),
            () -> assertFalse(complete.isTruncated()), () -> assertNull(complete.getNextCursor()),
            () -> assertEquals(List.of("9001", "9002"), discovered(attempts, "PAYMENT_ATTEMPT_ID")));
    }

    /** 指定 templateCodes 时只执行这些模板，不存在或不参与的编码忽略；本层没有参与模板时返回空节点。 */
    @Test
    void test_templateCodesAndEmptyLayer() {
        var retried = stepOk(admin, withTemplates(stepBody(1, "ORDER_ID", "5001"), ORDER_BY_ID, "NO_SUCH_TEMPLATE"));
        var empty = stepOk(admin, stepBody(1, "PAYMENT_ACTION_ID", "9101"));

        assertAll(() -> assertEquals(List.of(ORDER_BY_ID), retried.getNodes().stream()
                .map(JointQueryNode::getTemplateCode).toList()),
            () -> assertTrue(empty.getNodes().isEmpty()), () -> assertTrue(empty.getLinks().isEmpty()),
            () -> assertTrue(empty.getDiscoveredKeys().isEmpty()), () -> assertTrue(empty.getTruncations().isEmpty()));
    }

    /** 缺少 DATA_QUERY_EXECUTE 时三条接口都返回 403；非登记调用方被拒绝。 */
    @Test
    void test_accessRejected() {
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("DATA_QUERY_READER", "DATA_QUERY_READ")));

        joint(CALLER, reader, HttpMethod.GET, "/settings", null)
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        joint(CALLER, reader, HttpMethod.GET, "/graph?entryKeyType=ORDER_ID", null)
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        step(reader, stepBody(0, "ORDER_ID", "5001")).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        joint("other-service", admin, HttpMethod.POST, "/steps", stepBody(0, "ORDER_ID", "5001"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        joint("other-service", admin, HttpMethod.GET, "/settings", null)
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 入口不合法返回 422 并定位字段：缺键类型、超过 20 个值、非数字、正则不符、不可作入口或停用、层号越界、重复键类型。 */
    @Test
    void test_invalidStepInput() {
        jointQuery.keyType("DISABLED_KEY", "收单", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, null, 15,
            QueryKeyTypeStatus.INACTIVE);
        String[] tooMany = IntStream.rangeClosed(1, 21).mapToObj(Integer::toString).toArray(String[]::new);

        step(admin, stepBody(0, null, "5001")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].keyType");
        step(admin, stepBody(0, "ORDER_ID", tooMany)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].values");
        step(admin, stepBody(0, "ORDER_ID", "5001", "50a1")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].values[1]");
        step(admin, stepBody(0, "ORDER_ID", "99999999999999999999")).assertProblem(422,
            CommonErrorCode.VALIDATION_ERROR).assertFieldError("keys[0].values[0]");
        step(admin, stepBody(0, "REFUND_REQUEST_NO", "rfd_short")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].values[0]");
        step(admin, stepBody(0, "CHANNEL_INTERACTION_ID", "1")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].keyType");
        step(admin, stepBody(0, "DISABLED_KEY", "1")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("keys[0].keyType");
        step(admin, stepBody(MAX_DEPTH, "ORDER_ID", "5001")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("depth");
        JointQueryStepRequestBody twoEntries = stepBody(0, "ORDER_ID", "5001");
        twoEntries.setKeys(List.of(key("ORDER_ID", "5001"), key("CLIENT_ID", "3101")));
        step(admin, twoEntries).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("keys");
        JointQueryStepRequestBody duplicated = stepBody(1, "ORDER_ID", "5001");
        duplicated.setKeys(List.of(key("ORDER_ID", "5001"), key("ORDER_ID", "5002")));
        step(admin, duplicated).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("keys[1].keyType");

        var deeper = stepOk(admin, stepBody(1, "REFUND_REQUEST_NO", "not-a-refund-number"));
        assertEquals(List.of(List.of()), deeper.getNodes().stream().map(JointQueryNode::getRows).toList());
    }

    /** 可达预览。 */
    JointQueryGraphResponseBody graph(String entryKeyType) {
        return joint(CALLER, admin, HttpMethod.GET, "/graph?entryKeyType=" + entryKeyType, null).assertOk()
            .as(JointQueryGraphResponseBody.class);
    }

    /** 只执行给定模板。 */
    static JointQueryStepRequestBody withTemplates(JointQueryStepRequestBody body, String... templateCodes) {
        body.setTemplateCodes(List.of(templateCodes));
        return body;
    }
}

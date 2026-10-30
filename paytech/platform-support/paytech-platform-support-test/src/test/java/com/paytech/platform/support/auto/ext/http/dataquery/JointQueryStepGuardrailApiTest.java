package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.JointQueryApi;
import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryNode;
import com.paytech.platform.support.api.dataquery.response.JointQueryTruncation;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.dataquery.core.enums.JointQueryTruncationRule;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.test.base.JointQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;
import com.paytech.platform.support.test.dataquery.JointQueryMocker;

/**
 * 联合查询服务端单步护栏与失败隔离：单步预算用尽跳过未开始的执行、单步执行数上限、单键值数上限、单模板行数上限，
 * 以及超时、只读校验拒绝与 SQL 错误只记在各自节点（AC-08、AC-09）。mock 配置单步预算 3 秒、并发 2。
 *
 * @see JointQueryApi#executeJointQueryStep(String, String, String, JointQueryStepRequestBody)
 */
class JointQueryStepGuardrailApiTest extends JointQueryApiTestBase {

    /** 三表 CROSS JOIN 求和，超过模板或单步超时后被取消。 */
    private static final String SLOW = "SELECT o.order_id, (SELECT SUM(a.n + b.n + c.n) FROM "
        + DataQueryFixture.NUMBERS + " a CROSS JOIN " + DataQueryFixture.NUMBERS + " b CROSS JOIN "
        + DataQueryFixture.NUMBERS + " c) AS total FROM t_jq_order o WHERE o.order_id IN (#{orderIds})";

    private Operator admin;

    private long dataSourceId;

    /** 本类公共：平台管理员、链路键类型与一个 ACTIVE 只读数据源；模板由各用例插入。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_JOINT_GUARD");
        jointQuery.chainKeyTypes();
    }

    /** 两个慢查询占满并发，剩余不足 1 秒时第三个不再启动：记 SKIPPED 与 STEP_BUDGET；已启动的按单步剩余秒数超时。 */
    @Test
    void test_stepBudgetSkipsUnstartedExecutions() {
        for (String code : List.of("JQ_SLOW_A", "JQ_SLOW_B", "JQ_SLOW_C")) {
            orderTemplate(code, SLOW, 8);
        }

        long startedAt = System.nanoTime();
        var step = stepOk(admin, stepBody(1, "ORDER_ID", "5001"));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        Map<String, JointQueryNode> nodes = byCode(step.getNodes());
        assertAll(() -> assertEquals(JointQueryNodeStatus.FAILED.getCode(), nodes.get("JQ_SLOW_A").getStatus()),
            () -> assertEquals(DataQueryErrorCode.DATA_QUERY_TIMEOUT.getCode(), nodes.get("JQ_SLOW_A").getErrorCode()),
            () -> assertEquals(JointQueryNodeStatus.FAILED.getCode(), nodes.get("JQ_SLOW_B").getStatus()),
            () -> assertEquals(JointQueryNodeStatus.SKIPPED.getCode(), nodes.get("JQ_SLOW_C").getStatus()),
            () -> assertEquals("STEP_BUDGET_EXHAUSTED", nodes.get("JQ_SLOW_C").getErrorCode()),
            () -> assertEquals(0, nodes.get("JQ_SLOW_C").getElapsedMillis()),
            () -> assertTrue(nodes.get("JQ_SLOW_C").getRows().isEmpty()),
            () -> assertEquals(List.of(truncation(JointQueryTruncationRule.STEP_BUDGET, null, 1)),
                truncations(step.getTruncations())),
            () -> assertTrue(elapsedMillis < 4500, "单步应在预算加余量内返回，实际 " + elapsedMillis + " ms"));
    }

    /** 单步超过 maxExecutionsPerStep（20）时超出的执行记 SKIPPED，并给出 STEP_EXECUTION_LIMIT 截断。 */
    @Test
    void test_stepExecutionLimit() {
        jointQuery.template("JQ_SESSION_SCALAR", dataSourceId, "SELECT checkout_session_id, order_id"
            + " FROM t_jq_checkout_session WHERE checkout_session_id = #{sessionId}",
            JointQueryMocker.scalar("sessionId", "INTEGER"), null, JointQueryMocker.none(10),
            JointQueryMocker.joint("CHECKOUT_SESSION_ID", "sessionId", "CHECKOUT_SESSION_ID",
                "order_id:ORDER_ID:LINK"));
        String[] sessions = IntStream.rangeClosed(6001, 6021).mapToObj(Integer::toString).toArray(String[]::new);

        var step = stepOk(admin, stepBody(1, "CHECKOUT_SESSION_ID", sessions));

        List<String> statuses = step.getNodes().stream().map(JointQueryNode::getStatus).toList();
        JointQueryNode last = step.getNodes().getLast();
        assertAll(() -> assertEquals(21, statuses.size()),
            () -> assertEquals(20, statuses.stream().filter(JointQueryNodeStatus.SUCCEEDED.getCode()::equals).count()),
            () -> assertEquals(JointQueryNodeStatus.SKIPPED.getCode(), last.getStatus()),
            () -> assertEquals("STEP_EXECUTION_LIMIT", last.getErrorCode()),
            () -> assertEquals(List.of("6021"), last.getTriggerValues()),
            () -> assertEquals(List.of(truncation(JointQueryTruncationRule.STEP_EXECUTION_LIMIT, null, 1)),
                truncations(step.getTruncations())),
            () -> assertEquals(List.of("5001", "5002", "5003"), discovered(step, "ORDER_ID")));
    }

    /** 已加载的值不占单步执行数（technique 8.7）：21 个会话中 1 个已加载，“按会话 ID 查”逐值执行剩余 20 个，不再触发 STEP_EXECUTION_LIMIT。 */
    @Test
    void test_loadedSubjectsNotCountedTowardStepLimit() {
        jointQuery.template("JQ_SESSION_SCALAR", dataSourceId, "SELECT checkout_session_id, order_id"
            + " FROM t_jq_checkout_session WHERE checkout_session_id = #{sessionId}",
            JointQueryMocker.scalar("sessionId", "INTEGER"), null, JointQueryMocker.none(10),
            JointQueryMocker.joint("CHECKOUT_SESSION_ID", "sessionId", "CHECKOUT_SESSION_ID",
                "checkout_session_id:CHECKOUT_SESSION_ID:LINK", "order_id:ORDER_ID:LINK"));
        JointQueryStepRequestBody body = stepBody(1, "CHECKOUT_SESSION_ID",
            IntStream.rangeClosed(6001, 6021).mapToObj(Integer::toString).toArray(String[]::new));
        body.setLoadedSubjects(List.of(loadedSubject("CHECKOUT_SESSION_ID", "6001")));

        var step = stepOk(admin, body);

        assertAll(() -> assertEquals(20, step.getNodes().size()),
            () -> assertTrue(step.getNodes().stream().allMatch(node -> JointQueryNodeStatus.SUCCEEDED.getCode()
                .equals(node.getStatus()))),
            () -> assertEquals(List.of("6002"), step.getNodes().getFirst().getTriggerValues()),
            () -> assertTrue(step.getTruncations().isEmpty()),
            () -> assertEquals(1, step.getAlreadyLoaded().size()),
            () -> assertEquals(List.of("6001"), step.getAlreadyLoaded().getFirst().getValues()));
    }

    /** 模板只取 maxRowsPerTemplate（100）行并标截断；某键类型发现的值超过 maxValuesPerKey（50）时只返回前 50 个。 */
    @Test
    void test_rowAndValueLimits() {
        jointQuery.keyType("SEQ_START", "计费", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, true, "[0-9]{1,4}", 101);
        jointQuery.keyType("SEQ_NO", "计费", QueryKeyValueType.INTEGER, QueryKeyRole.LINK, false, null, 102);
        jointQuery.template("JQ_NUMBERS", dataSourceId, "SELECT n AS seq_no FROM " + DataQueryFixture.NUMBERS
            + " WHERE n >= #{start} ORDER BY n", JointQueryMocker.scalar("start", "INTEGER"), null,
            JointQueryMocker.none(1000), JointQueryMocker.joint("SEQ_NO", "start", "SEQ_START",
                "seq_no:SEQ_NO:LINK"));

        var step = stepOk(admin, stepBody(0, "SEQ_START", "0"));

        JointQueryNode node = step.getNodes().getFirst();
        List<String> values = discovered(step, "SEQ_NO");
        assertAll(() -> assertEquals(100, node.getRows().size()), () -> assertTrue(node.isTruncated()),
            () -> assertEquals(100, step.getLinks().size()), () -> assertEquals(50, values.size()),
            () -> assertEquals("0", values.getFirst()), () -> assertEquals("49", values.getLast()),
            () -> assertEquals(List.of(truncation(JointQueryTruncationRule.MAX_VALUES_PER_KEY, "SEQ_NO", 50)),
                truncations(step.getTruncations())));
    }

    /** 超时、只读校验拒绝与 SQL 错误只记在各自节点，同一步的其他节点照常成功。 */
    @Test
    void test_failureIsolatedToNode() {
        orderTemplate("JQ_ORDER_OK", "SELECT order_id, client_id FROM t_jq_order WHERE order_id IN (#{orderIds})"
            + " ORDER BY order_id", 5);
        orderTemplate("JQ_ORDER_SLOW", SLOW, 1);
        orderTemplate("JQ_ORDER_SYSTEM_SCHEMA", "SELECT user AS order_id FROM mysql.user WHERE user IN (#{orderIds})", 5);
        orderTemplate("JQ_ORDER_BAD_COLUMN", "SELECT no_such_column AS order_id FROM t_jq_order"
            + " WHERE order_id IN (#{orderIds})", 5);

        var step = stepOk(admin, stepBody(1, "ORDER_ID", "5001"));

        Map<String, JointQueryNode> nodes = byCode(step.getNodes());
        assertAll(() -> assertEquals(JointQueryNodeStatus.SUCCEEDED.getCode(), nodes.get("JQ_ORDER_OK").getStatus()),
            () -> assertEquals(List.of(List.of("5001", "3101")), nodes.get("JQ_ORDER_OK").getRows()),
            () -> assertEquals(DataQueryErrorCode.DATA_QUERY_TIMEOUT.getCode(),
                nodes.get("JQ_ORDER_SLOW").getErrorCode()),
            () -> assertEquals(JointQueryNodeStatus.FAILED.getCode(),
                nodes.get("JQ_ORDER_SYSTEM_SCHEMA").getStatus()),
            () -> assertNotNull(nodes.get("JQ_ORDER_SYSTEM_SCHEMA").getErrorMessage()),
            () -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
                nodes.get("JQ_ORDER_BAD_COLUMN").getErrorCode()),
            () -> assertTrue(nodes.values().stream().filter(node -> !node.getStatus()
                .equals(JointQueryNodeStatus.SUCCEEDED.getCode())).allMatch(node -> node.getRows().isEmpty()
                && node.getColumns().isEmpty())),
            () -> assertEquals(List.of("5001"), discovered(step, "ORDER_ID")),
            () -> assertTrue(step.getTruncations().isEmpty()));
    }

    /** 以 ORDER_ID 为触发键的 LIST 模板，行主体为订单。 */
    void orderTemplate(String code, String body, int timeoutSeconds) {
        jointQuery.template(code, dataSourceId, body, JointQueryMocker.list("orderIds", "INTEGER"), null,
            DataQueryMocker.constraints(100, timeoutSeconds, QueryPagingMode.NONE, null, false),
            JointQueryMocker.joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK"));
    }

    /** 节点按模板编码索引。 */
    static Map<String, JointQueryNode> byCode(List<JointQueryNode> nodes) {
        return nodes.stream().collect(Collectors.toMap(JointQueryNode::getTemplateCode, node -> node));
    }

    /** 截断的可比较形式。 */
    static List<String> truncations(List<JointQueryTruncation> truncations) {
        return truncations.stream().map(item -> item.getRule() + "/" + item.getKeyType() + "/" + item.getDropped())
            .toList();
    }

    /** 期望截断的可比较形式。 */
    static String truncation(JointQueryTruncationRule rule, String keyType, int dropped) {
        return rule.getCode() + "/" + keyType + "/" + dropped;
    }
}

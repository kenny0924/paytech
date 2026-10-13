package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ExecuteStatus;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.ExecutionFlow;
import com.paytech.platform.support.integration.domain.execution.ExecutionResult;
import com.paytech.platform.support.integration.domain.execution.FieldSchema;
import com.paytech.platform.support.integration.domainservice.execution.FlowEngine;
import com.paytech.platform.support.integration.domainservice.execution.ValueResolver;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.function.MoneyMinorUnitsFunction;
import com.paytech.platform.support.test.builder.ExternalApiSamples;

/**
 * 流程引擎的出边与出口规则（流程结构第 4.2、4.3、5 节）：出边按优先级判断、兜底最后、条件必须为布尔；
 * 出口按标准 API 输出结构映射，ERROR 出口与任何映射失败都输出空对象。节点执行由入口测试覆盖。
 *
 * @see FlowEngine#nextTarget(List, Map)
 * @see FlowEngine#mapReturn(ExecutionFlow.Return, FieldSchema, Map)
 */
class FlowEngineTest {

    private static final String SUCCESS = ExecuteStatus.SUCCESS.getCode();
    private static final String FAIL = ExecuteStatus.FAIL.getCode();
    private static final String ERROR = ExecuteStatus.ERROR.getCode();

    private final ExpressionEngine expressions = new ExpressionEngine();
    private final FlowEngine engine = new FlowEngine(
        new ValueResolver(expressions, List.of(new MoneyMinorUnitsFunction())), expressions);

    /** 条件连线按 priority 升序判断（与配置顺序无关），第一条为真即流转，全部为假走兜底。 */
    @Test
    void test_edgePriority() {
        var flow = ExecutionFlow.from(ExternalApiSamples.object("""
            {"timeoutMs":1000,"initializers":[],"entry":"a","nodes":[{"id":"a","integrationApi":"API","request":{}}],
             "returns":[{"id":"x","executeStatus":"ERROR"},{"id":"y","executeStatus":"ERROR"},{"id":"z","executeStatus":"ERROR"}],
             "edges":[{"from":"a","to":"z","default":true},
               {"from":"a","to":"y","condition":"steps.a.executeStatus != 'ERROR'","priority":2},
               {"from":"a","to":"x","condition":"steps.a.executeStatus == 'SUCCESS'","priority":1}]}"""));
        var edges = flow.getEdges().get("a");

        assertEquals("x", engine.nextTarget(edges, step(SUCCESS)));
        assertEquals("y", engine.nextTarget(edges, step(FAIL)));
        assertEquals("z", engine.nextTarget(edges, step(ERROR)));
    }

    /** 条件结果不是布尔或求值异常：整次调用 ERROR，不落入兜底。 */
    @Test
    void test_edgeConditionInvalid() {
        for (String condition : List.of("steps.a.executeStatus", "1 / 0")) {
            var edges = List.of(new ExecutionFlow.Edge("x", condition, 1), new ExecutionFlow.Edge("z", null, Long.MAX_VALUE));

            assertThrows(ExecutionErrorException.class, () -> engine.nextTarget(edges, step(SUCCESS)), condition);
        }
    }

    /** SUCCESS/FAIL 出口按输出结构组装嵌套对象，非必填 nil 省略；响应字段可带 outputs. 前缀。 */
    @Test
    void test_returnMapping() {
        var schema = FieldSchema.outputs(List.of(field("outputs", ApiFieldDataType.OBJECT, true),
            field("outputs.paymentStatus", ApiFieldDataType.STRING, true),
            field("outputs.transactionRef", ApiFieldDataType.STRING, false),
            field("outputs.detail", ApiFieldDataType.OBJECT, false),
            field("outputs.detail.code", ApiFieldDataType.STRING, true)));

        var success = engine.mapReturn(exit(SUCCESS, """
            {"paymentStatus":{"kind":"EXPRESSION","expression":"steps.a.outputs.state == 'CAPTURED' ? 'SUCCEEDED' : 'PROCESSING'"},
             "transactionRef":{"kind":"EXPRESSION","expression":"nil"},
             "detail.code":{"kind":"PATH","path":"steps.a.outputs.code"}}"""), schema, step(SUCCESS));
        var failed = engine.mapReturn(exit(FAIL, "{\"paymentStatus\":{\"kind\":\"EXPRESSION\",\"expression\":\"'FAILED'\"}}"),
            schema, step(FAIL));

        assertResult(ExecuteStatus.SUCCESS, Map.of("paymentStatus", "SUCCEEDED", "detail", Map.of("code", "C1")), success);
        assertResult(ExecuteStatus.FAIL, Map.of("paymentStatus", "FAILED"), failed);
    }

    /** ERROR 出口输出空对象；必填输出缺失或类型不符时 SUCCESS 出口也改为 ERROR。 */
    @Test
    void test_returnFallsBackToError() {
        var schema = FieldSchema.outputs(List.of(field("paymentStatus", ApiFieldDataType.STRING, true)));

        assertResult(ExecuteStatus.ERROR, Map.of(), engine.mapReturn(new ExecutionFlow.Return("e", ExecuteStatus.ERROR,
            ExternalApiSamples.object("{}")), schema, step(SUCCESS)));
        assertResult(ExecuteStatus.ERROR, Map.of(), engine.mapReturn(exit(SUCCESS,
            "{\"paymentStatus\":{\"kind\":\"PATH\",\"path\":\"steps.a.outputs.missing\"}}"), schema, step(SUCCESS)));
        assertResult(ExecuteStatus.ERROR, Map.of(), engine.mapReturn(exit(SUCCESS,
            "{\"paymentStatus\":{\"kind\":\"EXPRESSION\",\"expression\":\"1\"}}"), schema, step(SUCCESS)));
    }

    /** 节点 a 的结果：状态与固定输出。 */
    private static Map<String, Object> step(String status) {
        return Map.of("steps", Map.of("a", Map.of("executeStatus", status,
            "outputs", Map.of("state", "CAPTURED", "code", "C1"))));
    }

    private static ExecutionFlow.Return exit(String status, String outputs) {
        return new ExecutionFlow.Return("r", ExecuteStatus.fromCode(status), ExternalApiSamples.object(outputs));
    }

    private static void assertResult(ExecuteStatus status, Map<String, Object> outputs, ExecutionResult result) {
        assertAll(() -> assertEquals(status, result.getExecuteStatus()), () -> assertEquals(outputs, result.getOutputs()));
    }

    private static FieldDefinition field(String path, ApiFieldDataType type, boolean required) {
        return new FieldDefinition(ApiFieldLocation.BODY, path, path, type, required, null);
    }
}

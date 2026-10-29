package com.paytech.platform.support.workflow.auto.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryOutput;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;
import com.paytech.platform.support.workflow.domainservice.process.DataQueryParameterResolver;
import com.paytech.platform.support.workflow.domainservice.process.DataQueryResultReader;
import com.paytech.platform.support.workflow.domainservice.validator.ActionConfigValidator;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * DATA_QUERY 的纯规则（REQ-05）：单行约定解读与输出规整、参数取值、动作模板配置校验。
 *
 * @see DataQueryResultReader#read(List, List, boolean, List)
 * @see DataQueryParameterResolver#resolve(Map, WorkflowVariables)
 * @see ActionConfigValidator#validate(com.paytech.platform.support.workflow.domain.action.ActionConfig)
 */
class WorkflowDataQueryDomainTest {

    private static final List<DataQueryOutput> OUTPUTS = List.of(
        new DataQueryOutput("mode", FormFieldType.ENUM, List.of("MOR", "PSP")),
        new DataQueryOutput("ready", FormFieldType.BOOLEAN, List.of()),
        new DataQueryOutput("amount", FormFieldType.NUMBER, List.of()),
        new DataQueryOutput("codes", FormFieldType.TEXT, List.of()),
        new DataQueryOutput("since", FormFieldType.DATE, List.of()));

    private static final List<String> COLUMNS = List.of("SATISFIED", "missing", "out_mode", "out_ready", "out_amount",
        "out_codes", "out_since", "out_extra");

    private final DataQueryResultReader reader = new DataQueryResultReader();

    /** 满足：列名不区分大小写，按声明类型规整，未声明的 out_ 列不写入（B5 / AC-10）。 */
    @Test
    void test_satisfiedNormalizesDeclaredOutputs() {
        DataQueryCheckOutcome outcome = reader.read(COLUMNS, List.of(row("1", null, "PSP", "0", "12.50", "A,B",
            "2026-10-29", "x")), false, OUTPUTS);

        ObjectNode expected = JsonNodeFactory.instance.objectNode().put("mode", "PSP").put("ready", false)
            .put("amount", "12.50").put("codes", "A,B").put("since", "2026-10-29");
        Assertions.assertEquals(DataQueryCheckResult.SATISFIED, outcome.result());
        Assertions.assertEquals(expected, outcome.outputs());
    }

    /** 0 行与 satisfied=false 都是未满足；missing 逐项去空白。 */
    @Test
    void test_unsatisfied() {
        Assertions.assertEquals(DataQueryCheckOutcome.unsatisfied(List.of()), reader.read(COLUMNS, List.of(), false,
            OUTPUTS));
        DataQueryCheckOutcome outcome = reader.read(List.of("satisfied", "missing"),
            List.of(row(false, " TERM_NOT_ACTIVE, ,CLIENT_NOT_FOUND ")), false, OUTPUTS);
        Assertions.assertEquals(DataQueryCheckOutcome.unsatisfied(List.of("TERM_NOT_ACTIVE", "CLIENT_NOT_FOUND")),
            outcome);
    }

    /** 形状不符：多行、还有更多、缺 satisfied、取值非法、原因码非法、输出缺列或类型不符。 */
    @Test
    void test_shapeInvalid() {
        DataQueryCheckOutcome invalid = DataQueryCheckOutcome.failed(DataQueryCheckFailure.RESULT_SHAPE_INVALID);
        List<DataQueryOutput> none = List.of();
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied"), List.of(row("1"), row("1")), false, none));
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied"), List.of(row("1")), true, none));
        Assertions.assertEquals(invalid, reader.read(List.of("ok"), List.of(row("1")), false, none));
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied"), List.of(row((Object) null)), false, none));
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied"), List.of(row("yes")), false, none));
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied", "missing"), List.of(row("0", "bad code")),
            false, none));
        Assertions.assertEquals(invalid, reader.read(List.of("satisfied"), List.of(row("1")), false, OUTPUTS));
        Assertions.assertEquals(invalid, reader.read(COLUMNS, List.of(row("1", null, "OTHER", "1", "1", "a",
            "2026-10-29", null)), false, OUTPUTS));
        Assertions.assertEquals(invalid, reader.read(COLUMNS, List.of(row("1", null, "MOR", "1", "1e3", "a",
            "2026-10-29", null)), false, OUTPUTS));
        Assertions.assertEquals(invalid, reader.read(COLUMNS, List.of(row("1", null, "MOR", "1", "1", "a",
            "2026-02-30", null)), false, OUTPUTS));
    }

    /** 参数：VARIABLE 取变量当前值，缺失为 null；CONSTANT 取文本。 */
    @Test
    void test_parameterResolver() {
        WorkflowVariables variables = WorkflowVariables.builder().add(
            List.of(VariableDeclaration.of("start.clientId", FormFieldType.TEXT)),
            JsonNodeFactory.instance.objectNode().put("clientId", "1001")).build();
        Map<String, InputMapping> inputs = new LinkedHashMap<>();
        inputs.put("clientId", new InputMapping(InputMappingSource.VARIABLE, "start.clientId"));
        inputs.put("agreementId", new InputMapping(InputMappingSource.VARIABLE, "n3.agreement_id"));
        inputs.put("mode", new InputMapping(InputMappingSource.CONSTANT, "MOR"));

        ObjectNode params = new DataQueryParameterResolver().resolve(inputs, variables);

        ObjectNode expected = JsonNodeFactory.instance.objectNode().put("clientId", "1001").put("mode", "MOR");
        expected.putNull("agreementId");
        Assertions.assertEquals(expected, params);
    }

    /** 配置校验：编码、参数、输出名与类型、ENUM 可选值、文案键与说明上限。 */
    @Test
    void test_configValidation() {
        ActionConfigValidator validator = new ActionConfigValidator();
        ProblemChecker.of(validator.validate(new DataQueryActionConfig("GOLIVE_OK", List.of("clientId"), OUTPUTS,
            "workflow.dataQuery.criteria.GOLIVE_OK", "说明"))).none();
        ProblemChecker.of(validator.validate(new DataQueryActionConfig("bad", List.of("clientId", "clientId"),
            List.of(new DataQueryOutput("Mode", FormFieldType.ENUM, List.of()),
                new DataQueryOutput("mode", FormFieldType.FILE, List.of("X"))),
            "Bad Key", "x".repeat(2001))))
            .item(WorkflowProblemCode.ACTION_CONFIG_INVALID, "clientId")
            .item(WorkflowProblemCode.ACTION_CONFIG_INVALID, "Mode")
            .item(WorkflowProblemCode.ACTION_CONFIG_INVALID, "mode")
            .exactly(WorkflowProblemCode.ACTION_CONFIG_INVALID);
    }

    /** 构造一行结果值（允许 null）。 */
    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}

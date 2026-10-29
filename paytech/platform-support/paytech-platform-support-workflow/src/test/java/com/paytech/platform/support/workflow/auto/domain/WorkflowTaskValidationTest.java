package com.paytech.platform.support.workflow.auto.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.validation.DataQueryTemplateFact;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.PublicationInputBuilder;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * 发布校验 B1 任务节点：审批人或动作、人员 ACTIVE、动作 key、模板状态与类型、输入映射、动作上限（技术方案 3.4 第 5、6 项）。
 *
 * @see WorkflowGraphValidator#validate(com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput)
 */
class WorkflowTaskValidationTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    /** 以给定的 CHANNEL_READY 事实校验；fact 为 null 表示 data-query 模板不存在。 */
    private List<WorkflowGraphProblem> dataQuery(WorkflowGraph graph, DataQueryTemplateFact fact) {
        return validator.validate(PublicationInputBuilder.of(graph).dataQueryTemplate(WorkflowFixtures.QUERY_CODE, fact)
            .build());
    }

    @Test
    void test_normalFlow() {
        ProblemChecker.of(validate(single(task -> task
            .action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A)
            .optionalAction("files", WorkflowFixtures.TPL_UPLOAD, WorkflowFixtures.EXECUTOR_B)
            .approvers(ApprovalMode.ALL, WorkflowFixtures.APPROVER_A, WorkflowFixtures.APPROVER_B)))).none();
    }

    @Test
    void test_taskWithoutApproverOrAction() {
        ProblemChecker.of(validate(single(task -> {
        }))).exactly(WorkflowProblemCode.TASK_EMPTY);
    }

    @Test
    void test_approvalRules() {
        ProblemChecker.of(validate(single(task -> task.approvers(null, WorkflowFixtures.APPROVER_A))))
            .exactly(WorkflowProblemCode.APPROVAL_MODE_MISSING);
        ProblemChecker.of(validate(single(task -> task.approvers(ApprovalMode.ANY)
            .action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A))))
            .exactly(WorkflowProblemCode.APPROVER_MISSING);
        ProblemChecker.of(validate(single(task -> task.approvers(ApprovalMode.ALL, WorkflowFixtures.APPROVER_A,
                WorkflowFixtures.APPROVER_A, WorkflowFixtures.DISABLED_ACCOUNT))))
            .item(WorkflowProblemCode.APPROVER_DUPLICATE, Long.toString(WorkflowFixtures.APPROVER_A))
            .item(WorkflowProblemCode.APPROVER_INACTIVE, Long.toString(WorkflowFixtures.DISABLED_ACCOUNT));
    }

    @Test
    void test_executorRules() {
        ProblemChecker.of(validate(single(task -> task.action("confirm", WorkflowFixtures.TPL_CONFIRM, null))))
            .exactly(WorkflowProblemCode.EXECUTOR_MISSING);
        ProblemChecker.of(validate(single(task -> task.action("confirm", WorkflowFixtures.TPL_CONFIRM,
            WorkflowFixtures.DISABLED_ACCOUNT)))).exactly(WorkflowProblemCode.EXECUTOR_INACTIVE);
    }

    @Test
    void test_actionKeys() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("t1", task -> task.action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A)
                .action("start", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A)
                .action("1bad", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A))
            .task("t2", task -> task.action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A))
            .end("end").chain("start", "t1", "t2", "end").build();
        ProblemChecker.of(validate(graph))
            .item(WorkflowProblemCode.ACTION_KEY_RESERVED, "start")
            .item(WorkflowProblemCode.ACTION_KEY_INVALID, "1bad")
            .item(WorkflowProblemCode.ACTION_KEY_DUPLICATE, "confirm");
    }

    @Test
    void test_templateRules() {
        ProblemChecker.of(validate(single(task -> task.action("x", WorkflowFixtures.TPL_INACTIVE,
            WorkflowFixtures.EXECUTOR_A)))).exactly(WorkflowProblemCode.TEMPLATE_INACTIVE);
        ProblemChecker.of(validate(single(task -> task.action("x", null, WorkflowFixtures.EXECUTOR_A))))
            .exactly(WorkflowProblemCode.TEMPLATE_NOT_FOUND);
        WorkflowGraph graph = single(task -> task.action("x", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A));
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(graph)
            .withoutTemplate(WorkflowFixtures.TPL_CONFIRM).build())).exactly(WorkflowProblemCode.TEMPLATE_NOT_FOUND);
    }

    /** DATA_QUERY 可发布；必填查询参数须映射，模板配置须与发布时读取的 data-query 模板事实一致（REQ-05 B7）。 */
    @Test
    void test_dataQueryRules() {
        Map<String, InputMapping> mapped = Map.of("channelCode", new InputMapping(InputMappingSource.CONSTANT, "GEIDEA"));
        WorkflowGraph graph = single(task -> task.actionWithInputs("q", WorkflowFixtures.TPL_DATA_QUERY, mapped));
        ProblemChecker.of(validate(graph)).none();
        ProblemChecker.of(validate(single(task -> task.action("q", WorkflowFixtures.TPL_DATA_QUERY,
            WorkflowFixtures.EXECUTOR_A)))).item(WorkflowProblemCode.DATA_QUERY_INPUT_MISSING, "q")
            .exactly(WorkflowProblemCode.DATA_QUERY_INPUT_MISSING);
        ProblemChecker.of(dataQuery(graph, null)).exactly(WorkflowProblemCode.DATA_QUERY_TEMPLATE_NOT_FOUND);
        ProblemChecker.of(dataQuery(graph, WorkflowFixtures.dataQueryTemplate(false, Map.of("channelCode", true),
            Set.of(), Set.of()))).exactly(WorkflowProblemCode.DATA_QUERY_TEMPLATE_INACTIVE);
        ProblemChecker.of(dataQuery(graph, WorkflowFixtures.dataQueryTemplate(true, Map.of("clientId", true),
            Set.of(), Set.of()))).exactly(WorkflowProblemCode.DATA_QUERY_PARAMETER_UNDECLARED,
                WorkflowProblemCode.DATA_QUERY_PARAMETER_MISSING);
        ProblemChecker.of(dataQuery(graph, WorkflowFixtures.dataQueryTemplate(true, Map.of("channelCode", true),
            Set.of("satisfied", "missing"), Set.of("missing"))))
            .exactly(WorkflowProblemCode.DATA_QUERY_OUTPUT_UNDECLARED, WorkflowProblemCode.DATA_QUERY_OUTPUT_MASKED);
    }

    @Test
    void test_inputMappings() {
        Map<String, InputMapping> inputs = new LinkedHashMap<>();
        inputs.put("channelCode", new InputMapping(InputMappingSource.CONSTANT, "GEIDEA_SA"));
        ProblemChecker.of(validate(single(task -> task.actionWithInputs("page", WorkflowFixtures.TPL_PAGE, inputs))))
            .none();
        Map<String, InputMapping> undeclared = Map.of("merchantId", new InputMapping(InputMappingSource.CONSTANT, "1"));
        ProblemChecker.of(validate(single(task -> task.actionWithInputs("page", WorkflowFixtures.TPL_PAGE, undeclared))))
            .exactly(WorkflowProblemCode.INPUT_KEY_UNDECLARED);
        Map<String, InputMapping> confirmInput = Map.of("note", new InputMapping(InputMappingSource.CONSTANT, "x"));
        ProblemChecker.of(validate(single(task -> task.actionWithInputs("c", WorkflowFixtures.TPL_CONFIRM,
            confirmInput)))).exactly(WorkflowProblemCode.INPUT_KEY_UNDECLARED);
        Map<String, InputMapping> blank = new LinkedHashMap<>();
        blank.put("channelCode", new InputMapping(InputMappingSource.VARIABLE, " "));
        ProblemChecker.of(validate(single(task -> task.actionWithInputs("page", WorkflowFixtures.TPL_PAGE, blank))))
            .exactly(WorkflowProblemCode.INPUT_VALUE_INVALID);
        Map<String, InputMapping> formDefault = Map.of("riskLevel", new InputMapping(InputMappingSource.CONSTANT, null));
        ProblemChecker.of(validate(single(task -> task.actionWithInputs("form", WorkflowFixtures.TPL_FORM,
            formDefault)))).exactly(WorkflowProblemCode.INPUT_VALUE_INVALID);
    }

    @Test
    void test_tooManyActions() {
        ProblemChecker.of(validate(single(task -> addActions(task, 20)))).none();
        ProblemChecker.of(validate(single(task -> addActions(task, 21)))).exactly(WorkflowProblemCode.TOO_MANY_ACTIONS);
    }

    /**
     * 追加若干人工确认动作。
     *
     * @param task 节点配置
     * @param count 数量
     */
    private void addActions(WorkflowGraphBuilder.TaskSpec task, int count) {
        for (int index = 0; index < count; index++) {
            task.action("confirm" + index, WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A);
        }
    }

    /**
     * start → task → end，只有一个待测任务节点。
     *
     * @param spec 任务节点配置
     * @return 流程图
     */
    private WorkflowGraph single(Consumer<WorkflowGraphBuilder.TaskSpec> spec) {
        return WorkflowGraphBuilder.graph().start("start").task("task", spec).end("end").chain("start", "task", "end")
            .build();
    }

    /**
     * 以默认输入校验。
     *
     * @param graph 流程图
     * @return 问题
     */
    private List<WorkflowGraphProblem> validate(WorkflowGraph graph) {
        return validator.validate(PublicationInputBuilder.of(graph).build());
    }
}

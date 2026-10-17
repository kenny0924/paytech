package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.LEGACY_AUTHORING_NOT_SUPPORTED;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.VARIABLE_NOT_FOUND;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.RETIRED;
import static com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException.Reason.REFERENCE_UNAVAILABLE;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionFlowManagementApi;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.SaveFlowRequestBuilder;
import com.paytech.risk.test.checker.decision.FlowStoreChecker;
import com.paytech.risk.test.checker.decision.ValidateFlowResponseChecker;
import com.paytech.risk.test.checker.decision.ValidateFlowResponseChecker.ExpectedIssue;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 流程只读静态校验
 * <li>正常流程：合法新建草稿、基于当前版本的编辑与保留的已退休引用/停用原因均返回 valid=true，不写入任何流程数据。</li>
 * <li>图问题：结构、文本、节点/连线配置、出口顺序、端点、环与可达性问题以 valid=false 逐项定位，超过 100 项截断。</li>
 * <li>目录问题：规则版本/拒绝原因资格、编辑基准冲突、旧格式条件与未知变量同样作为问题报告。</li>
 * <li>未找到：编辑基准指向不存在的流程编码作为编辑基准冲突报告（valid=false），不返回 404。</li>
 * <li>参数：传输格式错误（未知/缺失键、类型、媒体类型、Query）返回 400/415，超出数组容量返回 422。</li>
 * <li>业务规则：决策点停用时不可配置（400）。</li>
 *
 * @see DecisionFlowManagementApi#validate(String, String, com.paytech.risk.api.decision.request.SaveFlowRequestBody)
 */
class FlowValidateApiAutoTest extends MockTestBase {
    /** 新保存的流程使用结账当前输入合同，只能引用同合同的规则集版本。 */
    private static final int CURRENT_CONTRACT = RuleSetBuilder.CURRENT_CONTRACT_VERSION;

    private static final String PATH = "/decision-flows/v1/flows/validate";

    private static final String TEXT_MESSAGE = "文本不能为空、首尾不能有空白且须满足长度和内容约束";

    private static final String REFERENCE_MESSAGE = "规则版本已删除、已退休或不属于当前输入合同，请重新选择";

    private static final String REASON_MESSAGE = "拒绝原因未登记或已停用，请重新选择";

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private FlowStoreChecker store;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 合法的新建草稿通过校验，不创建流程。 */
    @Test
    void test_normalFlow_create() {
        String code = flows.newFlowCode();

        validate(SaveFlowRequestBuilder.template(code)).assertValid();

        store.assertNoFlow(code, 0, 0);
    }

    /** 引用规则集与统一变量条件、基于当前版本的编辑均通过，原流程不变。 */
    @Test
    void test_normalFlow_editWithReferencesAndCondition() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT).variableReference());
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).inputContractVersion(CURRENT_CONTRACT));

        validate(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version())
            .graph(GraphSpec.branching(ruleSet.version(1).ruleSetVersionId()))).assertValid();

        store.assertUnchanged(flow);
    }

    /** 基准版本已引用的退休规则版本与停用原因（文案不变）可继续保留。 */
    @Test
    void test_normalFlow_retainedReferences() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(RETIRED, FROZEN).variableReference());
        String reason = flows.mockReason(DictionaryStatus.DISABLED);
        GraphSpec graph = ruleThenDeny(ruleSet.version(1).ruleSetVersionId(), reason, "Retained message");
        MockedFlow flow = flows.mock(FlowBuilder.versions(graph).inputContractVersion(CURRENT_CONTRACT));

        validate(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version())
            .graph(graph)).assertValid();
    }

    /** 结构、文本与图规则问题逐项定位，不查询目录。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("graphIssues")
    void test_businessRuleViolation_graphIssues(String scenario, Consumer<ObjectNode> mutation,
        ExpectedIssue[] expected) {
        ObjectNode body = tree(SaveFlowRequestBuilder.template(flows.newFlowCode()));
        mutation.accept(body);

        validate(body.toString()).assertIssues(expected);
    }

    /** 分支出口与条件问题逐项定位。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("exitIssues")
    void test_businessRuleViolation_exitIssues(String scenario, GraphSpec graph, ExpectedIssue[] expected) {
        validate(SaveFlowRequestBuilder.template(flows.newFlowCode()).graph(graph)).assertIssues(expected);
    }

    /** 新选择的规则版本：已退休、不存在、所属规则集已删除或输入合同不符均按节点位置报告。 */
    @Test
    void test_businessRuleViolation_referenceUnavailable() {
        MockedRuleSet retired = ruleSets.mock(versions(RETIRED, FROZEN).variableReference());
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN).variableReference().deleted());
        MockedRuleSet mismatched = ruleSets.mock(versions(DRAFT).variableReference());
        MockedRuleSet usable = ruleSets.mock(versions(FROZEN).variableReference());
        flowTestMapper.updateRuleSetContract(mismatched.currentVersionId(), RuleSetBuilder.UNREGISTERED_CONTRACT_VERSION);
        GraphSpec graph = GraphSpec.through(retired.version(1).ruleSetVersionId(), 999_999_999_999_999_999L,
            deleted.currentVersionId(), mismatched.currentVersionId(), usable.currentVersionId());

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode()).graph(graph)).assertIssues(
            reference("rules_1", 1), reference("rules_2", 2), reference("rules_3", 3), reference("rules_4", 4));
    }

    /** 新选择的拒绝原因未登记或已停用；保留的停用原因改了文案也视为新选择。 */
    @Test
    void test_businessRuleViolation_reasonUnavailable() {
        String disabled = flows.mockReason(DictionaryStatus.DISABLED);
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.deny(disabled, "Original message")).inputContractVersion(CURRENT_CONTRACT));

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode()).graph(GraphSpec.deny(disabled, "Message")))
            .assertIssues(reason());
        validate(SaveFlowRequestBuilder.template(flows.newFlowCode())
            .graph(GraphSpec.deny("MOCK_UNKNOWN_REASON", "Message"))).assertIssues(reason());
        validate(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version())
            .graph(GraphSpec.deny(disabled, "Changed message"))).assertIssues(reason());
    }

    /** 已启用的拒绝原因可作为新选择。 */
    @Test
    void test_normalFlow_activeReason() {
        String active = flows.mockReason(DictionaryStatus.ACTIVE);

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode()).graph(GraphSpec.deny(active, "Message")))
            .assertValid();
    }

    /** 编辑基准冲突：新建已存在（含已删除）的编码、过期修订号或基准版本、已删除流程。 */
    @Test
    void test_businessRuleViolation_baseConflict() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).inputContractVersion(CURRENT_CONTRACT));
        MockedFlow deleted = flows.mock(FlowBuilder.versions(1).deleted().inputContractVersion(CURRENT_CONTRACT));
        String exists = "流程编码已存在，删除后也不能复用";
        String stale = "流程当前版本已变化，请重新载入后编辑";

        validate(SaveFlowRequestBuilder.template(flow.flowCode())).assertIssues(base(exists));
        validate(SaveFlowRequestBuilder.template(deleted.flowCode())).assertIssues(base(exists));
        validate(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), 1))
            .assertIssues(base(stale));
        validate(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.version(1).flowVersionId(), 2))
            .assertIssues(base(stale));
        validate(SaveFlowRequestBuilder.template(deleted.flowCode()).base(deleted.current().flowVersionId(),
            deleted.version())).assertIssues(base("流程已删除"));
        store.assertUnchanged(flow);
        store.assertUnchanged(deleted);
    }

    /** 旧 schemaVersion=1 条件结构合法，但不能作为新内容保存。 */
    @Test
    void test_businessRuleViolation_legacyCondition() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode())
            .graph(GraphSpec.branching(ruleSet.currentVersionId(), FlowBuilder.LEGACY_MATCHED_CONDITION_JSON)))
            .assertIssues(new ExpectedIssue(LEGACY_AUTHORING_NOT_SUPPORTED.getCode(), null, "matched",
                "graph.edges[1].condition", "请转换为统一变量条件后保存"));
    }

    /** 条件引用不存在的变量时报告目录问题。 */
    @Test
    void test_businessRuleViolation_unknownVariable() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode()).graph(GraphSpec.branching(
            ruleSet.currentVersionId(), FlowBuilder.variableCondition("MOCK_UNKNOWN_VARIABLE"))))
            .assertIssues(new ExpectedIssue(VARIABLE_NOT_FOUND.getCode(), null, null, "graph",
                "变量不存在：MOCK_UNKNOWN_VARIABLE"));
    }

    /** 问题超过 100 项时只返回前 100 项并标记截断。 */
    @Test
    void test_edgeCase_issuesTruncated() {
        ObjectNode body = tree(SaveFlowRequestBuilder.template(flows.newFlowCode()));
        ArrayNode nodes = body.withArray("/graph/nodes");
        ObjectNode template = (ObjectNode) nodes.get(1);
        nodes.removeAll();
        for (int i = 0; i < 50; i++) {
            nodes.add(template.deepCopy().put("nodeKey", i + "bad").put("type", "GATEWAY").put("name", " "));
        }
        body.withArray("/graph/edges").removeAll();

        validate(body.toString()).assertTruncated("INVALID_NODE_KEY", "INVALID_TEXT", "INVALID_NODE_TYPE");
    }

    /** 编辑基准指向不存在的流程编码，与其他基准冲突一样报告问题且不创建流程。 */
    @Test
    void test_noDataFound() {
        String code = flows.newFlowCode();

        validate(SaveFlowRequestBuilder.template(code).base(999_999_999_999_999_999L, 1))
            .assertIssues(base("编辑的流程不存在，请重新载入后编辑"));

        store.assertNoFlow(code, 0, 0);
    }

    /** 传输格式错误在业务校验前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("transportErrors")
    void test_invalidParameter(String scenario, Consumer<ObjectNode> mutation) {
        ObjectNode body = tree(SaveFlowRequestBuilder.template(flows.newFlowCode()));
        mutation.accept(body);

        validate(body.toString()).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 节点数超过 50 时由请求校验拒绝。 */
    @Test
    void test_invalidParameter_tooManyNodes() {
        ObjectNode body = tree(SaveFlowRequestBuilder.template(flows.newFlowCode()));
        ArrayNode nodes = body.withArray("/graph/nodes");
        for (int i = 0; i < 49; i++) {
            nodes.add(((ObjectNode) nodes.get(1)).deepCopy().put("nodeKey", "extra_" + i));
        }

        validate(body.toString()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("graph.nodes");
    }

    /** 非 JSON 媒体类型与携带 Query 参数的请求。 */
    @Test
    void test_invalidParameter_mediaTypeAndQuery() {
        String body = tree(SaveFlowRequestBuilder.template(flows.newFlowCode())).toString();
        HttpHeaders text = headers(nextRequestId());
        text.setContentType(MediaType.TEXT_PLAIN);

        invoke(PATH, text, body).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());
        invoke(PATH + "?dryRun=true", headers(nextRequestId()), body).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 决策点停用时不可配置。 */
    @Test
    void test_businessRuleViolation_inactivePoint() {
        flows.deactivatePoint();

        validate(SaveFlowRequestBuilder.template(flows.newFlowCode())).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> graphIssues() {
        return Stream.of(
            issues("结束节点缺少决定", b -> node(b, 1).putNull("decisionCode"),
                issue("END_DECISION_REQUIRED", "finish", null, "graph.nodes[1].decisionCode", "请选择结束决定")),
            issues("拒绝缺少原因", b -> node(b, 1).put("decisionCode", "DENY"),
                issue("REASON_REQUIRED", "finish", null, "graph.nodes[1].reasonCode", "拒绝必须配置原因和文案")),
            issues("非拒绝携带原因", b -> node(b, 1).put("reasonCode", "MOCK_REASON").put("reasonMessage", "m"),
                issue("INVALID_NODE_CONFIG", "finish", null, "graph.nodes[1]", "非拒绝结束节点不能保存拒绝原因")),
            issues("开始节点配置规则版本", b -> node(b, 0).put("ruleSetVersionId", "1"),
                issue("INVALID_NODE_CONFIG", "start", null, "graph.nodes[0]", "开始节点不能配置规则或决定")),
            issues("结束节点引用规则版本", b -> node(b, 1).put("ruleSetVersionId", "1"),
                issue("INVALID_NODE_CONFIG", "finish", null, "graph.nodes[1]", "结束节点不能引用规则版本")),
            issues("规则集节点缺少规则版本", b -> {
                node(b, 1).put("nodeKey", "rules").put("type", "RULE_SET").putNull("decisionCode");
                b.withArray("/graph/nodes").add(node(b, 1).deepCopy().put("nodeKey", "finish").put("type", "END")
                    .put("decisionCode", "ALLOW"));
                edge(b, 0).put("targetNodeKey", "rules");
                b.withArray("/graph/edges").add(edge(b, 0).deepCopy().put("edgeKey", "done")
                    .put("sourceNodeKey", "rules").put("targetNodeKey", "finish"));
            }, issue("RULE_SET_REQUIRED", "rules", null, "graph.nodes[1].ruleSetVersionId", "请选择准确规则版本")),
            issues("节点类型不支持", b -> node(b, 1).put("type", "GATEWAY"),
                issue("INVALID_NODE_TYPE", "finish", null, "graph.nodes[1].type", "节点类型不支持"),
                issue("MISSING_END", null, null, "graph.nodes", "请添加结束节点")),
            issues("节点键格式无效", b -> {
                node(b, 1).put("nodeKey", "1finish");
                edge(b, 0).put("targetNodeKey", "1finish");
            }, issue("INVALID_NODE_KEY", "1finish", null, "graph.nodes[1].nodeKey", "节点键重复或格式无效"),
                issue("MISSING_END", null, null, "graph.nodes", "请添加结束节点"),
                issue("MISSING_ENDPOINT", null, "next", "graph.edges[0]", "连线端点不存在"),
                issue("START_EXIT_REQUIRED", "start", null, "graph.edges", "开始节点需要一条无条件出口")),
            issues("节点键重复", b -> b.withArray("/graph/nodes").add(node(b, 1).deepCopy()),
                issue("INVALID_NODE_KEY", "finish", null, "graph.nodes[2].nodeKey", "节点键重复或格式无效")),
            issues("节点名称首尾空白", b -> node(b, 1).put("name", " Finish"),
                issue("INVALID_TEXT", "finish", null, "graph.nodes[1].name", TEXT_MESSAGE)),
            issues("拒绝文案超过 500 字符", b -> node(b, 1).put("decisionCode", "DENY").put("reasonCode", "MOCK_REASON")
                .put("reasonMessage", "m".repeat(501)),
                issue("INVALID_TEXT", "finish", null, "graph.nodes[1].reasonMessage", TEXT_MESSAGE)),
            issues("流程名称超过 100 字符", b -> b.put("flowName", "n".repeat(101)),
                issue("INVALID_TEXT", null, null, "flowName", TEXT_MESSAGE)),
            issues("变更说明为空", b -> b.put("changeReason", ""),
                issue("INVALID_TEXT", null, null, "changeReason", TEXT_MESSAGE)),
            issues("变更说明超过 500 字符", b -> b.put("changeReason", "r".repeat(501)),
                issue("INVALID_TEXT", null, null, "changeReason", TEXT_MESSAGE)),
            issues("决策点不支持", b -> b.put("pointCode", "PAYOUT_CREATION"),
                issue("INVALID_POINT", null, null, "pointCode", "当前决策点不支持流程配置")),
            issues("流程编码格式无效", b -> b.put("flowCode", "mock_flow"),
                issue("INVALID_CODE", null, null, "flowCode", "流程编码格式无效")),
            issues("编辑基准只给出版本", b -> b.put("baseFlowVersionId", "1"),
                issue("BASE_CONFLICT", null, null, "baseFlowVersionId", "编辑基准必须同时提供")),
            issues("编辑基准只给出修订号", b -> b.put("expectedVersion", "1"),
                issue("BASE_CONFLICT", null, null, "baseFlowVersionId", "编辑基准必须同时提供")),
            issues("缺少开始节点", b -> {
                b.withArray("/graph/nodes").remove(0);
                b.withArray("/graph/edges").removeAll();
            }, issue("MISSING_START", null, null, "graph.nodes", "流程必须恰有一个开始节点")),
            issues("两个开始节点", b -> b.withArray("/graph/nodes").add(node(b, 0).deepCopy().put("nodeKey", "start2")),
                issue("MISSING_START", null, null, "graph.nodes", "流程必须恰有一个开始节点"),
                issue("START_EXIT_REQUIRED", "start2", null, "graph.edges", "开始节点需要一条无条件出口")),
            issues("连线端点不存在", b -> edge(b, 0).put("targetNodeKey", "ghost"),
                issue("MISSING_ENDPOINT", null, "next", "graph.edges[0]", "连线端点不存在"),
                issue("START_EXIT_REQUIRED", "start", null, "graph.edges", "开始节点需要一条无条件出口")),
            issues("连线键格式无效", b -> edge(b, 0).put("edgeKey", "1next"),
                issue("INVALID_EDGE_KEY", null, "1next", "graph.edges[0].edgeKey", "连线键重复或格式无效")),
            issues("连线名称为空", b -> edge(b, 0).put("name", ""),
                issue("INVALID_TEXT", null, "next", "graph.edges[0].name", TEXT_MESSAGE)),
            issues("非条件连线带顺序", b -> edge(b, 0).put("priority", 1),
                issue("INVALID_EDGE_CONFIG", null, "next", "graph.edges[0]", "非条件连线的顺序须为零且不能携带条件")),
            issues("连线类型不支持", b -> edge(b, 0).put("type", "JUMP"),
                issue("INVALID_EDGE_CONFIG", null, "next", "graph.edges[0]", "非条件连线的顺序须为零且不能携带条件"),
                issue("START_EXIT_REQUIRED", "start", null, "graph.edges", "开始节点需要一条无条件出口")),
            issues("结束节点出线并指回开始", b -> b.withArray("/graph/edges").add(edge(b, 0).deepCopy()
                .put("edgeKey", "back").put("sourceNodeKey", "finish").put("targetNodeKey", "start")),
                issue("INVALID_ENDPOINT", "finish", "back", "graph.edges[1]", "结束不能出线，开始不能入线"),
                issue("CYCLE", null, null, "graph.edges", "流程不能包含循环")),
            issues("不可达节点", b -> b.withArray("/graph/nodes").add(node(b, 1).deepCopy().put("nodeKey", "orphan")),
                issue("UNREACHABLE_NODE", "orphan", null, "graph.nodes", "节点无法从开始到达")));
    }

    static Stream<Arguments> exitIssues() {
        NodeSpec start = new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null);
        NodeSpec rules = new NodeSpec("rules", FlowNodeType.RULE_SET, "Rules", 120, 0, 1L, null, null, null);
        NodeSpec review = new NodeSpec("review", FlowNodeType.END, "Review", 240, 0, null, DecisionValue.REVIEW, null,
            null);
        NodeSpec finish = new NodeSpec("finish", FlowNodeType.END, "Finish", 240, 60, null, DecisionValue.ALLOW, null,
            null);
        EdgeSpec enter = new EdgeSpec("enter", "start", "rules", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null);
        EdgeSpec matched = new EdgeSpec("matched", "rules", "review", FlowEdgeType.CONDITIONAL, 1, "Matched",
            FlowBuilder.MATCHED_CONDITION_JSON);
        EdgeSpec otherwise = new EdgeSpec("otherwise", "rules", "finish", FlowEdgeType.ELSE, 0, "Otherwise", null);
        List<NodeSpec> nodes = List.of(start, rules, review, finish);
        return Stream.of(
            Arguments.of("条件分流缺少其他情况", new GraphSpec(List.of(start, rules, review), List.of(enter, matched)),
                new ExpectedIssue[] {issue("ELSE_REQUIRED", "rules", null, "graph.edges", "条件分流必须有一个其他情况出口")}),
            Arguments.of("条件顺序重复", new GraphSpec(nodes, List.of(enter, matched, otherwise,
                new EdgeSpec("matched_again", "rules", "finish", FlowEdgeType.CONDITIONAL, 1, "Again",
                    FlowBuilder.MATCHED_CONDITION_JSON))),
                new ExpectedIssue[] {issue("DUPLICATE_PRIORITY", "rules", null, "graph.edges",
                    "条件顺序必须从一连续排列且不能重复")}),
            Arguments.of("条件顺序不从一开始", new GraphSpec(nodes, List.of(enter, new EdgeSpec("matched", "rules",
                "review", FlowEdgeType.CONDITIONAL, 2, "Matched", FlowBuilder.MATCHED_CONDITION_JSON), otherwise)),
                new ExpectedIssue[] {issue("DUPLICATE_PRIORITY", "rules", null, "graph.edges",
                    "条件顺序必须从一连续排列且不能重复")}),
            Arguments.of("直连与条件出口混用", new GraphSpec(nodes, List.of(enter, matched, otherwise,
                new EdgeSpec("direct", "rules", "finish", FlowEdgeType.UNCONDITIONAL, 0, "Direct", null))),
                new ExpectedIssue[] {issue("INVALID_EXITS", "rules", null, "graph.edges", "请选择直连或条件加其他情况出口")}),
            Arguments.of("规则集节点没有出口", new GraphSpec(List.of(start, rules, finish), List.of(enter)),
                new ExpectedIssue[] {
                    issue("INVALID_EXITS", "rules", null, "graph.edges", "请选择直连或条件加其他情况出口"),
                    issue("ELSE_REQUIRED", "rules", null, "graph.edges", "条件分流必须有一个其他情况出口"),
                    issue("UNREACHABLE_NODE", "finish", null, "graph.nodes", "节点无法从开始到达")}),
            Arguments.of("条件出口缺少条件", new GraphSpec(nodes, List.of(enter, new EdgeSpec("matched", "rules", "review",
                FlowEdgeType.CONDITIONAL, 1, "Matched", null), otherwise)),
                new ExpectedIssue[] {issue("INVALID_CONDITION", null, "matched", "graph.edges[1]",
                    "条件出口必须配置条件和正整数顺序")}),
            Arguments.of("条件类型不支持大小比较", new GraphSpec(nodes, List.of(enter, new EdgeSpec("matched", "rules",
                "review", FlowEdgeType.CONDITIONAL, 1, "Matched",
                FlowBuilder.MATCHED_CONDITION_JSON.replace("\"EQ\"", "\"GT\"")), otherwise)),
                new ExpectedIssue[] {issue("INVALID_CONDITION", null, "matched", "graph.edges[1].condition",
                    "条件来源、类型、值域或大小不合法")}),
            Arguments.of("规则集节点指向自身", new GraphSpec(List.of(start, rules, finish), List.of(enter,
                new EdgeSpec("loop", "rules", "rules", FlowEdgeType.UNCONDITIONAL, 0, "Loop", null))),
                new ExpectedIssue[] {issue("CYCLE", "rules", "loop", "graph.edges[1]", "连线不能指向自身"),
                    issue("CYCLE", null, null, "graph.edges", "流程不能包含循环"),
                    issue("UNREACHABLE_NODE", "finish", null, "graph.nodes", "节点无法从开始到达")}));
    }

    static Stream<Arguments> transportErrors() {
        return Stream.of(Arguments.of("未知顶层字段", (Consumer<ObjectNode>) b -> b.put("actorId", "1")),
            Arguments.of("缺少可空键", (Consumer<ObjectNode>) b -> b.remove("baseFlowVersionId")),
            Arguments.of("坐标为数字", (Consumer<ObjectNode>) b -> node(b, 0).withObject("/position").put("x", 12)),
            Arguments.of("修订号为数字", (Consumer<ObjectNode>) b -> b.put("expectedVersion", 1)),
            Arguments.of("连线缺少条件键", (Consumer<ObjectNode>) b -> edge(b, 0).remove("condition")));
    }

    private static Arguments issues(String scenario, Consumer<ObjectNode> mutation, ExpectedIssue... expected) {
        return Arguments.of(scenario, mutation, expected);
    }

    private static ExpectedIssue issue(String code, String nodeKey, String edgeKey, String field, String message) {
        return new ExpectedIssue(code, nodeKey, edgeKey, field, message);
    }

    private static ExpectedIssue reference(String nodeKey, int index) {
        return new ExpectedIssue(REFERENCE_UNAVAILABLE.getCode(), nodeKey, null,
            "graph.nodes[" + index + "].ruleSetVersionId", REFERENCE_MESSAGE);
    }

    private static ExpectedIssue reason() {
        return new ExpectedIssue(REFERENCE_UNAVAILABLE.getCode(), "finish", null, "graph.nodes[1].reasonCode",
            REASON_MESSAGE);
    }

    private static ExpectedIssue base(String message) {
        return new ExpectedIssue("BASE_CONFLICT", null, null, "baseFlowVersionId", message);
    }

    /** 开始 → 规则集节点 → 拒绝结束。 */
    private static GraphSpec ruleThenDeny(long ruleSetVersionId, String reasonCode, String reasonMessage) {
        return new GraphSpec(List.of(new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("rules", FlowNodeType.RULE_SET, "Rules", 120, 0, ruleSetVersionId, null, null, null),
            new NodeSpec("finish", FlowNodeType.END, "Finish", 240, 0, null, DecisionValue.DENY, reasonCode,
                reasonMessage)),
            List.of(new EdgeSpec("enter", "start", "rules", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("done", "rules", "finish", FlowEdgeType.UNCONDITIONAL, 0, "Done", null)));
    }

    private static ObjectNode node(ObjectNode body, int index) {
        return (ObjectNode) body.withArray("/graph/nodes").get(index);
    }

    private static ObjectNode edge(ObjectNode body, int index) {
        return (ObjectNode) body.withArray("/graph/edges").get(index);
    }

    private ObjectNode tree(SaveFlowRequestBuilder builder) {
        return objectMapper.valueToTree(builder.build());
    }

    private ValidateFlowResponseChecker validate(SaveFlowRequestBuilder builder) {
        return validate(objectMapper.writeValueAsString(builder.build()));
    }

    private ValidateFlowResponseChecker validate(String body) {
        return invoke(PATH, headers(nextRequestId()), body);
    }

    private ValidateFlowResponseChecker invoke(String uri, HttpHeaders headers, String body) {
        return new ValidateFlowResponseChecker(http.post(uri, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-validate-" + UUID.randomUUID();
    }
}

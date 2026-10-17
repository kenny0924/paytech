package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_CODE_EXISTS;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_REFERENCE_UNAVAILABLE;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.LEGACY_AUTHORING_NOT_SUPPORTED;
import static com.paytech.risk.decision.domain.enums.FlowStatus.CONFIGURABLE;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.RETIRED;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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
import com.paytech.risk.test.checker.decision.FlowStoreChecker.ExpectedRoot;
import com.paytech.risk.test.checker.decision.FlowStoreChecker.ExpectedVersion;
import com.paytech.risk.test.checker.decision.SaveFlowResponseChecker;
import com.paytech.risk.test.checker.decision.SaveFlowResponseChecker.Saved;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 保存完整流程为新版本
 * <li>正常流程：首次创建写入根、版本 1 与全部节点/连线；编辑从当前准确版本生成下一版本并以 CAS 推进根；规则集引用、统一变量条件、拒绝原因原样落库。</li>
 * <li>保留资格：基准版本已引用的退休规则版本与停用原因（文案不变）可继续保存。</li>
 * <li>未找到：编辑基准指向不存在的流程编码返回 404。</li>
 * <li>参数：未知/缺失键、类型、重复键、非 JSON、超长 Body、ID/坐标格式、媒体类型与 Query 在写入前拒绝（400/413/415）；数组容量与图校验问题返回 422 并定位字段。</li>
 * <li>业务规则：编码已存在（含已删除）、过期基准、已删除流程、版本号上限返回 409；规则版本/原因资格不可用返回 422；旧格式条件返回 400；决策点停用返回 400；基准闭包损坏返回 500。</li>
 * <li>并发：同一基准的两次编辑只有一次成功，另一项冲突且不留下孤儿版本。</li>
 * <li>所有被拒绝的写入都不改变已有流程，也不创建新流程。</li>
 *
 * @see DecisionFlowManagementApi#save(String, String, com.paytech.risk.api.decision.request.SaveFlowRequestBody)
 */
class FlowSaveApiAutoTest extends MockTestBase {
    /** 新保存的流程使用结账当前输入合同，只能引用同合同的规则集版本。 */
    private static final int CURRENT_CONTRACT = RuleSetBuilder.CURRENT_CONTRACT_VERSION;

    private static final String PATH = "/decision-flows/v1/flows";

    private static final long ACTOR = 70001L;

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private FlowStoreChecker store;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 首次创建：根修订号 1，版本 1 及全部节点、连线以同一保存时间落库。 */
    @Test
    void test_normalFlow_create() {
        String code = flows.newFlowCode();
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(code);
        Instant before = now();

        Saved saved = save(request).assertSaved(1, 1, ACTOR, before, now());

        store.assertFlow(code, new ExpectedRoot(saved.flowId(), saved.flowVersionId(), CONFIGURABLE, 1, null, null,
            saved.savedTime(), saved.savedTime()), List.of(created(saved, 1, request)));
    }

    /** 编辑当前版本：保留原流程身份与历史，新增下一版本并推进根指针、修订号与更新时间。 */
    @Test
    void test_normalFlow_edit() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).inputContractVersion(CURRENT_CONTRACT));
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(flow.flowCode())
            .base(flow.current().flowVersionId(), flow.version()).flowName("Edited flow").changeReason("Second edit")
            .graph(GraphSpec.direct(DecisionValue.REVIEW));
        Instant before = now();

        Saved saved = save(request).assertSaved(3, 3, ACTOR, before, now());

        assertEquals(flow.flowId(), saved.flowId(), "编辑不应改变流程身份");
        store.assertFlow(flow.flowCode(), new ExpectedRoot(flow.flowId(), saved.flowVersionId(), CONFIGURABLE, 3, null,
            null, flow.createTime(), saved.savedTime()), List.of(FlowStoreChecker.inserted(flow.version(1)),
                FlowStoreChecker.inserted(flow.version(2)), created(saved, 3, request)));
    }

    /** 规则集节点、统一变量条件出口与 ELSE 出口原样落库，条件以规范 JSON 存储。 */
    @Test
    void test_normalFlow_ruleSetAndCondition() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT).variableReference());
        String code = flows.newFlowCode();
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(code)
            .graph(GraphSpec.branching(ruleSet.version(1).ruleSetVersionId()));
        Instant before = now();

        Saved saved = save(request).assertSaved(1, 1, ACTOR, before, now());

        store.assertFlow(code, new ExpectedRoot(saved.flowId(), saved.flowVersionId(), CONFIGURABLE, 1, null, null,
            saved.savedTime(), saved.savedTime()), List.of(created(saved, 1, request)));
    }

    /** 拒绝结束节点引用已启用原因，保存时文案作为快照落库。 */
    @Test
    void test_normalFlow_denyWithActiveReason() {
        String reason = flows.mockReason(DictionaryStatus.ACTIVE);
        String code = flows.newFlowCode();
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(code)
            .graph(GraphSpec.deny(reason, "Operator message"));
        Instant before = now();

        Saved saved = save(request).assertSaved(1, 1, ACTOR, before, now());

        store.assertFlow(code, new ExpectedRoot(saved.flowId(), saved.flowVersionId(), CONFIGURABLE, 1, null, null,
            saved.savedTime(), saved.savedTime()), List.of(created(saved, 1, request)));
    }

    /** 基准版本已引用的退休规则版本与停用原因在文案不变时可继续保存。 */
    @Test
    void test_normalFlow_retainedReferences() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(RETIRED, FROZEN).variableReference());
        String reason = flows.mockReason(DictionaryStatus.DISABLED);
        GraphSpec graph = ruleThenDeny(ruleSet.version(1).ruleSetVersionId(), reason, "Retained message");
        MockedFlow flow = flows.mock(FlowBuilder.versions(graph).inputContractVersion(CURRENT_CONTRACT));
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(flow.flowCode())
            .base(flow.current().flowVersionId(), flow.version()).graph(graph);
        Instant before = now();

        Saved saved = save(request).assertSaved(2, 2, ACTOR, before, now());

        store.assertFlow(flow.flowCode(), new ExpectedRoot(flow.flowId(), saved.flowVersionId(), CONFIGURABLE, 2, null,
            null, flow.createTime(), saved.savedTime()), List.of(FlowStoreChecker.inserted(flow.version(1)),
                created(saved, 2, request)));
    }

    /** 名称 100 字符、说明 500 字符、坐标 ±100000 与 64 字符节点键均为合法上限。 */
    @Test
    void test_edgeCase_limits() {
        String code = flows.newFlowCode();
        String key = "k".repeat(64);
        GraphSpec graph = new GraphSpec(
            List.of(new NodeSpec("start", FlowNodeType.START, "Start", -100000, 100000, null, null, null, null),
                new NodeSpec(key, FlowNodeType.END, "E".repeat(100), 100000, -100000, null, DecisionValue.ALLOW, null,
                    null)),
            List.of(new EdgeSpec("e".repeat(64), "start", key, FlowEdgeType.UNCONDITIONAL, 0, "N".repeat(100), null)));
        SaveFlowRequestBuilder request = SaveFlowRequestBuilder.template(code).flowName("F".repeat(100))
            .changeReason("R".repeat(500)).graph(graph);
        Instant before = now();

        Saved saved = save(request).assertSaved(1, 1, ACTOR, before, now());

        store.assertFlow(code, new ExpectedRoot(saved.flowId(), saved.flowVersionId(), CONFIGURABLE, 1, null, null,
            saved.savedTime(), saved.savedTime()), List.of(created(saved, 1, request)));
    }

    /** 编辑基准指向不存在的流程编码。 */
    @Test
    void test_noDataFound() {
        String code = flows.newFlowCode();

        save(SaveFlowRequestBuilder.template(code).base(999_999_999_999_999_999L, 1)).assertProblem(404,
            RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 传输格式或标识格式错误在写入前拒绝，不创建流程。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("transportErrors")
    void test_invalidParameter(String scenario, Consumer<ObjectNode> mutation) {
        String code = flows.newFlowCode();
        ObjectNode body = tree(SaveFlowRequestBuilder.template(code));
        mutation.accept(body);

        save(body.toString()).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 非对象 JSON、重复键、尾随内容与非 JSON 文本。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, String body) {
        save(body).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNoFlow("MOCK_FLOW_NONE", 0, 0);
    }

    /** 请求正文超过 64KB。 */
    @Test
    void test_invalidParameter_bodyTooLarge() {
        String code = flows.newFlowCode();
        ObjectNode body = tree(SaveFlowRequestBuilder.template(code));
        body.put("changeReason", "x".repeat(70_000));

        save(body.toString()).assertProblem(413, INVALID_REQUEST.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 非 JSON 媒体类型与携带 Query 参数的请求。 */
    @Test
    void test_invalidParameter_mediaTypeAndQuery() {
        String code = flows.newFlowCode();
        String body = tree(SaveFlowRequestBuilder.template(code)).toString();
        HttpHeaders text = headers(nextRequestId());
        text.setContentType(MediaType.TEXT_PLAIN);

        invoke(PATH, text, body).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());
        invoke(PATH + "?flowCode=" + code, headers(nextRequestId()), body).assertProblem(400,
            INVALID_REQUEST.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 节点或连线数量超过数组容量。 */
    @Test
    void test_invalidParameter_capacity() {
        String code = flows.newFlowCode();
        ObjectNode nodes = tree(SaveFlowRequestBuilder.template(code));
        ArrayNode nodeArray = nodes.withArray("/graph/nodes");
        for (int i = 0; i < 49; i++) {
            nodeArray.add(((ObjectNode) nodeArray.get(1)).deepCopy().put("nodeKey", "extra_" + i));
        }
        ObjectNode edges = tree(SaveFlowRequestBuilder.template(code));
        ArrayNode edgeArray = edges.withArray("/graph/edges");
        for (int i = 0; i < 100; i++) {
            edgeArray.add(((ObjectNode) edgeArray.get(0)).deepCopy().put("edgeKey", "extra_" + i));
        }

        save(nodes.toString()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("graph.nodes");
        save(edges.toString()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("graph.edges");

        store.assertNoFlow(code, 0, 0);
    }

    /** 图校验不通过时返回 422 并按字段定位，不写入。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("graphErrors")
    void test_invalidParameter_graph(String scenario, Consumer<ObjectNode> mutation, String field) {
        String code = flows.newFlowCode();
        ObjectNode body = tree(SaveFlowRequestBuilder.template(code));
        mutation.accept(body);

        save(body.toString()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        store.assertNoFlow(code, 0, 0);
    }

    /** 条件引用不存在的变量时按条件图定位，不写入。 */
    @Test
    void test_invalidParameter_unknownVariable() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());
        String code = flows.newFlowCode();

        save(SaveFlowRequestBuilder.template(code).graph(GraphSpec.branching(ruleSet.currentVersionId(),
            FlowBuilder.variableCondition("MOCK_UNKNOWN_VARIABLE")))).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError("graph");

        store.assertNoFlow(code, 0, 0);
    }

    /** 新建已存在的编码（含已删除流程的编码）冲突，原流程不变。 */
    @Test
    void test_businessRuleViolation_codeExists() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).inputContractVersion(CURRENT_CONTRACT));
        MockedFlow deleted = flows.mock(FlowBuilder.versions(2).deleted().inputContractVersion(CURRENT_CONTRACT));

        save(SaveFlowRequestBuilder.template(flow.flowCode())).assertProblem(409, RISK_FLOW_CODE_EXISTS.getCode());
        save(SaveFlowRequestBuilder.template(deleted.flowCode())).assertProblem(409, RISK_FLOW_CODE_EXISTS.getCode());

        store.assertUnchanged(flow);
        store.assertUnchanged(deleted);
    }

    /** 过期的根修订号或基准版本冲突，原流程不变。 */
    @Test
    void test_businessRuleViolation_staleBase() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).inputContractVersion(CURRENT_CONTRACT));

        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), 1))
            .assertProblem(409, RISK_FLOW_VERSION_CONFLICT.getCode());
        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.version(1).flowVersionId(), 2))
            .assertProblem(409, RISK_FLOW_VERSION_CONFLICT.getCode());

        store.assertUnchanged(flow);
    }

    /** 已整体删除的流程不再产生新版本。 */
    @Test
    void test_businessRuleViolation_deletedFlow() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).deleted().inputContractVersion(CURRENT_CONTRACT));

        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version()))
            .assertProblem(409, RISK_FLOW_DELETED.getCode());

        store.assertUnchanged(flow);
    }

    /** 当前业务版本号已达到整数上限时不能再保存。 */
    @Test
    void test_businessRuleViolation_versionNoLimit() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).firstVersionNo(Integer.MAX_VALUE).inputContractVersion(CURRENT_CONTRACT));

        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version()))
            .assertProblem(409, RISK_FLOW_VERSION_CONFLICT.getCode());

        store.assertUnchanged(flow);
    }

    /** 新选择的规则版本不可用（已退休、已删除规则集、合同不符、不存在）。 */
    @Test
    void test_businessRuleViolation_referenceUnavailable() {
        MockedRuleSet retired = ruleSets.mock(versions(RETIRED, FROZEN).variableReference());
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN).variableReference().deleted());
        MockedRuleSet mismatched = ruleSets.mock(versions(DRAFT).variableReference());
        flowTestMapper.updateRuleSetContract(mismatched.currentVersionId(), RuleSetBuilder.UNREGISTERED_CONTRACT_VERSION);
        String code = flows.newFlowCode();

        for (long versionId : new long[] {retired.version(1).ruleSetVersionId(), deleted.currentVersionId(),
            mismatched.currentVersionId(), 999_999_999_999_999_999L}) {
            save(SaveFlowRequestBuilder.template(code).graph(GraphSpec.through(versionId))).assertProblem(422,
                RISK_FLOW_REFERENCE_UNAVAILABLE.getCode());
        }

        store.assertNoFlow(code, 0, 0);
    }

    /** 新选择的拒绝原因已停用或未登记；保留的停用原因改了文案也不可用。 */
    @Test
    void test_businessRuleViolation_reasonUnavailable() {
        String disabled = flows.mockReason(DictionaryStatus.DISABLED);
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.deny(disabled, "Original message")).inputContractVersion(CURRENT_CONTRACT));
        String code = flows.newFlowCode();

        save(SaveFlowRequestBuilder.template(code).graph(GraphSpec.deny(disabled, "Message"))).assertProblem(422,
            RISK_FLOW_REFERENCE_UNAVAILABLE.getCode());
        save(SaveFlowRequestBuilder.template(code).graph(GraphSpec.deny("MOCK_UNKNOWN_REASON", "Message")))
            .assertProblem(422, RISK_FLOW_REFERENCE_UNAVAILABLE.getCode());
        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version())
            .graph(GraphSpec.deny(disabled, "Changed message"))).assertProblem(422,
                RISK_FLOW_REFERENCE_UNAVAILABLE.getCode());

        store.assertNoFlow(code, 1, 1);
        store.assertUnchanged(flow);
    }

    /** 旧 schemaVersion=1 条件不能作为新内容保存。 */
    @Test
    void test_businessRuleViolation_legacyCondition() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());
        String code = flows.newFlowCode();

        save(SaveFlowRequestBuilder.template(code).graph(GraphSpec.branching(ruleSet.currentVersionId(),
            FlowBuilder.LEGACY_MATCHED_CONDITION_JSON))).assertProblem(400, LEGACY_AUTHORING_NOT_SUPPORTED.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 决策点停用时不可配置。 */
    @Test
    void test_businessRuleViolation_inactivePoint() {
        String code = flows.newFlowCode();
        flows.deactivatePoint();

        save(SaveFlowRequestBuilder.template(code)).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNoFlow(code, 0, 0);
    }

    /** 基准流程的根修订号与当前版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedBase() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).inputContractVersion(CURRENT_CONTRACT));
        flows.corruptRootVersion(flow);

        save(SaveFlowRequestBuilder.template(flow.flowCode()).base(flow.current().flowVersionId(), flow.version() + 5))
            .assertProblem(500, INTERNAL_ERROR.getCode());

        assertEquals(1, flowTestMapper.versions(flow.flowId()).size(), "不应新增版本");
    }

    /** 同一基准的两次并发编辑只有一次成功，另一项冲突，根只推进一次且没有孤儿版本。 */
    @Test
    void test_edgeCase_concurrentEdits() throws Exception {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).inputContractVersion(CURRENT_CONTRACT));
        CyclicBarrier ready = new CyclicBarrier(2);
        List<CompletableFuture<SaveFlowResponseChecker>> results = new ArrayList<>();
        for (String name : List.of("Left edit", "Right edit")) {
            String body = objectMapper.writeValueAsString(SaveFlowRequestBuilder.template(flow.flowCode())
                .base(flow.current().flowVersionId(), flow.version()).flowName(name).build());
            results.add(CompletableFuture.supplyAsync(() -> {
                await(ready);
                return save(body);
            }));
        }

        List<SaveFlowResponseChecker> responses = new ArrayList<>();
        for (CompletableFuture<SaveFlowResponseChecker> result : results) {
            responses.add(result.get(30, TimeUnit.SECONDS));
        }

        responses.sort((a, b) -> Integer.compare(a.status(), b.status()));
        assertEquals(List.of(200, 409), responses.stream().map(SaveFlowResponseChecker::status).toList());
        responses.get(0).assertSaved(2, 2, ACTOR, flow.createTime(), now());
        // 后到者在锁内看到已推进的根（版本冲突），或等待锁超时（并发冲突）。
        responses.get(1).assertProblemIn(409, RISK_FLOW_VERSION_CONFLICT.getCode(), CONCURRENT_REQUEST.getCode());
        assertEquals(2, flowTestMapper.versions(flow.flowId()).size(), "只应新增一个版本");
        assertEquals(2, flowTestMapper.root(flow.flowCode()).orElseThrow().version(), "根只推进一次");
    }

    static Stream<Arguments> transportErrors() {
        return Stream.of(transport("未知顶层字段", b -> b.put("actorId", "1")),
            transport("缺少可空键", b -> b.remove("baseFlowVersionId")),
            transport("缺少图", b -> b.remove("graph")),
            transport("流程编码为数字", b -> b.put("flowCode", 1)),
            transport("流程名称为 null", b -> b.putNull("flowName")),
            transport("修订号为数字", b -> b.put("expectedVersion", 1)),
            transport("图结构版本为字符串", b -> b.withObject("/graph").put("schemaVersion", "1")),
            transport("节点数组为对象", b -> b.withObject("/graph").putObject("nodes")),
            transport("节点未知字段", b -> node(b, 0).put("color", "red")),
            transport("节点缺少规则版本键", b -> node(b, 0).remove("ruleSetVersionId")),
            transport("坐标为数字", b -> node(b, 0).withObject("/position").put("x", 12)),
            transport("坐标缺少 y", b -> node(b, 0).withObject("/position").remove("y")),
            transport("连线缺少条件键", b -> edge(b, 0).remove("condition")),
            transport("连线优先级为字符串", b -> edge(b, 0).put("priority", "0")),
            transport("条件格式版本不支持", b -> edge(b, 0).putObject("condition").put("schemaVersion", 3)
                .putObject("root").put("kind", "GROUP").put("operator", "AND").putArray("children")),
            transport("条件表达式类型未知", b -> edge(b, 0).putObject("condition").put("schemaVersion", 2)
                .putObject("root").put("kind", "LITERAL")),
            transport("统一变量条件携带旧来源", b -> edge(b, 0).putObject("condition").put("schemaVersion", 2)
                .putObject("root").put("kind", "PREDICATE").put("operator", "EQ").putArray("operands").add(1)),
            transport("旧条件字面量为数字", b -> edge(b, 0).putObject("condition").put("schemaVersion", 1)
                .putObject("root").put("kind", "PREDICATE").put("operator", "EQ").putArray("values").add(1)),
            transport("基准版本 ID 为 0", b -> b.put("baseFlowVersionId", "0").put("expectedVersion", "1")),
            transport("基准版本 ID 前导零", b -> b.put("baseFlowVersionId", "01").put("expectedVersion", "1")),
            transport("修订号非数字", b -> b.put("baseFlowVersionId", "1").put("expectedVersion", "one")),
            transport("规则版本 ID 非数字", b -> node(b, 0).put("ruleSetVersionId", "abc")),
            transport("坐标为负零", b -> node(b, 0).withObject("/position").put("x", "-0")),
            transport("坐标超出画布", b -> node(b, 0).withObject("/position").put("x", "100001")),
            transport("坐标为小数", b -> node(b, 0).withObject("/position").put("y", "1.5")),
            transport("坐标带正号", b -> node(b, 0).withObject("/position").put("y", "+1")));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("顶层为数组", "[]"), Arguments.of("非 JSON", "not json"),
            Arguments.of("空对象", "{}"),
            Arguments.of("重复键", "{\"pointCode\":\"CHECKOUT_PREPARE\",\"pointCode\":\"CHECKOUT_PREPARE\"}"),
            Arguments.of("尾随内容", "{} {}"));
    }

    static Stream<Arguments> graphErrors() {
        return Stream.of(Arguments.of("决策点不支持", (Consumer<ObjectNode>) b -> b.put("pointCode", "PAYOUT_CREATION"),
            "pointCode"),
            Arguments.of("流程编码小写", (Consumer<ObjectNode>) b -> b.put("flowCode", "mock_flow"), "flowCode"),
            Arguments.of("流程名称为空", (Consumer<ObjectNode>) b -> b.put("flowName", ""), "flowName"),
            Arguments.of("编辑基准只给一半", (Consumer<ObjectNode>) b -> b.put("baseFlowVersionId", "1"),
                "baseFlowVersionId"),
            Arguments.of("结束节点缺少决定", (Consumer<ObjectNode>) b -> node(b, 1).putNull("decisionCode"),
                "graph.nodes[1].decisionCode"),
            Arguments.of("拒绝缺少原因", (Consumer<ObjectNode>) b -> node(b, 1).put("decisionCode", "DENY"),
                "graph.nodes[1].reasonCode"),
            Arguments.of("连线端点不存在", (Consumer<ObjectNode>) b -> edge(b, 0).put("targetNodeKey", "ghost"),
                "graph.edges[0]"),
            Arguments.of("不可达节点", (Consumer<ObjectNode>) b -> b.withArray("/graph/nodes")
                .add(node(b, 1).deepCopy().put("nodeKey", "orphan")), "graph.nodes"));
    }

    private static Arguments transport(String scenario, Consumer<ObjectNode> mutation) {
        return Arguments.of(scenario, mutation);
    }

    /** 本次保存产生的版本事实：保存人、保存时间与请求中的名称、说明和完整图；新版本使用当前输入合同。 */
    private static ExpectedVersion created(Saved saved, int versionNo, SaveFlowRequestBuilder request) {
        return new ExpectedVersion(saved.flowVersionId(), versionNo, request.flowName(), ACTOR,
            request.changeReason(), saved.savedTime(), request.graphSpec(), CURRENT_CONTRACT);
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

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
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

    private SaveFlowResponseChecker save(SaveFlowRequestBuilder builder) {
        return save(objectMapper.writeValueAsString(builder.build()));
    }

    private SaveFlowResponseChecker save(String body) {
        return invoke(PATH, headers(nextRequestId()), body);
    }

    private SaveFlowResponseChecker invoke(String uri, HttpHeaders headers, String body) {
        return new SaveFlowResponseChecker(http.post(uri, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-save-" + UUID.randomUUID();
    }
}

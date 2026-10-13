package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.CONFIGURABLE;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.DELETED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionFlowQueryApi;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.checker.decision.FlowRuleSetReferenceResponseChecker;
import com.paytech.risk.test.checker.decision.FlowRuleSetReferenceResponseChecker.ExpectedSource;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedLegacyPoint;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 按已保存来源的真实节点绑定解析准确规则引用
 * <li>正常流程：FLOW_VERSION 按所选流程版本图中的规则集节点解析，LEGACY_POINT 按旧点绑定解析；返回准确规则版本详情及其当前管理状态。</li>
 * <li>历史保留：规则集整体删除或流程整体删除后，准确引用仍可读取。</li>
 * <li>未找到：来源版本不存在、节点不存在或不是规则集节点返回 404。</li>
 * <li>参数：来源类型、来源版本 ID、节点键缺失或格式非法，未知/重复 Query 与 GET Body 在查询前拒绝（400）。</li>
 * <li>闭包损坏：被引用规则版本的输入合同不符或旧绑定顺序断档时失败关闭（500）。</li>
 *
 * @see DecisionFlowQueryApi#ruleSetReference(String, String, String, String, String)
 */
class FlowRuleSetReferenceApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-flows/v1/rule-set-reference";

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 流程当前版本中规则集节点引用的准确冻结版本。 */
    @Test
    void test_normalFlow_flowVersion() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(ruleSet.version(1).ruleSetVersionId())));
        long versionId = flow.current().flowVersionId();
        Instant before = now();

        FlowRuleSetReferenceResponseChecker response = read("FLOW_VERSION", versionId, "rules_1");

        response.assertReference(new ExpectedSource("FLOW_VERSION", versionId, "rules_1"), ruleSet, 1, CONFIGURABLE,
            null, flowTestMapper.ruleNodeId(ruleSet.version(1).ruleSetVersionId()), before, now());
    }

    /** 历史流程版本按其自身图解析，不跟随当前版本的引用变化。 */
    @Test
    void test_normalFlow_historicalFlowVersion() {
        MockedRuleSet first = ruleSets.mock(versions(DRAFT));
        MockedRuleSet second = ruleSets.mock(versions(FROZEN));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(first.currentVersionId()),
            GraphSpec.through(second.currentVersionId(), first.currentVersionId())));
        long historical = flow.version(1).flowVersionId();
        long current = flow.version(2).flowVersionId();
        Instant before = now();

        read("FLOW_VERSION", historical, "rules_1").assertReference(
            new ExpectedSource("FLOW_VERSION", historical, "rules_1"), first, 1, CONFIGURABLE, null,
            flowTestMapper.ruleNodeId(first.currentVersionId()), before, now());
        read("FLOW_VERSION", current, "rules_1").assertReference(new ExpectedSource("FLOW_VERSION", current, "rules_1"),
            second, 1, CONFIGURABLE, null, flowTestMapper.ruleNodeId(second.currentVersionId()), before, now());
    }

    /** 规则集整体删除、流程整体删除后准确引用仍可读取，规则集状态为 DELETED。 */
    @Test
    void test_normalFlow_deletedRuleSetAndFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN).deleted());
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(ruleSet.currentVersionId())).deleted());
        long versionId = flow.current().flowVersionId();
        Instant before = now();

        read("FLOW_VERSION", versionId, "rules_1").assertReference(
            new ExpectedSource("FLOW_VERSION", versionId, "rules_1"), ruleSet, 1, DELETED, null,
            flowTestMapper.ruleNodeId(ruleSet.currentVersionId()), before, now());
    }

    /** 旧点版本按绑定节点键解析，规则版本详情带伴随点版本。 */
    @Test
    void test_normalFlow_legacyPoint() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        MockedLegacyPoint point = flows.mockLegacyPoint(ruleSet.version(1));
        String nodeKey = "binding_" + point.bindingIds().getFirst();
        Instant before = now();

        read("LEGACY_POINT", point.pointVersionId(), nodeKey).assertReference(
            new ExpectedSource("LEGACY_POINT", point.pointVersionId(), nodeKey), ruleSet, 1, CONFIGURABLE,
            point.pointVersionId(), flowTestMapper.ruleNodeId(ruleSet.currentVersionId()), before, now());
    }

    /** 来源版本不存在，或节点不存在、不是规则集节点。 */
    @Test
    void test_noDataFound() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(ruleSet.currentVersionId())));
        MockedLegacyPoint point = flows.mockLegacyPoint(ruleSet.version(1));
        long versionId = flow.current().flowVersionId();

        read("FLOW_VERSION", 999_999_999_999_999_999L, "rules_1").assertNotFound();
        read("FLOW_VERSION", versionId, "rules_9").assertNotFound();
        read("FLOW_VERSION", versionId, "start").assertNotFound();
        read("FLOW_VERSION", versionId, "finish").assertNotFound();
        // 节点键区分大小写。
        read("FLOW_VERSION", versionId, "Rules_1").assertNotFound();
        read("LEGACY_POINT", 999_999_999_999_999_999L, "binding_1").assertNotFound();
        read("LEGACY_POINT", point.pointVersionId(), "legacy_default").assertNotFound();
        read("LEGACY_POINT", point.pointVersionId(), "legacy_start").assertNotFound();
        // 两种来源的身份空间互不混用。
        read("LEGACY_POINT", versionId, "rules_1").assertNotFound();
        read("FLOW_VERSION", point.pointVersionId(), "binding_" + point.bindingIds().getFirst()).assertNotFound();
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        invoke(headers(), PATH + query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        String requestId = nextRequestId();

        new FlowRuleSetReferenceResponseChecker(http.exchange(HttpMethod.GET,
            PATH + "?sourceType=FLOW_VERSION&sourceVersionId=1&nodeKey=rules_1", headers(requestId), "{}"),
            objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 被引用规则版本的输入合同与流程版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_contractMismatch() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(ruleSet.currentVersionId())));
        flowTestMapper.updateRuleSetContract(ruleSet.currentVersionId(), 5);

        read("FLOW_VERSION", flow.current().flowVersionId(), "rules_1").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 旧点绑定顺序断档时不解析任何引用。 */
    @Test
    void test_businessRuleViolation_legacyBindingGap() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        MockedLegacyPoint point = flows.mockLegacyPoint(new int[] {2}, ruleSet.version(1));

        read("LEGACY_POINT", point.pointVersionId(), "binding_" + point.bindingIds().getFirst()).assertProblem(500,
            INTERNAL_ERROR.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        String valid = "?sourceType=FLOW_VERSION&sourceVersionId=1&nodeKey=rules_1";
        return Stream.of(Arguments.of("缺少 sourceType", "?sourceVersionId=1&nodeKey=rules_1"),
            Arguments.of("缺少 sourceVersionId", "?sourceType=FLOW_VERSION&nodeKey=rules_1"),
            Arguments.of("缺少 nodeKey", "?sourceType=FLOW_VERSION&sourceVersionId=1"),
            Arguments.of("sourceType 小写", "?sourceType=flow_version&sourceVersionId=1&nodeKey=rules_1"),
            Arguments.of("sourceType 不支持", "?sourceType=RULE_SET&sourceVersionId=1&nodeKey=rules_1"),
            Arguments.of("sourceType 为空值", "?sourceType=&sourceVersionId=1&nodeKey=rules_1"),
            Arguments.of("sourceVersionId 为 0", "?sourceType=FLOW_VERSION&sourceVersionId=0&nodeKey=rules_1"),
            Arguments.of("sourceVersionId 非数字", "?sourceType=FLOW_VERSION&sourceVersionId=abc&nodeKey=rules_1"),
            Arguments.of("sourceVersionId 前导零", "?sourceType=FLOW_VERSION&sourceVersionId=01&nodeKey=rules_1"),
            Arguments.of("nodeKey 数字开头", "?sourceType=FLOW_VERSION&sourceVersionId=1&nodeKey=1rules"),
            Arguments.of("nodeKey 含空格", "?sourceType=FLOW_VERSION&sourceVersionId=1&nodeKey=rules 1"),
            Arguments.of("nodeKey 超过 64 字符", "?sourceType=FLOW_VERSION&sourceVersionId=1&nodeKey=r" + "1".repeat(64)),
            Arguments.of("nodeKey 重复", valid + "&nodeKey=rules_1"), Arguments.of("未知参数", valid + "&page=1"));
    }

    private FlowRuleSetReferenceResponseChecker read(String sourceType, long sourceVersionId, String nodeKey) {
        return invoke(headers(), PATH + "?sourceType=" + sourceType + "&sourceVersionId=" + sourceVersionId
            + "&nodeKey=" + nodeKey);
    }

    private FlowRuleSetReferenceResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new FlowRuleSetReferenceResponseChecker(http.get(uri, headers), objectMapper, requestId);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static HttpHeaders headers() {
        return headers(nextRequestId());
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-reference-" + UUID.randomUUID();
    }
}

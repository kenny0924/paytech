package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedLegacyPoint;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 旧点版本只读顺序图的 HTTP 响应 Checker
 * <li>图按合同固定形态展开：开始 → 每个绑定的规则集节点（按绑定顺序，间隔 300） → 原默认决定结束。</li>
 *
 * @author Kenny
 */
public final class LegacyPointResponseChecker extends AbstractHttpResponseChecker<LegacyPointResponseChecker> {

    public LegacyPointResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected LegacyPointResponseChecker self() {
        return this;
    }

    /** 成功响应：点版本事实、顺序图与按绑定顺序的规则集引用；ruleSets/statuses 与绑定一一对应。 */
    public LegacyPointResponseChecker assertProjection(MockedLegacyPoint point, List<MockedRuleSet> ruleSets,
        List<RuleSetManagementStatus> statuses) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "sourceType", "pointVersionId", "pointCode", "pointName", "versionNo", "inputContractCode",
            "inputContractVersion", "defaultDecision", "evaluationStrategy", "failureStrategy", "nodes", "edges",
            "ruleSetReferences");
        assertAll("旧点版本", () -> assertEquals("LEGACY_POINT", text(body.path("sourceType"))),
            () -> assertEquals(Long.toString(point.pointVersionId()), text(body.path("pointVersionId"))),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(body.path("pointCode"))),
            () -> assertEquals("Checkout Prepare", text(body.path("pointName"))),
            () -> assertEquals(point.versionNo(), body.path("versionNo").asInt()),
            () -> assertEquals("ORDER_PREPARATION", text(body.path("inputContractCode"))),
            () -> assertEquals(4, body.path("inputContractVersion").asInt()),
            () -> assertEquals(DecisionValue.REVIEW.getCode(), text(body.path("defaultDecision"))),
            () -> assertEquals("FIRST_TERMINAL", text(body.path("evaluationStrategy"))),
            () -> assertEquals("FAIL_CLOSED", text(body.path("failureStrategy"))));
        List<NodeSpec> nodes = new ArrayList<>();
        List<EdgeSpec> edges = new ArrayList<>();
        nodes.add(new NodeSpec("legacy_start", FlowNodeType.START, "开始", 0, 0, null, null, null, null));
        String previous = "legacy_start";
        int count = point.bindingIds().size();
        for (int i = 0; i < count; i++) {
            String key = "binding_" + point.bindingIds().get(i);
            nodes.add(new NodeSpec(key, FlowNodeType.RULE_SET, point.ruleSetVersions().get(i).spec().ruleSetName(),
                (i + 1) * 300, 0, point.ruleSetVersions().get(i).ruleSetVersionId(), null, null, null));
            edges.add(new EdgeSpec("legacy_edge_" + (i + 1), previous, key, FlowEdgeType.UNCONDITIONAL, 0,
                "顺序 " + (i + 1), null));
            previous = key;
        }
        nodes.add(new NodeSpec("legacy_default", FlowNodeType.END, "原默认决定", (count + 1) * 300, 0, null,
            DecisionValue.REVIEW, null, null));
        edges.add(new EdgeSpec("legacy_default_edge", previous, "legacy_default", FlowEdgeType.UNCONDITIONAL, 0,
            "原默认决定", null));
        FlowGraphChecker.assertNodes(body.path("nodes"), nodes);
        FlowGraphChecker.assertEdges(body.path("edges"), edges);
        JsonNode references = body.path("ruleSetReferences");
        assertEquals(count, references.size(), "规则集引用数量不符");
        for (int i = 0; i < count; i++) {
            FlowGraphChecker.assertReference(references.get(i), "binding_" + point.bindingIds().get(i),
                ruleSets.get(i), point.ruleSetVersions().get(i).versionNo(), statuses.get(i));
        }
        return this;
    }

    /** 旧点版本不存在。 */
    public LegacyPointResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }
}

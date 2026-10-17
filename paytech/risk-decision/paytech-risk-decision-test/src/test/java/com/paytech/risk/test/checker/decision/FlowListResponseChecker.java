package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlowVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 流程列表分页的 HTTP 响应 Checker
 * <li>每个流程根一行，名称、版本号、规则集节点数与更新人取自当前版本，更新时间取自管理根。</li>
 *
 * @author Kenny
 */
public final class FlowListResponseChecker extends AbstractHttpResponseChecker<FlowListResponseChecker> {

    public FlowListResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected FlowListResponseChecker self() {
        return this;
    }

    /** 成功响应及分页元数据。 */
    public FlowListResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "items", "page", "size", "total");
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定流程顺序返回，逐项核对全部字段。 */
    public FlowListResponseChecker assertFlows(MockedFlow... expected) {
        return assertFlows(List.of(expected));
    }

    /** 当前页按给定流程顺序返回，逐项核对全部字段。 */
    public FlowListResponseChecker assertFlows(List<MockedFlow> expected) {
        JsonNode items = body().path("items");
        assertEquals(expected.size(), items.size(), "列表项数量不符");
        for (int i = 0; i < expected.size(); i++) {
            MockedFlow flow = expected.get(i);
            MockedFlowVersion head = flow.current();
            JsonNode actual = items.get(i);
            assertFields(actual, "flowId", "pointCode", "flowCode", "flowName", "currentFlowVersionId",
                "currentVersionNo", "version", "ruleSetNodeCount", "updatedBy", "updateTime");
            assertAll("列表项 " + flow.flowCode(), () -> assertEquals(Long.toString(flow.flowId()), text(actual.path("flowId"))),
                () -> assertEquals(RuleSetBuilder.POINT_CODE, text(actual.path("pointCode"))),
                () -> assertEquals(flow.flowCode(), text(actual.path("flowCode"))),
                () -> assertEquals(head.spec().flowName(), text(actual.path("flowName"))),
                () -> assertEquals(Long.toString(head.flowVersionId()), text(actual.path("currentFlowVersionId"))),
                () -> assertEquals(head.versionNo(), actual.path("currentVersionNo").asInt()),
                () -> assertEquals(Long.toString(flow.version()), text(actual.path("version"))),
                () -> assertEquals(head.spec().graph().ruleSetNodeCount(), actual.path("ruleSetNodeCount").asInt()),
                () -> assertEquals(Long.toString(head.spec().createdBy()), text(actual.path("updatedBy"))),
                () -> assertEquals(time(flow.updateTime()), text(actual.path("updateTime"))));
        }
        return this;
    }

    /** 决策点不受支持或不存在。 */
    public FlowListResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }
}

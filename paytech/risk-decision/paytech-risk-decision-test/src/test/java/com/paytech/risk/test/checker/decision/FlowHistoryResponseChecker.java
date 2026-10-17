package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlowVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 流程历史分页的 HTTP 响应 Checker
 * <li>逐字段核对历史项与插入的版本事实。</li>
 *
 * @author Kenny
 */
public final class FlowHistoryResponseChecker extends AbstractHttpResponseChecker<FlowHistoryResponseChecker> {

    public FlowHistoryResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected FlowHistoryResponseChecker self() {
        return this;
    }

    /** 成功响应及分页元数据。 */
    public FlowHistoryResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "items", "page", "size", "total");
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定业务版本号顺序返回，逐项核对全部字段。 */
    public FlowHistoryResponseChecker assertVersions(MockedFlow flow, int... versionNos) {
        JsonNode items = body().path("items");
        assertEquals(versionNos.length, items.size(), "历史项数量不符");
        for (int i = 0; i < versionNos.length; i++) {
            MockedFlowVersion expected = flow.version(versionNos[i]);
            JsonNode actual = items.get(i);
            assertFields(actual, "flowVersionId", "versionNo", "flowName", "createdBy", "createTime", "changeReason");
            assertAll("历史项 v" + versionNos[i],
                () -> assertEquals(Long.toString(expected.flowVersionId()), text(actual.path("flowVersionId"))),
                () -> assertEquals(expected.versionNo(), actual.path("versionNo").asInt()),
                () -> assertEquals(expected.spec().flowName(), text(actual.path("flowName"))),
                () -> assertEquals(Long.toString(expected.spec().createdBy()), text(actual.path("createdBy"))),
                () -> assertEquals(time(expected.spec().createTime()), text(actual.path("createTime"))),
                () -> assertEquals(expected.spec().changeReason(), text(actual.path("changeReason"))));
        }
        return this;
    }

    /** 流程不存在。 */
    public FlowHistoryResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }
}

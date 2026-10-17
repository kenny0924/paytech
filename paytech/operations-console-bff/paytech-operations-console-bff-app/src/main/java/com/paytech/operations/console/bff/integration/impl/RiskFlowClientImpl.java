package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.RiskConfigurationResponsePolicy.*;

import org.springframework.stereotype.Component;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskFlowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskFlowModels.*;
import com.paytech.risk.decision.api.DecisionFlowManagementApi;
import com.paytech.risk.decision.api.DecisionFlowQueryApi;

/** 每次操作只转发一次 Risk 请求，严格转换自有页面模型，不自动重试写入。 */
@Component
public class RiskFlowClientImpl implements RiskFlowClient {
    private final DecisionFlowQueryApi queries;
    private final DecisionFlowManagementApi management;

    public RiskFlowClientImpl(DecisionFlowQueryApi queries, DecisionFlowManagementApi management) {
        this.queries = queries;
        this.management = management;
    }

    @Override
    public FlowAuthoringContextResponseBody authoringContext(OperatorContext o, String pointCode) {
        var r = RiskFlowConverter.local(queries.authoringContext(o.accountId(), o.sessionId(), pointCode));
        RiskFlowResponsePolicy.authoring(r); filter(pointCode, r.pointCode()); return r;
    }

    @Override
    public PagingResponseBody<FlowListItemResponseBody> list(OperatorContext o, String pointCode, String flowCode, String keyword, int page, int size) {
        var owner = queries.list(o.accountId(), o.sessionId(), pointCode, flowCode, keyword, page, size);
        page(owner, page, size); var result = owner.map(RiskFlowConverter::local);
        var ids = new java.util.HashSet<String>();
        for (var r : result.items()) { RiskFlowResponsePolicy.summary(r); equal(pointCode, r.pointCode()); filter(flowCode, r.flowCode()); require(ids.add(r.flowId())); }
        return result;
    }

    @Override
    public FlowDetailResponseBody current(OperatorContext o, String flowId) {
        var r = RiskFlowConverter.local(queries.current(o.accountId(), o.sessionId(), flowId));
        RiskFlowResponsePolicy.detail(r); equal(flowId, r.flowId()); equal("CONFIGURABLE", r.flowStatus()); equal(r.currentFlowVersionId(), r.flowVersionId()); return r;
    }

    @Override
    public FlowDetailResponseBody version(OperatorContext o, String flowId, String flowVersionId) {
        var r = RiskFlowConverter.local(queries.version(o.accountId(), o.sessionId(), flowId, flowVersionId));
        RiskFlowResponsePolicy.detail(r); equal(flowId, r.flowId()); equal(flowVersionId, r.flowVersionId()); return r;
    }

    @Override
    public PagingResponseBody<FlowHistoryItemResponseBody> history(OperatorContext o, String flowId, int maxVersionNo, int page, int size) {
        var owner = queries.history(o.accountId(), o.sessionId(), flowId, maxVersionNo, page, size);
        page(owner, page, size); var result = owner.map(RiskFlowConverter::local); long previous = (long) maxVersionNo + 1;
        for (var r : result.items()) { RiskFlowResponsePolicy.history(r); require(r.versionNo() < previous); previous = r.versionNo(); }
        return result;
    }

    @Override
    public RuleSetReferenceResponseBody ruleSetReference(OperatorContext o, String sourceType, String sourceVersionId, String nodeKey) {
        var r = RiskFlowConverter.local(queries.ruleSetReference(o.accountId(), o.sessionId(), sourceType, sourceVersionId, nodeKey));
        require(r != null); equal(sourceType, r.sourceType()); equal(sourceVersionId, r.sourceVersionId()); equal(nodeKey, r.nodeKey());
        one(r.ruleSetStatus(), "CONFIGURABLE", "DELETED"); return r;
    }

    @Override
    public LegacyPointResponseBody legacyPoint(OperatorContext o, String pointVersionId) {
        var r = RiskFlowConverter.local(queries.legacyPoint(o.accountId(), o.sessionId(), pointVersionId));
        require(r != null); equal("LEGACY_POINT", r.sourceType()); equal(pointVersionId, r.pointVersionId()); code(r.pointCode());
        text(r.pointName(), 100); code(r.inputContractCode()); require(r.versionNo() > 0 && r.inputContractVersion() > 0);
        one(r.defaultDecision(), "ALLOW", "REVIEW", "DENY"); one(r.evaluationStrategy(), "FIRST_TERMINAL"); one(r.failureStrategy(), "FAIL_CLOSED");
        RiskFlowResponsePolicy.graph(r.nodes(), r.edges(), r.ruleSetReferences(), true); return r;
    }

    @Override
    public SaveFlowResponseBody save(OperatorContext o, SaveFlowRequestBody body) {
        SaveFlowResponseBody r;
        try {
            r = RiskFlowConverter.local(management.save(o.accountId(), o.sessionId(), RiskFlowConverter.owner(body)));
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
        require(r != null); id(r.flowId()); id(r.flowVersionId()); id(r.version()); id(r.savedBy()); time(r.savedTime());
        equal(o.accountId(), r.savedBy()); equal(body.expectedVersion() == null ? "1" : nextVersion(body.expectedVersion()), r.version());
        require(r.versionNo() > 0 && Long.parseLong(r.version()) == r.versionNo()); return r;
    }

    @Override
    public ValidationResponseBody validate(OperatorContext o, SaveFlowRequestBody body) {
        ValidationResponseBody r;
        try {
            r = RiskFlowConverter.local(management.validate(o.accountId(), o.sessionId(), RiskFlowConverter.owner(body)));
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
        RiskFlowResponsePolicy.validation(r); return r;
    }

    @Override
    public DeleteFlowResponseBody delete(OperatorContext o, String flowId, String expectedVersion) {
        var r = RiskFlowConverter.local(management.delete(o.accountId(), o.sessionId(), flowId, expectedVersion));
        require(r != null); id(r.flowId()); id(r.version()); id(r.deletedBy()); time(r.deletedTime()); equal(flowId, r.flowId());
        equal("DELETED", r.status()); equal(nextVersion(expectedVersion), r.version()); equal(o.accountId(), r.deletedBy()); return r;
    }
}

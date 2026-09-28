package com.paytech.operations.console.bff.integration.impl;

import com.paytech.risk.api.decision.DecisionFlowModels;
import com.paytech.operations.console.bff.service.model.RiskFlowModels;

/** Owner 与页面合同的显式字段转换；不执行图规则，不使用反射复制业务对象。 */
final class RiskFlowConverter {
    private RiskFlowConverter() { }

    static RiskFlowModels.SaveFlowRequestBody local(com.paytech.risk.api.decision.request.SaveFlowRequestBody r) {
        if (r == null) return null;
        return new RiskFlowModels.SaveFlowRequestBody(r.getPointCode(), r.getFlowCode(), r.getFlowName(),
            r.getBaseFlowVersionId(), r.getExpectedVersion(), r.getChangeReason(),
            local(r.getGraph()));
    }

    static RiskFlowModels.Graph local(DecisionFlowModels.Graph r) {
        if (r == null) return null;
        return new RiskFlowModels.Graph(r.getSchemaVersion(), r.getNodes().stream().map(RiskFlowConverter::local).toList(), r.getEdges().stream().map(RiskFlowConverter::local).toList());
    }

    static RiskFlowModels.Position local(DecisionFlowModels.Position r) {
        if (r == null) return null;
        return new RiskFlowModels.Position(r.getX(), r.getY());
    }

    static RiskFlowModels.Node local(DecisionFlowModels.Node r) {
        if (r == null) return null;
        return new RiskFlowModels.Node(r.getNodeKey(), r.getType(), r.getName(),
            local(r.getPosition()), r.getRuleSetVersionId(), r.getDecisionCode(),
            r.getReasonCode(), r.getReasonMessage());
    }

    static RiskFlowModels.Edge local(DecisionFlowModels.Edge r) {
        if (r == null) return null;
        return new RiskFlowModels.Edge(r.getEdgeKey(), r.getSourceNodeKey(), r.getTargetNodeKey(),
            r.getType(), r.getPriority(), r.getName(),
            local(r.getCondition()));
    }

    static RiskFlowModels.Condition local(DecisionFlowModels.Condition r) {
        if (r == null) return null;
        return new RiskFlowModels.Condition(r.getSchemaVersion(), local(r.getRoot()));
    }

    static RiskFlowModels.GroupExpression local(DecisionFlowModels.GroupExpression r) {
        if (r == null) return null;
        return new RiskFlowModels.GroupExpression(r.getOperator(), r.getChildren().stream().map(RiskFlowConverter::local).toList());
    }

    static RiskFlowModels.PredicateExpression local(DecisionFlowModels.PredicateExpression r) {
        if (r == null) return null;
        return new RiskFlowModels.PredicateExpression(local(r.getSource()), r.getOperator(), r.getValues(),
            r.getOperands() == null ? null : r.getOperands().stream().map(RiskOperandConverter::local).toList());
    }

    static RiskFlowModels.Source local(DecisionFlowModels.Source r) {
        if (r == null) return null;
        return new RiskFlowModels.Source(r.getType(), r.getFieldCode());
    }

    static RiskFlowModels.SupportedPoint local(DecisionFlowModels.SupportedPoint r) {
        if (r == null) return null;
        return new RiskFlowModels.SupportedPoint(r.getPointCode(), r.getPointName());
    }

    static RiskFlowModels.Field local(DecisionFlowModels.Field r) {
        if (r == null) return null;
        return new RiskFlowModels.Field(r.getFieldCode(), r.getLabel(), r.getValueType(),
            r.getRequired(), r.getOperators(), r.getLiteralDomain(), r.getVariableCode());
    }

    static RiskFlowModels.NodeOutput local(DecisionFlowModels.NodeOutput r) {
        if (r == null) return null;
        return new RiskFlowModels.NodeOutput(r.getNodeType(), r.getFieldCode(), r.getLabel(),
            r.getValueType(), r.getRequired(), r.getOperators(), r.getVariableCode());
    }

    static RiskFlowModels.Limits local(DecisionFlowModels.Limits r) {
        if (r == null) return null;
        return new RiskFlowModels.Limits(r.getBodyBytes(), r.getNodes(), r.getEdges(),
            r.getConditionBytes(), r.getConditionNodes(), r.getConditionDepth());
    }

    static RiskFlowModels.FlowAuthoringContextResponseBody local(com.paytech.risk.api.decision.response.FlowAuthoringContextResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.FlowAuthoringContextResponseBody(r.getSupportedPoints().stream().map(RiskFlowConverter::local).toList(), r.getPointCode(), r.getInputContractCode(),
            r.getInputContractVersion(), r.getGraphSchemaVersion(), r.getFields().stream().map(RiskFlowConverter::local).toList(),
            r.getNodeOutputs().stream().map(RiskFlowConverter::local).toList(), local(r.getLimits()),
            r.getDescriptorCode(), r.getConditionSchemaVersion(), RiskVariableConverter.local(r.getVariableQueryContext()));
    }

    static RiskFlowModels.FlowListItemResponseBody local(com.paytech.risk.api.decision.response.FlowListItemResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.FlowListItemResponseBody(r.getFlowId(), r.getPointCode(), r.getFlowCode(),
            r.getFlowName(), r.getCurrentFlowVersionId(), r.getCurrentVersionNo(),
            r.getVersion(), r.getRuleSetNodeCount(), r.getUpdatedBy(),
            r.getUpdateTime());
    }

    static RiskFlowModels.SaveFlowResponseBody local(com.paytech.risk.api.decision.response.SaveFlowResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.SaveFlowResponseBody(r.getFlowId(), r.getFlowVersionId(), r.getVersionNo(),
            r.getVersion(), r.getSavedBy(), r.getSavedTime());
    }

    static RiskFlowModels.Issue local(DecisionFlowModels.Issue r) {
        if (r == null) return null;
        return new RiskFlowModels.Issue(r.getCode(), r.getNodeKey(), r.getEdgeKey(),
            r.getField(), r.getMessage());
    }

    static RiskFlowModels.ValidationResponseBody local(com.paytech.risk.api.decision.response.ValidateFlowResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.ValidationResponseBody(r.getValid(), r.getIssues().stream().map(RiskFlowConverter::local).toList(), r.getTruncated());
    }

    static RiskFlowModels.RuleSetReference local(DecisionFlowModels.RuleSetReference r) {
        if (r == null) return null;
        return new RiskFlowModels.RuleSetReference(r.getNodeKey(), r.getRuleSetId(), r.getRuleSetCode(),
            r.getRuleSetName(), r.getRuleSetVersionId(), r.getVersionNo(),
            r.getRuleCount(), r.getRuleSetStatus(), r.getVersionStatus());
    }

    static RiskFlowModels.FlowDetailResponseBody local(com.paytech.risk.api.decision.response.FlowDetailResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.FlowDetailResponseBody(r.getFlowId(), r.getPointCode(), r.getFlowCode(),
            r.getFlowStatus(), r.getCurrentFlowVersionId(), r.getCurrentVersionNo(),
            r.getVersion(), r.getFlowVersionId(), r.getVersionNo(),
            r.getFlowName(), r.getInputContractCode(), r.getInputContractVersion(),
            r.getCreatedBy(), r.getCreateTime(), r.getChangeReason(),
            local(r.getGraph()), r.getRuleSetReferences().stream().map(RiskFlowConverter::local).toList(),
            r.getCurrentVariables().stream().map(RiskVariableConverter::local).toList(), r.getValuesAsOf(),
            r.getVariableIssues().stream().map(RiskVariableConverter::local).toList());
    }

    static RiskFlowModels.FlowHistoryItemResponseBody local(com.paytech.risk.api.decision.response.FlowHistoryItemResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.FlowHistoryItemResponseBody(r.getFlowVersionId(), r.getVersionNo(), r.getFlowName(),
            r.getCreatedBy(), r.getCreateTime(), r.getChangeReason());
    }

    static RiskFlowModels.DeleteFlowResponseBody local(com.paytech.risk.api.decision.response.DeleteFlowResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.DeleteFlowResponseBody(r.getFlowId(), r.getStatus(), r.getVersion(),
            r.getDeletedBy(), r.getDeletedTime());
    }

    static RiskFlowModels.RuleSetReferenceResponseBody local(com.paytech.risk.api.decision.response.RuleSetReferenceResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.RuleSetReferenceResponseBody(r.getSourceType(), r.getSourceVersionId(), r.getNodeKey(),
            r.getRuleSetStatus(), RiskDecisionConfigurationClientImpl.ruleSetDetail(r.getRuleSet(), r.getRuleSet().getRuleSetVersionId()));
    }

    static RiskFlowModels.LegacyPointResponseBody local(com.paytech.risk.api.decision.response.LegacyPointResponseBody r) {
        if (r == null) return null;
        return new RiskFlowModels.LegacyPointResponseBody(r.getSourceType(), r.getPointVersionId(), r.getPointCode(),
            r.getPointName(), r.getVersionNo(), r.getInputContractCode(),
            r.getInputContractVersion(), r.getDefaultDecision(), r.getEvaluationStrategy(),
            r.getFailureStrategy(), r.getNodes().stream().map(RiskFlowConverter::local).toList(), r.getEdges().stream().map(RiskFlowConverter::local).toList(),
            r.getRuleSetReferences().stream().map(RiskFlowConverter::local).toList());
    }

    static RiskFlowModels.Expression local(DecisionFlowModels.Expression value) {
        return switch (value) {
            case DecisionFlowModels.GroupExpression group -> local(group);
            case DecisionFlowModels.PredicateExpression predicate -> local(predicate);
        };
    }

    static com.paytech.risk.api.decision.request.SaveFlowRequestBody owner(RiskFlowModels.SaveFlowRequestBody r) {
        if (r == null) return null;
        var value = new com.paytech.risk.api.decision.request.SaveFlowRequestBody();
        value.setPointCode(r.pointCode());
        value.setFlowCode(r.flowCode());
        value.setFlowName(r.flowName());
        value.setBaseFlowVersionId(r.baseFlowVersionId());
        value.setExpectedVersion(r.expectedVersion());
        value.setChangeReason(r.changeReason());
        value.setGraph(owner(r.graph()));
        return value;
    }

    static DecisionFlowModels.Graph owner(RiskFlowModels.Graph r) {
        if (r == null) return null;
        return new DecisionFlowModels.Graph(r.schemaVersion(), r.nodes().stream().map(RiskFlowConverter::owner).toList(), r.edges().stream().map(RiskFlowConverter::owner).toList());
    }

    static DecisionFlowModels.Position owner(RiskFlowModels.Position r) {
        if (r == null) return null;
        return new DecisionFlowModels.Position(r.x(), r.y());
    }

    static DecisionFlowModels.Node owner(RiskFlowModels.Node r) {
        if (r == null) return null;
        return new DecisionFlowModels.Node(r.nodeKey(), r.type(), r.name(),
            owner(r.position()), r.ruleSetVersionId(), r.decisionCode(),
            r.reasonCode(), r.reasonMessage());
    }

    static DecisionFlowModels.Edge owner(RiskFlowModels.Edge r) {
        if (r == null) return null;
        return new DecisionFlowModels.Edge(r.edgeKey(), r.sourceNodeKey(), r.targetNodeKey(),
            r.type(), r.priority(), r.name(),
            owner(r.condition()));
    }

    static DecisionFlowModels.Condition owner(RiskFlowModels.Condition r) {
        if (r == null) return null;
        return new DecisionFlowModels.Condition(r.schemaVersion(), owner(r.root()));
    }

    static DecisionFlowModels.GroupExpression owner(RiskFlowModels.GroupExpression r) {
        if (r == null) return null;
        return new DecisionFlowModels.GroupExpression(r.operator(), r.children().stream().map(RiskFlowConverter::owner).toList());
    }

    static DecisionFlowModels.PredicateExpression owner(RiskFlowModels.PredicateExpression r) {
        if (r == null) return null;
        return new DecisionFlowModels.PredicateExpression(owner(r.source()), r.operator(), r.values(),
            r.operands() == null ? null : r.operands().stream().map(RiskOperandConverter::owner).toList());
    }

    static DecisionFlowModels.Source owner(RiskFlowModels.Source r) {
        if (r == null) return null;
        return new DecisionFlowModels.Source(r.type(), r.fieldCode());
    }

    static DecisionFlowModels.Expression owner(RiskFlowModels.Expression value) {
        return switch (value) {
            case RiskFlowModels.GroupExpression group -> owner(group);
            case RiskFlowModels.PredicateExpression predicate -> owner(predicate);
        };
    }
}

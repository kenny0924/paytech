package com.paytech.risk.ext.rest.decision;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.DecisionFlowModels;
import com.paytech.risk.api.decision.DecisionFlowModels.Condition;
import com.paytech.risk.api.decision.DecisionFlowModels.Edge;
import com.paytech.risk.api.decision.DecisionFlowModels.Expression;
import com.paytech.risk.api.decision.DecisionFlowModels.Field;
import com.paytech.risk.api.decision.DecisionFlowModels.Graph;
import com.paytech.risk.api.decision.DecisionFlowModels.GroupExpression;
import com.paytech.risk.api.decision.DecisionFlowModels.Issue;
import com.paytech.risk.api.decision.DecisionFlowModels.Limits;
import com.paytech.risk.api.decision.DecisionFlowModels.Node;
import com.paytech.risk.api.decision.DecisionFlowModels.NodeOutput;
import com.paytech.risk.api.decision.DecisionFlowModels.Position;
import com.paytech.risk.api.decision.DecisionFlowModels.PredicateExpression;
import com.paytech.risk.api.decision.DecisionFlowModels.RuleSetReference;
import com.paytech.risk.api.decision.DecisionFlowModels.Source;
import com.paytech.risk.api.decision.DecisionFlowModels.SupportedPoint;
import com.paytech.risk.api.decision.RiskVariableModels;
import com.paytech.risk.api.decision.request.SaveFlowRequestBody;
import com.paytech.risk.api.decision.response.DeleteFlowResponseBody;
import com.paytech.risk.api.decision.response.FlowAuthoringContextResponseBody;
import com.paytech.risk.api.decision.response.FlowDetailResponseBody;
import com.paytech.risk.api.decision.response.FlowHistoryItemResponseBody;
import com.paytech.risk.api.decision.response.FlowListItemResponseBody;
import com.paytech.risk.api.decision.response.LegacyPointResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReferenceResponseBody;
import com.paytech.risk.api.decision.response.SaveFlowResponseBody;
import com.paytech.risk.api.decision.response.ValidateFlowResponseBody;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.query.FlowAuthoringContextView;
import com.paytech.risk.decision.domain.query.FlowDetailView;
import com.paytech.risk.decision.domain.query.FlowHistoryItem;
import com.paytech.risk.decision.domain.query.FlowRuleSetReferenceView;
import com.paytech.risk.decision.domain.query.FlowSummaryView;
import com.paytech.risk.decision.domain.query.LegacyPointFlowView;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;

/** 显式转换配置合同；不把图形库字段或任意 Map 传进 Risk 业务层。 */
final class DecisionFlowWireConverter {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private DecisionFlowWireConverter() { }

    static DecisionFlow.Save command(SaveFlowRequestBody value) {
        return new DecisionFlow.Save(value.getPointCode(), value.getFlowCode(), value.getFlowName(), id(value.getBaseFlowVersionId()),
            id(value.getExpectedVersion()), value.getChangeReason(), new DecisionFlow.Graph(value.getGraph().getSchemaVersion(),
                value.getGraph().getNodes().stream().map(n -> new DecisionFlow.Node(n.getNodeKey(), n.getType(), n.getName(),
                    coordinate(n.getPosition().getX()), coordinate(n.getPosition().getY()), id(n.getRuleSetVersionId()), n.getDecisionCode(),
                    n.getReasonCode(), n.getReasonMessage(), n.getOutputs())).toList(), value.getGraph().getEdges().stream().map(e -> new DecisionFlow.Edge(
                    e.getEdgeKey(), e.getSourceNodeKey(), e.getTargetNodeKey(), e.getType(), e.getPriority(), e.getName(), condition(e.getCondition()))).toList()));
    }

    private static DecisionFlow.Condition condition(Condition value) {
        return value == null ? null : new DecisionFlow.Condition(value.getSchemaVersion(), expression(value.getRoot()));
    }

    private static DecisionFlow.Expression expression(Expression value) {
        return switch (value) {
            case GroupExpression g -> new DecisionFlow.Group(g.getOperator(), g.getChildren().stream().map(DecisionFlowWireConverter::expression).toList());
            case PredicateExpression p -> p.getOperands() != null ? new DecisionFlow.Predicate(null, null, p.getOperator(), null,
                p.getOperands().stream().map(o -> DecisionRuleSetWireConverter.toDomain(o).variableOperand()).toList())
                : new DecisionFlow.Predicate(p.getSource().getType(), p.getSource().getFieldCode(), p.getOperator(), p.getValues().stream().map(v -> {
                if (v instanceof String text) return (LiteralValue) new StringLiteral(text);
                if (v instanceof Boolean bool) return (LiteralValue) new BooleanLiteral(bool);
                throw new InvalidParameterException("条件字面量必须为字符串或布尔值");
            }).toList());
        };
    }

    static Graph graph(DecisionFlow.Graph graph) {
        return new Graph(graph.schemaVersion(), graph.nodes().stream().map(DecisionFlowWireConverter::node).toList(),
            graph.edges().stream().map(DecisionFlowWireConverter::edge).toList());
    }

    static Node node(DecisionFlow.Node n) {
        Node node = new Node(n.nodeKey(), n.type(), n.name(), new Position(Integer.toString(n.x()), Integer.toString(n.y())),
            n.ruleSetVersionId() == null ? null : n.ruleSetVersionId().toString(), n.decisionCode(), n.reasonCode(), n.reasonMessage());
        node.setOutputs(n.outputs().isEmpty() ? null : n.outputs());
        return node;
    }

    static Edge edge(DecisionFlow.Edge e) {
        return new Edge(e.edgeKey(), e.sourceNodeKey(), e.targetNodeKey(), e.type(), e.priority(), e.name(),
            e.condition() == null ? null : new Condition(e.condition().schemaVersion(), expression(e.condition().root())));
    }

    private static Expression expression(DecisionFlow.Expression value) {
        return switch (value) {
            case DecisionFlow.Group g -> new GroupExpression(g.operator(), g.children().stream().map(DecisionFlowWireConverter::expression).toList());
            case DecisionFlow.Predicate p -> p.operands() != null ? new PredicateExpression(null, p.operator(), null,
                p.operands().stream().map(DecisionRuleSetWireConverter::toResponse).toList())
                : new PredicateExpression(new Source(p.sourceType(), p.fieldCode()), p.operator(), p.values().stream().map(v -> switch (v) {
                case BooleanLiteral b -> (Object) b.value();
                case StringLiteral s -> (Object) s.value();
                default -> throw new ErrorException("条件字面量持久化类型损坏");
            }).toList());
        };
    }

    static FlowDetailResponseBody detail(FlowDetailView d) {
        var catalog = d.getCatalog();
        return new FlowDetailResponseBody(id(d.getFlowId()), d.getPointCode(), d.getFlowCode(), d.getFlowStatus(), id(d.getCurrentFlowVersionId()),
            d.getCurrentVersionNo(), id(d.getVersion()), id(d.getFlowVersionId()), d.getVersionNo(), d.getFlowName(), d.getInputContractCode(),
            d.getInputContractVersion(), id(d.getCreatedBy()), time(d.getCreateTime()), d.getChangeReason(), graph(d.getGraph()),
            d.getRuleSetReferences().stream().map(DecisionFlowWireConverter::reference).toList(),
            catalog.variables().values().stream().sorted(java.util.Comparator.comparing(v -> v.entry().definition().variableCode()))
                .map(RiskVariableWireConverter::summary).toList(), time(catalog.valuesAsOf()),
            catalog.issues().stream().map(i -> new RiskVariableModels.VariableIssue(i.variableCode(), i.code(), i.message())).toList());
    }

    static RuleSetReference reference(DecisionFlow.Reference r) {
        return new RuleSetReference(r.nodeKey(), id(r.ruleSetId()), r.ruleSetCode(), r.ruleSetName(), id(r.ruleSetVersionId()),
            r.versionNo(), r.ruleCount(), r.ruleSetStatus(), r.versionStatus());
    }

    static FlowListItemResponseBody summary(FlowSummaryView s) {
        return new FlowListItemResponseBody(id(s.getFlowId()), s.getPointCode(), s.getFlowCode(), s.getFlowName(), id(s.getCurrentFlowVersionId()),
            s.getCurrentVersionNo(), id(s.getVersion()), s.getRuleSetNodeCount(), id(s.getUpdatedBy()), time(s.getUpdateTime()));
    }

    static FlowHistoryItemResponseBody history(FlowHistoryItem h) {
        return new FlowHistoryItemResponseBody(id(h.getFlowVersionId()), h.getVersionNo(), h.getFlowName(), id(h.getCreatedBy()), time(h.getCreateTime()), h.getChangeReason());
    }

    static SaveFlowResponseBody saved(DecisionFlow.Saved s) {
        var response = new SaveFlowResponseBody();
        response.setFlowId(id(s.flowId()));
        response.setFlowVersionId(id(s.flowVersionId()));
        response.setVersionNo(s.versionNo());
        response.setVersion(id(s.version()));
        response.setSavedBy(id(s.savedBy()));
        response.setSavedTime(time(s.savedTime()));
        return response;
    }

    static DeleteFlowResponseBody deleted(DecisionFlow.Deleted d) {
        var response = new DeleteFlowResponseBody();
        response.setFlowId(id(d.flowId()));
        response.setStatus("DELETED");
        response.setVersion(id(d.version()));
        response.setDeletedBy(id(d.deletedBy()));
        response.setDeletedTime(time(d.deletedTime()));
        return response;
    }

    static ValidateFlowResponseBody validation(DecisionFlow.Validation v) {
        var response = new ValidateFlowResponseBody();
        response.setValid(v.valid());
        response.setIssues(v.issues().stream().map(i -> new Issue(i.code(), i.nodeKey(), i.edgeKey(), i.field(), i.message())).toList());
        response.setTruncated(v.truncated());
        return response;
    }

    static RuleSetReferenceResponseBody storedReference(FlowRuleSetReferenceView r) {
        return new RuleSetReferenceResponseBody(r.getSourceType(), id(r.getSourceVersionId()), r.getNodeKey(), r.getRuleSetStatus(),
            DecisionRuleSetWireConverter.toResponse(r.getRuleSet(), r.getCatalog()));
    }

    static LegacyPointResponseBody legacy(LegacyPointFlowView p) {
        return new LegacyPointResponseBody("LEGACY_POINT", id(p.getPointVersionId()), p.getPointCode(), p.getPointName(), p.getVersionNo(),
            p.getInputContractCode(), p.getInputContractVersion(), p.getDefaultDecision(), p.getEvaluationStrategy(), p.getFailureStrategy(),
            p.getNodes().stream().map(DecisionFlowWireConverter::node).toList(), p.getEdges().stream().map(DecisionFlowWireConverter::edge).toList(),
            p.getRuleSetReferences().stream().map(DecisionFlowWireConverter::reference).toList());
    }

    /** 仅将 Owner 已形成的能力投影转换为 HTTP 字段。 */
    static FlowAuthoringContextResponseBody authoring(FlowAuthoringContextView view) {
        var point = view.getDescriptor().point();
        FlowAuthoringContextResponseBody body = new FlowAuthoringContextResponseBody(List.of(new SupportedPoint(point.pointCode(), point.pointName())),
            point.pointCode(), point.inputContractCode(), point.inputContractVersion(), 1,
            view.getFields().stream().map(f -> new Field(f.getFieldCode(), f.getLabel(), f.getValueType(),
                f.isRequired(), f.getOperators(), f.getLiteralDomain(), f.getVariableCode())).toList(),
            view.getNodeOutputs().stream().map(f -> new NodeOutput(f.getNodeType(), f.getFieldCode(), f.getLabel(), f.getValueType(),
                f.isRequired(), f.getOperators(), f.getVariableCode())).toList(),
            new Limits(65536, 50, 100, 8192, 32, 8), "RISK_VARIABLE_AUTHORING", 2,
            new RiskVariableModels.VariableQueryContext(point.pointCode(), point.inputContractCode(), point.inputContractVersion(), "FLOW_CONDITION"));
        body.setOutputs(view.getDescriptor().outputs().stream().map(DecisionFlowWireConverter::output).toList());
        return body;
    }

    /** 决策点附加输出声明；可配置的结束决定与流程保存校验同源。 */
    static DecisionFlowModels.OutputDeclaration output(DecisionPointAuthoringDescriptor.OutputDescriptor output) {
        return new DecisionFlowModels.OutputDeclaration(output.outputCode(), output.name(),
            output.values().stream().map(value -> new DecisionFlowModels.OutputValue(value.value(), value.name())).toList(),
            output.defaultValue(), List.of(DecisionPointAuthoringDescriptor.OutputDescriptor.END_DECISION.getCode()));
    }

    private static Long id(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException failure) { throw new InvalidParameterException("标识或版本必须为正 BIGINT 字符串"); }
    }

    private static int coordinate(String value) {
        try {
            if (!value.matches("0|-?[1-9][0-9]{0,5}")) throw new NumberFormatException();
            int coordinate = Integer.parseInt(value);
            if (Math.abs(coordinate) > 100000) throw new NumberFormatException();
            return coordinate;
        } catch (NumberFormatException failure) { throw new InvalidParameterException("画布坐标格式或范围无效"); }
    }

    private static String id(long id) { return Long.toString(id); }

    private static String time(Instant time) { return UTC.format(time); }
}

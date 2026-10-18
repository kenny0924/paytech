package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.RiskConfigurationResponsePolicy.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.paytech.operations.console.bff.service.model.RiskFlowModels.*;

/** 只校验 Owner Wire 的类型、坐标及响应身份；拓扑和引用资格由 Risk 拥有。 */
final class RiskFlowResponsePolicy {
    private RiskFlowResponsePolicy() { }

    static void authoring(FlowAuthoringContextResponseBody r) {
        require(r != null && r.supportedPoints() != null && !r.supportedPoints().isEmpty());
        code(r.pointCode()); code(r.inputContractCode()); require(r.inputContractVersion() > 0 && r.graphSchemaVersion() == 1);
        require(r.supportedPoints().stream().anyMatch(p -> p.pointCode().equals(r.pointCode())));
        equal("RISK_VARIABLE_AUTHORING", r.descriptorCode()); require(r.conditionSchemaVersion() == 2);
        require(r.variableQueryContext() != null);
        var fields = new HashSet<String>();
        require(r.fields() != null && r.nodeOutputs() != null);
        for (Field f : r.fields()) {
            require(f.fieldCode() != null && f.fieldCode().matches("[a-z][a-z0-9_]{0,63}") && fields.add(f.variableCode()));
            text(f.label(), 100); one(f.valueType(), "STRING", "NUMBER", "BOOLEAN", "DATETIME", "DURATION");
            require(f.operators() != null && !f.operators().isEmpty());
            code(f.variableCode()); code(f.literalDomain());
        }
        require(r.limits() != null && r.limits().bodyBytes() == 65536 && r.limits().nodes() == 50 && r.limits().edges() == 100
            && r.limits().conditionBytes() == 8192 && r.limits().conditionNodes() == 32 && r.limits().conditionDepth() == 8);
    }

    static void summary(FlowListItemResponseBody r) {
        id(r.flowId()); code(r.pointCode()); code(r.flowCode()); text(r.flowName(), 100); id(r.currentFlowVersionId());
        id(r.version()); require(r.currentVersionNo() > 0 && Long.parseLong(r.version()) == r.currentVersionNo());
        require(r.ruleSetNodeCount() >= 0 && r.ruleSetNodeCount() <= 48); id(r.updatedBy()); time(r.updateTime());
    }

    static void history(FlowHistoryItemResponseBody r) {
        id(r.flowVersionId()); require(r.versionNo() > 0); text(r.flowName(), 100); id(r.createdBy()); time(r.createTime()); text(r.changeReason(), 500);
    }

    static void detail(FlowDetailResponseBody r) {
        require(r != null); id(r.flowId()); code(r.flowCode()); code(r.pointCode()); id(r.currentFlowVersionId()); id(r.flowVersionId()); id(r.version());
        one(r.flowStatus(), "CONFIGURABLE", "DELETED"); require(r.versionNo() > 0 && r.versionNo() <= r.currentVersionNo());
        require(Long.parseLong(r.version()) == (long) r.currentVersionNo() + ("DELETED".equals(r.flowStatus()) ? 1 : 0));
        require((r.versionNo() == r.currentVersionNo()) == r.flowVersionId().equals(r.currentFlowVersionId()));
        text(r.flowName(), 100); text(r.changeReason(), 500); code(r.inputContractCode()); require(r.inputContractVersion() > 0);
        id(r.createdBy()); time(r.createTime()); require(r.graph() != null && r.graph().schemaVersion() == 1);
        graph(r.graph().nodes(), r.graph().edges(), r.ruleSetReferences(), false);
    }

    static void graph(List<Node> nodes, List<Edge> edges, List<RuleSetReference> refs, boolean legacy) {
        require(nodes != null && edges != null && refs != null && nodes.size() <= (legacy ? 202 : 50)
            && edges.size() <= (legacy ? 201 : 100));
        var nodeKeys = new HashSet<String>();
        for (Node n : nodes) {
            key(n.nodeKey()); require(nodeKeys.add(n.nodeKey())); text(n.name(), 100); one(n.type(), "START", "RULE_SET", "END");
            require(n.position() != null); coordinate(n.position().x()); coordinate(n.position().y());
            if (n.ruleSetVersionId() != null) id(n.ruleSetVersionId());
            if (n.decisionCode() != null) one(n.decisionCode(), "ALLOW", "REVIEW", "DENY");
            if (n.reasonCode() != null) code(n.reasonCode()); if (n.reasonMessage() != null) text(n.reasonMessage(), 500);
        }
        var edgeKeys = new HashSet<String>();
        for (Edge e : edges) {
            key(e.edgeKey()); require(edgeKeys.add(e.edgeKey())); key(e.sourceNodeKey()); key(e.targetNodeKey());
            require(nodeKeys.contains(e.sourceNodeKey()) && nodeKeys.contains(e.targetNodeKey())); text(e.name(), 100);
            one(e.type(), "UNCONDITIONAL", "CONDITIONAL", "ELSE"); require(e.priority() >= 0 && e.priority() <= 100);
            require("CONDITIONAL".equals(e.type()) == (e.condition() != null));
        }
        var referenceKeys = new HashSet<String>();
        for (RuleSetReference r : refs) {
            key(r.nodeKey()); require(referenceKeys.add(r.nodeKey())); id(r.ruleSetId()); id(r.ruleSetVersionId()); code(r.ruleSetCode());
            text(r.ruleSetName(), 100); require(r.versionNo() > 0 && r.ruleCount() > 0 && r.ruleCount() <= 200);
            one(r.ruleSetStatus(), "CONFIGURABLE", "DELETED"); one(r.versionStatus(), "DRAFT", "FROZEN", "RETIRED");
            require(nodes.stream().anyMatch(n -> n.nodeKey().equals(r.nodeKey()) && "RULE_SET".equals(n.type())
                && Objects.equals(n.ruleSetVersionId(), r.ruleSetVersionId())));
        }
        require(nodes.stream().filter(n -> "RULE_SET".equals(n.type())).count() == refs.size());
    }

    static void validation(ValidationResponseBody r) {
        require(r != null && r.issues() != null && r.issues().size() <= 100);
        require(r.valid() == (r.issues().isEmpty() && !r.truncated()));
        for (Issue i : r.issues()) {
            code(i.code()); text(i.message(), 500);
            if (i.nodeKey() != null) key(i.nodeKey()); if (i.edgeKey() != null) key(i.edgeKey());
            if (i.field() != null) require(i.field().matches("[A-Za-z][A-Za-z0-9]*(?:(?:\\.[A-Za-z][A-Za-z0-9]*)|(?:\\[[0-9]+]))*"));
        }
    }

    static void key(String key) { require(key != null && key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")); }

    private static void coordinate(String value) {
        require(value != null && value.matches("0|-?[1-9][0-9]{0,5}") && Math.abs(Integer.parseInt(value)) <= 100000);
    }
}

package com.paytech.risk.decision.domain.flow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CanonicalHash;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;

/** Risk 拥有的显式投影和条件存储 Reader；Foundation 仅提供 JCS 字节。 */
@Component
public final class DecisionFlowJsonCodec {
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

    public CanonicalHash saveHash(Save command) {
        return hash("RISK_DECISION_FLOW_SAVE", object("pointCode", command.pointCode(), "flowCode", command.flowCode(),
            "flowName", command.flowName(), "baseFlowVersionId", id(command.baseFlowVersionId()),
            "expectedVersion", id(command.expectedVersion()), "changeReason", command.changeReason(),
            "graph", graph(command.graph())));
    }

    public CanonicalHash deleteHash(long flowId, long expectedVersion) {
        return hash("RISK_DECISION_FLOW_DELETE", object("flowId", Long.toString(flowId),
            "expectedVersion", Long.toString(expectedVersion)));
    }

    public byte[] conditionBytes(Condition condition) {
        return canonical(object("schemaVersion", condition.schemaVersion(), "root", expression(condition.root())));
    }

    public String conditionJson(Condition condition) {
        return condition == null ? null : new String(conditionBytes(condition), StandardCharsets.UTF_8);
    }

    /** 数据库中条件格式损坏必须报系统错误，不能读为空条件。 */
    public Condition readCondition(String json) {
        if (json == null) return null;
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 8192) throw new IllegalArgumentException();
            JsonNode node = JSON.readTree(Rfc8785JsonCanonicalizer.canonicalizeObject(bytes));
            keys(node, "schemaVersion", "root");
            if (!node.get("schemaVersion").isIntegralNumber() || !Set.of(1, 2).contains(node.get("schemaVersion").intValue()))
                throw new IllegalArgumentException();
            int version = node.get("schemaVersion").intValue();
            return new Condition(version, readExpression(node.get("root"), 1, new int[]{0}, version));
        } catch (RuntimeException failure) {
            throw new ErrorException("流程条件持久化内容损坏", failure);
        }
    }

    private Expression readExpression(JsonNode node, int depth, int[] count, int schemaVersion) {
        if (depth > 8 || ++count[0] > 32 || !node.isObject()) throw new IllegalArgumentException();
        String kind = string(node.get("kind"));
        if ("GROUP".equals(kind)) {
            keys(node, "kind", "operator", "children");
            if (!node.get("children").isArray()) throw new IllegalArgumentException();
            String operator = string(node.get("operator"));
            if (!Set.of("AND", "OR", "NOT").contains(operator)
                || ("NOT".equals(operator) ? node.get("children").size() != 1 : node.get("children").size() < 2)) throw new IllegalArgumentException();
            return new Group(operator, node.get("children").valueStream()
                .map(n -> readExpression(n, depth + 1, count, schemaVersion)).toList());
        }
        if (!"PREDICATE".equals(kind)) throw new IllegalArgumentException();
        if (schemaVersion == 2) {
            keys(node, "kind", "operator", "operands");
            if (!node.get("operands").isArray() || node.get("operands").size() < 1 || node.get("operands").size() > 101) {
                throw new IllegalArgumentException();
            }
            var operands = node.get("operands").valueStream().map(VariableOperandJson::read).toList();
            VariableOperandJson.validatePredicate(com.paytech.risk.decision.domain.enums.ComparisonOperator.valueOf(string(node.get("operator"))), operands);
            return new Predicate(null, null, string(node.get("operator")), null, operands);
        }
        keys(node, "kind", "source", "operator", "values");
        JsonNode source = node.get("source"); keys(source, "type", "fieldCode");
        if (!node.get("values").isArray()) throw new IllegalArgumentException();
        List<LiteralValue> values = node.get("values").valueStream().map(n -> {
            if (n.isString()) return (LiteralValue) new StringLiteral(n.stringValue());
            if (n.isBoolean()) return (LiteralValue) new BooleanLiteral(n.booleanValue());
            throw new IllegalArgumentException();
        }).toList();
        String type = string(source.get("type")); String field = string(source.get("fieldCode"));
        String operator = string(node.get("operator"));
        var comparison = com.paytech.risk.decision.domain.enums.ComparisonOperator.valueOf(operator);
        if ("NODE_RESULT".equals(type)) {
            if (!"matched".equals(field) || !Set.of("EQ", "NE").contains(operator) || values.size() != 1
                || !(values.getFirst() instanceof BooleanLiteral)) throw new IllegalArgumentException();
        } else if (!"CONTEXT".equals(type)
            || com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.checkoutPrepare().fields().stream().noneMatch(f -> f.fieldCode().equals(field))) {
            throw new IllegalArgumentException();
        }
        int required = comparison == com.paytech.risk.decision.domain.enums.ComparisonOperator.BETWEEN ? 2
            : Set.of("IS_NULL", "IS_NOT_NULL").contains(operator) ? 0 : 1;
        if (Set.of("IN", "NOT_IN").contains(operator) ? values.isEmpty() || values.size() > 100 : values.size() != required)
            throw new IllegalArgumentException();
        return new Predicate(type, field, operator, values);
    }

    private static String string(JsonNode node) {
        if (node == null || !node.isString()) throw new IllegalArgumentException();
        return node.stringValue();
    }

    private static void keys(JsonNode node, String... keys) {
        if (node == null || !node.isObject() || node.size() != keys.length
            || !node.propertyNames().equals(Set.of(keys))) throw new IllegalArgumentException();
    }

    private Object graph(Graph graph) {
        return object("schemaVersion", graph.schemaVersion(), "nodes", graph.nodes().stream().map(this::node).toList(),
            "edges", graph.edges().stream().map(e -> object("edgeKey", e.edgeKey(), "sourceNodeKey", e.sourceNodeKey(),
                "targetNodeKey", e.targetNodeKey(), "type", e.type(), "priority", e.priority(), "name", e.name(),
                "condition", e.condition() == null ? null : object("schemaVersion", e.condition().schemaVersion(),
                    "root", expression(e.condition().root())))).toList());
    }

    /** 节点投影；没有附加输出时不出现 outputs 键，保持既有流程的摘要不变。 */
    private Object node(Node n) {
        Map<String, Object> value = object("nodeKey", n.nodeKey(), "type", n.type(), "name", n.name(),
            "position", object("x", Integer.toString(n.x()), "y", Integer.toString(n.y())),
            "ruleSetVersionId", id(n.ruleSetVersionId()), "decisionCode", n.decisionCode(),
            "reasonCode", n.reasonCode(), "reasonMessage", n.reasonMessage());
        if (!n.outputs().isEmpty()) {
            value.put("outputs", new TreeMap<>(n.outputs()));
        }
        return value;
    }

    private Object expression(Expression expression) {
        return switch (expression) {
            case Group g -> object("kind", "GROUP", "operator", g.operator(), "children",
                g.children().stream().map(this::expression).toList());
            case Predicate p -> p.operands() != null
                ? object("kind", "PREDICATE", "operator", p.operator(), "operands", p.operands().stream().map(VariableOperandJson::projection).toList())
                : object("kind", "PREDICATE", "source", object("type", p.sourceType(), "fieldCode", p.fieldCode()),
                "operator", p.operator(), "values", p.values().stream().map(v -> switch (v) {
                    case StringLiteral s -> (Object) s.value();
                    case BooleanLiteral b -> (Object) b.value();
                    default -> throw new IllegalArgumentException("条件不支持空字面量");
                }).toList());
        };
    }

    private CanonicalHash hash(String domain, Object request) {
        byte[] bytes = canonical(object("domain", domain, "schemaVersion", 1, "request", request));
        try {
            return new CanonicalHash(domain, bytes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException failure) { throw new ErrorException("SHA-256 不可用", failure); }
    }

    private static byte[] canonical(Object value) { return Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(value)); }
    private static String id(Long value) { return value == null ? null : value.toString(); }
    private static Map<String, Object> object(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return result;
    }
}

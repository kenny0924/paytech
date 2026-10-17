package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 流程图与规则集引用 JSON 的共享断言
 * <li>节点按节点键、连线按来源节点键/优先级/连线键排序，与合同中的重建顺序一致。</li>
 * <li>对象断言同时核对字段集合，防止响应多出或缺少字段。</li>
 *
 * @author Kenny
 */
public final class FlowGraphChecker {
    /** 合同时间格式：UTC、固定六位小数。 */
    static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FlowGraphChecker() {
    }

    /** 完整图：结构版本 1，节点与连线逐字段核对。 */
    static void assertGraph(JsonNode graph, GraphSpec expected) {
        assertFields(graph, "schemaVersion", "nodes", "edges");
        assertEquals(1, graph.path("schemaVersion").asInt(), "graph.schemaVersion");
        assertNodes(graph.path("nodes"), expected.nodes().stream().sorted(Comparator.comparing(NodeSpec::nodeKey))
            .toList());
        assertEdges(graph.path("edges"), expected.edges().stream().sorted(Comparator.comparing(EdgeSpec::sourceNodeKey)
            .thenComparingInt(EdgeSpec::priority).thenComparing(EdgeSpec::edgeKey)).toList());
    }

    /** 节点按给定顺序逐字段核对。 */
    static void assertNodes(JsonNode actual, List<NodeSpec> expected) {
        assertEquals(expected.size(), actual.size(), "节点数量不符");
        for (int i = 0; i < expected.size(); i++) {
            NodeSpec node = expected.get(i);
            JsonNode item = actual.get(i);
            assertFields(item, "nodeKey", "type", "name", "position", "ruleSetVersionId", "decisionCode", "reasonCode",
                "reasonMessage");
            assertFields(item.path("position"), "x", "y");
            assertAll("节点 " + node.nodeKey(), () -> assertEquals(node.nodeKey(), text(item.path("nodeKey"))),
                () -> assertEquals(node.type().getCode(), text(item.path("type"))),
                () -> assertEquals(node.name(), text(item.path("name"))),
                () -> assertEquals(Integer.toString(node.x()), text(item.path("position").path("x"))),
                () -> assertEquals(Integer.toString(node.y()), text(item.path("position").path("y"))),
                () -> assertEquals(id(node.ruleSetVersionId()), text(item.path("ruleSetVersionId"))),
                () -> assertEquals(node.decision() == null ? null : node.decision().getCode(),
                    text(item.path("decisionCode"))),
                () -> assertEquals(node.reasonCode(), text(item.path("reasonCode"))),
                () -> assertEquals(node.reasonMessage(), text(item.path("reasonMessage"))));
        }
    }

    /** 连线按给定顺序逐字段核对，条件与存储 JSON 结构等价。 */
    static void assertEdges(JsonNode actual, List<EdgeSpec> expected) {
        assertEquals(expected.size(), actual.size(), "连线数量不符");
        for (int i = 0; i < expected.size(); i++) {
            EdgeSpec edge = expected.get(i);
            JsonNode item = actual.get(i);
            assertFields(item, "edgeKey", "sourceNodeKey", "targetNodeKey", "type", "priority", "name", "condition");
            assertAll("连线 " + edge.edgeKey(), () -> assertEquals(edge.edgeKey(), text(item.path("edgeKey"))),
                () -> assertEquals(edge.sourceNodeKey(), text(item.path("sourceNodeKey"))),
                () -> assertEquals(edge.targetNodeKey(), text(item.path("targetNodeKey"))),
                () -> assertEquals(edge.type().getCode(), text(item.path("type"))),
                () -> assertEquals(edge.priority(), item.path("priority").asInt()),
                () -> assertEquals(edge.name(), text(item.path("name"))));
            if (edge.conditionJson() == null) {
                assertTrue(item.path("condition").isNull(), "非条件连线不应携带条件");
            } else {
                assertJsonEquivalent(JSON.readTree(edge.conditionJson()), item.path("condition"), "condition");
            }
        }
    }

    /** 规则集节点引用：准确版本身份与当前管理资格。 */
    static void assertReference(JsonNode actual, String nodeKey, MockedRuleSet ruleSet, int versionNo,
        RuleSetManagementStatus ruleSetStatus) {
        MockedVersion version = ruleSet.version(versionNo);
        assertFields(actual, "nodeKey", "ruleSetId", "ruleSetCode", "ruleSetName", "ruleSetVersionId", "versionNo",
            "ruleCount", "ruleSetStatus", "versionStatus");
        assertAll("引用 " + nodeKey, () -> assertEquals(nodeKey, text(actual.path("nodeKey"))),
            () -> assertEquals(Long.toString(ruleSet.ruleSetId()), text(actual.path("ruleSetId"))),
            () -> assertEquals(ruleSet.ruleSetCode(), text(actual.path("ruleSetCode"))),
            () -> assertEquals(version.spec().ruleSetName(), text(actual.path("ruleSetName"))),
            () -> assertEquals(Long.toString(version.ruleSetVersionId()), text(actual.path("ruleSetVersionId"))),
            () -> assertEquals(versionNo, actual.path("versionNo").asInt()),
            () -> assertEquals(1, actual.path("ruleCount").asInt()),
            () -> assertEquals(ruleSetStatus.getCode(), text(actual.path("ruleSetStatus"))),
            () -> assertEquals(version.spec().status().getCode(), text(actual.path("versionStatus"))));
    }

    /** 结构等价：对象逐键比较，缺失键视同显式 null。 */
    static void assertJsonEquivalent(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject()) {
            assertTrue(actual.isObject(), path + " 应为对象");
            Set<String> keys = new TreeSet<>(expected.propertyNames());
            actual.propertyNames().forEach(keys::add);
            for (String key : keys) {
                JsonNode left = expected.path(key);
                JsonNode right = actual.path(key);
                if (left.isMissingNode() || left.isNull()) {
                    assertTrue(right.isMissingNode() || right.isNull(), path + "." + key + " 应为 null");
                } else {
                    assertJsonEquivalent(left, right, path + "." + key);
                }
            }
        } else if (expected.isArray()) {
            assertEquals(expected.size(), actual.size(), path + " 数量不符");
            for (int i = 0; i < expected.size(); i++) {
                assertJsonEquivalent(expected.get(i), actual.get(i), path + "[" + i + "]");
            }
        } else {
            assertEquals(expected, actual, path);
        }
    }

    /** 对象恰好包含给定字段。 */
    static void assertFields(JsonNode actual, String... names) {
        assertTrue(actual.isObject(), "应为 JSON 对象：" + actual);
        assertEquals(new TreeSet<>(List.of(names)), new TreeSet<>(actual.propertyNames()), "字段集合不符");
    }

    /** 字符串或显式 null。 */
    static String text(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    static String time(Instant value) {
        return value == null ? null : TIME.format(value);
    }

    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** 时间字符串符合合同格式且位于给定区间内（含边界）。 */
    static Instant assertTimeWithin(String actual, Instant from, Instant to, String field) {
        assertTrue(actual != null && actual.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"),
            field + " 格式不符：" + actual);
        Instant value = Instant.parse(actual);
        assertTrue(!value.isBefore(from) && !value.isAfter(to), field + " 不在请求时间窗口内：" + actual);
        return value;
    }
}

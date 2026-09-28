package com.paytech.operations.console.bff.test.simulator;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Request;

/** 变量 Owner HTTP 场景：响应由请求和当前目录派生，不接入本地数据库替身。 */
final class RiskVariableScenario {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/decision-variables/v1";
    private static final String TIME = RiskDecisionConfigurationSimulator.TIME;
    private final Map<String, ObjectNode> entries = new LinkedHashMap<>();
    private final Map<String, List<ObjectNode>> members = new HashMap<>();
    void reset() { entries.clear(); members.clear(); }

    boolean known(String method, String path) {
        return method.equals("GET") && path.equals(ROOT + "/authoring-context")
            || Set.of("GET", "POST").contains(method) && path.equals(ROOT + "/variables")
            || Set.of("GET", "PUT", "DELETE").contains(method) && path.matches(ROOT + "/variables/[A-Z][A-Z0-9_]{0,63}")
            || Set.of("GET", "PUT").contains(method) && path.matches(ROOT + "/variables/[A-Z][A-Z0-9_]{0,63}/members")
            || method.equals("GET") && path.matches(ROOT + "/variables/[A-Z][A-Z0-9_]{0,63}/references");
    }

    Object dispatch(Request request) {
        String path = request.path().substring(ROOT.length());
        var q = query(request.query());
        if (path.equals("/authoring-context")) return JSON.readTree("""
            {"supportedPoints":[{"pointCode":"CHECKOUT_PREPARE","pointName":"结账准备"}],
             "sources":[{"sourceType":"CONSTANT","createAllowed":true},{"sourceType":"COLLECTION","createAllowed":true}],
             "valueTypes":["STRING","NUMBER","BOOLEAN","DATETIME","DURATION"],
             "formatProfiles":[{"formatCode":"TEXT","label":"文本","help":"保留原值"},{"formatCode":"NONE","label":"无","help":"类型约束"}],
             "numericUnits":["SCALAR","MONEY"],"limits":{"memberCount":1000,"memberBodyBytes":65536,"literalSetCount":100},"contracts":[]}
            """);
        if (path.equals("/variables") && request.method().equals("POST")) {
            var root = (ObjectNode) JSON.readTree(request.body()); String code = root.path("variableCode").asString();
            if (entries.containsKey(code)) throw new RiskDecisionConfigurationSimulator.SemanticFailure(409, "VARIABLE_CODE_EXISTS");
            root.put("version", "1").put("systemManaged", false).put("createBy", request.header("X-Gateway-Actor-Subject"))
                .put("updateBy", request.header("X-Gateway-Actor-Subject")).put("createTime", TIME).put("updateTime", TIME).put("valuesAsOf", TIME);
            root.putNull("allowedValues"); root.putArray("previewMembers");
            if (root.path("sourceType").asString().equals("COLLECTION")) root.put("memberCount", 0); else root.putNull("memberCount");
            entries.put(code, root); return root.deepCopy();
        }
        if (path.equals("/variables")) {
            int page = Integer.parseInt(q.getOrDefault("page", "1")), size = Integer.parseInt(q.getOrDefault("size", "20"));
            var result = JSON.createObjectNode(); var items = result.putArray("items");
            if (q.containsKey("codes")) {
                var missing = result.putArray("missingCodes");
                for (String code : q.get("codes").split(",")) { if (entries.containsKey(code)) items.add(summary(entries.get(code))); else missing.add(code); }
            } else {
                var selected = entries.values().stream().filter(v -> q.entrySet().stream().filter(e -> Set.of("sourceType", "valueType", "status").contains(e.getKey()))
                    .allMatch(e -> v.path(e.getKey()).asString().equals(e.getValue()))).toList();
                selected.stream().skip((long)(page - 1) * size).limit(size).forEach(v -> items.add(summary(v)));
                result.put("page", page).put("size", size).put("total", selected.size());
            }
            return result.put("valuesAsOf", TIME);
        }
        String code = path.split("/")[2]; var root = entries.get(code);
        if (root == null) throw new RiskDecisionConfigurationSimulator.SemanticFailure(404, "VARIABLE_NOT_FOUND");
        if (path.endsWith("/references")) {
            var result = JSON.createObjectNode(); result.putArray("items"); return result.putNull("nextCursor").put("hasMore", false).put("scannedRows", 0);
        }
        if (path.endsWith("/members") && request.method().equals("GET")) {
            if (q.containsKey("expectedVersion")) expected(root, q.get("expectedVersion"));
            int page = Integer.parseInt(q.getOrDefault("page", "1")), size = Integer.parseInt(q.getOrDefault("size", "50"));
            var result = JSON.createObjectNode().put("variableCode", code).put("version", root.path("version").asString());
            var rows = members.getOrDefault(code, List.of()); var items = result.putArray("items");
            rows.stream().skip((long)(page - 1) * size).limit(size).forEach(items::add);
            return result.put("page", page).put("size", size).put("total", rows.size()).put("valuesAsOf", TIME);
        }
        if (request.method().equals("GET")) return root.deepCopy();
        var body = request.body().isEmpty() ? JSON.createObjectNode() : JSON.readTree(request.body());
        expected(root, request.method().equals("DELETE") ? q.get("expectedVersion") : body.path("expectedVersion").asString());
        String next = Long.toString(Long.parseLong(root.path("version").asString()) + 1);
        root.put("version", next).put("updateTime", TIME).put("updateBy", request.header("X-Gateway-Actor-Subject"));
        if (request.method().equals("DELETE")) {
            root.put("status", "DELETED"); return Map.of("variableCode", code, "status", "DELETED", "version", next);
        }
        if (path.endsWith("/members")) {
            var rows = new ArrayList<ObjectNode>(); var preview = root.putArray("previewMembers"); int order = 1;
            for (var value : body.get("members")) { var member = (ObjectNode)value.deepCopy(); if (order <= 100) preview.add(member.deepCopy()); member.put("sortOrder", order++); rows.add(member); }
            members.put(code, rows); root.put("memberCount", rows.size());
            return Map.of("variableCode", code, "version", next, "memberCount", rows.size(), "updateTime", TIME);
        }
        for (String field : List.of("variableName", "description", "currentValue", "status")) root.set(field, body.get(field));
        return root.deepCopy();
    }

    private static ObjectNode summary(ObjectNode root) {
        var copy = root.deepCopy();
        copy.remove(List.of("description", "sourceContractCode", "sourceContractVersion", "sourceField", "createBy", "updateBy", "createTime", "valuesAsOf", "allowedValues"));
        return copy;
    }
    private static void expected(ObjectNode root, String version) {
        if (!root.path("version").asString().equals(version)) throw new RiskDecisionConfigurationSimulator.SemanticFailure(409, "VARIABLE_VERSION_CONFLICT");
    }
    private static Map<String, String> query(String raw) {
        var result = new HashMap<String, String>();
        if (raw != null) for (String pair : raw.split("&")) { var p = pair.split("=", 2); result.put(p[0], java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8)); }
        return result;
    }
}

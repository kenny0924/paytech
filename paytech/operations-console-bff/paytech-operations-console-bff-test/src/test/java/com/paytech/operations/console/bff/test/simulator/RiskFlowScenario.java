package com.paytech.operations.console.bff.test.simulator;

import com.paytech.risk.api.decision.request.*;
import com.paytech.risk.api.decision.response.*;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionFlowModels.*;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;

/** 外部 Risk 配置存储场景，保存和回放从请求及历史状态派生。 */
final class RiskFlowScenario {
    private static final String ROOT = "/decision-flows/v1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final List<FlowDetailResponseBody> versions = new ArrayList<>();
    private final Map<String, Object> receipts = new HashMap<>();
    private boolean deleted;
    void reset() { versions.clear(); receipts.clear(); deleted = false; }
    boolean known(String method, String path) { return path.startsWith(ROOT) && Set.of("GET", "POST", "DELETE").contains(method); }
    Object dispatch(RiskDecisionConfigurationSimulator.Request r, RuleSetDetailResponseBody rules) {
        var query = new HashMap<String, String>();
        if (r.query() != null) for (String pair : r.query().split("&")) {
            var parts = pair.split("=", 2); query.put(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        int page = Integer.parseInt(query.getOrDefault("page", "1")), size = Integer.parseInt(query.getOrDefault("size", "10"));
        String path = r.path().substring(ROOT.length()), actor = r.header("X-Gateway-Actor-Subject");
        String time = RiskDecisionConfigurationSimulator.TIME;
        if (path.equals("authoring-context") && "PAYMENT_ATTEMPT_RISK".equals(query.get("pointCode"))) {
            // 支付点声明认证要求附加输出；结账点不输出 outputs 键。
            var context = new FlowAuthoringContextResponseBody(
                List.of(new SupportedPoint("CHECKOUT_PREPARE", "结账准备"), new SupportedPoint("PAYMENT_ATTEMPT_RISK", "支付尝试")),
                "PAYMENT_ATTEMPT_RISK", "PAYMENT_ATTEMPT", 1, 1, List.of(), List.of(), new Limits(65536, 50, 100, 8192, 32, 8),
                "RISK_VARIABLE_AUTHORING", 2,
                new com.paytech.risk.api.decision.RiskVariableModels.VariableQueryContext("PAYMENT_ATTEMPT_RISK", "PAYMENT_ATTEMPT", 1, "FLOW_CONDITION"));
            context.setOutputs(List.of(new OutputDeclaration("AUTHENTICATION_REQUIREMENT", "认证要求",
                List.of(new OutputValue("NOT_REQUIRED", "无需认证"), new OutputValue("THREE_DS_REQUIRED", "需要 3DS")), "NOT_REQUIRED", List.of("ALLOW"))));
            return context;
        }
        if (path.equals("authoring-context")) return new FlowAuthoringContextResponseBody(
            List.of(new SupportedPoint("CHECKOUT_PREPARE", "结账准备")), "CHECKOUT_PREPARE", "ORDER_PREPARATION", 4, 1,
            List.of(new Field("country_code", "国家", "STRING", true, List.of("EQ", "NE"), "UPPER_ALPHA_2", "REGISTERED_COUNTRY")),
            List.of(new NodeOutput("RULE_SET", "matched", "本节点是否命中", "BOOLEAN", true, List.of("EQ", "NE"), "REGISTERED_MATCHED")),
            new Limits(65536, 50, 100, 8192, 32, 8), "RISK_VARIABLE_AUTHORING", 2,
            new com.paytech.risk.api.decision.RiskVariableModels.VariableQueryContext("CHECKOUT_PREPARE", "ORDER_PREPARATION", 4, "FLOW_CONDITION"));
        if (path.equals("flows/validate")) {
            var request = JSON.readValue(r.body(), SaveFlowRequestBody.class);
            var issues = request.getGraph().getNodes().stream().filter(n -> n.getType().equals("END") && n.getDecisionCode() == null)
                .map(n -> new Issue("END_DECISION_REQUIRED", n.getNodeKey(), null, "graph.nodes[1].decisionCode", "请选择结束决定")).toList();
            var validation = new ValidateFlowResponseBody(); validation.setValid(issues.isEmpty()); validation.setIssues(issues); validation.setTruncated(false);
            return validation;
        }
        if (path.equals("flows") && r.method().equals("POST")) {
            String key = "SAVE:" + actor + ":" + r.body(); if (receipts.containsKey(key)) return receipts.get(key);
            var request = JSON.readValue(r.body(), SaveFlowRequestBody.class); int no = versions.size() + 1;
            String id = Integer.toString(60000 + no);
            versions.add(new FlowDetailResponseBody("50001", request.getPointCode(), request.getFlowCode(), "CONFIGURABLE", id, no,
                Integer.toString(no), id, no, request.getFlowName(), "ORDER_PREPARATION", 4, actor, time, request.getChangeReason(), request.getGraph(), List.of(), List.of(), time, List.of()));
            var receipt = new SaveFlowResponseBody(); receipt.setFlowId("50001"); receipt.setFlowVersionId(id); receipt.setVersionNo(no);
            receipt.setVersion(Integer.toString(no)); receipt.setSavedBy(actor); receipt.setSavedTime(time); receipts.put(key, receipt); return receipt;
        }
        if (path.equals("flows") && r.method().equals("GET")) {
            var items = new ArrayList<FlowListItemResponseBody>();
            if (!versions.isEmpty() && !deleted) { var head = versions.getLast();
                items.add(new FlowListItemResponseBody(head.getFlowId(), head.getPointCode(), head.getFlowCode(), head.getFlowName(), head.getFlowVersionId(),
                    head.getVersionNo(), head.getVersion(), 0, head.getCreatedBy(), time)); }
            return new PagingResponseBody<>(items, page, size, items.size());
        }
        if (r.method().equals("DELETE")) {
            String key = "DELETE:" + actor + ":" + r.path() + "?" + r.query(); if (receipts.containsKey(key)) return receipts.get(key);
            deleted = true; var receipt = new DeleteFlowResponseBody(); receipt.setFlowId("50001"); receipt.setStatus("DELETED");
            receipt.setVersion(Integer.toString(versions.size() + 1)); receipt.setDeletedBy(actor); receipt.setDeletedTime(time);
            receipts.put(key, receipt); return receipt;
        }
        if (path.endsWith("/versions")) {
            int max = Integer.parseInt(query.get("maxVersionNo")); var items = versions.reversed().stream().filter(v -> v.getVersionNo() <= max)
                .map(v -> new FlowHistoryItemResponseBody(v.getFlowVersionId(), v.getVersionNo(), v.getFlowName(), v.getCreatedBy(), time, v.getChangeReason())).toList();
            return new PagingResponseBody<>(items.stream().skip((long)(page - 1) * size).limit(size).toList(), page, size, items.size());
        }
        if (path.equals("rule-set-reference")) return new RuleSetReferenceResponseBody(query.get("sourceType"), query.get("sourceVersionId"), query.get("nodeKey"), "CONFIGURABLE", rules);
        if (path.startsWith("legacy-point-versions/")) {
            var nodes = List.of(new Node("start", "START", "开始", new Position("0", "0"), null, null, null, null),
                new Node("binding_1", "RULE_SET", "原规则", new Position("250", "0"), rules.getRuleSetVersionId(), null, null, null),
                new Node("end", "END", "原默认决定", new Position("500", "0"), null, "REVIEW", null, null));
            var edges = List.of(new Edge("first", "start", "binding_1", "UNCONDITIONAL", 0, "继续", null), new Edge("last", "binding_1", "end", "UNCONDITIONAL", 0, "继续", null));
            return new LegacyPointResponseBody("LEGACY_POINT", path.substring(path.lastIndexOf('/') + 1), "CHECKOUT_PREPARE", "结账准备", 1, "ORDER_PREPARATION", 4, "REVIEW", "FIRST_TERMINAL", "FAIL_CLOSED", nodes, edges,
                List.of(new RuleSetReference("binding_1", "90001", rules.getRuleSetCode(), rules.getRuleSetName(), rules.getRuleSetVersionId(), 1, 1, "CONFIGURABLE", "FROZEN")));
        }
        var selected = path.contains("/versions/") ? versions.stream().filter(v -> path.endsWith("/" + v.getFlowVersionId())).findFirst().orElseThrow() : versions.getLast();
        var head = versions.getLast();
        return new FlowDetailResponseBody(selected.getFlowId(), selected.getPointCode(), selected.getFlowCode(), deleted ? "DELETED" : "CONFIGURABLE", head.getFlowVersionId(), head.getVersionNo(),
            Integer.toString(versions.size() + (deleted ? 1 : 0)), selected.getFlowVersionId(), selected.getVersionNo(), selected.getFlowName(), selected.getInputContractCode(), selected.getInputContractVersion(), selected.getCreatedBy(), time, selected.getChangeReason(), selected.getGraph(), selected.getRuleSetReferences(), List.of(), time, List.of());
    }
}

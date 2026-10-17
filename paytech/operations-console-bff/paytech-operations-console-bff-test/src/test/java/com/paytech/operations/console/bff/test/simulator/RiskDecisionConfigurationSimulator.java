package com.paytech.operations.console.bff.test.simulator;

import com.paytech.risk.api.decision.request.*;
import com.paytech.risk.api.decision.response.*;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.paytech.foundation.web.paging.PagingResponseBody;

/** 有状态 Risk 协议边界；故障只在 HTTP 层注入，业务结果由请求和场景存储计算。 */
public final class RiskDecisionConfigurationSimulator implements AutoCloseable {
    public static final String HASH = "a".repeat(64);
    public static final String TIME = "2026-09-07T01:02:03.123456Z";
    public static final String UPSTREAM_REQUEST_ID = "risk-private-upstream-request";
    public static final String UPSTREAM_DETAIL_CANARY = "risk-private-detail-canary";
    public static final List<String> CHECKS = List.of("REFERENCE_INTEGRITY", "AST_SHAPE", "AST_TYPES",
        "FEATURE_DEPENDENCY", "HANDLER_HASH", "ACTION_PARAMETERS", "TERMINAL_ACTION", "SYSTEM_OBLIGATION");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RULES = "/decision-rule-sets/v1/rule-sets";
    private static final String CANDIDATES = "/decision-releases/v1/candidates";
    private final HttpServer server;
    private final AtomicLong journalSequence;
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Request> journal = new CopyOnWriteArrayList<>();
    private final List<String> unexpected = new CopyOnWriteArrayList<>();
    private final List<String> responses = new CopyOnWriteArrayList<>();
    private final Map<String, RuleSetDetailResponseBody> ruleSets = new LinkedHashMap<>();
    private final Map<String, RuleSetState> ruleRoots = new LinkedHashMap<>();
    private final Map<String, CandidateState> candidates = new LinkedHashMap<>();
    private final RiskVariableScenario variables = new RiskVariableScenario();
    private final RiskFlowScenario flow = new RiskFlowScenario();
    private final RiskDictionaryScenario dictionary = new RiskDictionaryScenario();
    private final RiskControlScenario control = new RiskControlScenario();
    private final RiskEvaluationScenario evaluation = new RiskEvaluationScenario();
    private final Map<String, CompletedCommand> commands = new LinkedHashMap<>();
    private volatile Mode mode = Mode.SUCCESS;
    private volatile Consumer<ObjectNode> wireMutation = ignored -> {};
    private volatile CountDownLatch releaseTimeout = new CountDownLatch(1);
    private volatile CountDownLatch requestArrival = new CountDownLatch(1);
    private boolean staticValidationFails;
    private boolean staleCommandReplay;
    private boolean legacyError;
    private boolean timeoutAfterStart;
    private int problemStatus = 400;
    private List<Map<String, String>> problemFields = List.of();
    private String problemCode = "BUSINESS_CHECK_FAILED";
    private long nextRule = 30002;
    private long nextRelease = 40002;

    public enum Mode {
        SUCCESS,

        PROBLEM,

        MALFORMED_SUCCESS,

        REDIRECT,

        TIMEOUT,

        DISCONNECT
    }

    public RiskDecisionConfigurationSimulator() throws IOException {
        this(new AtomicLong());
    }

    public RiskDecisionConfigurationSimulator(AtomicLong journalSequence) throws IOException {
        this.journalSequence = journalSequence;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        reset();
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public List<Request> requests() { return List.copyOf(journal); }
    public List<String> unexpectedRequests() { return List.copyOf(unexpected); }
    public List<String> responses() { return List.copyOf(responses); }
    public boolean awaitRequestArrival(Duration timeout) throws InterruptedException {
        return requestArrival.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
    public void mode(Mode value) { mode = value; }
    public void corruptWire(Consumer<ObjectNode> mutation) { wireMutation = mutation; }
    public void problem(int status, String code) { problemStatus = status; problemCode = code; mode = Mode.PROBLEM; }
    public void problemFields(List<Map<String, String>> fields) { problemFields = List.copyOf(fields); }
    public void legacyError(int status, String code) { legacyError = true; problem(status, code); }
    public void timeoutAfterValidationStarted() { timeoutAfterStart = true; mode = Mode.TIMEOUT; }
    public synchronized void validationFails(boolean value) { staticValidationFails = value; }
    public synchronized void staleCommandReplay() { staleCommandReplay = true; }

    public synchronized void reset() {
        releaseTimeout.countDown();
        releaseTimeout = new CountDownLatch(1);
        requestArrival.countDown();
        requestArrival = new CountDownLatch(1);
        mode = Mode.SUCCESS;
        problemFields = List.of();
        wireMutation = ignored -> {};
        staticValidationFails = false;
        staleCommandReplay = false;
        legacyError = false;
        timeoutAfterStart = false;
        problemStatus = 400;
        problemCode = "BUSINESS_CHECK_FAILED";
        journal.clear();
        unexpected.clear();
        responses.clear();
        commands.clear();
        variables.reset();
        dictionary.reset();
        flow.reset();
        control.reset();
        evaluation.reset();
        ruleSets.clear();
        ruleRoots.clear();
        candidates.clear();
        nextRule = 30002;
        nextRelease = 40002;
        addRuleSet("30001", "FROZEN");
        candidateState("DRAFT");
    }

    public synchronized void addRuleSet(String id, String status) {
        ruleSets.put(id, new RuleSetDetailResponseBody(id, "20001", "CHECKOUT_PREPARE", id.equals("30001") ? "DEFAULT_RULES" : "RULES_" + id,
            "规则集", 1, status, HASH, "10001", "初始版本", TIME,
            status.equals("FROZEN") ? "10001" : null, status.equals("FROZEN") ? TIME : null, sampleRules()));
        var rule = ruleSets.get(id);
        ruleRoots.put(rule.getRuleSetCode(), new RuleSetState(Long.toString(90001 + ruleRoots.size())));
    }

    /** 登记完整的规则集版本详情（含条件树与当前变量），供评估解释组装条件。 */
    public synchronized void addRuleSet(RuleSetDetailResponseBody detail) {
        ruleSets.put(detail.getRuleSetVersionId(), detail);
        ruleRoots.putIfAbsent(detail.getRuleSetCode(), new RuleSetState(Long.toString(90001 + ruleRoots.size())));
    }

    /** 登记一条评估的结论与解释 DTO。 */
    public synchronized void addEvaluation(com.paytech.risk.api.evaluation.response.EvaluationResponseBody summary,
        com.paytech.risk.api.evaluation.response.EvaluationExplanationResponseBody explanation) {
        evaluation.add(summary, explanation);
    }

    public synchronized void candidateState(String state) {
        candidates.put("40001", new CandidateState("40001", 1, "30001", "10001", state));
    }

    public synchronized void addCandidate(String id) {
        candidates.put(id, new CandidateState(id, 2, "30001", "10001", "DRAFT"));
    }

    public static List<DecisionRuleSetManagementModels.RuleRequestBody> sampleRules() {
        var operands = List.of(new DecisionRuleSetManagementModels.OperandRequestBody("LEFT", 1, "CONTEXT", "country_code", "STRING", null),
            new DecisionRuleSetManagementModels.OperandRequestBody("RIGHT", 1, "LITERAL", null, "STRING", "AE"));
        return List.of(new DecisionRuleSetManagementModels.RuleRequestBody("ALLOW_RULE", "允许规则", 1, true, null,
            List.of(new DecisionRuleSetManagementModels.ConditionNodeRequestBody("8", null, 1, "GROUP", "AND", null, List.of()),
                new DecisionRuleSetManagementModels.ConditionNodeRequestBody("9", "8", 1, "PREDICATE", null, "EQ", operands),
                new DecisionRuleSetManagementModels.ConditionNodeRequestBody("10", "8", 1, "PREDICATE", null, "EQ", operands)),
            new DecisionRuleSetManagementModels.RuleActionRequestBody("ALLOW_CHECKOUT", 1), null, List.of()));
    }

    public static RuleAuthoringContextResponseBody authoring() { return authoring("CHECKOUT_PREPARE"); }

    /** 支付点的终态动作以 _PAYMENT 结尾，其余目录与结账点一致。 */
    public static RuleAuthoringContextResponseBody authoring(String pointCode) {
        String suffix = pointCode.equals("PAYMENT_ATTEMPT_RISK") ? "_PAYMENT" : "_CHECKOUT";
        var point = new DecisionRuleSetManagementModels.DecisionPointDescriptorResponseBody(pointCode, "Checkout Prepare", "CHECKOUT",
            "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT", "REVIEW", "FIRST_TERMINAL", "FAIL_CLOSED", 500, "ACTIVE");
        var fields = List.of(new DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody("country_code", "STRING", true,
            "INTERNAL", true, false, "REGISTERED_COUNTRY"), new DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody("order_amount", "NUMBER",
            true, "INTERNAL", true, false, "REGISTERED_ORDER_AMOUNT"));
        var actions = List.of(action("ALLOW" + suffix, "ALLOW"), action("DENY" + suffix, "DENY"),
            action("REVIEW" + suffix, "REVIEW"));
        return new RuleAuthoringContextResponseBody("RISK_VARIABLE_AUTHORING", point, fields,
            List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL"),
            actions, List.of(), List.of(), List.of(), List.of(), List.of(), "VARIABLE_REFERENCE",
            new com.paytech.risk.api.decision.RiskVariableModels.VariableQueryContext(pointCode, "ORDER_PREPARATION", 4, "RULE_CONDITION"));
    }

    private static DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody action(String code, String decision) {
        return new DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody(code, "Action " + decision, "DECISION", 1,
            "CHECKOUT_DECISION", "1.0.0", HASH, true, decision, "MOST_RESTRICTIVE", true, "ACTIVE");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestArrival.countDown();
        long sequence = journalSequence.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, List.copyOf(values)));
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            exchange.getRequestURI().getRawQuery(), Map.copyOf(headers), body, sequence);
        journal.add(request);
        // 评估查询为内部直连，不得携带 Gateway 身份头；其他 Risk 管理接口必须携带。
        boolean internal = evaluation.known(request.method(), request.path());
        boolean identity = request.header("X-Gateway-Actor-Subject") != null && request.header("X-Gateway-Session-Id") != null;
        boolean anyIdentity = request.header("X-Gateway-Actor-Subject") != null || request.header("X-Gateway-Session-Id") != null;
        if (!known(request) || (internal ? anyIdentity : !identity) || request.header("X-Request-Id") == null
            || request.header("Idempotency-Key") != null) {
            unexpected.add(request.method() + " " + request.path());
            sendProblem(exchange, 500, "SYSTEM_ERROR");
            return;
        }
        if (mode == Mode.PROBLEM) { sendProblem(exchange, problemStatus, problemCode); return; }
        if (mode == Mode.REDIRECT) {
            exchange.getResponseHeaders().set("Location", baseUrl() + "/unexpected-redirect");
            send(exchange, 307, "application/json", "{}");
            return;
        }
        if (mode == Mode.TIMEOUT) {
            if (timeoutAfterStart) beginValidationRecovery(request);
            try { releaseTimeout.await(8, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
            return;
        }
        if (mode == Mode.DISCONNECT) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 10000);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            exchange.close();
            return;
        }
        try {
            Object result = dispatch(request);
            if (mode == Mode.MALFORMED_SUCCESS) { send(exchange, 200, "application/json", "{bad-json"); return; }
            ObjectNode wire = (ObjectNode) JSON.valueToTree(result);
            wireMutation.accept(wire);
            String response = JSON.writeValueAsString(wire);
            responses.add(response);
            send(exchange, request.method().equals("POST") && request.path().equals("/decision-variables/v1/variables") ? 201 : 200, "application/json", response);
        } catch (SemanticFailure failure) {
            sendProblem(exchange, failure.status, failure.code);
        } catch (RuntimeException failure) {
            unexpected.add("Scenario dispatch failed: " + failure.getClass().getSimpleName());
            sendProblem(exchange, 500, "SYSTEM_ERROR");
        }
    }

    private boolean known(Request r) {
        if (variables.known(r.method(), r.path())) return true;
        if (flow.known(r.method(), r.path())) return true;
        if (dictionary.known(r.method(), r.path())) return true;
        if (control.known(r.method(), r.path())) return true;
        if (evaluation.known(r.method(), r.path())) return true;
        if (r.method().equals("GET")) return r.path().startsWith("/decision-rule-sets/v1/authoring-context/")
            || r.path().equals(RULES) || r.path().matches(RULES + "/[1-9][0-9]*")
            || r.path().equals(CANDIDATES) || r.path().matches(CANDIDATES + "/[1-9][0-9]*");
        if (r.method().equals("DELETE")) return r.path().matches(RULES + "/[1-9][0-9]*");
        return r.method().equals("POST") && (r.path().equals(RULES) || r.path().matches(RULES + "/[1-9][0-9]*/freeze")
            || r.path().equals(CANDIDATES) || r.path().matches(CANDIDATES + "/[1-9][0-9]*/validate"));
    }

    private synchronized void beginValidationRecovery(Request request) {
        if (!request.method().equals("POST") || !request.path().endsWith("/validate"))
            throw new IllegalStateException("Recovery timeout requires Validate command");
        String id = request.path().substring(CANDIDATES.length() + 1, request.path().length() - 9);
        CandidateState state = candidate(id);
        var body = JSON.readValue(request.body(), ValidateTestReleaseRequestBody.class);
        if (!state.state.equals("DRAFT") || body.getExpectedVersion() != state.version())
            throw new IllegalStateException("Recovery timeout requires current DRAFT version");
        // Risk 已提交 STARTED，再丢失 HTTP 完成信号；查询必须保留 Owner 存储的原始 Actor。
        state.state = "RECOVERY";
    }

    private synchronized Object dispatch(Request request) {
        String path = request.path();
        if (variables.known(request.method(), path)) return variables.dispatch(request);
        if (flow.known(request.method(), path)) return flow.dispatch(request, ruleSets.get("30001"));
        if (dictionary.known(request.method(), path)) return dictionary.dispatch(request);
        if (control.known(request.method(), path)) return control.dispatch(request);
        if (evaluation.known(request.method(), path)) return evaluation.dispatch(request);
        if (request.method().equals("GET")) {
            // 只有支付点返回支付目录；其他风控点保持结账目录，供“请求与回执风控点不一致”用例使用。
            if (path.equals("/decision-rule-sets/v1/authoring-context/PAYMENT_ATTEMPT_RISK")) return authoring("PAYMENT_ATTEMPT_RISK");
            if (path.startsWith("/decision-rule-sets/v1/authoring-context/")) return authoring();
            if (path.equals(RULES)) return rulePage(request);
            if (path.equals(CANDIDATES)) return candidatePage(request);
            if (path.startsWith(RULES)) return rule(path.substring(RULES.length() + 1));
            return candidate(path.substring(CANDIDATES.length() + 1)).detail();
        }
        // Gateway 负责请求幂等，Risk 命令不携带 Idempotency-Key；同一操作者对同一资源的相同命令按原结果重放，
        // 显式故障模式下即使请求体不同也重放旧结果。
        String key = request.header("X-Gateway-Actor-Subject") + ":" + request.method() + ":" + path;
        var completed = commands.get(key);
        if (completed != null && (staleCommandReplay || completed.body().equals(request.body()))) {
            return completed.response();
        }
        Object result = command(request);
        commands.put(key, new CompletedCommand(request.body(), result));
        return result;
    }

    private Object command(Request r) {
        String actor = r.header("X-Gateway-Actor-Subject");
        if (r.method().equals("DELETE")) {
            String id = r.path().substring(RULES.length() + 1);
            RuleSetState root = ruleRoots.values().stream().filter(value -> value.id.equals(id)).findFirst()
                .orElseThrow(() -> new SemanticFailure(404, "RISK_DECISION_CONFIGURATION_NOT_FOUND"));
            var body = JSON.readValue(r.body(), DeleteRuleSetRequestBody.class);
            if (root.deleted) throw new SemanticFailure(409, "RISK_RULE_SET_DELETED");
            if (root.version != Long.parseLong(body.getExpectedVersion())) throw new SemanticFailure(409, "RISK_RULE_SET_CHANGED");
            if (root.inUse) throw new SemanticFailure(409, "RISK_RULE_SET_IN_USE");
            root.deleted = true; root.version++;
            return new DeleteRuleSetResponseBody(id, "DELETED", Long.toString(root.version), actor, TIME);
        }
        if (r.path().equals(RULES)) {
            var body = JSON.readValue(r.body(), CreateRuleSetVersionRequestBody.class);
            for (var rule : body.getRules()) {
                if (rule.getConditionNodes().stream().map(DecisionRuleSetManagementModels.ConditionNodeRequestBody::getNodeKey).distinct().count()
                    != rule.getConditionNodes().size()) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            }
            String id = Long.toString(nextRule++);
            RuleSetState root = ruleRoots.get(body.getRuleSetCode());
            if (root != null && root.deleted) throw new SemanticFailure(409, "RISK_RULE_SET_DELETED");
            if (root == null) ruleRoots.put(body.getRuleSetCode(), new RuleSetState(Long.toString(90001 + ruleRoots.size())));
            else root.version++;
            ruleSets.put(id, new RuleSetDetailResponseBody(id, "20002", body.getPointCode(), body.getRuleSetCode(),
                body.getRuleSetName(), 2, "DRAFT", HASH, actor, body.getChangeReason(), TIME, null, null, body.getRules()));
            return new CreateRuleSetVersionResponseBody(id, "20002", body.getRuleSetCode(), 2, "DRAFT", HASH, actor, TIME);
        }
        if (r.path().endsWith("/freeze")) {
            String id = r.path().substring(RULES.length() + 1, r.path().length() - 7);
            var old = rule(id);
            var body = JSON.readValue(r.body(), FreezeRuleSetVersionRequestBody.class);
            if (!old.getContentHash().equals(body.getExpectedContentHash())) throw new SemanticFailure(409, "CONCURRENT_REQUEST");
            ruleRoots.get(old.getRuleSetCode()).version++;
            ruleSets.put(id, new RuleSetDetailResponseBody(id, old.getPointVersionId(), old.getPointCode(), old.getRuleSetCode(),
                old.getRuleSetName(), old.getVersionNo(), "FROZEN", old.getContentHash(), old.getCreatedBy(), old.getChangeReason(),
                old.getCreateTime(), actor, TIME, old.getRules()));
            return new FreezeRuleSetVersionResponseBody(id, old.getPointVersionId(), old.getRuleSetCode(), old.getVersionNo(),
                "FROZEN", old.getContentHash(), actor, TIME);
        }
        if (r.path().equals(CANDIDATES)) {
            var body = JSON.readValue(r.body(), CreateTestReleaseRequestBody.class);
            var rule = rule(body.getRuleSetVersionId());
            if (!rule.getStatus().equals("FROZEN") || !rule.getContentHash().equals(body.getExpectedRuleSetContentHash())) {
                throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            }
            String id = Long.toString(nextRelease++);
            var state = new CandidateState(id, 2, body.getRuleSetVersionId(), actor, "DRAFT");
            candidates.put(id, state);
            return new CreateTestReleaseResponseBody(id, 2, "10002", "CHECKOUT_PREPARE", "20001", state.ruleId,
                "TEST", "DRAFT", 1, HASH, actor, TIME, new DecisionReleaseManagementModels.InitialStateChangeResponseBody("50001", TIME, null,
                "DRAFT", "CANDIDATE_CREATED", "OPERATIONS_COMMAND", actor, 1));
        }
        String id = r.path().substring(CANDIDATES.length() + 1, r.path().length() - 9);
        var state = candidate(id);
        var body = JSON.readValue(r.body(), ValidateTestReleaseRequestBody.class);
        // 显式故障模式返回旧的完整合法状态矩阵，供浏览器验证当前请求的 successor 关联。
        if (body.getExpectedVersion() != 1 && !staleCommandReplay) throw new SemanticFailure(409, "CONCURRENT_REQUEST");
        state.state = staticValidationFails ? "REJECTED" : "PASSED";
        return new ValidateTestReleaseResponseBody(id, state.number, "TEST", state.status(), state.version(), HASH,
            !staticValidationFails, validationChecks(staticValidationFails));
    }

    private Object rulePage(Request request) {
        var q = query(request.query());
        var heads = new LinkedHashMap<String, RuleSetDetailResponseBody>();
        ruleSets.values().forEach(rule -> heads.merge(rule.getRuleSetCode(), rule, (left, right) ->
            right.getVersionNo() >= left.getVersionNo() ? right : left));
        var items = heads.values().stream().filter(r -> !ruleRoots.get(r.getRuleSetCode()).deleted)
            .filter(r -> matches(q, "pointCode", r.getPointCode())
            && matches(q, "ruleSetCode", r.getRuleSetCode()) && matches(q, "status", r.getStatus()))
            .sorted(Comparator.comparing(RuleSetDetailResponseBody::getCreateTime).reversed()
                .thenComparing(r -> new java.math.BigInteger(r.getRuleSetVersionId()), Comparator.reverseOrder()))
            .map(r -> new RuleSetListItemResponseBody(r.getRuleSetVersionId(), r.getPointVersionId(), r.getPointCode(),
                r.getRuleSetCode(), r.getRuleSetName(), r.getVersionNo(), r.getStatus(), r.getContentHash(), r.getCreatedBy(),
                r.getChangeReason(), r.getCreateTime(), r.getFrozenBy(), r.getFrozenTime(), ruleRoots.get(r.getRuleSetCode()).id,
                Long.toString(ruleRoots.get(r.getRuleSetCode()).version), ruleRoots.get(r.getRuleSetCode()).inUse)).toList();
        return page(items, q);
    }

    private Object candidatePage(Request request) {
        var q = query(request.query());
        var items = candidates.values().stream().filter(c -> matches(q, "pointCode", "CHECKOUT_PREPARE")
            && matches(q, "status", c.status())).sorted(Comparator.comparing(c -> new java.math.BigInteger(c.id),
                Comparator.reverseOrder())).map(CandidateState::item).toList();
        return page(items, q);
    }

    private <T> PagingResponseBody<T> page(List<T> items, Map<String, String> q) {
        int page = Integer.parseInt(q.getOrDefault("page", "1"));
        int size = Integer.parseInt(q.getOrDefault("size", "10"));
        return new PagingResponseBody<>(items.stream().skip((long) (page - 1) * size).limit(size).toList(), page, size, items.size());
    }

    /** 配置真实外部 Owner 协议场景中的使用事实。 */
    public synchronized void ruleSetInUse(String code, boolean inUse) { ruleRoots.get(code).inUse = inUse; }

    private static final class RuleSetState {
        private final String id;
        private long version = 1;
        private boolean inUse;
        private boolean deleted;
        private RuleSetState(String id) { this.id = id; }
    }

    private RuleSetDetailResponseBody rule(String id) {
        if (!ruleSets.containsKey(id)) throw new SemanticFailure(404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        var rule = ruleSets.get(id);
        if (ruleRoots.get(rule.getRuleSetCode()).deleted) throw new SemanticFailure(404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        return rule;
    }

    private CandidateState candidate(String id) {
        if (!candidates.containsKey(id)) throw new SemanticFailure(404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        return candidates.get(id);
    }

    private static boolean matches(Map<String, String> q, String key, String value) {
        return !q.containsKey(key) || q.get(key).equals(value);
    }

    public static Map<String, String> query(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value != null) for (String pair : value.split("&")) {
            String[] parts = pair.split("=", 2);
            if (result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException("duplicate query");
        }
        return result;
    }

    private static List<DecisionReleaseManagementModels.ValidationCheckResponseBody> validationChecks(boolean failed) {
        return CHECKS.stream().map(code -> {
            boolean fail = failed && code.equals("AST_TYPES");
            return new DecisionReleaseManagementModels.ValidationCheckResponseBody(code, fail ? "FAILED" : "PASSED", fail ? "ERROR" : "INFO",
                fail ? "AST_TYPE_INVALID" : null, fail ? "first=AST_TYPE_INVALID@nodes/9; violation_count=1" : null);
        }).toList();
    }

    private static final class CandidateState {
        private final String id;
        private final int number;
        private final String ruleId;
        private final String actor;
        private String state;

        private CandidateState(String id, int number, String ruleId, String actor, String state) {
            this.id = id; this.number = number; this.ruleId = ruleId; this.actor = actor; this.state = state;
        }

        private String status() { return state.equals("PASSED") || state.equals("RECOVERY") ? "VALIDATING" : state; }
        private int version() { return state.equals("DRAFT") ? 1 : state.equals("REJECTED") ? 3 : 2; }
        private Boolean passed() { return state.equals("DRAFT") || state.equals("RECOVERY") ? null : state.equals("PASSED"); }
        private TestReleaseListItemResponseBody item() {
            return new TestReleaseListItemResponseBody(id, number, "10002", "CHECKOUT_PREPARE", "20001", ruleId,
                "TEST", status(), version(), HASH, actor, TIME, passed());
        }

        private TestReleaseDetailResponseBody detail() {
            List<DecisionReleaseManagementModels.ReleaseStateChangeResponseBody> states = new ArrayList<>();
            states.add(stateChange(1, null, "DRAFT", "CANDIDATE_CREATED"));
            if (!state.equals("DRAFT")) states.add(stateChange(2, "DRAFT", "VALIDATING", "STATIC_VALIDATION_STARTED"));
            if (state.equals("REJECTED")) states.add(stateChange(3, "VALIDATING", "REJECTED", "STATIC_VALIDATION_FAILED"));
            List<DecisionReleaseManagementModels.ReleaseStaticCheckResponseBody> checks = new ArrayList<>();
            if (passed() != null) for (var check : validationChecks(!passed())) {
                checks.add(new DecisionReleaseManagementModels.ReleaseStaticCheckResponseBody(Integer.toString(60001 + checks.size()), TIME,
                    check.getCheckCode(), check.getCheckStatus(), check.getSeverity(), check.getErrorCode(), check.getErrorSummary()));
            }
            return new TestReleaseDetailResponseBody(id, number, "10002", "CHECKOUT_PREPARE", "20001", ruleId,
                "TEST", status(), version(), HASH, actor, TIME, passed(), state.equals("PASSED"),
                new DecisionReleaseManagementModels.FrozenCompositionResponseBody("10002", "CHECKOUT_PREPARE", "20001", 1, HASH,
                    ruleId, "DEFAULT_RULES", 1, HASH), states, checks);
        }

        private DecisionReleaseManagementModels.ReleaseStateChangeResponseBody stateChange(int version, String from, String to, String reason) {
            return new DecisionReleaseManagementModels.ReleaseStateChangeResponseBody(Integer.toString(50000 + version), TIME, from, to, reason,
                "OPERATIONS_COMMAND", version == 1 ? "b".repeat(64) : HASH, actor, version);
        }
    }

    private void sendProblem(HttpExchange exchange, int status, String code) throws IOException {
        String title = switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Content";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Internal Server Error";
        };
        exchange.getResponseHeaders().set("X-Request-Id", UPSTREAM_REQUEST_ID);
        if (legacyError) {
            send(exchange, status, "application/json", JSON.writeValueAsString(Map.of("code", code,
                "message", UPSTREAM_DETAIL_CANARY, "requestId", UPSTREAM_REQUEST_ID, "timestamp", TIME)));
            return;
        }
        ObjectNode problem = JSON.valueToTree(Map.of("type", "about:blank", "title", title, "code", code,
            "detail", UPSTREAM_DETAIL_CANARY, "requestId", UPSTREAM_REQUEST_ID, "timestamp", TIME));
        if (!problemFields.isEmpty()) problem.set("errors", JSON.valueToTree(problemFields));
        send(exchange, status, "application/problem+json", JSON.writeValueAsString(problem));
    }

    private static void send(HttpExchange exchange, int status, String media, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", media);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    @Override public void close() {
        releaseTimeout.countDown();
        requestArrival.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body,
                          long sequence) {
        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().size() == 1 ? entry.getValue().getFirst() : null).findFirst().orElse(null);
        }
        @Override public String toString() { return method + " " + path + " [内容已隐藏]"; }
    }

    private record CompletedCommand(String body, Object response) {}

    static final class SemanticFailure extends RuntimeException {
        private final int status;
        private final String code;
        SemanticFailure(int status, String code) { this.status = status; this.code = code; }
    }
}

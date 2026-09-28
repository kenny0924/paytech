package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.HASH;
import static com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.TIME;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.configuration.RiskClientConfiguration;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.integration.RiskDiscoveryInterceptor;
import com.paytech.operations.console.bff.integration.impl.RiskDecisionConfigurationClientImpl;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.*;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.*;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Mode;

/** 真实严格代理 → Risk Loopback 协议；本地边界校验不得把上游损坏值输出到页面或日志。 */
@ExtendWith(OutputCaptureExtension.class)
class RiskDecisionConfigurationClientAutoTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OperatorContext OPERATOR = new OperatorContext("10001", "session-10001", "RISK_READ");
    private static final String REQUEST_ID = "risk-adapter-request";
    private static final String KEY = "Original_Risk-Key_123";
    private static final String CANARY = "literal-strategy-canary-unsafe";
    private static final String RULE_PATH = "/decision-rule-sets/v1/rule-sets";
    private static final String CANDIDATE_PATH = "/decision-releases/v1/candidates";

    enum Operation {
        AUTHORING,

        RULE_LIST,

        RULE_DETAIL,

        SAVE,

        FREEZE,

        DELETE,

        CANDIDATE_LIST,

        CANDIDATE_DETAIL,

        CREATE,

        VALIDATE
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void test_exactRequestAndEverySuccessfulResponseField(Operation operation) throws Exception {
        withClient((server, client) -> {
            Object response = invoke(client, operation);
            ObjectNode expected = (ObjectNode) JSON.readTree(server.responses().getFirst());
            if (operation == Operation.AUTHORING) expected.get("actions").forEach(action -> {
                var value = (ObjectNode) action;
                value.remove("handlerCode"); value.remove("implementationVersion"); value.remove("implementationHash");
            });
            assertEquals(expected, JSON.readTree(JSON.writeValueAsString(response)), operation.name());
            assertExactRequest(server, operation);
            String browser = JSON.writeValueAsString(response);
            for (String privateName : List.of("handlerCode", "implementationVersion", "implementationHash")) {
                assertFalse(browser.contains(privateName));
            }
            assertEquals(10, RiskDecisionConfigurationClient.class.getDeclaredMethods().length);
        });
    }

    @Test
    void test_commandsChangeSimulatorStateAndReplayOriginalResult() throws Exception {
        withClient((server, client) -> {
            var saved = client.createRuleSetVersion(OPERATOR, KEY, save());
            assertEquals("30002", saved.ruleSetVersionId());
            assertEquals(saved, client.createRuleSetVersion(OPERATOR, KEY, save()));
            assertEquals("DRAFT", client.ruleSet(OPERATOR, "30002").status());
            client.freezeRuleSetVersion(OPERATOR, "freeze_key_123456", "30002", new FreezeRuleSetVersionRequestBody(HASH));
            assertEquals("FROZEN", client.ruleSet(OPERATOR, "30002").status());
            var created = client.createCandidate(OPERATOR, "create_key_123456", new CreateTestCandidateRequestBody("30001", HASH));
            assertEquals("40002", created.releaseId());
            assertEquals("DRAFT", client.candidate(OPERATOR, "40002").status());
            var validated = client.validateCandidate(OPERATOR, "risk-validate-v1-40002-1", "40002", new ValidateTestCandidateRequestBody(1));
            assertEquals("VALIDATING", validated.status());
            assertTrue(client.candidate(OPERATOR, "40002").waitingForShadow());
            assertEquals(9, server.requests().size());
        });
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"RULE_LIST", "RULE_DETAIL", "SAVE", "FREEZE"})
    void test_ruleResponsesKeepExplicitNullWhenNoLegacyCompositionExists(Operation operation) throws Exception {
        withClient((server, client) -> {
            server.corruptWire(root -> {
                ObjectNode rule = operation == Operation.RULE_LIST ? (ObjectNode) root.at("/items/0") : root;
                rule.putNull("pointVersionId");
            });
            JsonNode response = JSON.valueToTree(invoke(client, operation));
            JsonNode rule = operation == Operation.RULE_LIST ? response.at("/items/0") : response;
            assertTrue(rule.has("pointVersionId"));
            assertTrue(rule.get("pointVersionId").isNull());
            assertEquals(1, server.requests().size());
        });
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void test_everyScalarRejectsNoncanonicalOrUnknownValues(Operation operation, CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            invoke(client, operation);
            JsonNode baseline = JSON.readTree(server.responses().getFirst());
            List<Mutation> mutations = new ArrayList<>();
            scalarMutations(baseline, "", mutations);
            assertFalse(mutations.isEmpty(), "scalar generator must exercise an actual response");
            Set<String> paths = new java.util.HashSet<>();
            for (Mutation mutation : mutations) {
                assertTrue(paths.add(mutation.label()), "duplicate mutation label");
                server.reset();
                server.corruptWire(mutation.change());
                assertInvalid(() -> invoke(client, operation), mutation.label(), output);
                assertEquals(1, server.requests().size(), mutation.label());
            }
            System.out.println("Risk adapter scalar mutations " + operation + ": " + mutations.size());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "RECOVERY", "PASSED", "REJECTED"})
    void test_candidateStateMatrixAndEveryAuditAndCheckScalar(String state, CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            server.candidateState(state);
            var valid = client.candidate(OPERATOR, "40001");
            assertEquals(state.equals("RECOVERY") || state.equals("PASSED") ? "VALIDATING" : state, valid.status());
            assertEquals(state.equals("DRAFT") ? 1 : state.equals("REJECTED") ? 3 : 2, valid.version());
            assertEquals(state.equals("PASSED"), valid.waitingForShadow());
            assertEquals(state.equals("PASSED") || state.equals("REJECTED") ? 8 : 0, valid.staticChecks().size());
            JsonNode baseline = JSON.readTree(server.responses().getFirst());
            assertEquals(baseline, JSON.readTree(JSON.writeValueAsString(valid)));
            List<Mutation> mutations = new ArrayList<>();
            scalarMutations(baseline, "", mutations);
            for (String status : List.of("DRAFT", "VALIDATING", "REJECTED", "ACTIVE")) {
                if (!status.equals(valid.status())) mutations.add(set("status=" + status, "/status", status));
            }
            for (int version : List.of(1, 2, 3, 4)) if (version != valid.version()) mutations.add(set("version=" + version, "/version", version));
            for (Boolean passed : Arrays.asList(null, true, false)) {
                if (!java.util.Objects.equals(passed, valid.allStaticChecksPassed())) mutations.add(set("passed=" + passed, "/allStaticChecksPassed", passed));
            }
            mutations.add(set("waiting", "/waitingForShadow", !valid.waitingForShadow()));
            mutations.add(set("missing states", "/stateChanges", List.of()));
            if (state.equals("DRAFT")) mutations.add(set("draft checks", "/staticChecks", List.of(validCheck())));
            else {
                mutations.add(set("wrong start from", "/stateChanges/1/fromStatus", "VALIDATING"));
                mutations.add(set("wrong start to", "/stateChanges/1/toStatus", "DRAFT"));
                mutations.add(set("wrong start reason", "/stateChanges/1/reasonCode", "CANDIDATE_CREATED"));
                mutations.add(set("start from null", "/stateChanges/1/fromStatus", null));
                mutations.add(set("duplicate state audit", "/stateChanges/1/auditEventId", "50001"));
                mutations.add(new Mutation("states reversed", root -> reverse(root, "/stateChanges")));
            }
            mutations.add(set("initial from not null", "/stateChanges/0/fromStatus", "DRAFT"));
            mutations.add(set("initial wrong to", "/stateChanges/0/toStatus", "VALIDATING"));
            mutations.add(set("initial reason", "/stateChanges/0/reasonCode", "STATIC_VALIDATION_STARTED"));
            mutations.add(set("initial aggregate", "/stateChanges/0/aggregateVersion", 2));
            if (state.equals("REJECTED")) {
                mutations.add(set("final actor mismatch", "/stateChanges/2/actorId", "10002"));
                mutations.add(set("final trigger mismatch", "/stateChanges/2/triggerId", "b".repeat(64)));
                mutations.add(set("final from mismatch", "/stateChanges/2/fromStatus", "DRAFT"));
                mutations.add(set("final to mismatch", "/stateChanges/2/toStatus", "DRAFT"));
                mutations.add(set("final reason mismatch", "/stateChanges/2/reasonCode", "CANDIDATE_CREATED"));
            }
            if (!valid.staticChecks().isEmpty()) addCheckMutations(mutations, "/staticChecks", state.equals("REJECTED"));
            for (Mutation mutation : mutations) {
                server.reset(); server.candidateState(state); server.corruptWire(mutation.change());
                assertInvalid(() -> client.candidate(OPERATOR, "40001"), state + ":" + mutation.label(), output);
                assertEquals(1, server.requests().size());
            }
            System.out.println("Risk candidate matrix " + state + ": " + mutations.size());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_validateResultMatrixAndCheckGrammar(boolean rejected, CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            server.validationFails(rejected);
            var valid = client.validateCandidate(OPERATOR, KEY, "40001", new ValidateTestCandidateRequestBody(1));
            assertEquals(rejected ? "REJECTED" : "VALIDATING", valid.status());
            assertEquals(rejected ? 3 : 2, valid.version());
            assertEquals(JSON.readTree(server.responses().getFirst()), JSON.readTree(JSON.writeValueAsString(valid)));
            List<Mutation> mutations = new ArrayList<>();
            addCheckMutations(mutations, "/checks", rejected);
            mutations.add(set("wrong version", "/version", rejected ? 2 : 3));
            mutations.add(set("wrong state", "/status", rejected ? "VALIDATING" : "REJECTED"));
            mutations.add(set("wrong derived", "/allStaticChecksPassed", rejected));
            for (Mutation mutation : mutations) {
                server.reset(); server.validationFails(rejected); server.corruptWire(mutation.change());
                assertInvalid(() -> invoke(client, Operation.VALIDATE), mutation.label(), output);
                assertEquals(1, server.requests().size());
            }
        });
    }

    @Test
    void test_authoringRelationsOrderUniquenessAndPrivateFingerprints(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            List<Mutation> mutations = new ArrayList<>();
            JsonNode authoring = JSON.valueToTree(RiskDecisionConfigurationSimulator.authoring());
            authoring.get("point").propertyNames().forEach(name -> mutations.add(set("point " + name, "/point/" + name,
                authoring.at("/point/" + name).isIntegralNumber() ? 0 : " ")));
            for (String name : List.of("ruleReferenceFlag")) {
                mutations.add(set("field " + name, "/fields/0/" + name, name.equals("actionParameterFlag")));
            }
            for (String name : List.of("valueType", "dataClassification")) mutations.add(set("field " + name, "/fields/0/" + name, "OTHER"));
            for (String name : List.of("actionCategory", "decisionCode", "mergePolicy", "status")) {
                mutations.add(set("action " + name, "/actions/0/" + name, "OTHER"));
            }
            mutations.add(set("decision wrong pair", "/actions/0/decisionCode", "DENY"));
            for (String name : List.of("terminalFlag", "shadowSupportedFlag")) mutations.add(set(name, "/actions/0/" + name, false));
            for (String name : List.of("handlerCode", "implementationVersion", "implementationHash")) {
                mutations.add(set(name + " blank", "/actions/0/" + name, " "));
                mutations.add(set(name + " null", "/actions/0/" + name, null));
            }
            for (String name : List.of("fields", "actions", "operators")) {
                if (!name.equals("fields")) mutations.add(new Mutation(name + " reversed", root -> reverse(root, "/" + name)));
                mutations.add(new Mutation(name + " duplicate", root -> duplicate(root, "/" + name)));
                mutations.add(set(name + " null element", "/" + name + "/0", null));
            }
            mutations.add(set("operator sequence", "/operators/0", "NE"));
            for (String name : List.of("features", "actionParameters", "obligations", "limitBindings")) {
                mutations.add(set(name + " nonempty", "/" + name, Arrays.asList((Object) null)));
                mutations.add(set(name + " null", "/" + name, null));
            }
            for (Mutation mutation : mutations) {
                server.reset(); server.corruptWire(mutation.change());
                assertInvalid(() -> invoke(client, Operation.AUTHORING), mutation.label(), output);
                assertEquals(1, server.requests().size());
            }
        });
    }

    @Test
    void test_ruleDetailNullRelationsUniquenessAndPreorder(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            List<Mutation> mutations = new ArrayList<>();
            mutations.add(new Mutation("duplicate rule", root -> duplicate(root, "/rules")));
            mutations.add(new Mutation("duplicate node", root -> duplicate(root, "/rules/0/conditionNodes")));
            mutations.add(new Mutation("duplicate operand", root -> duplicate(root, "/rules/0/conditionNodes/1/operands")));
            mutations.add(new Mutation("numeric sibling 10 before 9", root -> swap(root, "/rules/0/conditionNodes", 1, 2)));
            mutations.add(new Mutation("child before parent", root -> swap(root, "/rules/0/conditionNodes", 0, 1)));
            mutations.add(new Mutation("operand right before left", root -> reverse(root, "/rules/0/conditionNodes/1/operands")));
            mutations.add(set("orphan", "/rules/0/conditionNodes/2/parentNodeKey", "777"));
            mutations.add(set("cycle", "/rules/0/conditionNodes/0/parentNodeKey", "10"));
            mutations.add(set("two roots", "/rules/0/conditionNodes/2/parentNodeKey", null));
            mutations.add(set("group comparison", "/rules/0/conditionNodes/0/comparisonOperator", "EQ"));
            mutations.add(set("group operator missing", "/rules/0/conditionNodes/0/groupOperator", null));
            mutations.add(set("predicate group", "/rules/0/conditionNodes/1/groupOperator", "AND"));
            mutations.add(set("predicate comparison missing", "/rules/0/conditionNodes/1/comparisonOperator", null));
            mutations.add(set("context missing field", "/rules/0/conditionNodes/1/operands/0/fieldCode", null));
            mutations.add(set("context literal", "/rules/0/conditionNodes/1/operands/0/literalValue", "AE"));
            mutations.add(set("literal field", "/rules/0/conditionNodes/1/operands/1/fieldCode", "country_code"));
            mutations.add(set("literal wrong token", "/rules/0/conditionNodes/1/operands/1/literalValue", true));
            mutations.add(set("rule empty", "/rules", List.of()));
            mutations.add(set("node empty", "/rules/0/conditionNodes", List.of()));
            mutations.add(set("null rule", "/rules/0", null));
            mutations.add(set("null node", "/rules/0/conditionNodes/0", null));
            mutations.add(set("null operand", "/rules/0/conditionNodes/1/operands/0", null));
            for (Mutation mutation : mutations) {
                server.reset(); server.corruptWire(mutation.change());
                assertInvalid(() -> invoke(client, Operation.RULE_DETAIL), mutation.label(), output);
            }
            server.reset();
            var response = client.ruleSet(OPERATOR, "30001");
            assertEquals(List.of("8", "9", "10"), response.rules().getFirst().conditionNodes().stream()
                .map(ConditionNodeRequestBody::nodeKey).toList());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "FROZEN", "RETIRED"})
    void test_ruleSetFrozenNullMatrix(String status, CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            server.addRuleSet("30001", status);
            assertEquals(status, client.ruleSet(OPERATOR, "30001").status());
            for (String field : List.of("frozenBy", "frozenTime")) {
                server.reset(); server.addRuleSet("30001", status);
                server.corruptWire(set(field, "/" + field, status.equals("FROZEN") ? null : field.equals("frozenBy") ? "10001" : TIME).change());
                assertInvalid(() -> invoke(client, Operation.RULE_DETAIL), status + field, output);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_pagesBoundsUniqueIdsAndNumericTieBreak(boolean candidate, CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            Consumer<RiskDecisionConfigurationSimulator> prepare = s -> {
                if (candidate) { s.addCandidate("9"); s.addCandidate("10"); }
                else { s.addRuleSet("9", "FROZEN"); s.addRuleSet("10", "FROZEN"); }
            };
            prepare.accept(server);
            Object response = (candidate ? invoke(client, Operation.CANDIDATE_LIST) : client.ruleSets(OPERATOR, 1, 10, new RuleSetQueryRequestBody("CHECKOUT_PREPARE", null, "FROZEN")));
            JsonNode items = JSON.valueToTree(response).get("items");
            String id = candidate ? "releaseId" : "ruleSetVersionId";
            assertEquals(List.of(candidate ? "40001" : "30001", "10", "9"), Stream.of(0, 1, 2).map(i -> items.get(i).get(id).asString()).toList());
            List<Mutation> mutations = new ArrayList<>(List.of(set("wrong page", "/page", 2), set("wrong size", "/size", 20),
                set("negative total", "/total", -1), set("too small total", "/total", 2), set("empty with total", "/items", List.of()),
                set("null item", "/items/0", null), new Mutation("duplicate primary", root -> duplicate(root, "/items")),
                new Mutation("lexical numeric order", root -> swap(root, "/items", 1, 2)),
                set("date out of order", "/items/2/createTime", "2026-09-08T01:02:03.123456Z")));
            mutations.add(new Mutation("over size", root -> { var array = (ArrayNode) root.get("items"); for (int i = 0; i < 11; i++) array.add(array.get(0).deepCopy()); }));
            for (Mutation mutation : mutations) {
                server.reset(); prepare.accept(server); server.corruptWire(mutation.change());
                assertInvalid(() -> {
                    if (candidate) invoke(client, Operation.CANDIDATE_LIST);
                    else client.ruleSets(OPERATOR, 1, 10, new RuleSetQueryRequestBody("CHECKOUT_PREPARE", null, "FROZEN"));
                }, mutation.label(), output);
            }
            server.reset();
            Object empty = candidate ? client.candidates(OPERATOR, 2, 10, new CandidateQueryRequestBody(null, null))
                : client.ruleSets(OPERATOR, 2, 10, new RuleSetQueryRequestBody(null, null, null));
            assertTrue(JSON.valueToTree(empty).get("items").isEmpty());
            server.corruptWire(root -> root.put("total", 11));
            assertInvalid(() -> {
                if (candidate) client.candidates(OPERATOR, 2, 10, new CandidateQueryRequestBody(null, null));
                else client.ruleSets(OPERATOR, 2, 10, new RuleSetQueryRequestBody(null, null, null));
            }, "empty reachable second page", output);
        });
    }

    @Test
    void test_allNineResponseCorrelationsRejectValidButDifferentIdentities(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            var checks = List.of(
                new Correlation(Operation.AUTHORING, "/point/pointCode", "OTHER_POINT"),
                new Correlation(Operation.RULE_LIST, "/items/0/pointCode", "OTHER_POINT"),
                new Correlation(Operation.RULE_LIST, "/items/0/ruleSetCode", "OTHER_RULES"),
                new Correlation(Operation.RULE_LIST, "/items/0/status", "DRAFT"),
                new Correlation(Operation.RULE_DETAIL, "/ruleSetVersionId", "30002"),
                new Correlation(Operation.SAVE, "/ruleSetCode", "OTHER_RULES"),
                new Correlation(Operation.SAVE, "/status", "FROZEN"),
                new Correlation(Operation.FREEZE, "/ruleSetVersionId", "30002"),
                new Correlation(Operation.FREEZE, "/contentHash", "b".repeat(64)),
                new Correlation(Operation.CANDIDATE_LIST, "/items/0/pointCode", "OTHER_POINT"),
                new Correlation(Operation.CANDIDATE_LIST, "/items/0/status", "VALIDATING"),
                new Correlation(Operation.CANDIDATE_DETAIL, "/releaseId", "40002"),
                new Correlation(Operation.CANDIDATE_DETAIL, "/frozenComposition/decisionPointId", "10003"),
                new Correlation(Operation.CANDIDATE_DETAIL, "/frozenComposition/pointCode", "OTHER_POINT"),
                new Correlation(Operation.CANDIDATE_DETAIL, "/frozenComposition/pointVersionId", "20002"),
                new Correlation(Operation.CANDIDATE_DETAIL, "/frozenComposition/ruleSetVersionId", "30002"),
                new Correlation(Operation.CREATE, "/ruleSetVersionId", "30002"),
                new Correlation(Operation.VALIDATE, "/releaseId", "40002"));
            assertEquals(9, checks.stream().map(Correlation::operation).distinct().count());
            for (Correlation check : checks) {
                server.reset(); server.corruptWire(root -> {
                    setAt(root, check.path(), check.value());
                    if (check.operation() == Operation.RULE_LIST && check.path().endsWith("/status")) {
                        setAt(root, "/items/0/frozenBy", null); setAt(root, "/items/0/frozenTime", null);
                    }
                    if (check.operation() == Operation.CANDIDATE_LIST && check.path().endsWith("/status")) setAt(root, "/items/0/version", 2);
                });
                assertInvalid(() -> invoke(client, check.operation()), check.toString(), output);
                assertEquals(1, server.requests().size());
            }
            for (int expected : List.of(2, Integer.MAX_VALUE)) for (boolean rejected : List.of(false, true)) {
                server.reset(); server.validationFails(rejected);
                // 模拟上游错误重放原成功响应；BFF 仍必须检查本次请求的 successor。
                client.validateCandidate(OPERATOR, KEY, "40001", new ValidateTestCandidateRequestBody(1));
                server.staleCommandReplay();
                assertInvalid(() -> client.validateCandidate(OPERATOR, KEY, "40001", new ValidateTestCandidateRequestBody(expected)),
                    "successor " + expected + " rejected=" + rejected, output);
                assertEquals(2, server.requests().size());
            }
        });
    }

    @Test
    void test_nullFiltersRemainAbsentAndNondefaultPagingIsForwarded() throws Exception {
        withClient((server, client) -> {
            var rules = client.ruleSets(OPERATOR, 3, 20, new RuleSetQueryRequestBody(null, null, null));
            assertEquals(3, rules.page()); assertEquals(20, rules.size()); assertEquals(1, rules.total());
            assertEquals(Map.of("page", "3", "size", "20"), RiskDecisionConfigurationSimulator.query(server.requests().getFirst().query()));
            server.reset();
            var candidates = client.candidates(OPERATOR, 2, 50, new CandidateQueryRequestBody(null, null));
            assertEquals(2, candidates.page()); assertEquals(50, candidates.size()); assertEquals(1, candidates.total());
            assertEquals(Map.of("page", "2", "size", "50"), RiskDecisionConfigurationSimulator.query(server.requests().getFirst().query()));
            assertEquals(1, server.requests().size());
        });
    }

    @Test
    void test_validAuthoringForDifferentRequestedPointFailsCorrelation(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            assertInvalid(() -> client.authoringContext(OPERATOR, "OTHER_POINT"), "different requested Point", output);
            assertEquals(1, server.requests().size());
            assertTrue(server.requests().getFirst().path().endsWith("/OTHER_POINT"));
        });
    }

    @Test
    void test_ownerNotFoundAndConflictRemainAuthoritative() throws Exception {
        withClient((server, client) -> {
            var missing = assertThrows(FailException.class, () -> client.ruleSet(OPERATOR, "777"));
            assertEquals("RISK_DECISION_CONFIGURATION_NOT_FOUND", missing.getErrorCode().getCode());
            assertEquals(1, server.requests().size());
            assertEquals(RULE_PATH + "/777", server.requests().getFirst().path());
            server.reset();
            var conflict = assertThrows(FailException.class, () -> client.freezeRuleSetVersion(OPERATOR, KEY, "30001",
                new FreezeRuleSetVersionRequestBody("b".repeat(64))));
            assertEquals(CommonErrorCode.CONCURRENT_REQUEST, conflict.getErrorCode());
            assertEquals(1, server.requests().size());
            assertEquals("b".repeat(64), JSON.readTree(server.requests().getFirst().body()).get("expectedContentHash").asString());
        });
    }

    @Test
    void test_portAndPrivateValidatorDoNotExposeOwnerTypes() throws Exception {
        assertEquals(10, RiskDecisionConfigurationClient.class.getDeclaredMethods().length);
        var validator = Class.forName(RiskDecisionConfigurationClientImpl.class.getName() + "$Validator");
        assertTrue(java.lang.reflect.Modifier.isPrivate(validator.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(validator.getModifiers()));
        for (Class<?> type : List.of(RiskDecisionConfigurationClient.class, validator)) {
            for (var method : type.getDeclaredMethods()) {
                assertFalse(method.getGenericReturnType().getTypeName().contains("com.paytech.risk.api"));
                for (var parameter : method.getGenericParameterTypes()) {
                    assertFalse(parameter.getTypeName().contains("com.paytech.risk.api"));
                }
            }
        }
    }

    @Test
    void test_ruleOrderUsesPriorityThenCodeAndNeverRepairsWireOrder(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            Consumer<ObjectNode> twoRules = root -> {
                var rules = (ArrayNode) root.get("rules");
                var second = (ObjectNode) rules.get(0).deepCopy();
                second.put("ruleCode", "Z_RULE"); rules.add(second);
            };
            server.corruptWire(twoRules);
            assertEquals(List.of("ALLOW_RULE", "Z_RULE"), client.ruleSet(OPERATOR, "30001").rules().stream().map(RuleRequestBody::ruleCode).toList());
            for (Consumer<ObjectNode> wrong : List.<Consumer<ObjectNode>>of(root -> reverse(root, "/rules"),
                root -> setAt(root, "/rules/0/priorityNo", 2))) {
                server.reset(); server.corruptWire(twoRules.andThen(wrong));
                assertInvalid(() -> client.ruleSet(OPERATOR, "30001"), "rule ordering", output);
                assertEquals(1, server.requests().size());
            }
        });
    }

    @Test
    void test_nodePreorderIsDepthFirstAndUsesSequenceBeforeNumericId(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            Consumer<ObjectNode> nestedTree = root -> {
                var nodes = (ArrayNode) root.at("/rules/0/conditionNodes");
                var leaf = nodes.get(1).deepCopy();
                setAt(root, "/rules/0/conditionNodes/1/nodeType", "GROUP");
                setAt(root, "/rules/0/conditionNodes/1/groupOperator", "AND");
                setAt(root, "/rules/0/conditionNodes/1/comparisonOperator", null);
                setAt(root, "/rules/0/conditionNodes/1/operands", List.of());
                ((ObjectNode) leaf).put("nodeKey", "11").put("parentNodeKey", "9");
                var sibling = nodes.remove(2); nodes.add(leaf); nodes.add(sibling);
            };
            server.corruptWire(nestedTree);
            assertEquals(List.of("8", "9", "11", "10"), client.ruleSet(OPERATOR, "30001").rules().getFirst().conditionNodes()
                .stream().map(ConditionNodeRequestBody::nodeKey).toList());
            server.reset(); server.corruptWire(nestedTree.andThen(root -> swap(root, "/rules/0/conditionNodes", 2, 3)));
            assertInvalid(() -> client.ruleSet(OPERATOR, "30001"), "breadth-first wire", output);
            server.reset(); server.corruptWire(root -> setAt(root, "/rules/0/conditionNodes/1/sequenceNo", 2));
            assertInvalid(() -> client.ruleSet(OPERATOR, "30001"), "wrong sibling sequence", output);
            server.reset(); server.corruptWire(root -> {
                setAt(root, "/rules/0/conditionNodes/1/sequenceNo", 2);
                swap(root, "/rules/0/conditionNodes", 1, 2);
            });
            assertEquals(List.of("8", "10", "9"), client.ruleSet(OPERATOR, "30001").rules().getFirst().conditionNodes()
                .stream().map(ConditionNodeRequestBody::nodeKey).toList());
        });
    }

    @Test
    void test_operandOrderingAndAuthoringActionVersionOrdering(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            Consumer<ObjectNode> operands = root -> {
                var array = (ArrayNode) root.at("/rules/0/conditionNodes/1/operands");
                var another = (ObjectNode) array.get(1).deepCopy(); another.put("valueSequence", 2); array.add(another);
                setAt(root, "/rules/0/conditionNodes/1/comparisonOperator", "IN");
            };
            server.corruptWire(operands);
            assertEquals(List.of(1, 1, 2), client.ruleSet(OPERATOR, "30001").rules().getFirst().conditionNodes().get(1).operands()
                .stream().map(OperandRequestBody::valueSequence).toList());
            server.reset(); server.corruptWire(operands.andThen(root -> swap(root, "/rules/0/conditionNodes/1/operands", 1, 2)));
            assertInvalid(() -> client.ruleSet(OPERATOR, "30001"), "operand valueSequence", output);
            Consumer<ObjectNode> actions = root -> {
                var array = (ArrayNode) root.get("actions");
                var secondVersion = (ObjectNode) array.get(0).deepCopy(); secondVersion.put("versionNo", 2);
                array.insert(1, secondVersion);
            };
            server.reset(); server.corruptWire(actions);
            assertEquals(2, client.authoringContext(OPERATOR, "CHECKOUT_PREPARE").actions().get(1).versionNo());
            server.reset(); server.corruptWire(actions.andThen(root -> swap(root, "/actions", 0, 1)));
            assertInvalid(() -> client.authoringContext(OPERATOR, "CHECKOUT_PREPARE"), "action version ordering", output);
        });
    }

    @Test
    void test_initialCandidateTransitionRelationsAndNullableRuleReason(CapturedOutput output) throws Exception {
        withClient((server, client) -> {
            for (Mutation mutation : List.of(set("from", "/initialStateChange/fromStatus", "DRAFT"),
                set("to", "/initialStateChange/toStatus", "VALIDATING"), set("reason", "/initialStateChange/reasonCode", "STATIC_VALIDATION_STARTED"),
                set("aggregate", "/initialStateChange/aggregateVersion", 2), set("actor", "/initialStateChange/actorId", "10002"),
                set("time", "/initialStateChange/eventTime", "2026-09-08T01:02:03.123456Z"), set("status", "/status", "VALIDATING"),
                set("version", "/version", 2))) {
                server.reset(); server.corruptWire(mutation.change());
                assertInvalid(() -> invoke(client, Operation.CREATE), mutation.label(), output);
            }
            server.reset(); server.corruptWire(root -> setAt(root, "/rules/0/reasonCode", "REASON_VALID"));
            assertEquals("REASON_VALID", client.ruleSet(OPERATOR, "30001").rules().getFirst().reasonCode());
            server.reset(); server.corruptWire(root -> setAt(root, "/rules/0/reasonCode", "bad-code"));
            assertInvalid(() -> client.ruleSet(OPERATOR, "30001"), "optional nonnull reason code", output);
        });
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"PROBLEM", "MALFORMED_SUCCESS", "REDIRECT", "TIMEOUT", "DISCONNECT"})
    void test_failureModesUseStrictErrorsWithoutWriteRetry(Mode mode, CapturedOutput output) throws Exception {
        for (Operation operation : Operation.values()) {
            withClient((server, client) -> {
                server.mode(mode);
                if (mode == Mode.PROBLEM) {
                    var failure = assertThrows(FailException.class, () -> invoke(client, operation));
                    assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED, failure.getErrorCode());
                } else {
                    var failure = assertThrows(ErrorException.class, () -> invoke(client, operation));
                    assertEquals(mode == Mode.TIMEOUT ? OperationsBffErrorCode.DEPENDENCY_TIMEOUT
                        : mode == Mode.DISCONNECT ? OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE
                        : CommonErrorCode.UPSTREAM_INVALID_RESPONSE, failure.getErrorCode());
                    assertFalse(failure.getMessage().contains(CANARY));
                }
                assertExactRequest(server, operation);
                assertFalse(output.getAll().contains(CANARY));
            });
        }
    }

    @Test
    void test_duplicateNodeKeysAreForwardedUnchangedAndRiskOwnsSemantic400() throws Exception {
        withClient((server, client) -> {
            var request = save();
            var rule = request.rules().getFirst();
            var nodes = new ArrayList<>(rule.conditionNodes());
            nodes.add(nodes.getLast());
            var duplicateRule = new RuleRequestBody(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabledFlag(),
                rule.reasonCode(), nodes, rule.action());
            var duplicate = new CreateRuleSetVersionRequestBody(request.pointCode(), request.ruleSetCode(), request.ruleSetName(),
                request.baseRuleSetVersionId(), request.baseContentHash(), request.changeReason(), List.of(duplicateRule));
            var failure = assertThrows(FailException.class, () -> client.createRuleSetVersion(OPERATOR, KEY, duplicate));
            assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED, failure.getErrorCode());
            assertEquals(1, server.requests().size());
            assertEquals(JSON.valueToTree(duplicate), JSON.readTree(server.requests().getFirst().body()));
        });
    }

    @Test
    void test_safeFailedSummaryGrammarAcceptsEveryApprovedPath() throws Exception {
        withClient((server, client) -> {
            List<String> paths = List.of("-", "actionParameters", "businessCoordinates", "closure", "duplicates", "features",
                "ids", "inputFields", "nodes", "obligations", "operands", "persistedActions", "point", "release", "ruleActions",
                "rules", "ruleSetBindings", "ruleSets", "node.9", "node_10", "inputFields/country_code", "actionVersions/1",
                "nodes/9", "operands/10", "rules/1");
            for (String path : paths) {
                server.reset(); server.validationFails(true);
                server.corruptWire(root -> setAt(root, "/checks/2/errorSummary", "first=AST_TYPE_INVALID@" + path + "; violation_count=2147483647"));
                var result = client.validateCandidate(OPERATOR, KEY, "40001", new ValidateTestCandidateRequestBody(1));
                assertTrue(result.checks().get(2).errorSummary().contains("@" + path + ";"));
            }
        });
    }

    private static void addCheckMutations(List<Mutation> result, String path, boolean rejected) {
        result.add(set("missing checks", path, List.of()));
        result.add(new Mutation("checks reversed", root -> reverse(root, path)));
        result.add(new Mutation("duplicate check", root -> duplicate(root, path)));
        result.add(set("null check", path + "/0", null));
        result.add(set("passed severity", path + "/0/severity", "ERROR"));
        result.add(set("passed error code", path + "/0/errorCode", "AST_TYPE_INVALID"));
        result.add(set("passed error summary", path + "/0/errorSummary", "first=AST_TYPE_INVALID@-; violation_count=1"));
        if (path.equals("/staticChecks")) {
            result.add(set("duplicate audit", path + "/1/auditEventId", "60001"));
            result.add(set("state check audit collision", path + "/0/auditEventId", "50001"));
        }
        if (!rejected) return;
        result.add(set("failed severity", path + "/2/severity", "INFO"));
        result.add(set("failed null code", path + "/2/errorCode", null));
        result.add(set("failed null summary", path + "/2/errorSummary", null));
        for (String summary : List.of(" ", "first=OTHER@-; violation_count=1", "first=AST_TYPE_INVALID@bad/path; violation_count=1",
            "first=AST_TYPE_INVALID@node.0; violation_count=1", "first=AST_TYPE_INVALID@nodes/01; violation_count=1",
            "first=AST_TYPE_INVALID@inputFields/Bad; violation_count=1", "first=AST_TYPE_INVALID@-; violation_count=0",
            "first=AST_TYPE_INVALID@-; violation_count=01", "first=AST_TYPE_INVALID@-; violation_count=2147483648",
            "first=AST_TYPE_INVALID@-; violation_count=1; " + CANARY, CANARY, "x".repeat(1001))) {
            result.add(set("failed grammar " + result.size(), path + "/2/errorSummary", summary));
        }
        result.add(new Mutation("rejected with all passed", root -> {
            setAt(root, path + "/2/checkStatus", "PASSED"); setAt(root, path + "/2/severity", "INFO");
            setAt(root, path + "/2/errorCode", null); setAt(root, path + "/2/errorSummary", null);
        }));
    }

    private static void scalarMutations(JsonNode value, String path, List<Mutation> result) {
        if (value.isObject()) {
            value.properties().forEach(entry -> scalarMutations(entry.getValue(), path + "/" + entry.getKey(), result));
        } else if (value.isArray()) {
            for (int i = 0; i < value.size(); i++) scalarMutations(value.get(i), path + "/" + i, result);
        } else {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (value.isIntegralNumber() && !name.equals("total")) {
                result.add(set(path + " zero", path, 0)); result.add(set(path + " negative", path, -1));
            } else if (value.isString()) {
                if (name.endsWith("Id") || name.endsWith("By") || name.equals("nodeKey") || name.equals("parentNodeKey")) {
                    if (name.equals("triggerId")) { result.add(set(path + " invalid hash", path, "A".repeat(64))); return; }
                    for (String invalid : List.of("0", "01", "-1", "1e3", "9".repeat(20))) result.add(set(path + invalid, path, invalid));
                } else if (name.endsWith("Hash")) {
                    for (String invalid : List.of("a".repeat(63), "A".repeat(64), "g".repeat(64))) result.add(set(path + invalid.charAt(0) + invalid.length(), path, invalid));
                } else if (name.endsWith("Time")) {
                    for (String invalid : List.of("2026-02-30T01:02:03.123456Z", "2026-09-07T24:00:00.000000Z",
                        "2026-09-07T01:02:60.123456Z", "2026-09-07T01:02:03.123Z", "2026-09-07T01:02:03.123456+00:00")) result.add(set(path + invalid, path, invalid));
                } else if (!name.equals("literalValue") && !name.equals("errorSummary")) {
                    result.add(set(path + " blank", path, " "));
                    if (name.endsWith("Code") || name.equals("fieldCode")) result.add(set(path + " malformed code", path, "invalid-code!"));
                    if (Set.of("status", "nodeType", "groupOperator", "comparisonOperator", "operandRole", "sourceType",
                        "valueType", "environmentCode", "triggerType", "checkStatus", "severity").contains(name)) result.add(set(path + " unknown", path, "UNKNOWN"));
                }
            }
        }
    }

    private static Object invoke(RiskDecisionConfigurationClient client, Operation operation) {
        return switch (operation) {
            case DELETE -> client.deleteRuleSet(OPERATOR, KEY, "90001", new DeleteRuleSetRequestBody("1"));
            case AUTHORING -> client.authoringContext(OPERATOR, "CHECKOUT_PREPARE");
            case RULE_LIST -> client.ruleSets(OPERATOR, 1, 10, new RuleSetQueryRequestBody("CHECKOUT_PREPARE", "DEFAULT_RULES", "FROZEN"));
            case RULE_DETAIL -> client.ruleSet(OPERATOR, "30001");
            case SAVE -> client.createRuleSetVersion(OPERATOR, KEY, save());
            case FREEZE -> client.freezeRuleSetVersion(OPERATOR, KEY, "30001", new FreezeRuleSetVersionRequestBody(HASH));
            case CANDIDATE_LIST -> client.candidates(OPERATOR, 1, 10, new CandidateQueryRequestBody("CHECKOUT_PREPARE", "DRAFT"));
            case CANDIDATE_DETAIL -> client.candidate(OPERATOR, "40001");
            case CREATE -> client.createCandidate(OPERATOR, KEY, new CreateTestCandidateRequestBody("30001", HASH));
            case VALIDATE -> client.validateCandidate(OPERATOR, KEY, "40001", new ValidateTestCandidateRequestBody(1));
        };
    }

    private static CreateRuleSetVersionRequestBody save() {
        var operands = List.of(new OperandRequestBody("LEFT", 1, "CONTEXT", "country_code", "STRING", null),
            new OperandRequestBody("RIGHT", 1, "LITERAL", null, "STRING", "AE"));
        var nodes = List.of(new ConditionNodeRequestBody("8", null, 1, "GROUP", "AND", null, List.of()),
            new ConditionNodeRequestBody("9", "8", 1, "PREDICATE", null, "EQ", operands),
            new ConditionNodeRequestBody("10", "8", 1, "PREDICATE", null, "EQ", operands));
        return new CreateRuleSetVersionRequestBody("CHECKOUT_PREPARE", "DEFAULT_RULES", "规则集", "30001", HASH,
            "初始版本", List.of(new RuleRequestBody("ALLOW_RULE", "允许规则", 1, true, null, nodes,
                new RuleActionRequestBody("ALLOW_CHECKOUT", 1))));
    }

    private static void assertExactRequest(RiskDecisionConfigurationSimulator server, Operation operation) {
        assertEquals(1, server.requests().size());
        assertEquals(List.of(), server.unexpectedRequests());
        var request = server.requests().getFirst();
        assertEquals(OPERATOR.accountId(), request.header("X-Gateway-Actor-Subject"));
        assertEquals(OPERATOR.sessionId(), request.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, request.header("X-Request-Id"));
        boolean post = Set.of(Operation.SAVE, Operation.FREEZE, Operation.CREATE, Operation.VALIDATE, Operation.DELETE).contains(operation);
        assertEquals(operation == Operation.DELETE ? "DELETE" : post ? "POST" : "GET", request.method());
        assertEquals(post ? KEY : null, request.header("Idempotency-Key"));
        String path = switch (operation) {
            case DELETE -> RULE_PATH + "/90001";
            case AUTHORING -> "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE";
            case RULE_LIST, SAVE -> RULE_PATH;
            case RULE_DETAIL -> RULE_PATH + "/30001";
            case FREEZE -> RULE_PATH + "/30001/freeze";
            case CANDIDATE_LIST, CREATE -> CANDIDATE_PATH;
            case CANDIDATE_DETAIL -> CANDIDATE_PATH + "/40001";
            case VALIDATE -> CANDIDATE_PATH + "/40001/validate";
        };
        assertEquals(path, request.path());
        Map<String, String> query = switch (operation) {
            case RULE_LIST -> Map.of("page", "1", "size", "10", "pointCode", "CHECKOUT_PREPARE", "ruleSetCode", "DEFAULT_RULES", "status", "FROZEN");
            case CANDIDATE_LIST -> Map.of("page", "1", "size", "10", "pointCode", "CHECKOUT_PREPARE", "status", "DRAFT");
            default -> Map.of();
        };
        assertEquals(query, RiskDecisionConfigurationSimulator.query(request.query()));
        if (!post) { assertEquals("", request.body()); return; }
        JsonNode expected = switch (operation) {
            case DELETE -> JSON.valueToTree(Map.of("expectedVersion", "1"));
            case SAVE -> JSON.valueToTree(save());
            case FREEZE -> JSON.valueToTree(Map.of("expectedContentHash", HASH));
            case CREATE -> JSON.valueToTree(Map.of("ruleSetVersionId", "30001", "expectedRuleSetContentHash", HASH));
            case VALIDATE -> JSON.valueToTree(Map.of("expectedVersion", 1));
            default -> throw new AssertionError();
        };
        assertEquals(expected, JSON.readTree(request.body()));
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable action, String label, CapturedOutput output) {
        var error = assertThrows(ErrorException.class, action, label);
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, error.getErrorCode(), label);
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getMessage(), error.getMessage(), label);
        StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains(CANARY), label);
        assertFalse(output.getAll().contains(CANARY), label);
    }

    private static void withClient(ClientTest test) throws Exception {
        try (var server = new RiskDecisionConfigurationSimulator()) {
            new ApplicationContextRunner().withUserConfiguration(RiskClientConfiguration.class)
                .withBean(RiskDiscoveryInterceptor.class, () -> new RiskDiscoveryInterceptor(new NoDiscovery()))
                .withBean(RiskDecisionConfigurationClientImpl.class)
                .withPropertyValues("spring.profiles.active=auto", "operations.risk-test-base-url=" + server.baseUrl())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    try (var ignored = MDC.putCloseable(RequestIdFilter.MDC_KEY, REQUEST_ID)) {
                        test.run(server, context.getBean(RiskDecisionConfigurationClient.class));
                    }
                    assertEquals(List.of(), server.unexpectedRequests());
                });
        }
    }

    private static Mutation set(String label, String path, Object value) {
        return new Mutation(label, root -> setAt(root, path, value));
    }

    private static void setAt(ObjectNode root, String path, Object value) {
        int separator = path.lastIndexOf('/');
        JsonNode parent = root.at(path.substring(0, separator));
        String key = path.substring(separator + 1);
        JsonNode replacement = JSON.valueToTree(value);
        if (parent instanceof ObjectNode object) object.set(key, replacement);
        else ((ArrayNode) parent).set(Integer.parseInt(key), replacement);
    }

    private static void reverse(ObjectNode root, String path) {
        var array = (ArrayNode) root.at(path);
        assertTrue(array.size() > 1, "order mutation requires multiple elements");
        for (int i = 0; i < array.size() / 2; i++) swap(root, path, i, array.size() - 1 - i);
    }

    private static void swap(ObjectNode root, String path, int first, int second) {
        var array = (ArrayNode) root.at(path);
        var saved = array.get(first); array.set(first, array.get(second)); array.set(second, saved);
    }

    private static void duplicate(ObjectNode root, String path) {
        var array = (ArrayNode) root.at(path);
        assertFalse(array.isEmpty(), "uniqueness mutation requires data");
        array.insert(1, array.get(0).deepCopy());
    }

    private static Object validCheck() {
        return Map.of("auditEventId", "60001", "eventTime", TIME, "checkCode", "AST_TYPES", "checkStatus", "FAILED",
            "severity", "ERROR", "errorCode", "AST_TYPE_INVALID", "errorSummary", "first=AST_TYPE_INVALID@-; violation_count=1");
    }

    private record Mutation(String label, Consumer<ObjectNode> change) {}
    private record Correlation(Operation operation, String path, String value) {}
    @FunctionalInterface private interface ClientTest {
        void run(RiskDecisionConfigurationSimulator server, RiskDecisionConfigurationClient client) throws Exception;
    }

    private static final class NoDiscovery implements LoadBalancerClient {
        @Override public ServiceInstance choose(String serviceId) { throw new AssertionError("Unexpected discovery"); }
        @Override public <T> ServiceInstance choose(String serviceId, Request<T> request) { throw new AssertionError("Unexpected discovery"); }
        @Override public <T> T execute(String serviceId, LoadBalancerRequest<T> request) { throw new AssertionError("Unexpected discovery"); }
        @Override public <T> T execute(String serviceId, ServiceInstance instance, LoadBalancerRequest<T> request) { throw new AssertionError("Unexpected discovery"); }
        @Override public URI reconstructURI(ServiceInstance instance, URI original) { throw new AssertionError("Unexpected discovery"); }
    }
}

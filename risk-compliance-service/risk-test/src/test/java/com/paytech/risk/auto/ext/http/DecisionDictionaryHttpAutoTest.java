package com.paytech.risk.auto.ext.http;

import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.ACTOR;
import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.SESSION;
import static com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem;
import static org.junit.jupiter.api.Assertions.*;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 字典合同从真实 HTTP 入口进入 MySQL/Redis 生产链，不在启动时建表或种子化。 */
class DecisionDictionaryHttpAutoTest extends RiskServiceAutoTestBase {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String DICTIONARIES="/decision-dictionaries/v1/";
    private static final String RULES="/decision-rule-sets/v1/rule-sets";
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.beans.factory.annotation.Autowired
    private com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator identifiers;
    @org.springframework.beans.factory.annotation.Autowired
    private org.redisson.api.RedissonClient redis;
    @org.springframework.beans.factory.annotation.Autowired
    private com.paytech.risk.decision.domainservice.service.DecisionRuleSetService ruleSetService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer canonicalizer;
    /** 生成本用例专属字典编码，避免触及人工维护数据。 */
    private static String code(){return "RCO16_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);}
    /** 构造原因创建请求及非敏感测试内容。 */
    private static String createBody(String kind,String code){return "{\""+(kind.equals("rejection-reasons")?"reasonCode":"tagCode")+"\":\""+code+"\",\"name\":\"人工复核\",\"description\":null"+(kind.equals("rejection-reasons")?",\"defaultMessage\":\"默认内部消息\"":"")+"}";}
    /** 断言成功状态，避免后续把错误体当作业务结果处理。 */
    private static void success(HttpResponse<String> result){assertEquals(200,result.statusCode(),result.body());}
    /** 验证未登记代码返回字典专属的未找到 Problem。 */
    @Test void test_unregisteredCodeHasDictionaryNotFoundProblem()throws Exception {
        try(var http=new RiskDecisionHttpClient(port)){assertProblem(http.get(DICTIONARIES+"rejection-reasons/"+code(),ACTOR,SESSION),404,"RISK_DICTIONARY_NOT_FOUND");}
    }
    /** 经真实 HTTP 验证字典创建、编辑、启停、查询及停用后代码不可复用。 */
    @ParameterizedTest @ValueSource(strings={"rejection-reasons","subject-tags"})
    void test_createEditStatusAndPermanentUniqueness(String kind)throws Exception {
        String code=code();String path=DICTIONARIES+kind;
        try(var http=new RiskDecisionHttpClient(port)){
            var created=http.dictionary("POST",path,ACTOR,SESSION,createBody(kind,code));success(created);assertEquals("1",http.json(created).path("version").asString());
            assertProblem(http.dictionary("POST",path,ACTOR,SESSION,createBody(kind,code)),409,"RISK_DICTIONARY_CODE_EXISTS");
            var updated=JSON.createObjectNode().put("expectedVersion","1").put("name","新的名称").put("description","");if(kind.equals("rejection-reasons"))updated.put("defaultMessage","新默认文案");
            success(http.dictionary("PUT",path+"/"+code,ACTOR,SESSION,updated.toString()));
            assertProblem(http.dictionary("PUT",path+"/"+code,ACTOR,SESSION,updated.toString()),409,"RISK_DICTIONARY_CHANGED");
            var disabled=http.dictionary("POST",path+"/"+code+"/status",ACTOR,SESSION,"{\"expectedVersion\":\"2\",\"status\":\"DISABLED\"}");success(disabled);
            var detail=http.get(path+"/"+code,ACTOR,SESSION);success(detail);assertEquals("3",http.json(detail).path("version").asString());assertTrue(http.json(detail).path("description").isNull());
            assertEquals("DISABLED",http.json(detail).path("status").asString());
            var list=http.get(path+"?page=1&size=10&keyword="+code+"&status=ACTIVE",ACTOR,SESSION);success(list);assertEquals(0,http.json(list).path("total").asInt());
            success(http.get(path+"/"+code+"/references?page=1&size=10&scope=ALL",ACTOR,SESSION));
            assertProblem(http.dictionary("POST",path,ACTOR,SESSION,createBody(kind,code)),409,"RISK_DICTIONARY_CODE_EXISTS");assertEquals(1,dictionaryTestMapper.countOwned(code));
        }finally{dictionaryTestMapper.deleteOwned(code);}
    }
    /** 同时提交相同期望版本，验证真实锁与数据库 CAS 仅允许一个成功。 */
    @Test void test_concurrentUpdatesHaveExactlyOneCasWinner()throws Exception {
        String code=code();String path=DICTIONARIES+"subject-tags";
        try(var http=new RiskDecisionHttpClient(port);var pool=Executors.newVirtualThreadPerTaskExecutor()){
            success(http.dictionary("POST",path,ACTOR,SESSION,createBody("subject-tags",code)));var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            List<Future<HttpResponse<String>>> calls=new ArrayList<>();for(int i=0;i<2;i++)calls.add(pool.submit(()->{ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));return http.dictionary("PUT",path+"/"+code,ACTOR,SESSION,"{\"expectedVersion\":\"1\",\"name\":\"并发更新\",\"description\":null}");}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();List<Integer> statuses=new ArrayList<>();for(var call:calls)statuses.add(call.get(10,TimeUnit.SECONDS).statusCode());assertEquals(List.of(200,409),statuses.stream().sorted().toList());
            assertEquals("2",http.json(http.get(path+"/"+code,ACTOR,SESSION)).path("version").asString());
        }finally{dictionaryTestMapper.deleteOwned(code);}
    }
    /** 验证停用原因的精确沿用、新选拒绝、历史查看及原成功请求回放。 */
    @Test void test_historyAndDisabledReasonRetentionKeepOriginalReplay()throws Exception {
        String reason=code();String ruleSet=decisionTestMapper.uniqueRuleSetCode();var identities=new ArrayList<OwnedCommandIdentity>();
        try(var http=new RiskDecisionHttpClient(port)){
            success(http.dictionary("POST",DICTIONARIES+"rejection-reasons",ACTOR,SESSION,createBody("rejection-reasons",reason)));
            ObjectNode draft=JSON.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSet));ObjectNode rule=(ObjectNode)draft.at("/rules/0");((ObjectNode)rule.get("action")).put("actionCode","DENY_CHECKOUT");rule.put("reasonCode",reason).put("reasonMessage","手工覆盖默认消息");rule.set("additionalActions",JSON.createArrayNode());
            String originalKey=key(identities);var first=http.post(RULES,ACTOR,SESSION,originalKey,draft);success(first);
            var firstBody=http.json(first);String firstId=firstBody.path("ruleSetVersionId").asString();String hash=firstBody.path("contentHash").asString();
            var root=http.json(http.get(RULES+"?pointCode=CHECKOUT_PREPARE&ruleSetCode="+ruleSet+"&page=1&size=10",ACTOR,SESSION)).at("/items/0");String rootId=root.path("ruleSetId").asString();
            success(http.dictionary("POST",DICTIONARIES+"rejection-reasons/"+reason+"/status",ACTOR,SESSION,"{\"expectedVersion\":\"1\",\"status\":\"DISABLED\"}"));
            ObjectNode next=draft.deepCopy();next.put("baseRuleSetVersionId",firstId).put("baseContentHash",hash).put("changeReason","只调整配置名称").put("ruleSetName","新的规则集名称");
            var second=http.post(RULES,ACTOR,SESSION,key(identities),next);success(second);
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post(RULES,ACTOR,SESSION,originalKey,draft), 409, "CONCURRENT_REQUEST");
            String secondId=http.json(second).path("ruleSetVersionId").asString();next.put("baseRuleSetVersionId",secondId).put("baseContentHash",http.json(second).path("contentHash").asString());((ObjectNode)next.at("/rules/0")).put("reasonMessage","修改停用原因的消息");
            assertProblem(http.post(RULES,ACTOR,SESSION,key(identities),next),409,"RISK_DICTIONARY_SELECTION_UNAVAILABLE");
            var history=http.get(RULES+"/"+rootId+"/versions?page=1&size=10&maxVersionNo=1",ACTOR,SESSION);success(history);assertEquals(1,http.json(history).path("total").asInt());assertFalse(http.json(history).at("/items/0/isCurrent").asBoolean());
            var all=http.get(RULES+"/"+rootId+"/versions?page=1&size=10&maxVersionNo=2",ACTOR,SESSION);success(all);assertEquals(secondId,http.json(all).at("/items/0/ruleSetVersionId").asString());
            var references=http.get(DICTIONARIES+"rejection-reasons/"+reason+"/references?page=1&size=10&scope=HISTORY",ACTOR,SESSION);success(references);assertEquals(firstId,http.json(references).at("/items/0/ruleSetVersionId").asString());
            success(http.get(RULES+"/"+rootId+"/release-references?page=1&size=10",ACTOR,SESSION));
        }finally{decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSet),identities);dictionaryTestMapper.deleteOwned(reason);}
    }
    /** 验证缺失、重复或未知字段等非法 JSON 形状在写入前被拒绝。 */
    @ParameterizedTest @ValueSource(strings={"{}","{\"tagCode\":\"BAD\",\"name\":\"测试\",\"description\":null,\"subjectTypes\":[\"CLIENT\"]}","{\"tagCode\":\"BAD\",\"name\":\"测试\",\"name\":\"重复\",\"description\":null}","{\"tagCode\":\"BAD\",\"name\":123,\"description\":null}"})
    void test_strictBodyShape(String body)throws Exception {try(var http=new RiskDecisionHttpClient(port)){assertProblem(http.dictionary("POST",DICTIONARIES+"subject-tags",ACTOR,SESSION,body),400,"INVALID_REQUEST");}}
    /** 验证 GET Body、未知 Query 和超限 Body 不产生字典写入。 */
    @Test void test_getBodyUnknownQueryAndLimitFailBeforeDatabaseMutation()throws Exception {
        try(var http=new RiskDecisionHttpClient(port)){
            assertProblem(http.dictionary("GET",DICTIONARIES+"subject-tags",ACTOR,SESSION,"{}"),400,"INVALID_REQUEST");
            assertProblem(http.get(DICTIONARIES+"subject-tags?subjectType=CLIENT",ACTOR,SESSION),400,"INVALID_REQUEST");
            assertProblem(http.dictionary("POST",DICTIONARIES+"subject-tags",ACTOR,SESSION," ".repeat(16385)),413,"INVALID_REQUEST");
        }
    }
    /** 缺失与停用标签均可原样沿用及重排，但复制动作会增加选择次数，必须拒绝。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_oldTagRetentionDoesNotPermitCopyingActions(boolean removeDictionary) throws Exception {
        String tag = code();
        String ruleSet = decisionTestMapper.uniqueRuleSetCode();
        var identities = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            success(http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", tag)));
            ObjectNode draft = withTag(ruleSet, tag);
            var actions = (tools.jackson.databind.node.ArrayNode) draft.at("/rules/0/additionalActions");
            actions.add(JSON.readTree("""
                {"sequenceNo":2,"actionCode":"APPLY_COOLDOWN","actionVersionNo":1,
                 "targetBindingCode":"CURRENT_CLIENT_V1","subjectVariableCode":"INPUT_CLIENT_ID","parameters":[
                  {"parameterCode":"DURATION","valueSequence":1,"sourceType":"LITERAL",
                   "fieldCode":null,"valueType":"DURATION","literalValue":"300000"}]}
                """));
            String originalKey = key(identities);
            var first = http.post(RULES, ACTOR, SESSION, originalKey, draft);
            success(first);
            if (removeDictionary) dictionaryTestMapper.deleteOwned(tag);
            else success(http.dictionary("POST", DICTIONARIES + "subject-tags/" + tag + "/status", ACTOR, SESSION,
                "{\"expectedVersion\":\"1\",\"status\":\"DISABLED\"}"));
            ObjectNode next = nextDraft(draft, http.json(first));
            var reordered = JSON.createArrayNode();
            reordered.add(((ObjectNode) actions.get(1)).deepCopy().put("sequenceNo", 1));
            reordered.add(((ObjectNode) actions.get(0)).deepCopy().put("sequenceNo", 2));
            ((ObjectNode) next.at("/rules/0")).set("additionalActions", reordered);
            var saved = http.post(RULES, ACTOR, SESSION, key(identities), next);
            success(saved);
            com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem(http.post(RULES, ACTOR, SESSION, originalKey, draft), 409, "CONCURRENT_REQUEST");
            ObjectNode copied = nextDraft(next, http.json(saved));
            ((tools.jackson.databind.node.ArrayNode) copied.at("/rules/0/additionalActions"))
                .add(((ObjectNode) reordered.get(1)).deepCopy().put("sequenceNo", 3));
            String rejectedKey = key(identities);
            assertProblem(http.post(RULES, ACTOR, SESSION, rejectedKey, copied), 409, "RISK_DICTIONARY_SELECTION_UNAVAILABLE");
            assertEquals(2, decisionTestMapper.ruleSetVersionCount(ruleSet));
            ObjectNode copiedRule = nextDraft(next, http.json(saved));
            ((tools.jackson.databind.node.ArrayNode) copiedRule.get("rules")).add(
                ((ObjectNode) copiedRule.at("/rules/0")).deepCopy().put("ruleCode", "COPIED_RULE").put("priorityNo", 20));
            assertProblem(http.post(RULES, ACTOR, SESSION, key(identities), copiedRule), 409,
                "RISK_DICTIONARY_SELECTION_UNAVAILABLE");
            ObjectNode changedTarget = nextDraft(next, http.json(saved));
            ((ObjectNode) changedTarget.at("/rules/0/additionalActions/1")).put("targetBindingCode", "UNREGISTERED_TARGET");
            var targetRejected = http.post(RULES, ACTOR, SESSION, key(identities), changedTarget);
            assertEquals(400, targetRejected.statusCode(), targetRejected.body());
            assertEquals(2, decisionTestMapper.ruleSetVersionCount(ruleSet));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSet), identities);
            dictionaryTestMapper.deleteOwned(tag);
        }
    }

    /** 删除根后保留精确标签引用，但普通版本与历史入口不可访问。 */
    @Test
    void test_deletedRootReferencesRetainCurrentPointerWithoutExposingVersionDetail() throws Exception {
        String tag = code();
        String ruleSet = decisionTestMapper.uniqueRuleSetCode();
        var identities = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            success(http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", tag)));
            ObjectNode draft = withTag(ruleSet, tag);
            // 另一条 ALLOW 规则的同码普通原因不是标签选择，不应增加反查条数。
            ObjectNode ordinaryRule = ((ObjectNode) draft.at("/rules/0")).deepCopy()
                .put("ruleCode", "ORDINARY_REASON").put("priorityNo", 20).put("reasonCode", tag);
            ordinaryRule.set("additionalActions", JSON.createArrayNode());
            ((tools.jackson.databind.node.ArrayNode) draft.get("rules")).add(ordinaryRule);
            var saved = http.post(RULES, ACTOR, SESSION, key(identities), draft);
            success(saved);
            var root = ruleSetRoot(http, ruleSet);
            String deleteKey = decisionTestMapper.uniqueNamespace();
            identities.add(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, "DELETE_RULE_SET", deleteKey));
            success(http.delete(RULES + "/" + root.path("ruleSetId").asString(), ACTOR, SESSION, deleteKey,
                Map.of("expectedVersion", root.path("managementVersion").asString())));
            var references = http.get(DICTIONARIES + "subject-tags/" + tag + "/references?scope=DELETED", ACTOR, SESSION);
            success(references);
            assertEquals(1, http.json(references).path("total").asInt());
            var item = http.json(references).at("/items/0");
            assertEquals("DELETED", item.path("ruleSetStatus").asString());
            assertTrue(item.path("isCurrentConfiguration").asBoolean());
            assertEquals(0, item.path("activeEnvironments").size());
            assertEquals("CURRENT_CLIENT_V1", item.path("targetBindingCode").asString());
            for (String scope : List.of("CURRENT", "HISTORY")) {
                var response = http.get(DICTIONARIES + "subject-tags/" + tag + "/references?scope=" + scope, ACTOR, SESSION);
                success(response);
                assertEquals(0, http.json(response).path("total").asInt());
            }
            assertProblem(http.get(RULES + "/" + http.json(saved).path("ruleSetVersionId").asString(), ACTOR, SESSION),
                404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertProblem(http.get(RULES + "/" + root.path("ruleSetId").asString() + "/versions?maxVersionNo=1", ACTOR, SESSION),
                404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSet), identities);
            dictionaryTestMapper.deleteOwned(tag);
        }
    }

    /** 同时创建相同代码只能有一次成功，另一请求返回永久代码冲突。 */
    @Test
    void test_concurrentCreatesKeepOnePermanentCode() throws Exception {
        String tag = code();
        try (var http = new RiskDecisionHttpClient(port); var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var calls = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < 2; i++) calls.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", tag));
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var responses = new ArrayList<HttpResponse<String>>();
            for (var call : calls) responses.add(call.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            assertProblem(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(),
                409, "RISK_DICTIONARY_CODE_EXISTS");
            assertEquals(1, dictionaryTestMapper.countOwned(tag));
        } finally { dictionaryTestMapper.deleteOwned(tag); }
    }

    /** 一个字典代码的 Redis 锁不阻塞另一代码；竞争失败不留下字典记录。 */
    @Test
    void test_dictionaryResourceLockIsPerTypeAndCode() throws Exception {
        String blocked = code();
        String independent = code();
        var lock = redis.getLock("decision:dictionary:SUBJECT_TAG:" + blocked);
        try (var http = new RiskDecisionHttpClient(port)) {
            lock.lock(10, TimeUnit.SECONDS);
            try {
                success(http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", independent)));
                assertProblem(http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", blocked)),
                    409, "CONCURRENT_REQUEST");
                assertEquals(0, dictionaryTestMapper.countOwned(blocked));
            } finally { lock.unlock(); }
            success(http.dictionary("POST", DICTIONARIES + "subject-tags", ACTOR, SESSION, createBody("subject-tags", blocked)));
            assertFalse(lock.isLocked());
        } finally { dictionaryTestMapper.deleteOwned(blocked); dictionaryTestMapper.deleteOwned(independent); }
    }

    /** 新增版本不会改变固定历史上界的结果，响应当前标志继续取自真实根指针。 */
    @Test
    void test_historyUpperBoundDoesNotDriftWhenAnotherVersionIsSaved() throws Exception {
        try (var fixture = releaseFixture(); var http = new RiskDecisionHttpClient(port)) {
            var first = fixture.arrangeFrozenCandidate();
            var second = http.post(RULES, ACTOR, SESSION, fixture.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                fixture.ruleSetCode(), first.created().getRuleSetVersionId(), first.request().getExpectedRuleSetContentHash()));
            success(second);
            var root = ruleSetRoot(http, fixture.ruleSetCode());
            String path = RULES + "/" + root.path("ruleSetId").asString() + "/versions?maxVersionNo=2&size=10";
            var before = http.get(path, ACTOR, SESSION);
            success(before);
            success(http.post(RULES, ACTOR, SESSION, fixture.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                fixture.ruleSetCode(), http.json(second).path("ruleSetVersionId").asString(), http.json(second).path("contentHash").asString())));
            var after = http.get(path, ACTOR, SESSION);
            success(after);
            assertEquals(2, http.json(after).path("total").asInt());
            assertEquals(http.json(before).at("/items/0/ruleSetVersionId"), http.json(after).at("/items/0/ruleSetVersionId"));
            assertFalse(http.json(after).at("/items/0/isCurrent").asBoolean());
            var emptyPage = http.get(path + "&page=2", ACTOR, SESSION);
            success(emptyPage);
            assertEquals(2, http.json(emptyPage).path("total").asInt());
            assertEquals(0, http.json(emptyPage).path("items").size());
        }
    }

    /** 旧冻结版在任一环境生效时，新草稿的空引用筛选仍必须返回整体 inUse。 */
    @ParameterizedTest
    @ValueSource(strings = {"TEST", "PRODUCTION"})
    void test_activeOldVersionSurvivesEmptyReleaseFilters(String environment) throws Exception {
        try (var fixture = releaseFixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = fixture.arrangeFrozenCandidate();
            var second = http.post(RULES, ACTOR, SESSION, fixture.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                fixture.ruleSetCode(), candidate.created().getRuleSetVersionId(), candidate.request().getExpectedRuleSetContentHash()));
            success(second);
            jdbc.update("UPDATE t_decision_release SET environment_code=?,status='ACTIVE',active_slot=1,activated_time=UTC_TIMESTAMP(6) WHERE release_id=?",
                environment, candidate.created().getReleaseId());
            String path = RULES + "/" + ruleSetRoot(http, fixture.ruleSetCode()).path("ruleSetId").asString() + "/release-references";
            var result = http.get(path, ACTOR, SESSION);
            success(result);
            var body = http.json(result);
            assertTrue(body.path("inUse").asBoolean());
            assertEquals(1, body.at("/references/total").asInt());
            assertEquals(environment, body.at("/references/items/0/environmentCode").asString());
            assertEquals(candidate.created().getRuleSetVersionId(), body.at("/references/items/0/ruleSetVersionId").asString());
            for (String filter : List.of("status=DRAFT", "page=2", "ruleSetVersionId=" + http.json(second).path("ruleSetVersionId").asString())) {
                var empty = http.get(path + "?" + filter, ACTOR, SESSION);
                success(empty);
                assertTrue(http.json(empty).path("inUse").asBoolean());
                assertEquals(0, http.json(empty).at("/references/items").size());
            }
        }
    }

    /** 发布断边即使不落入当前状态筛选也必须报错，不能显示为空引用。 */
    @Test
    void test_brokenReleaseRelationshipFailsBeforeFiltering() throws Exception {
        try (var fixture = releaseFixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = fixture.arrangeFrozenCandidate();
            String path = RULES + "/" + ruleSetRoot(http, fixture.ruleSetCode()).path("ruleSetId").asString() + "/release-references?status=ACTIVE";
            String pointVersionId = candidate.created().getPointVersionId();
            jdbc.update("UPDATE t_decision_release SET point_version_id=? WHERE release_id=?", Long.MAX_VALUE, candidate.created().getReleaseId());
            try { assertEquals(500, http.get(path, ACTOR, SESSION).statusCode()); }
            finally { jdbc.update("UPDATE t_decision_release SET point_version_id=? WHERE release_id=?", pointVersionId, candidate.created().getReleaseId()); }
            success(http.get(path, ACTOR, SESSION));
        }
    }

    /** 人工构造仅属于本用例的旧格式空消息闭包，公开回读须保留原值，新保存仍拒绝。 */
    @Test
    void test_legacyNullReasonMessageRemainsReadableButCannotBeNewlySaved() throws Exception {
        String reason = code();
        String ruleSet = decisionTestMapper.uniqueRuleSetCode();
        var identities = new ArrayList<OwnedCommandIdentity>();
        try (var http = new RiskDecisionHttpClient(port)) {
            success(http.dictionary("POST", DICTIONARIES + "rejection-reasons", ACTOR, SESSION,
                createBody("rejection-reasons", reason)));
            ObjectNode draft = JSON.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSet));
            ObjectNode rule = (ObjectNode) draft.at("/rules/0");
            ((ObjectNode) rule.get("action")).put("actionCode", "DENY_CHECKOUT");
            rule.put("reasonCode", reason).put("reasonMessage", "当前格式创建时必填");
            rule.set("additionalActions", JSON.createArrayNode());
            var created = http.post(RULES, ACTOR, SESSION, key(identities), draft);
            success(created);
            var identity = http.json(created);
            long versionId = Long.parseLong(identity.path("ruleSetVersionId").asString());
            // 使用真实保存闭包的非敏感投影构造历史夹具；所有业务断言仍从公开 HTTP 入口进行。
            var aggregate = ruleSetService.detail(versionId);
            var projection = aggregate.ruleSetProjection();
            var oldRules = projection.rules().stream().map(r ->
                new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleProjection(
                    r.ruleCode(), r.ruleName(), r.priorityNo(), r.enabledFlag(), r.reasonCode(),
                    new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.PredicateProjection(
                        com.paytech.risk.decision.domain.enums.ComparisonOperator.EQ, List.of(
                            new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection(
                                com.paytech.risk.decision.domain.enums.OperandRole.LEFT, 1,
                                com.paytech.risk.decision.domain.enums.OperandSource.CONTEXT, "country_code",
                                com.paytech.risk.decision.domain.enums.DecisionValueType.STRING,
                                com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.NullValue.INSTANCE),
                            new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection(
                                com.paytech.risk.decision.domain.enums.OperandRole.RIGHT, 1,
                                com.paytech.risk.decision.domain.enums.OperandSource.LITERAL, null,
                                com.paytech.risk.decision.domain.enums.DecisionValueType.STRING,
                                new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue("AE")))),
                    r.action())).toList();
            var legacy = new com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection(
                projection.pointCode(), projection.ruleSetCode(), projection.ruleSetName(), projection.versionNo(), oldRules);
            String hash = canonicalizer.ruleSetVersion(legacy).sha256LowerHex();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertEquals(1, jdbc.update("UPDATE t_rule_operand o JOIN t_rule_node n ON n.node_id=o.node_id "
                    + "JOIN t_risk_rule r ON r.rule_id=n.rule_id SET o.source_type='CONTEXT',o.field_code='country_code',o.variable_code=NULL "
                    + "WHERE r.rule_set_version_id=? AND o.operand_role='LEFT'", versionId));
                assertEquals(1, jdbc.update("UPDATE t_risk_rule SET reason_message=NULL WHERE rule_set_version_id=?", versionId));
                assertEquals(1, jdbc.update("UPDATE t_rule_set_version SET content_format='LEGACY',content_hash=? WHERE rule_set_version_id=?", hash, versionId));
            });
            success(http.dictionary("POST", DICTIONARIES + "rejection-reasons/" + reason + "/status", ACTOR, SESSION,
                "{\"expectedVersion\":\"1\",\"status\":\"DISABLED\"}"));
            var detail = http.get(RULES + "/" + versionId, ACTOR, SESSION);
            success(detail);
            assertTrue(http.json(detail).at("/rules/0/reasonMessage").isNull());
            assertEquals(hash, http.json(detail).path("contentHash").asString());
            var next = nextDraft(draft, http.json(detail));
            ((ObjectNode) next.at("/rules/0")).putNull("reasonMessage");
            var rejected = http.post(RULES, ACTOR, SESSION, key(identities), next);
            assertProblem(rejected, 400, "BUSINESS_CHECK_FAILED");
            assertEquals(1, decisionTestMapper.ruleSetVersionCount(ruleSet));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSet), identities);
            dictionaryTestMapper.deleteOwned(reason);
        }
    }

    /** 构造含一个有效标签动作的规则配置，其他字段沿用原 API Builder。 */
    private ObjectNode withTag(String ruleSet, String tag) {
        ObjectNode draft = JSON.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSet));
        ObjectNode action = JSON.createObjectNode().put("sequenceNo", 1).put("actionCode", "ADD_RISK_TAG")
            .put("actionVersionNo", 1).put("targetBindingCode", "CURRENT_CLIENT_V1").put("subjectVariableCode", "INPUT_CLIENT_ID");
        action.set("parameters", JSON.createArrayNode().add(JSON.createObjectNode().put("parameterCode", "TAG")
            .put("valueSequence", 1).put("sourceType", "LITERAL").putNull("fieldCode").put("valueType", "STRING").put("literalValue", tag)));
        ((ObjectNode) draft.at("/rules/0")).set("additionalActions", JSON.createArrayNode().add(action));
        return draft;
    }

    /** 基于真实保存响应构造后续版本，不从列表猜测内容 Hash。 */
    private ObjectNode nextDraft(ObjectNode previous, tools.jackson.databind.JsonNode result) {
        ObjectNode next = previous.deepCopy();
        return next.put("baseRuleSetVersionId", result.path("ruleSetVersionId").asString())
            .put("baseContentHash", result.path("contentHash").asString()).put("changeReason", "核对字典旧项兼容");
    }

    /** 经规则集公开列表定位本用例整体根。 */
    private tools.jackson.databind.JsonNode ruleSetRoot(RiskDecisionHttpClient http, String code) throws Exception {
        var response = http.get(RULES + "?pointCode=CHECKOUT_PREPARE&ruleSetCode=" + code, ACTOR, SESSION);
        success(response);
        assertEquals(1, http.json(response).path("total").asInt());
        return http.json(response).at("/items/0");
    }

    /** 使用既有 Fixture 创建真实冻结闭包；活动与损坏状态仅属于本用例数据。 */
    private com.paytech.risk.test.decision.DecisionReleaseTestFixture releaseFixture() {
        return new com.paytech.risk.test.decision.DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }

    /** 为旧规则集保存命令生成独立幂等键，不用于字典写入。 */
    private String key(List<OwnedCommandIdentity> identities){String key=decisionTestMapper.uniqueNamespace();identities.add(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:"+ACTOR,"CREATE_RULE_SET_VERSION",key));return key;}
}

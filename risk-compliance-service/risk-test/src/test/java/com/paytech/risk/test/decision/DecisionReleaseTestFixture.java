package com.paytech.risk.test.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.paytech.risk.api.decision.request.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.CreateTestReleaseResponseBody;
import com.paytech.risk.api.decision.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 为 Decision Release 真实 HTTP/MySQL 测试准备完全 test-owned 的冻结闭包和恢复状态。 */
public final class DecisionReleaseTestFixture implements AutoCloseable {

    public static final String ACTOR = "910000000000000001";
    public static final long ACTOR_ID = 910000000000000001L;
    public static final String OTHER_ACTOR = "910000000000000002";
    public static final long OTHER_ACTOR_ID = 910000000000000002L;
    public static final String SESSION = "risk-auto-release-session";

    private final RiskDecisionHttpClient http;
    private final RiskDecisionTestMapper decisionMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DecisionIdentifierGenerator identifiers;
    private final String ruleSetCode;
    private final List<String> ownedKeys = new ArrayList<>();

    public DecisionReleaseTestFixture(
        int port,
        RiskDecisionTestMapper decisionMapper,
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager,
        DecisionIdentifierGenerator identifiers
    ) {
        this.http = new RiskDecisionHttpClient(port);
        this.decisionMapper = decisionMapper;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.identifiers = identifiers;
        this.ruleSetCode = decisionMapper.uniqueRuleSetCode();
    }

    /** 当前 Fixture 独占的规则集编码。 */
    public String ruleSetCode() { return ruleSetCode; }

    /** 冻结 Candidate 场景最初的保存命令键，用于检验历史成功回放。 */
    public String originalRuleCreateKey() { return ownedKeys.get(0); }

    /** 冻结 Candidate 场景最初的冻结命令键。 */
    public String originalRuleFreezeKey() { return ownedKeys.get(1); }

    /** 只准备旧 DRAFT 事实，不通过新版保存生成旧协议。 */
    public LegacyRuleSetTestMapper.Version arrangeLegacyDraft() {
        return new LegacyRuleSetTestMapper(jdbc, transaction, identifiers)
            .insertFirstVersion(ruleSetCode, ACTOR_ID, nextKey());
    }

    public Candidate arrangeFrozenCandidate() throws Exception {
        var createdRule = arrangeLegacyDraft();

        String freezeKey = nextKey();
        HttpResponse<String> frozenResponse = http.post(
            "/decision-rule-sets/v1/rule-sets/" + createdRule.ruleSetVersionId() + "/freeze",
            ACTOR, SESSION, freezeKey, new FreezeRuleSetVersionRequestBody(createdRule.contentHash()));
        assertEquals(200, frozenResponse.statusCode(), frozenResponse.body());

        CreateTestReleaseRequestBody request = new CreateTestReleaseRequestBody(
            createdRule.ruleSetVersionId(), createdRule.contentHash());
        String candidateKey = nextKey();
        HttpResponse<String> createdResponse = http.post(
            "/decision-releases/v1/candidates", ACTOR, SESSION, candidateKey, request);
        assertEquals(200, createdResponse.statusCode(), createdResponse.body());
        CreateTestReleaseResponseBody created = http.body(
            createdResponse, CreateTestReleaseResponseBody.class);
        return new Candidate(request, candidateKey, createdResponse.body(), created);
    }

    public HttpResponse<String> postCandidate(String key, CreateTestReleaseRequestBody request) throws Exception {
        own(key);
        return http.post("/decision-releases/v1/candidates", ACTOR, SESSION, key, request);
    }

    public HttpResponse<String> postValidate(
        Candidate candidate,
        String actor,
        String key,
        int expectedVersion
    ) throws Exception {
        own(key);
        return http.post("/decision-releases/v1/candidates/" + candidate.created().getReleaseId() + "/validate",
            actor, SESSION, key, new ValidateTestReleaseRequestBody(expectedVersion));
    }

    public HttpResponse<String> getCandidate(Candidate candidate) throws Exception {
        return http.get("/decision-releases/v1/candidates/" + candidate.created().getReleaseId(), ACTOR, SESSION);
    }

    public HttpResponse<String> listDraftCandidates() throws Exception {
        return http.get("/decision-releases/v1/candidates?page=1&size=10"
            + "&pointCode=CHECKOUT_PREPARE&status=DRAFT", ACTOR, SESSION);
    }

    public <T> T body(HttpResponse<String> response, Class<T> type) {
        return http.body(response, type);
    }

    public JsonNode json(HttpResponse<String> response) {
        return http.json(response);
    }

    /**
     * 种入与 Validate 开始事务提交后完全一致的恢复起点：根进入 VALIDATING，只有开始审计、尚无检查结果
     *
     * @param candidate 已创建的 DRAFT Candidate
     * @param stateActor 开始审计记录的操作者
     */
    public void seedValidationStarted(Candidate candidate, long stateActor) {
        long releaseId = Long.parseLong(candidate.created().getReleaseId());
        Instant now = nextEventTime(releaseId);
        transaction.executeWithoutResult(ignored -> {
            assertEquals(1, jdbc.update("UPDATE t_decision_release SET status='VALIDATING',version=2,update_time=? "
                + "WHERE release_id=? AND status='DRAFT' AND version=1", Timestamp.from(now), releaseId));
            assertEquals(1, jdbc.update("INSERT INTO t_release_audit_event(audit_event_id,release_id,event_type,"
                    + "event_time,check_code,check_status,severity,error_code,error_summary,shadow_run_id,"
                    + "approval_request_id,approval_status,reviewer_id,review_comment,from_status,to_status,"
                    + "reason_code,trigger_type,trigger_id,actor_id,aggregate_version,create_time) "
                    + "VALUES(?,?, 'STATE_CHANGE', ?,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,"
                    + "'DRAFT','VALIDATING','STATIC_VALIDATION_STARTED','OPERATIONS_COMMAND',?,?,2,?)",
                identifiers.nextIdentifier(), releaseId, Timestamp.from(now),
                ReleaseAuditEvent.triggerId(releaseId, "VALIDATE"), stateActor, Timestamp.from(now)));
        });
    }

    public ReleaseFacts releaseFacts(Candidate candidate) {
        long releaseId = Long.parseLong(candidate.created().getReleaseId());
        return jdbc.queryForObject("SELECT status,version,content_hash FROM t_decision_release WHERE release_id=?",
            (result, ignored) -> new ReleaseFacts(result.getString("status"), result.getInt("version"),
                result.getString("content_hash"), countAudits(releaseId, "STATE_CHANGE"),
                countAudits(releaseId, "STATIC_CHECK")), releaseId);
    }

    public List<String> checkCodes(Candidate candidate) {
        return jdbc.queryForList("SELECT check_code FROM t_release_audit_event WHERE release_id=? "
                + "AND event_type='STATIC_CHECK' ORDER BY audit_event_id",
            String.class, Long.parseLong(candidate.created().getReleaseId()));
    }

    public long releaseCount() {
        return decisionMapper.releaseCount(ruleSetCode);
    }

    public String nextKey() {
        String key = decisionMapper.uniqueNamespace();
        own(key);
        return key;
    }

    private long countAudits(long releaseId, String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_release_audit_event WHERE release_id=? AND event_type=?",
            Long.class, releaseId, eventType);
    }

    private Instant nextEventTime(long releaseId) {
        Instant previous = jdbc.queryForObject("SELECT update_time FROM t_decision_release WHERE release_id=?",
            (result, ignored) -> result.getTimestamp(1).toInstant(), releaseId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return now.isAfter(previous) ? now : previous.plus(1, ChronoUnit.MICROS);
    }

    private void own(String key) {
        if (!ownedKeys.contains(key)) {
            ownedKeys.add(key);
        }
    }

    @Override
    public void close() {
        http.close();
        decisionMapper.deleteOwnedDecisionConfiguration(ruleSetCode, ownedKeys);
        assertEquals(0, decisionMapper.snapshotCounts(ruleSetCode).total());
        assertEquals(0, releaseCount());
    }

    public record Candidate(
        CreateTestReleaseRequestBody request,
        String createKey,
        String firstBody,
        CreateTestReleaseResponseBody created
    ) {
    }

    public record ReleaseFacts(String status, int version, String contentHash, long stateChanges, long staticChecks) {
    }

}

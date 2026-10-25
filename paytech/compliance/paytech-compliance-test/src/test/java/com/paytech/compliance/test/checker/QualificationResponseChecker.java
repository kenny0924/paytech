package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.compliance.api.enums.QualificationErrorCode;
import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.test.mapper.QualificationTestMapper.DecisionRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.QualificationRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.StateChangeRow;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 角色资格判定与运营登记接口的 HTTP 响应 Checker
 * <li>协议、Request ID 与 Problem 断言复用 Foundation 基类；这里核对判定结果、资格、分页与历史的全部字段。</li>
 *
 * @author Kenny
 */
public final class QualificationResponseChecker extends AbstractHttpResponseChecker<QualificationResponseChecker> {
    /** UTC 六位微秒时间格式。 */
    static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);

    static final Set<String> QUALIFICATION_FIELDS = Set.of("qualificationId", "roleType", "roleId", "qualificationType",
        "status", "effectiveStatus", "validFrom", "validTo", "currentDecisionId", "decisionSource", "reviewStatus", "version",
        "updatedAt");

    public QualificationResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected QualificationResponseChecker self() {
        return this;
    }

    /** 一个主体的期望判定结论；未登记时资格字段为空。 */
    public record ExpectedResult(String roleType, String roleId, QualificationEvaluationStatus status, String qualificationType,
        Long qualificationId, Instant validFrom, Instant validTo) {
        /** 未登记主体。 */
        public static ExpectedResult notRegistered(String roleType, String roleId) {
            return new ExpectedResult(roleType, roleId, QualificationEvaluationStatus.NOT_REGISTERED, null, null, null, null);
        }

        /** 据给定资格行判定。 */
        public static ExpectedResult of(QualificationRow row, QualificationEvaluationStatus status) {
            return new ExpectedResult(row.roleType(), row.roleId(), status, row.qualificationType(), row.qualificationId(),
                row.validFrom(), row.validTo());
        }
    }

    /** 判定成功：判定时间在调用区间内，结果数量与顺序和期望一致。 */
    public QualificationResponseChecker assertEvaluated(Instant startedAt, List<ExpectedResult> expected) {
        assertSuccess();
        JsonNode body = body();
        assertEquals(Set.of("evaluatedAt", "results"), Set.copyOf(body.propertyNames()));
        Instant evaluatedAt = Instant.parse(body.path("evaluatedAt").asString());
        assertTrue(!evaluatedAt.isBefore(startedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
            && !evaluatedAt.isAfter(Instant.now()), "判定时间：" + evaluatedAt);
        JsonNode results = body.path("results");
        assertEquals(expected.size(), results.size(), "结论数量");
        for (int index = 0; index < expected.size(); index++) {
            ExpectedResult item = expected.get(index);
            JsonNode actual = results.get(index);
            assertAll("结论 " + index, () -> assertEquals(item.roleType(), actual.path("roleType").asString()),
                () -> assertEquals(item.roleId(), actual.path("roleId").asString()),
                () -> assertEquals(item.status() == QualificationEvaluationStatus.ELIGIBLE, actual.path("eligible").asBoolean()),
                () -> assertTrue(actual.path("eligible").isBoolean(), "eligible 必须为布尔"),
                () -> assertEquals(item.status().getCode(), actual.path("status").asString()),
                () -> assertEquals(item.qualificationType(), text(actual, "qualificationType")),
                () -> assertEquals(item.qualificationId() == null ? null : Long.toString(item.qualificationId()),
                    text(actual, "qualificationId")),
                () -> assertEquals(time(item.validFrom()), text(actual, "validFrom")),
                () -> assertEquals(time(item.validTo()), text(actual, "validTo")));
        }
        return this;
    }

    /** 单条资格结果与库中行一致。 */
    public QualificationResponseChecker assertQualification(QualificationRow row, QualificationEvaluationStatus effectiveStatus,
        String decisionSource) {
        assertSuccess();
        assertItem(body(), row, effectiveStatus, decisionSource);
        return this;
    }

    /** 分页外壳与条目资格 ID 顺序。 */
    public QualificationResponseChecker assertPage(int page, int size, long total, List<Long> qualificationIds) {
        assertSuccess();
        JsonNode body = body();
        List<Long> actual = new ArrayList<>();
        body.path("items").forEach(item -> actual.add(Long.parseLong(item.path("qualificationId").asString())));
        assertAll("分页", () -> assertEquals(page, body.path("page").asInt()), () -> assertEquals(size, body.path("size").asInt()),
            () -> assertEquals(total, body.path("total").asLong()), () -> assertEquals(qualificationIds, actual, "条目顺序"));
        return this;
    }

    /** 分页中某条目与库中行一致。 */
    public QualificationResponseChecker assertPageItem(int index, QualificationRow row,
        QualificationEvaluationStatus effectiveStatus, String decisionSource) {
        assertItem(body().path("items").get(index), row, effectiveStatus, decisionSource);
        return this;
    }

    /** 一次状态变更与其决定的期望。 */
    public record ExpectedChange(StateChangeRow change, DecisionRow decision) {
    }

    /** 历史：资格 ID 与按版本倒序的变更。 */
    public QualificationResponseChecker assertHistory(long qualificationId, List<ExpectedChange> expected) {
        assertSuccess();
        JsonNode body = body();
        assertEquals(Long.toString(qualificationId), body.path("qualificationId").asString());
        JsonNode items = body.path("items");
        assertEquals(expected.size(), items.size(), "历史条数");
        for (int index = 0; index < expected.size(); index++) {
            StateChangeRow change = expected.get(index).change();
            DecisionRow decision = expected.get(index).decision();
            JsonNode actual = items.get(index);
            assertAll("历史 " + index, () -> assertEquals(Long.toString(change.changeId()), actual.path("changeId").asString()),
                () -> assertEquals(Long.toString(change.aggregateVersion()), actual.path("aggregateVersion").asString()),
                () -> assertEquals(change.fromStatus(), text(actual, "fromStatus")),
                () -> assertEquals(change.toStatus(), actual.path("toStatus").asString()),
                () -> assertEquals(change.triggerType(), actual.path("triggerType").asString()),
                () -> assertEquals(change.reasonCode(), text(actual, "reasonCode")),
                () -> assertEquals(change.actorId() == null ? null : Long.toString(change.actorId()), text(actual, "actorId")),
                () -> assertEquals(time(change.occurredTime()), actual.path("occurredAt").asString()),
                () -> assertEquals(Long.toString(decision.decisionId()), actual.path("decisionId").asString()),
                () -> assertEquals(decision.decisionSource(), actual.path("decisionSource").asString()),
                () -> assertEquals(decision.outcome(), actual.path("outcome").asString()),
                () -> assertEquals(decision.decidedBy() == null ? null : Long.toString(decision.decidedBy()),
                    text(actual, "decidedBy")),
                () -> assertEquals(decision.basisNote(), text(actual, "basisNote")),
                () -> assertEquals(time(decision.validFrom()), text(actual, "validFrom")),
                () -> assertEquals(time(decision.validTo()), text(actual, "validTo")));
        }
        return this;
    }

    /** 资格业务失败。 */
    public QualificationResponseChecker assertQualificationError(int status, QualificationErrorCode code) {
        return assertProblem(status, code.getCode());
    }

    /** 响应中的资格 ID。 */
    public long qualificationId() {
        return Long.parseLong(body().path("qualificationId").asString());
    }

    void assertSuccess() {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    static void assertItem(JsonNode actual, QualificationRow row, QualificationEvaluationStatus effectiveStatus,
        String decisionSource) {
        assertAll("资格 " + row.qualificationId(), () -> assertEquals(QUALIFICATION_FIELDS, Set.copyOf(actual.propertyNames())),
            () -> assertEquals(Long.toString(row.qualificationId()), actual.path("qualificationId").asString()),
            () -> assertEquals(row.roleType(), actual.path("roleType").asString()),
            () -> assertEquals(row.roleId(), actual.path("roleId").asString()),
            () -> assertEquals(row.qualificationType(), actual.path("qualificationType").asString()),
            () -> assertEquals(row.status(), actual.path("status").asString()),
            () -> assertEquals(effectiveStatus.getCode(), actual.path("effectiveStatus").asString()),
            () -> assertEquals(time(row.validFrom()), text(actual, "validFrom")),
            () -> assertEquals(time(row.validTo()), text(actual, "validTo")),
            () -> assertEquals(Long.toString(row.currentDecisionId()), actual.path("currentDecisionId").asString()),
            () -> assertEquals(decisionSource, actual.path("decisionSource").asString()),
            () -> assertEquals(row.reviewStatus(), actual.path("reviewStatus").asString()),
            () -> assertEquals(Long.toString(row.version()), actual.path("version").asString()),
            () -> assertEquals(time(row.updateTime()), actual.path("updatedAt").asString()));
    }

    /** 可空文本字段；JSON null 或缺失返回 null。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    static String time(Instant value) {
        return value == null ? null : MICROSECOND_UTC.format(value);
    }
}

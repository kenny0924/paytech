package com.paytech.operations.console.bff.service.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Operations 自有 TEST Candidate 合同；状态组合由适配器验证后再创建响应。 */
public final class RiskTestCandidateModels {
    private RiskTestCandidateModels() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CandidateQueryRequestBody(
        @Pattern(regexp = RiskRuleSetModels.CODE) String pointCode,
        @Pattern(regexp = "^(DRAFT|VALIDATING|REJECTED)$") String status) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CreateTestCandidateRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull
        @Pattern(regexp = RiskRuleSetModels.POSITIVE_DECIMAL_ID) String ruleSetVersionId,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull
        @Pattern(regexp = RiskRuleSetModels.CONTENT_HASH) String expectedRuleSetContentHash) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ValidateTestCandidateRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer expectedVersion) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestCandidateListItemResponseBody(
        String releaseId, int releaseNo, String decisionPointId, String pointCode, String pointVersionId,
        String ruleSetVersionId, String environmentCode, String status, int version, String contentHash,
        String createdBy, String createTime, Boolean allStaticChecksPassed) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestCandidateDetailResponseBody(
        String releaseId, int releaseNo, String decisionPointId, String pointCode, String pointVersionId,
        String ruleSetVersionId, String environmentCode, String status, int version, String contentHash,
        String createdBy, String createTime, Boolean allStaticChecksPassed, boolean waitingForShadow,
        FrozenCompositionResponseBody frozenComposition, List<ReleaseStateChangeResponseBody> stateChanges,
        List<ReleaseStaticCheckResponseBody> staticChecks) {

        public TestCandidateDetailResponseBody {
            stateChanges = List.copyOf(stateChanges);
            staticChecks = List.copyOf(staticChecks);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FrozenCompositionResponseBody(
        String decisionPointId, String pointCode, String pointVersionId, int pointVersionNo,
        String pointVersionContentHash, String ruleSetVersionId, String ruleSetCode, int ruleSetVersionNo,
        String ruleSetContentHash) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReleaseStateChangeResponseBody(
        String auditEventId, String eventTime, String fromStatus, String toStatus, String reasonCode,
        String triggerType, String triggerId, String actorId, int aggregateVersion) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReleaseStaticCheckResponseBody(
        String auditEventId, String eventTime, String checkCode, String checkStatus, String severity,
        String errorCode, String errorSummary) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record InitialStateChangeResponseBody(
        String auditEventId, String eventTime, String fromStatus, String toStatus, String reasonCode,
        String triggerType, String actorId, int aggregateVersion) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ValidationCheckResponseBody(
        String checkCode, String checkStatus, String severity, String errorCode, String errorSummary) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CreateTestCandidateResponseBody(
        String releaseId, int releaseNo, String decisionPointId, String pointCode, String pointVersionId,
        String ruleSetVersionId, String environmentCode, String status, int version, String contentHash,
        String createdBy, String createTime, InitialStateChangeResponseBody initialStateChange) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ValidateTestCandidateResponseBody(
        String releaseId, int releaseNo, String environmentCode, String status, int version,
        String contentHash, boolean allStaticChecksPassed, List<ValidationCheckResponseBody> checks) {

        public ValidateTestCandidateResponseBody {
            checks = List.copyOf(checks);
        }
    }
}

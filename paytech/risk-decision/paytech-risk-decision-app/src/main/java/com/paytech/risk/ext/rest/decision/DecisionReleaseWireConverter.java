package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.ext.rest.decision.DecisionWireValues.*;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels.FrozenCompositionResponseBody;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels.InitialStateChangeResponseBody;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels.ReleaseStateChangeResponseBody;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels.ReleaseStaticCheckResponseBody;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels.ValidationCheckResponseBody;
import com.paytech.risk.decision.api.response.CreateTestReleaseResponseBody;
import com.paytech.risk.decision.api.response.TestReleaseDetailResponseBody;
import com.paytech.risk.decision.api.response.TestReleaseListItemResponseBody;
import com.paytech.risk.decision.api.response.ValidateTestReleaseResponseBody;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.StaticCheckEvidence;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.CreatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ReleasePageView;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ReleaseSummaryView;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ValidatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;

/** Test Release 领域结果与 HTTP 模型之间的协议转换，不查询数据库或执行业务规则。 */
final class DecisionReleaseWireConverter {
    /** 仅提供固定边界转换。 */
    private DecisionReleaseWireConverter() {
    }

    /** Candidate 分页结果转换为公共分页响应。 */
    static PagingResponseBody<TestReleaseListItemResponseBody> toResponse(ReleasePageView page) {
        ReleasePageView value = require(page, "releasePage");
        return new PagingResponseBody<>(value.items().stream().map(DecisionReleaseWireConverter::toResponseRelease).toList(),
            value.page(), value.size(), value.total());
    }

    /** 单个 Candidate 列表项投影。 */
    private static TestReleaseListItemResponseBody toResponseRelease(ReleaseSummaryView value) {
        return new TestReleaseListItemResponseBody(positiveId(value.releaseId()), positiveInt(value.releaseNo()),
            positiveId(value.decisionPointId()), value.pointCode(), positiveId(value.pointVersionId()),
            positiveId(value.ruleSetVersionId()), code(value.environment()), code(value.status()), value.version(),
            value.contentHash(), positiveId(value.createdBy()), time(value.createTime()), value.allStaticChecksPassed());
    }

    /** Candidate 详情，含冻结组合、状态变更与静态检查证据。 */
    static TestReleaseDetailResponseBody toResponse(DecisionReleaseAggregate aggregate) {
        DecisionReleaseAggregate value = require(aggregate, "releaseAggregate");
        var root = value.release();
        var frozen = value.frozenComposition();
        return new TestReleaseDetailResponseBody(positiveId(root.releaseId()), positiveInt(root.releaseNo()),
            positiveId(root.decisionPointId()), root.pointCode(), positiveId(root.pointVersionId()),
            positiveId(root.ruleSetVersionId()), code(root.environment()), code(root.status()), root.version(),
            root.contentHash(), positiveId(root.createdBy()), time(root.createTime()), value.allStaticChecksPassed(),
            value.waitingForShadow(), new FrozenCompositionResponseBody(positiveId(frozen.decisionPointId()),
                frozen.pointCode(), positiveId(frozen.pointVersionId()), frozen.pointVersionNo(),
                frozen.pointVersionContentHash(), positiveId(frozen.ruleSetVersionId()), frozen.ruleSetCode(),
                frozen.ruleSetVersionNo(), frozen.ruleSetContentHash()),
            value.stateChanges().stream().map(DecisionReleaseWireConverter::toResponse).toList(),
            value.staticChecks().stream().map(DecisionReleaseWireConverter::toResponse).toList());
    }

    /** 单条状态变更审计投影。 */
    private static ReleaseStateChangeResponseBody toResponse(ReleaseStateChange value) {
        return new ReleaseStateChangeResponseBody(positiveId(value.auditEventId()), time(value.eventTime()),
            nullableCode(value.fromStatus()), code(value.toStatus()), value.reasonCode(), value.triggerType(),
            value.triggerId(), nullableId(value.actorId()), value.aggregateVersion());
    }

    /** 单条静态检查证据投影。 */
    private static ReleaseStaticCheckResponseBody toResponse(StaticCheckEvidence value) {
        return new ReleaseStaticCheckResponseBody(positiveId(value.auditEventId()), time(value.eventTime()),
            code(value.checkCode()), code(value.status()), code(value.severity()), value.errorCode(),
            value.errorSummary());
    }

    /** 创建 Candidate 结果及初始状态变更投影。 */
    static CreateTestReleaseResponseBody toResponse(CreatedCandidate value) {
        CreatedCandidate created = require(value, "createdCandidate");
        ReleaseStateChange initial = created.initialStateChange();
        return new CreateTestReleaseResponseBody(positiveId(created.releaseId()), positiveInt(created.releaseNo()),
            positiveId(created.decisionPointId()), created.pointCode(), positiveId(created.pointVersionId()),
            positiveId(created.ruleSetVersionId()), code(created.environment()), code(created.status()),
            created.version(), created.contentHash(), positiveId(created.createdBy()), time(created.createTime()),
            new InitialStateChangeResponseBody(positiveId(initial.auditEventId()), time(initial.eventTime()),
                nullableCode(initial.fromStatus()), code(initial.toStatus()), initial.reasonCode(),
                initial.triggerType(), nullableId(initial.actorId()), initial.aggregateVersion()));
    }

    /** 静态校验结果投影。 */
    static ValidateTestReleaseResponseBody toResponse(ValidatedCandidate value) {
        ValidatedCandidate validated = require(value, "validatedCandidate");
        return new ValidateTestReleaseResponseBody(positiveId(validated.releaseId()),
            positiveInt(validated.releaseNo()), code(validated.environment()), code(validated.status()),
            validated.version(), validated.contentHash(), validated.allStaticChecksPassed(),
            validated.checks().stream().map(DecisionReleaseWireConverter::toResponse).toList());
    }

    /** 单项静态检查结果投影。 */
    private static ValidationCheckResponseBody toResponse(StaticCheckResult value) {
        return new ValidationCheckResponseBody(code(value.checkCode()), code(value.checkStatus()),
            code(value.severity()), value.errorCode(), value.errorSummary());
    }

    /** 解析列表状态筛选，空值表示不筛选，未知 Code 拒绝。 */
    static DecisionReleaseStatus parseReleaseStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "DRAFT" -> DecisionReleaseStatus.DRAFT;
            case "VALIDATING" -> DecisionReleaseStatus.VALIDATING;
            case "REJECTED" -> DecisionReleaseStatus.REJECTED;
            default -> throw invalid("status");
        };
    }
}

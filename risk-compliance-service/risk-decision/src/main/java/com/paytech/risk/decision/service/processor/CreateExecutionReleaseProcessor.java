package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.release.ExecutionReleaseContent;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService;
import com.paytech.risk.decision.service.processor.request.CreateExecutionReleaseRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseViewReader;
import com.paytech.risk.decision.service.query.ExecutionSnapshotReader;

import lombok.AllArgsConstructor;

/**
 * 创建冻结决策流的执行发布（TEST，DRAFT）
 * <li>决策点必须支持运行期；决策流须属于该点且未删除；点版本须为冻结的工程合同。</li>
 * <li>每次调用新建一个发布；内容摘要覆盖点版本、流程图、规则集版本与 Feature Version。</li>
 */
@Component
@AllArgsConstructor
public class CreateExecutionReleaseProcessor
    extends AbstractLockProcessor<CreateExecutionReleaseRequest, ExecutionReleaseView> {
    /** 执行发布使用的决策点版本号。 */
    static final int POINT_VERSION_NO = 1;

    private final Clock clock;
    private final DecisionRuleSetRepository points;
    private final ExecutionSnapshotReader snapshots;
    private final ExecutionReleaseService service;
    private final ExecutionReleaseViewReader views;
    private final DecisionFlowRootRepository flowRoots;
    private final RiskDecisionCanonicalizer canonicalizer;
    private final DecisionIdentifierGenerator identifiers;
    private final DecisionFlowVersionRepository flowVersions;
    private final DecisionPointVersionRepository pointVersions;

    /** 同一决策点的执行发布创建与激活串行。 */
    @Override
    protected LockCommand lockCommand(CreateExecutionReleaseRequest request) {
        return LockCommand.tryLocks(List.of(ExecutionReleaseLocks.point(request.getPointCode())),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected ExecutionReleaseView processWithinLock(CreateExecutionReleaseRequest request) {
        // 业务验证
        DecisionPointAuthoringDescriptor descriptor = DecisionPointAuthoringDescriptor.find(request.getPointCode())
            .filter(DecisionPointAuthoringDescriptor::runtimeSupported)
            .orElseThrow(() -> new BizCheckFailException("决策点不支持执行发布: " + request.getPointCode()));
        DecisionPoint point = points.findPointByCode(request.getPointCode())
            .orElseThrow(() -> new ErrorException("决策点未初始化: " + request.getPointCode()));
        DecisionPointVersion pointVersion = requirePointVersion(descriptor, point);
        requireFlow(point, request.getFlowVersionId());

        // 构造发布并保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ExecutionRelease release = buildRelease(point, pointVersion, request, now);
        release.setContentHash(ExecutionReleaseContent.hash(pointVersion.contentHash(), snapshots.read(release)));
        service.create(release, request.getActorAccountId());
        return views.read(release);
    }

    /** 点版本必须是冻结的工程合同，摘要与描述一致。 */
    DecisionPointVersion requirePointVersion(DecisionPointAuthoringDescriptor descriptor, DecisionPoint point) {
        DecisionPointVersion version = pointVersions.findByPointAndVersion(point.decisionPointId(), POINT_VERSION_NO)
            .orElseThrow(() -> new ErrorException("决策点版本未初始化: " + point.pointCode()));
        String expected = canonicalizer.decisionPointVersion(ExecutionReleaseContent.pointVersion(descriptor,
            POINT_VERSION_NO)).sha256LowerHex();
        if (version.status() != DecisionPointVersionStatus.FROZEN || !expected.equals(version.contentHash())) {
            throw new ErrorException("决策点版本与工程合同不一致: " + point.pointCode());
        }
        return version;
    }

    /** 决策流版本须属于该决策点且流程未删除。 */
    void requireFlow(DecisionPoint point, long flowVersionId) {
        DecisionFlowVersionRow version = flowVersions.findById(flowVersionId)
            .orElseThrow(() -> new BizCheckFailException("决策流版本不存在: " + flowVersionId));
        DecisionFlowRow root = flowRoots.findById(version.flowId())
            .orElseThrow(() -> new ErrorException("决策流版本缺少流程根: " + flowVersionId));
        if (root.decisionPointId() != point.decisionPointId()) {
            throw new BizCheckFailException("决策流不属于该决策点: " + flowVersionId);
        }
        if (root.status() != FlowStatus.CONFIGURABLE) {
            throw new BizCheckFailException("决策流已删除: " + flowVersionId);
        }
    }

    /** DRAFT/version=1 的 TEST 执行发布；发布编号由保存事务分配。 */
    ExecutionRelease buildRelease(DecisionPoint point, DecisionPointVersion pointVersion,
        CreateExecutionReleaseRequest request, Instant now) {
        ExecutionRelease release = new ExecutionRelease();
        release.setReleaseId(identifiers.nextIdentifier());
        release.setDecisionPointId(point.decisionPointId());
        release.setPointVersionId(pointVersion.pointVersionId());
        release.setDecisionFlowVersionId(request.getFlowVersionId());
        release.setEnvironmentCode(DecisionEnvironment.TEST);
        release.setStatus(DecisionReleaseStatus.DRAFT);
        release.setCreatedBy(request.getActorAccountId());
        release.setVersion(1);
        release.setCreateTime(now);
        release.setUpdateTime(now);
        return release;
    }
}

package com.paytech.risk.decision.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.ExecutionReleaseContent;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.FeatureVersionRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.ExecutionReleaseService;
import com.paytech.risk.decision.service.processor.request.ExecutionReleaseCommandRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseViewReader;
import com.paytech.risk.decision.service.query.ExecutionSnapshotReader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行发布的一轮静态校验
 * <li>只接受 DRAFT；通过进入 SHADOWING，失败进入 REJECTED。已校验的发布直接返回原结果。</li>
 * <li>发布内容无法读取或内容摘要变化，记为引用完整性失败。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ValidateExecutionReleaseProcessor
    extends AbstractLockProcessor<ExecutionReleaseCommandRequest, ExecutionReleaseView> {
    private final ExecutionSnapshotReader snapshots;
    private final ExecutionReleaseService service;
    private final ExecutionReleaseViewReader views;
    private final ExecutionReleaseRepository releases;
    private final FeatureVersionRepository featureVersions;
    private final DecisionPointVersionRepository pointVersions;

    /** 同一发布的校验与影子运行启动串行。 */
    @Override
    protected LockCommand lockCommand(ExecutionReleaseCommandRequest request) {
        return LockCommand.tryLocks(List.of(ExecutionReleaseLocks.release(request.getReleaseId())),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected ExecutionReleaseView processWithinLock(ExecutionReleaseCommandRequest request) {
        ExecutionRelease release = releases.findById(request.getReleaseId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        if (release.getStatus() != DecisionReleaseStatus.DRAFT) {
            return views.read(release);
        }
        service.validate(release, checks(release), request.getActorAccountId());
        return views.read(release);
    }

    /** 读取发布内容并执行八项检查；读取失败只给出安全摘要。 */
    List<StaticCheckResult> checks(ExecutionRelease release) {
        ExecutionSnapshot snapshot;
        try {
            snapshot = snapshots.read(release);
        } catch (RuntimeException unreadable) {
            log.warn("执行发布内容无法读取, releaseId={}", release.getReleaseId(), unreadable);
            return ExecutionReleaseContent.check(null, "发布引用的流程、规则集或变量无法读取", Map.of());
        }
        List<StaticCheckResult> checks = new ArrayList<>(ExecutionReleaseContent.check(snapshot, null, statuses(snapshot)));
        String pointHash = pointVersions.findById(release.getPointVersionId()).map(DecisionPointVersion::contentHash)
            .orElse(null);
        if (pointHash == null || !ExecutionReleaseContent.hash(pointHash, snapshot).equals(release.getContentHash())) {
            checks.set(0, new StaticCheckResult(StaticCheckCode.REFERENCE_INTEGRITY,
                com.paytech.risk.decision.domain.enums.StaticCheckStatus.FAILED,
                com.paytech.risk.decision.domain.enums.StaticCheckSeverity.ERROR, "CONTENT_HASH_MISMATCH",
                "发布内容与创建时不一致"));
        }
        return checks;
    }

    /** 引用的 Feature Version 当前状态。 */
    Map<Long, ExecutionVersionStatus> statuses(ExecutionSnapshot snapshot) {
        List<Long> ids = snapshot.variables().values().stream().map(RiskVariable.Entry::definition)
            .filter(RiskVariable.Definition::featureBacked).map(RiskVariable.Definition::featureVersionId).distinct()
            .toList();
        return featureVersions.findByIds(ids).stream()
            .collect(Collectors.toMap(FeatureVersion::featureVersionId, FeatureVersion::status));
    }
}

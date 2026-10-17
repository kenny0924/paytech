package com.paytech.risk.decision.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;

/** 执行发布生命周期的本地保存；状态变化与审计在同一事务中写入。 */
public interface ExecutionReleaseService {
    /**
     * 分配全局发布编号并保存 DRAFT 发布及创建审计
     *
     * @param release 已构造的发布，发布编号在事务内分配
     * @param actorId 创建人
     */
    void create(ExecutionRelease release, long actorId);

    /**
     * 保存一轮静态校验：DRAFT→VALIDATING，写入八项检查，再进入 SHADOWING 或 REJECTED
     *
     * @param release 已锁定的 DRAFT 发布
     * @param checks 八项检查结果
     * @param actorId 操作人
     */
    void validate(ExecutionRelease release, List<StaticCheckResult> checks, long actorId);

    /** 保存影子批次，并在同一事务登记回放任务。 */
    void startShadowRun(ReleaseShadowRun run, CompensationEventRequest event);

    /**
     * 激活候选：替代原生效发布，候选依次经过审核、批准进入生效
     *
     * @param context 候选、原生效发布、影子批次与操作人
     */
    void activate(ActivationContext context);

    /**
     * 激活所需的已校验事实
     *
     * @param candidate 处于 SHADOWING 的候选
     * @param baseline 原生效发布；首个发布为空
     * @param shadowRunId 通过的影子批次；首个发布为空
     * @param actorId 操作人，同时记录为审批人
     * @param time 本次时间
     */
    record ActivationContext(ExecutionRelease candidate, ExecutionRelease baseline, Long shadowRunId, long actorId,
        Instant time) {
        /** 候选激活所经过的状态，依次推进并逐步审计。 */
        public static final List<DecisionReleaseStatus> PATH = List.of(DecisionReleaseStatus.REVIEW_PENDING,
            DecisionReleaseStatus.APPROVED, DecisionReleaseStatus.ACTIVE);
    }
}

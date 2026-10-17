package com.paytech.risk.test.builder.decision;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

/**
 * 发布记录的测试 Builder
 * <li>ACTIVE 占用环境生效槽并带激活时间；SUPERSEDED 同时带激活与替代时间；其他状态两者为空。</li>
 *
 * @author Kenny
 */
public final class ReleaseBuilder {
    static final Instant ACTIVATED_TIME = Instant.parse("2026-02-03T04:05:06.123456Z");

    static final Instant SUPERSEDED_TIME = Instant.parse("2026-03-04T05:06:07.654321Z");

    private final DecisionEnvironment environment;

    private final DecisionReleaseStatus status;

    private ReleaseBuilder(DecisionEnvironment environment, DecisionReleaseStatus status) {
        this.environment = environment;
        this.status = status;
    }

    /** 指定环境与生命周期状态的发布。 */
    public static ReleaseBuilder of(DecisionEnvironment environment, DecisionReleaseStatus status) {
        return new ReleaseBuilder(environment, status);
    }

    public DecisionEnvironment environment() {
        return environment;
    }

    public DecisionReleaseStatus status() {
        return status;
    }

    public Integer activeSlot() {
        return status == DecisionReleaseStatus.ACTIVE ? 1 : null;
    }

    public Instant activatedTime() {
        return status == DecisionReleaseStatus.ACTIVE || status == DecisionReleaseStatus.SUPERSEDED ? ACTIVATED_TIME
            : null;
    }

    public Instant supersededTime() {
        return status == DecisionReleaseStatus.SUPERSEDED ? SUPERSEDED_TIME : null;
    }
}

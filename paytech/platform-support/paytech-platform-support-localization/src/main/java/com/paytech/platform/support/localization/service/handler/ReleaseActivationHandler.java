package com.paytech.platform.support.localization.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.enums.BundleFileState;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domain.ReleaseCheckTarget;
import com.paytech.platform.support.localization.domain.release.ReleaseCheckDecision;
import com.paytech.platform.support.localization.domainservice.service.ReleaseService;
import com.paytech.platform.support.localization.integration.ReleaseBundleFileChecker;

/**
 * 发布上线（技术方案 3.3 第 3 步）：事务外逐个读取语言包文件的检查状态，再在短事务里写结论。
 * <li>仍有检查未完成返回可重试；任一被拒绝把发布标为 FAILED 并记原因；全部可用标为 READY，并在应用当前发布号更小时切换指针。</li>
 * <li>已到最后一次允许的尝试（{@value #MAXIMUM_ATTEMPTS} 次）仍在检查，或最后一次尝试出错时，把发布标为 FAILED，原因“文件检查超时”，
 * 不让发布静默停在 PENDING_CHECK。</li>
 * <li>发布已不是 PENDING_CHECK 时直接成功；条件更新冲突（并发执行者已写结论）按可重试处理，下次读到终态即结束。</li>
 */
@Component
public class ReleaseActivationHandler implements CompensationEventHandler {

    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "I18N_RELEASE_ACTIVATE";

    /** 没有业务子类型时的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    /** 最大执行次数；最后一次仍在检查时判为超时。 */
    static final int MAXIMUM_ATTEMPTS = 60;

    /** 失败原因列出的拒绝文件数上限。 */
    static final int LISTED_REJECTIONS = 5;

    private final Clock clock;
    private final ReleaseService releases;
    private final TransactionTemplate write;
    private final ReleaseBundleFileChecker checker;

    /**
     * 装配依赖并固定结论写入事务：读已提交、10 秒超时。
     *
     * @param clock 时钟
     * @param releases 发布服务
     * @param manager 本库事务管理器
     * @param checker 语言包文件检查状态
     */
    public ReleaseActivationHandler(Clock clock, ReleaseService releases,
        @Qualifier("transactionManager") PlatformTransactionManager manager, ReleaseBundleFileChecker checker) {
        this.clock = clock;
        this.releases = releases;
        this.checker = checker;
        this.write = new TransactionTemplate(manager);
        this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.write.setTimeout(10);
    }

    /**
     * 纯工厂：与发布登记同事务登记，事件键为发布 ID。
     *
     * @param releaseId 发布 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long releaseId) {
        if (releaseId <= 0) {
            throw new IllegalArgumentException("发布 ID 必须为正数");
        }
        return new CompensationEventRequest(Long.toString(releaseId), EVENT_TYPE, NO_SUB_EVENT_TYPE);
    }

    /** 持久化任务类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒，最多 60 次，退避 2 秒起、上限 1 分钟；文件检查通常几秒完成。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), MAXIMUM_ATTEMPTS, Duration.ofSeconds(2),
            Duration.ofMinutes(1), 2, 0.2);
    }

    /** 事务外读取检查状态，短事务写结论。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.retryableFailure("I18N_RELEASE_ACTIVATE_IN_TRANSACTION",
                "发布上线不能处于已有事务中");
        }
        Long releaseId = releaseId(event.eventKey());
        if (releaseId == null) {
            return CompensationHandlerResult.terminalFailure("I18N_RELEASE_ACTIVATE_INVALID", "发布上线任务身份不合法");
        }
        try {
            return activate(releaseId, event.executeCount());
        } catch (RuntimeException unavailable) {
            if (ReleaseCheckDecision.isLastAttempt(event.executeCount(), MAXIMUM_ATTEMPTS)) {
                return timeOutAfterFailure(releaseId);
            }
            return CompensationHandlerResult.retryableFailure("I18N_RELEASE_ACTIVATE_RETRY", "发布上线需要重试");
        }
    }

    /**
     * 按文件检查结论处理一个发布。
     *
     * @param releaseId 发布 ID
     * @param executeCount 本次执行次数
     * @return 任务结果
     */
    CompensationHandlerResult activate(long releaseId, int executeCount) {
        ReleaseCheckTarget target = releases.loadCheckTarget(releaseId);
        if (target == null) {
            return CompensationHandlerResult.terminalFailure("I18N_RELEASE_NOT_FOUND", "发布不存在");
        }
        I18nRelease release = target.getRelease();
        if (release.getStatus() != ReleaseStatus.PENDING_CHECK) {
            return CompensationHandlerResult.success();
        }
        List<BundleFileState> states = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (I18nReleaseBundle bundle : target.getBundles()) {
            BundleFileState state = checker.state(FileAddress.parse(bundle.getFileAddress()));
            states.add(state);
            if (state == BundleFileState.REJECTED) {
                rejected.add(bundle.getLocaleCode() + "/" + bundle.getNamespaceCode());
            }
        }
        return switch (ReleaseCheckDecision.decide(states, executeCount, MAXIMUM_ATTEMPTS)) {
            case REJECTED -> fail(release, failureReason(rejected));
            case TIMED_OUT -> fail(release, ReleaseCheckDecision.TIMEOUT_REASON);
            case RETRY -> CompensationHandlerResult.retryableFailure("I18N_RELEASE_CHECK_PENDING", "语言包仍在检查");
            case READY -> {
                write.executeWithoutResult(status -> releases.completeCheck(release, now()));
                yield CompensationHandlerResult.success();
            }
        };
    }

    /** 事件键解析为发布 ID；不是正整数或超出范围时为 null。 */
    static Long releaseId(String eventKey) {
        if (!eventKey.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(eventKey);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    /** 短事务把发布标为 FAILED 并结束任务。 */
    CompensationHandlerResult fail(I18nRelease release, String reason) {
        write.executeWithoutResult(status -> releases.failCheck(release, reason, now()));
        return CompensationHandlerResult.success();
    }

    /**
     * 最后一次尝试出错：仍为 PENDING_CHECK 时按超时标为 FAILED；标记本身也失败时交由补偿框架置任务 FAILED 并告警。
     *
     * @param releaseId 发布 ID
     * @return 任务结果
     */
    CompensationHandlerResult timeOutAfterFailure(long releaseId) {
        try {
            ReleaseCheckTarget target = releases.loadCheckTarget(releaseId);
            if (target != null && target.getRelease().getStatus() == ReleaseStatus.PENDING_CHECK) {
                return fail(target.getRelease(), ReleaseCheckDecision.TIMEOUT_REASON);
            }
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            return CompensationHandlerResult.retryableFailure("I18N_RELEASE_ACTIVATE_RETRY", "发布上线需要重试");
        }
    }

    /** 失败原因：列出最多 5 个被拒绝的语言包。 */
    static String failureReason(List<String> rejected) {
        List<String> listed = rejected.subList(0, Math.min(LISTED_REJECTIONS, rejected.size()));
        return "语言包检查未通过：" + String.join("、", listed) + (rejected.size() > listed.size() ? " 等 "
            + rejected.size() + " 个" : "");
    }

    /** 本次 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

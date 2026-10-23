package com.paytech.platform.support.localization.domain.release;

import java.util.Collection;

import com.paytech.platform.support.localization.core.enums.BundleFileState;
import com.paytech.platform.support.localization.core.enums.ReleaseCheckVerdict;

/**
 * 上线检查的判定（技术方案 3.3 第 3 步）：拒绝优先于等待；补偿任务已到最后一次允许的尝试仍在检查时判为超时，不让发布静默停在 PENDING_CHECK。
 * 纯 Java。
 */
public final class ReleaseCheckDecision {

    /** 检查超时的失败原因。 */
    public static final String TIMEOUT_REASON = "文件检查超时";

    private ReleaseCheckDecision() {
    }

    /**
     * 判定一次检查。
     *
     * @param states 各语言包的检查结论
     * @param executeCount 补偿任务当前执行次数（从 1 开始）
     * @param maximumAttempts 补偿任务允许的最大执行次数
     * @return 结论
     */
    public static ReleaseCheckVerdict decide(Collection<BundleFileState> states, int executeCount,
        int maximumAttempts) {
        if (states.contains(BundleFileState.REJECTED)) {
            return ReleaseCheckVerdict.REJECTED;
        }
        if (!states.contains(BundleFileState.PENDING)) {
            return ReleaseCheckVerdict.READY;
        }
        return isLastAttempt(executeCount, maximumAttempts) ? ReleaseCheckVerdict.TIMED_OUT : ReleaseCheckVerdict.RETRY;
    }

    /**
     * 是否已到最后一次允许的尝试。
     *
     * @param executeCount 当前执行次数
     * @param maximumAttempts 最大执行次数
     * @return 到达或超过上限返回 true
     */
    public static boolean isLastAttempt(int executeCount, int maximumAttempts) {
        return executeCount >= maximumAttempts;
    }
}

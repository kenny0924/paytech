package com.paytech.platform.support.localization.auto.release;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.core.enums.BundleFileState;
import com.paytech.platform.support.localization.core.enums.ReleaseCheckVerdict;
import com.paytech.platform.support.localization.domain.release.ReleaseCheckDecision;

/**
 * 上线检查判定（技术方案 3.3 第 3 步）：全部可用为 READY，任一拒绝为 REJECTED（优先于等待），仍在检查时未到上限重试、
 * 到达最后一次允许的尝试判为超时（发布标为 FAILED，原因“文件检查超时”）。
 *
 * @see ReleaseCheckDecision#decide(java.util.Collection, int, int)
 * @see ReleaseCheckDecision#isLastAttempt(int, int)
 */
class ReleaseCheckDecisionTest {

    private static final int MAXIMUM_ATTEMPTS = 60;

    /** 全部可用、拒绝优先、等待重试。 */
    @Test
    void test_verdicts() {
        assertAll(
            () -> assertEquals(ReleaseCheckVerdict.READY, ReleaseCheckDecision.decide(
                List.of(BundleFileState.AVAILABLE, BundleFileState.AVAILABLE), 1, MAXIMUM_ATTEMPTS)),
            () -> assertEquals(ReleaseCheckVerdict.REJECTED, ReleaseCheckDecision.decide(
                List.of(BundleFileState.PENDING, BundleFileState.REJECTED), MAXIMUM_ATTEMPTS, MAXIMUM_ATTEMPTS)),
            () -> assertEquals(ReleaseCheckVerdict.RETRY, ReleaseCheckDecision.decide(
                List.of(BundleFileState.AVAILABLE, BundleFileState.PENDING), MAXIMUM_ATTEMPTS - 1, MAXIMUM_ATTEMPTS)));
    }

    /** 最后一次允许的尝试仍在检查判为超时；接管后的超限次数同样判为超时；全部可用时即使到上限也上线。 */
    @Test
    void test_timeoutOnLastAttempt() {
        assertAll(
            () -> assertEquals(ReleaseCheckVerdict.TIMED_OUT, ReleaseCheckDecision.decide(
                List.of(BundleFileState.PENDING), MAXIMUM_ATTEMPTS, MAXIMUM_ATTEMPTS)),
            () -> assertEquals(ReleaseCheckVerdict.TIMED_OUT, ReleaseCheckDecision.decide(
                List.of(BundleFileState.PENDING), MAXIMUM_ATTEMPTS + 1, MAXIMUM_ATTEMPTS)),
            () -> assertEquals(ReleaseCheckVerdict.READY, ReleaseCheckDecision.decide(
                List.of(BundleFileState.AVAILABLE), MAXIMUM_ATTEMPTS, MAXIMUM_ATTEMPTS)),
            () -> assertEquals("文件检查超时", ReleaseCheckDecision.TIMEOUT_REASON),
            () -> assertTrue(ReleaseCheckDecision.isLastAttempt(MAXIMUM_ATTEMPTS, MAXIMUM_ATTEMPTS)),
            () -> assertFalse(ReleaseCheckDecision.isLastAttempt(1, MAXIMUM_ATTEMPTS)));
    }
}

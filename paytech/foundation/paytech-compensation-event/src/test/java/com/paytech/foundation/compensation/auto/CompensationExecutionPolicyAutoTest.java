package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;

/**
 * 重试策略值对象测试。
 *
 * @author Codex
 */
class CompensationExecutionPolicyAutoTest {

    @Test
    void test_defaultsAreBounded() {
        CompensationExecutionPolicy policy = CompensationExecutionPolicy.defaults();

        assertTrue(policy.timeout().isPositive());
        assertTrue(policy.maximumAttempts() > 0);
        assertTrue(policy.initialBackoff().compareTo(policy.maximumBackoff()) <= 0);
    }

    @Test
    void test_zeroTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CompensationExecutionPolicy(Duration.ZERO, 1,
            Duration.ofSeconds(1), Duration.ofSeconds(1), 1.0D, 0.0D));
    }
}

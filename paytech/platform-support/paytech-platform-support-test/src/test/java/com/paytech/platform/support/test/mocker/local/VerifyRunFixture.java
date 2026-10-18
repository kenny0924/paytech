package com.paytech.platform.support.test.mocker.local;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.dal.VerifyTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 沙箱验证运行的异步收尾：真实补偿执行器在提交后异步执行运行。等待全部运行任务结束后才允许断言或清理；
 * 任务未结束即标记环境不可复用。清理只回收 INTEGRATION_VERIFY_RUN 类型的补偿任务。
 */
@TestComponent
@Order(-90)
@RequiredArgsConstructor
public class VerifyRunFixture implements Clear {

    private final VerifyTestMapper mapper;
    private boolean reusable = true;

    /** 等待全部验证运行任务结束且没有 RUNNING 运行。 */
    public void awaitIdle() {
        try {
            await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(50))
                .until(() -> mapper.activeTasks() == 0 && mapper.runningRuns() == 0);
        } catch (RuntimeException | AssertionError timeout) {
            reusable = false;
            throw timeout;
        }
    }

    /** 公共 AfterEach：先确认后台任务结束，再回收补偿任务。 */
    @Override
    public void clear() {
        assertTrue(reusable, "沙箱验证后台任务未停止，拒绝清理");
        if (mapper.activeTasks() > 0) {
            awaitIdle();
        }
        mapper.deleteTasks();
    }
}

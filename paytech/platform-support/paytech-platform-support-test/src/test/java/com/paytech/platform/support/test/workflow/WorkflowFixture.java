package com.paytech.platform.support.test.workflow;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.dal.WorkflowTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 工作流测试环境：每例开始前要求没有工作流补偿任务，后置先等待后台投递结束再删除本例任务；
 * 工作流十表由 {@link com.paytech.platform.support.test.mocker.local.PlatformDataCleaner} 整表回收。
 */
@TestComponent
@Order(-90)
@RequiredArgsConstructor
public class WorkflowFixture implements Clear {

    private final WorkflowTestMapper mapper;
    private boolean reusable = true;

    /** 本例开始：没有残留的工作流补偿任务。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一例工作流后台任务未结束，环境不可复用");
        assertEquals(0, mapper.events().size(), "存在残留的工作流补偿任务，请人工确认后清空");
    }

    /** 等待提交后唤醒的投递执行结束（任务删除或进入 ERROR / FAILED）。 */
    public void awaitIdle() {
        try {
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> mapper.activeEvents() == 0);
        } catch (RuntimeException | AssertionError timeout) {
            reusable = false;
            throw timeout;
        }
    }

    /** 等后台结束后删除工作流补偿任务。 */
    @Override
    public void clear() {
        awaitIdle();
        mapper.deleteEvents();
    }
}

package com.paytech.foundation.compensation.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.RegisteredCompensationEvent;
import com.paytech.foundation.compensation.test.base.MockTestBase;
import com.paytech.foundation.compensation.test.support.DatabaseLookupCompensationHandler;
import com.paytech.foundation.compensation.test.support.RecordingExecutionSequencer;

/**
 * 宿主提供 {@code CompensationExecutionSequencer} 时，已 Claim 任务经编排器执行；编排器拒绝时任务按可重试失败释放。
 *
 * @author Claude
 */
@Import(CompensationExecutionSequencerAutoTest.SequencerConfiguration.class)
class CompensationExecutionSequencerAutoTest extends MockTestBase {

    @Autowired
    RecordingExecutionSequencer sequencer;

    @BeforeEach
    void resetSequencer() {
        sequencer.reset();
    }

    /** 登记后的任务在编排线程上执行，编排身份为任务的事件类型与业务键；完成后记录删除。 */
    @Test
    void test_normalFlow() {
        createBusinessSource("sequenced-1");

        inTransaction(() -> registrar.register(
            new CompensationEventRequest("sequenced-1", DatabaseLookupCompensationHandler.EVENT_TYPE, "TEST_SOURCE")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, testMapper.findProcessedCount("sequenced-1", "TEST_SOURCE"));
            assertEquals(0, testMapper.countCompensationEvents());
        });
        assertEquals(List.of(new RecordingExecutionSequencer.Sequenced(DatabaseLookupCompensationHandler.EVENT_TYPE,
            "sequenced-1", RecordingExecutionSequencer.THREAD_NAME)), sequencer.sequenced());
    }

    /** 编排器拒绝：Handler 不执行，任务转为可重试并记录 SEQUENCER_UNAVAILABLE，保留给扫描兜底。 */
    @Test
    void test_businessRuleViolation() {
        createBusinessSource("sequenced-2");
        sequencer.rejecting(true);

        RegisteredCompensationEvent registered = inTransaction(() -> registrar.register(
            new CompensationEventRequest("sequenced-2", DatabaseLookupCompensationHandler.EVENT_TYPE, "TEST_SOURCE")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals("ERROR", testMapper.findCompensationEventStatus(registered.eventId()));
            assertEquals("SEQUENCER_UNAVAILABLE", testMapper.findCompensationEventErrorCode(registered.eventId()));
        });
        assertEquals(0, testMapper.findProcessedCount("sequenced-2", "TEST_SOURCE"));
        assertEquals(1, testMapper.findCompensationEventExecuteCount(registered.eventId()));
        assertTrue(sequencer.sequenced().isEmpty());
    }

    /** 本类上下文追加的编排器。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SequencerConfiguration {

        @Bean(destroyMethod = "close")
        RecordingExecutionSequencer recordingExecutionSequencer() {
            return new RecordingExecutionSequencer();
        }
    }
}

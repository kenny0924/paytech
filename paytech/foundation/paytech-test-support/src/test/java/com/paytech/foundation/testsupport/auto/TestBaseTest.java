package com.paytech.foundation.testsupport.auto;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.opentest4j.MultipleFailuresError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.TestBase;

/**
 * 通过真实 JUnit 生命周期与 Spring 列表注入验证公共清理，不直接手动调用回调。
 *
 * @see TestBase
 * @see Clear#clear()
 */
class TestBaseTest {
    private static State state;

    /** 每次启动独立的机制夹具，业务测试不使用此审计状态。 */
    @BeforeEach
    void setUp() {
        state = new State();
    }

    /** 夹具上下文已关闭后释放本例审计数据。 */
    @AfterEach
    void tearDown() {
        state = null;
    }

    /** 不在准备前清理，两级准备完成后才执行用例和后置清理。 */
    @Test
    void test_cleanupOnlyAfterEachAndBetweenCases() {
        var result = runFixture(LifecycleFixture.class);
        assertEquals(2, result.getTestsSucceededCount());
        assertEquals(0, result.getTotalFailureCount());
        var oneCase = List.of("global-before", "class-before", "test", "class-after", "global-after",
            "clear-first", "clear-second");
        assertEquals(oneCase, state.events.subList(0, 7));
        assertEquals(oneCase, state.events.subList(7, 14));
        assertEquals(0, state.ownedRows);
    }

    /** 准备、用例或意外调用检查失败时，父类 AfterEach 仍清理全部资源。 */
    @ParameterizedTest
    @ValueSource(strings = {"class-before", "test", "global-after"})
    void test_failuresDoNotSkipCleanup(String phase) {
        state.failurePhase = phase;
        var result = runFixture(LifecycleFixture.class);
        assertEquals(2, result.getTestsFailedCount());
        assertEquals(2, state.events.stream().filter("clear-first"::equals).count());
        assertEquals(2, state.events.stream().filter("clear-second"::equals).count());
        assertEquals(0, state.ownedRows);
    }

    /** 第一个清理失败不能阻止后续清理，多个失败一起交给 JUnit 报告。 */
    @Test
    void test_allCleanupFailuresAreReported() {
        state.failCleanup = true;
        var result = runFixture(LifecycleFixture.class);
        assertEquals(2, result.getTestsFailedCount());
        assertEquals(2, state.events.stream().filter("clear-second"::equals).count());
        for (var failure : result.getFailures()) {
            var aggregated = assertInstanceOf(MultipleFailuresError.class, failure.getException());
            assertEquals(2, aggregated.getFailures().size());
            assertEquals(List.of("first cleanup", "second cleanup"),
                aggregated.getFailures().stream().map(Throwable::getMessage).toList());
        }
    }

    /** 宿主确认异步尚未停止时禁止清理，也不能让下一例继续使用该环境。 */
    @Test
    void test_unsafeEnvironmentIsNotClearedOrReused() {
        var result = runFixture(UnsafeFixture.class);
        assertEquals(2, result.getTestsFailedCount());
        assertEquals(1, state.events.stream().filter("test"::equals).count());
        assertTrue(state.events.stream().noneMatch(event -> event.startsWith("clear-")));
        assertEquals(1, state.ownedRows);
    }

    /** 启动嵌套夹具并检查真实执行结果，预期失败不泄漏到外层测试引擎。 */
    private TestExecutionSummary runFixture(Class<?> fixture) {
        var listener = new SummaryGeneratingListener();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        try (var session = LauncherFactory.openSession()) {
            session.getLauncher().execute(request, listener);
        }
        assertEquals(2, listener.getSummary().getTestsFoundCount());
        return listener.getSummary();
    }

    /** 框架机制的审计事实，不模拟业务数据库或外部服务。 */
    static class State {
        final List<String> events = new ArrayList<>();
        String failurePhase;
        boolean failCleanup;
        boolean safeToClear = true;
        int ownedRows;

        /** 在真实生命周期中记录事件，并按场景触发失败。 */
        void record(String phase) {
            events.add(phase);
            if (phase.equals(failurePhase)) {
                fail(phase);
            }
        }
    }

    /** 为实际 Spring List 注入提供两个独立 Clear Bean。 */
    @Configuration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        /** 每次外层用例使用自己的状态，夹具结束关闭上下文。 */
        @Bean
        State fixtureState() {
            return state;
        }

        /** 清理依赖需要顺序时使用 Spring 现有排序，不新增调度规则。 */
        @Bean
        @Order(1)
        Clear firstClear(State fixtureState) {
            return () -> {
                fixtureState.events.add("clear-first");
                if (fixtureState.failCleanup) {
                    fail("first cleanup");
                }
                fixtureState.ownedRows = 0;
            };
        }

        /** 第二个清理用于证明前项失败不截断整个清理过程。 */
        @Bean
        @Order(2)
        Clear secondClear(State fixtureState) {
            return () -> {
                fixtureState.events.add("clear-second");
                if (fixtureState.failCleanup) {
                    fail("second cleanup");
                }
            };
        }
    }

    /** 模拟宿主的全局准备和意外调用检查，不手动调用公共生命周期。 */
    @SpringJUnitConfig(FixtureConfiguration.class)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    abstract static class HostBase extends TestBase {
        @Autowired
        State fixtureState;

        /** 上一例必须已清理，本例公共数据在全局 BeforeEach 重新准备。 */
        @BeforeEach
        final void globalSetUp() {
            assertTrue(fixtureState.safeToClear, "环境禁止复用");
            fixtureState.record("global-before");
            if (!fixtureState.failCleanup) {
                assertEquals(0, fixtureState.ownedRows);
            }
            fixtureState.ownedRows++;
        }

        /** 宿主检查先发生，公共基类随后清理。 */
        @AfterEach
        final void globalTearDown() {
            fixtureState.record("global-after");
        }
    }

    /** 两次方法执行共享 Spring Bean，检查实例重建不掩盖状态污染。 */
    static class LifecycleFixture extends HostBase {
        /** 只准备当前测试类数据。 */
        @BeforeEach
        final void classSetUp() {
            fixtureState.record("class-before");
        }

        /** 标记真实用例执行。 */
        @RepeatedTest(2)
        void exercise() {
            fixtureState.record("test");
        }

        /** 子类结束先于宿主和公共基类。 */
        @AfterEach
        final void classTearDown() {
            fixtureState.record("class-after");
        }
    }

    /** 模拟不能安全停止的异步写入。 */
    static class UnsafeFixture extends LifecycleFixture {
        /** 停止失败后保存污染标记，禁止擦除尚可能被回写的数据。 */
        @AfterEach
        void stopWork() {
            fixtureState.safeToClear = false;
        }

        /** 实际宿主以已等待的异步结果决定是否允许清理。 */
        @Override
        protected void checkClearAllowed() {
            assertTrue(fixtureState.safeToClear, "异步尚未停止");
        }
    }
}

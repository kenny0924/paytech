package com.paytech.foundation.testsupport.test.mockhttp;

import java.util.Map;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.testsupport.api.Clear;

/** 自动扫描测试专用夹具，含必须忽略的普通组件与配置。 */
public final class MockHttpFixtures {
    /** 对外响应和生命周期证据由用例直接配置与读取。 */
    @TestComponent
    @Primary
    public static class ResponseMocker implements Clear, Supplier<String> {
        public String value;
        public int calls;
        public int initialized;
        public int destroyed;

        /** 通过同接口多候选验证扫描组件的 Primary 注解生效。 */
        @Override
        public String get() {
            return value;
        }

        /** 每例只清响应与调用记录，生命周期计数用于观察容器。 */
        @Override
        public void clear() {
            value = null;
            calls = 0;
        }
    }

    /** 模拟外部 API JAR，映射只在接口声明。 */
    @HttpExchange("/managed-mock")
    public interface ExternalApi {
        /** 验证请求绑定及共享 Mocker 的响应。 */
        @GetExchange
        Map<String, String> get(@RequestParam("id") String id);
    }

    /** 只能进入独立 Mock 服务，不能与宿主的真实 API 代理竞争注入。 */
    @RestController
    @TestComponent
    public static class MockExternalApi implements ExternalApi {
        private final ResponseMocker mocker;

        /** 自动注入宿主的同一个 Mocker。 */
        public MockExternalApi(ResponseMocker mocker) {
            this.mocker = mocker;
        }

        /** 观察 Spring 是否完整执行初始化。 */
        @PostConstruct
        public void initialize() {
            mocker.initialized++;
        }

        /** 服务关闭后销毁内部 Controller。 */
        @PreDestroy
        public void destroy() {
            mocker.destroyed++;
        }

        /** 直接返回用例配置，保留真实 HTTP 参数绑定。 */
        @Override
        public Map<String, String> get(String id) {
            mocker.calls++;
            return Map.of("id", id, "value", mocker.value);
        }
    }

    /** 没有 TestComponent 的普通组件不能被测试扫描器启动。 */
    @Component
    public static class UnrelatedComponent {
        /** 意外创建时立即暴露扫描越界。 */
        public UnrelatedComponent() {
            throw new AssertionError("不应扫描普通业务组件");
        }
    }

    /** 配置类必须显式导入，不能因继承 TestComponent 元注解被启动。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class UnrelatedConfiguration {
        /** 意外创建时立即暴露配置扫描越界。 */
        public UnrelatedConfiguration() {
            throw new AssertionError("不应扫描测试配置类");
        }
    }
}

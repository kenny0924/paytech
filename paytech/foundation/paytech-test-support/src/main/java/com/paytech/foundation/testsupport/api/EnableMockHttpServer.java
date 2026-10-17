package com.paytech.foundation.testsupport.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.paytech.foundation.testsupport.core.autoconfigure.MockHttpServerRegistrar;

/**
 * 扫描指定测试包的 TestComponent，自动创建独立 HTTP Mock 服务。 Mocker 注册为宿主 Bean；Controller 作为服务内部 Bean，避免与真实 API 代理和路由冲突。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MockHttpServerRegistrar.class)
public @interface EnableMockHttpServer {
    /** 仅扫描当前切片的 Mock API 和 Mocker 所在包，不扫描业务组件或配置类。 */
    String[] basePackages();

    /** 动态地址写入的配置键，由测试 YAML 的服务发现配置引用。 */
    String baseUrlProperty() default "test.mock-http.base-url";
}

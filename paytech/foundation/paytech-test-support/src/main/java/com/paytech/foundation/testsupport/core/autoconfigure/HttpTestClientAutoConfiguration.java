package com.paytech.foundation.testsupport.core.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer;
import org.springframework.boot.test.http.server.LocalTestWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;

/** 由 AutoConfigureRestTestClient 激活，集中复用宿主传输和随机端口配置。 */
@AutoConfiguration(after = ImperativeHttpClientAutoConfiguration.class,
    beforeName = "org.springframework.boot.resttestclient.autoconfigure.RestTestClientTestAutoConfiguration")
public class HttpTestClientAutoConfiguration {
    /** 延迟绑定实际服务地址，并保留宿主超时、重试、重定向及有序定制器。 */
    @Bean
    @Lazy
    @Conditional(OnServletWebServerCondition.class)
    @ConditionalOnBean(ClientHttpRequestFactory.class)
    @ConditionalOnMissingBean(RestTestClient.class)
    RestTestClient restTestClient(ApplicationContext context, ClientHttpRequestFactory requestFactory,
        ObjectProvider<RestTestClientBuilderCustomizer> customizers) {
        var builder = RestTestClient.bindToServer(requestFactory)
            .uriBuilderFactory(LocalTestWebServer.get(context).uriBuilderFactory());
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /** 调用器使用最终选定的客户端，用户显式定义时退让。 */
    @Bean
    @Lazy
    @ConditionalOnMissingBean(HttpTestInvoker.class)
    HttpTestInvoker httpTestInvoker(RestTestClient client) {
        return new HttpTestInvoker(client);
    }

    /** 仅实际 Servlet 服务复用网络传输；MOCK 场景保留 Boot 原有客户端。 */
    static class OnServletWebServerCondition implements Condition {
        /** 只判断上下文类型，条件评估阶段不提前创建服务器或读取随机端口。 */
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getResourceLoader() instanceof ServletWebServerApplicationContext;
        }
    }
}

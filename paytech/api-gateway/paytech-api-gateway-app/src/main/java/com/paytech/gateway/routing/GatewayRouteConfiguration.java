package com.paytech.gateway.routing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import org.springframework.web.reactive.accept.ApiVersionStrategy;

/** 为请求固定的动态目标装配标准转发链，保留既有 Operations 传输边界。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OperationsRoutingProperties.class, ClientRoutingProperties.class})
public class GatewayRouteConfiguration {
    /** 请求路由不使用独立过滤器缓存，防止热更新积累请求级条目。 */
    public GatewayRouteConfiguration(OperationsRoutingProperties properties, Environment environment,
        GatewayProperties gatewayProperties) {
        String[] profiles = environment.getActiveProfiles();
        if (properties.testHttpEnabled() && (profiles.length != 1 || !"test".equals(profiles[0]))) {
            throw new IllegalStateException("Operations HTTP 只允许显式启用且唯一激活 test Profile");
        }
        if (gatewayProperties.isRouteFilterCacheEnabled()) {
            throw new IllegalStateException("数据库动态路由必须关闭独立 Route Filter Cache");
        }
    }

    /** 替代默认按 RouteLocator 二次匹配的处理器，继续复用框架过滤器及版本策略。 */
    @Bean
    GatewayRequestRouteHandlerMapping gatewayRequestRouteHandlerMapping(FilteringWebHandler webHandler,
        GlobalCorsProperties corsProperties, Environment environment, ObjectProvider<ApiVersionStrategy> versionStrategy) {
        var mapping = new GatewayRequestRouteHandlerMapping(webHandler, corsProperties, environment);
        versionStrategy.ifAvailable(mapping::setApiVersionStrategy);
        return mapping;
    }
}

package com.paytech.channel.connector.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/** 本服务数据源只扫描 channel-connector core 的 Mapper；DataSource、事务管理器与 TypeHandler 由 Spring Boot 与 Mapper XML 提供。 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.channel.connector.core.dal.mapper")
public class ChannelConnectorConfiguration {
    /** 禁止把数字 ID、字符串 Boolean 或未知字段静默转换为合法请求（与 Payment 合同口径一致）。 */
    @Bean
    JsonMapperBuilderCustomizer channelConnectorJsonContract() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(String.class, coercion -> {
                coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
    }
}

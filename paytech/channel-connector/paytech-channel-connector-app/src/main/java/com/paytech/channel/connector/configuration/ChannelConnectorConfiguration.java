package com.paytech.channel.connector.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/**
 * 本服务数据源只扫描 channel-connector core 的 Mapper；DataSource 与事务管理器由 Spring Boot 提供
 * <li>全部 Mapper XML 以内联 CodeEnumTypeHandler（按 javaType）映射闭合枚举列，参数写入枚举的稳定 Code；不全局登记 CodeEnumTypeHandler：
 * MyBatis 按 Handler 类缓存实例，全局登记会让 XML 内联引用取到另一枚举类型的实例。</li>
 * <li>全局只登记 UTC InstantTypeHandler 并开启下划线转驼峰（渠道成本 Mapper 自动映射其余列）；主键由应用分配。</li>
 */
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

    /** channel-connector MyBatis 全局定制：UTC Instant TypeHandler、下划线转驼峰、应用分配主键。 */
    @Bean
    ConfigurationCustomizer channelConnectorMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
        };
    }
}

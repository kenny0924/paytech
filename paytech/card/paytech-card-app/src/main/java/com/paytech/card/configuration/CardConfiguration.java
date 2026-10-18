package com.paytech.card.configuration;

import java.time.Clock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.id.SnowflakeIdGenerator;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/** card-service 的基础装配：本服务唯一数据源只扫描 card core 的 Mapper（R11：卡表只在 card-core / card-app）。 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.card.core.dal.mapper")
public class CardConfiguration {
    /** 正式 Snowflake ID 生成器。 */
    @Bean
    IdGenerator cardIdGenerator(CardIdentifierProperties properties) {
        return new SnowflakeIdGenerator(properties.getRegionId(), properties.getWorkerId());
    }

    /** 统一 UTC 时钟。 */
    @Bean
    Clock cardClock() {
        return Clock.systemUTC();
    }

    /** 资源不存在（含调用方不符）返回 404，错误体复用 Foundation Problem。 */
    @Bean
    ApiErrorStatusResolver cardErrorStatus() {
        return (code, fallback) -> "NOT_FOUND".equals(code.getCode()) ? HttpStatus.NOT_FOUND : fallback;
    }

    /** 禁止把数字 ID、字符串 Boolean 或未知字段静默转换为合法请求（与 Payment 合同口径一致）。 */
    @Bean
    JsonMapperBuilderCustomizer cardJsonContract() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(String.class, coercion -> {
                coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            }).withCoercionConfig(Boolean.class, coercion -> {
                coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            });
    }
}

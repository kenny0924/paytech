package com.paytech.payment.configuration;

import java.time.Clock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/** 支付受理所需的基础组件集中装配：本服务唯一数据源扫描 execution 与 card（第 12 步前）的 Mapper，不扫描渠道表。 */
@Configuration(proxyBeanMethods = false)
@MapperScan({"com.paytech.payment.execution.core.dal.mapper", "com.paytech.payment.card.core.dal.mapper"})
public class PaymentConfiguration {
    /** 为实际 Repository 提供已配置的正式 ID 生成器，不使用测试专用替身。 */
    @Bean
    IdGenerator paymentIdGenerator(PaymentIdentifierProperties properties) {
        return new SnowflakeIdGenerator(properties.getRegionId(), properties.getWorkerId());
    }

    /** 使用统一 UTC 时钟。 */
    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }


    /** 延续 Payment 的缺失资源和失效工具状态，错误体复用 Foundation Problem。 */
    @Bean
    ApiErrorStatusResolver paymentErrorStatus() {
        return (code, fallback) -> switch (code.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PAYMENT_INSTRUMENT_EXPIRED", "PAYMENT_INSTRUMENT_REVOKED" -> HttpStatus.CONFLICT;
            default -> fallback;
        };
    }

    /** 禁止把数字 ID、字符串 Boolean 或已删除的请求字段静默转换为合法请求。 */
    @Bean
    JsonMapperBuilderCustomizer paymentJsonContract() {
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

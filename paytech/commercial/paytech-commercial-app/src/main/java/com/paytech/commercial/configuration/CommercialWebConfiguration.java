package com.paytech.commercial.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** 应用级 JSON、时间和既有 Checkout 错误合同；不承载数据库连接或业务支持范围。 */
@Configuration(proxyBeanMethods = false)
public class CommercialWebConfiguration {
    /** 拒绝未知字段、重复键与文本字段的隐式类型转换（沿用原 CCT 合同）。 */
    @Bean
    public org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer commercialJson() {
        return builder -> builder.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(tools.jackson.databind.type.LogicalType.Textual, coercion -> {
                coercion.setCoercion(tools.jackson.databind.cfg.CoercionInputShape.Integer,
                    tools.jackson.databind.cfg.CoercionAction.Fail);
                coercion.setCoercion(tools.jackson.databind.cfg.CoercionInputShape.Float,
                    tools.jackson.databind.cfg.CoercionAction.Fail);
                coercion.setCoercion(tools.jackson.databind.cfg.CoercionInputShape.Boolean,
                    tools.jackson.databind.cfg.CoercionAction.Fail);
            });
    }

    /** 业务时钟。 */
    @Bean
    public Clock commercialClock() {
        return Clock.systemUTC();
    }

    /** Checkout 合同错误的 HTTP 状态，与原 CCT 一致。 */
    @Bean
    public ApiErrorStatusResolver commercialErrorStatusResolver() {
        return (code, fallback) -> switch (code.getCode()) {
            case "CLIENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_CHECKOUT_QUERY", "CLIENT_NOT_ELIGIBLE", "NO_ELIGIBLE_ENTITY" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "CCT_CONTEXT_AMBIGUOUS", "CCT_CONTEXT_CHANGED" -> HttpStatus.CONFLICT;
            case "OWNER_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> fallback;
        };
    }
}

package com.paytech.client.contract.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** 应用级 JSON、时间和既有错误合同；不承载数据库连接或业务支持范围。 */
@Configuration(proxyBeanMethods = false)
public class ClientContractWebConfiguration {
    @Bean
    public org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer clientContractJson() {
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
    @Bean
    public Clock clientContractClock() {
        return Clock.systemUTC();
    }
    @Bean
    public ApiErrorStatusResolver clientContractErrorStatusResolver() {
        return (code, fallback) -> switch (code.getCode()) {
            case "CLIENT_SESSION_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "INVALID_DRAFT", "ORGANIZATION_REQUIRED", "MATERIAL_COUNTRY_REQUIRED", "MATERIAL_REQUIREMENT_INVALID" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "DOCUMENT_NOT_FOUND", "APPLICATION_NOT_FOUND", "PERSON_NOT_FOUND", "RELATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DOCUMENT_ALREADY_EXISTS", "MATERIAL_FILE_UNAVAILABLE", "MATERIAL_CONTEXT_CHANGED", "APPLICATION_NOT_EDITABLE", "ACTIVE_APPLICATION_TYPE_MISMATCH", "IDEMPOTENCY_CONFLICT",
                 "PERSON_NOT_EDITABLE", "RELATION_NOT_EDITABLE", "RELATION_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
            case "DRAFT_CONTEXT_INVALID", "ONBOARDING_DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "CLIENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_CHECKOUT_QUERY", "CLIENT_NOT_ELIGIBLE", "NO_ELIGIBLE_ENTITY" ->
                HttpStatus.UNPROCESSABLE_CONTENT;
            case "CCT_CONTEXT_AMBIGUOUS", "CCT_CONTEXT_CHANGED" -> HttpStatus.CONFLICT;
            case "OWNER_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> fallback;
        };
    }
}

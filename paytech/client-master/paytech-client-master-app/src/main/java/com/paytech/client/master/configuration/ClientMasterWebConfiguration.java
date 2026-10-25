package com.paytech.client.master.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** 应用级 JSON、时间和既有错误合同；不承载数据库连接或业务支持范围。 */
@Configuration(proxyBeanMethods = false)
public class ClientMasterWebConfiguration {
    @Bean
    public org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer clientMasterJson() {
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
    public Clock clientMasterClock() {
        return Clock.systemUTC();
    }
    @Bean
    public ApiErrorStatusResolver clientMasterErrorStatusResolver() {
        return (code, fallback) -> switch (code.getCode()) {
            case "CLIENT_SESSION_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "INVALID_DRAFT", "ORGANIZATION_REQUIRED", "MATERIAL_REQUIREMENT_INVALID", "SUBMISSION_INCOMPLETE" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "DOCUMENT_NOT_FOUND", "APPLICATION_NOT_FOUND", "PERSON_NOT_FOUND", "RELATION_NOT_FOUND", "REVIEW_CASE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CLASSIFICATION_INVALID" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "CLASSIFICATION_CONFIRMED" -> HttpStatus.CONFLICT;
            case "DOCUMENT_ALREADY_EXISTS", "MATERIAL_FILE_UNAVAILABLE", "MATERIAL_CONTEXT_CHANGED", "APPLICATION_NOT_EDITABLE", "ACTIVE_APPLICATION_TYPE_MISMATCH",
                 "PERSON_NOT_EDITABLE", "RELATION_NOT_EDITABLE", "RELATION_ALREADY_EXISTS", "IDENTITY_CONFLICT" -> HttpStatus.CONFLICT;
            case "DRAFT_CONTEXT_INVALID", "ONBOARDING_DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "CLIENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_CANDIDATE_QUERY", "CLIENT_NOT_ELIGIBLE" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "CANDIDATE_CONTEXT_AMBIGUOUS" -> HttpStatus.CONFLICT;
            case "CANDIDATE_SOURCE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "OPERATING_ENTITY_NOT_FOUND", "LOCAL_PARTNER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ENTITY_CHANGE_VERSION_CONFLICT" -> HttpStatus.CONFLICT;
            case "ENTITY_ATTRIBUTE_INVALID" -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> fallback;
        };
    }
}

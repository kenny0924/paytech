package com.paytech.acquiring.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/** Commerce HTTP JSON scalar binding rules. */
@Configuration(proxyBeanMethods = false)
public class CommerceJsonConfiguration {

    /**
     * Prevents JSON numbers and booleans from being silently converted into String wire fields.
     *
     * @return the Commerce Jackson builder customization
     */
    @Bean
    JsonMapperBuilderCustomizer rejectStringScalarCoercion() {
        return builder -> builder.withCoercionConfig(String.class, coercion -> {
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}

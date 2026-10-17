package com.paytech.consumer.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/** consumer HTTP JSON scalar binding rules (copied from acquiring). */
@Configuration(proxyBeanMethods = false)
public class ConsumerJsonConfiguration {

    /**
     * Prevents JSON numbers and booleans from being silently converted into String wire fields.
     *
     * @return the consumer Jackson builder customization
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

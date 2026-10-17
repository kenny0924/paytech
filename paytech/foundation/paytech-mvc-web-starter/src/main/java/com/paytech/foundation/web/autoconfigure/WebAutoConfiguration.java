package com.paytech.foundation.web.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemExceptionFilter;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.OperationRequestContextParser;
import com.paytech.foundation.web.request.OperationRequestContextParserImpl;
import com.paytech.foundation.web.request.RequestIdFilter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring MVC 公共能力自动配置。
 *
 * @author Codex
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebLoggingProperties.class)
public class WebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiProblemResponseFactory apiProblemResponseFactory() {
        return new ApiProblemResponseFactory(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpRequestResponseLoggingFilter httpRequestResponseLoggingFilter(WebLoggingProperties properties) {
        return new HttpRequestResponseLoggingFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestBodyFieldErrorProjector requestBodyFieldErrorProjector(ObjectProvider<ObjectMapper> objectMapper) {
        return new RequestBodyFieldErrorProjector(objectMapper.getIfAvailable(WebAutoConfiguration::defaultMapper));
    }

    @Bean
    @ConditionalOnMissingBean
    public MvcProblemExceptionMapper mvcProblemExceptionMapper(ObjectProvider<ApiErrorStatusResolver> statusResolver,
        RequestBodyFieldErrorProjector fieldErrorProjector) {
        ApiErrorStatusResolver resolver =
            statusResolver.getIfAvailable(() -> (errorCode, defaultStatus) -> defaultStatus);
        return new MvcProblemExceptionMapper(resolver, fieldErrorProjector);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemResponseWriter problemResponseWriter(ApiProblemResponseFactory responseFactory,
        ObjectProvider<ObjectMapper> objectMapper) {
        return new ProblemResponseWriter(responseFactory,
            objectMapper.getIfAvailable(WebAutoConfiguration::defaultMapper));
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemExceptionFilter problemExceptionFilter(MvcProblemExceptionMapper exceptionMapper,
        ProblemResponseWriter responseWriter) {
        return new ProblemExceptionFilter(exceptionMapper, responseWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(MvcProblemExceptionMapper exceptionMapper,
        ProblemResponseWriter responseWriter) {
        return new GlobalExceptionHandler(exceptionMapper, responseWriter);
    }

    /**
     * Operations 入口元数据解析。
     *
     * @return 解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationRequestContextParser operationRequestContextParser() {
        return new OperationRequestContextParserImpl();
    }

    private static ObjectMapper defaultMapper() {
        return JsonMapper.builder().build();
    }
}

package com.paytech.platform.support.configuration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 配置管理请求的严格边界，查询字段从已部署 Controller 合同读取。 */
@Configuration(proxyBeanMethods = false)
public class ConfigurationManagementRequestConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    /** 只为本轮配置 DTO 派生严格解析器，不改变既有其他业务的 JSON 策略。 */
    public ConfigurationManagementRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, c -> c.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail).setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .build();
    }

    /** 禁止未知、重复、空查询值和查询 Body；不会修改浏览器实际传入的值。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                if (!(handler instanceof HandlerMethod method)) return true;
                if (request.getServletPath().startsWith("/api-credential-admin/") || request.getServletPath().startsWith("/api-scope-admin/") || request.getServletPath().startsWith("/tenant-access-admin/") || request.getServletPath().startsWith("/tenant-projections/"))
                    { response.setHeader("Cache-Control", "no-store"); }
                Set<String> keys = new HashSet<>();
                for (var parameter : method.getMethodParameters()) {
                    RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
                    if (query != null) keys.add(!query.name().isBlank() ? query.name() : !query.value().isBlank()
                        ? query.value() : parameter.getParameterName());
                    if (parameter.hasParameterAnnotation(ModelAttribute.class) && parameter.getParameterType().isRecord())
                        for (var field : parameter.getParameterType().getRecordComponents()) keys.add(field.getName());
                }
                for (var parameter : request.getParameterMap().entrySet()) {
                    if (!keys.contains(parameter.getKey()) || parameter.getValue().length != 1 || (parameter.getValue()[0].isBlank() && !request.getServletPath().startsWith("/tenant-access-admin/")))
                        throw new InvalidParameterException("Query 参数未知、重复或为空");
                }
                boolean emptyBody=request.getMethod().equals("GET");
                if (emptyBody && request.getInputStream().read() != -1)
                    throw new InvalidParameterException("当前请求不接受 Body");
                return true;
            }
        }).addPathPatterns("/gateway-admin/v1/**", "/console-admin/v1/**", "/permission-admin/v1/**", "/api-credential-admin/v1/**", "/api-scope-admin/v1/**", "/tenant-access-admin/v1/**", "/tenant-projections/v1/**");
    }

    /** 写 Body 有界解析，响应序列化继续走原有转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new ConfigurationRequestConverter(mapper)));
    }

    private static final class ConfigurationRequestConverter extends JacksonJsonHttpMessageConverter {
        private ConfigurationRequestConverter(JsonMapper mapper) { super(mapper); }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            Class<?> value = type.resolve();
            return value != null && value.getSimpleName().endsWith("RequestBody") && (value.getName().startsWith("com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels$") || value.getName().startsWith("com.paytech.platform.support.api.consolemanagement.request.") || value.getName().startsWith("com.paytech.platform.support.api.permissionmanagement.PermissionManagementModels$") || value.getName().startsWith("com.paytech.platform.support.api.apicredentialmanagement.request.") || value.getName().startsWith("com.paytech.platform.support.api.apiscopemanagement.request.") || value.getName().startsWith("com.paytech.platform.support.api.tenantaccessmanagement.request.") || value.getName().startsWith("com.paytech.platform.support.api.tenantprojection.request.") || value.getName().startsWith("com.paytech.platform.support.api.onboardingfiles.request."))
                && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(65537);
            if (body.length > 65536) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try {
                var tree = getMapper().readTree(body);
                if (tree == null || !tree.isObject()) throw new HttpMessageNotReadableException("配置 Body 必须为对象", input);
                if (type.resolve().isRecord()) {
                    for (var field : type.resolve().getRecordComponents()) {
                        if (field.getType().isPrimitive() && !tree.has(field.getName())) {
                            throw new HttpMessageNotReadableException("配置 Body 缺少必填字段", input);
                        }
                    }
                }
                if (type.resolve().getSimpleName().equals("UpdateApiScopeRequestBody") && !tree.has("description"))
                    { throw new HttpMessageNotReadableException("修改 Scope 必须显式提供 description，可为 null", input); }
                return getMapper().readValue(body, type.resolve());
            } catch (tools.jackson.core.JacksonException failure) {
                throw new HttpMessageNotReadableException("配置请求不符合 JSON 协议", input);
            }
        }

        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }
    }
}

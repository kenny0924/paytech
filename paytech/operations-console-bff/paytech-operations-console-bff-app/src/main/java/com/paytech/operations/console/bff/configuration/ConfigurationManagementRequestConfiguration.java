package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.foundation.common.error.InvalidParameterException;

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
                if (request.getMethod().equals("GET") && request.getInputStream().read() != -1)
                    throw new InvalidParameterException("查询请求不接受 Body");
                // Webhook 测试事件等无 RequestBody 的命令同样不接受 Body。
                if (request.getServletPath().startsWith("/webhook-admin/") && !hasRequestBody(method)
                    && request.getInputStream().read() != -1)
                    throw new InvalidParameterException("该请求不接受 Body");
                return true;
            }
        }).addPathPatterns("/gateway-admin/v1/**", "/console-admin/v1/**", "/permission-admin/v1/**", "/api-credential-admin/v1/**", "/api-scope-admin/v1/**", "/tenant-access-admin/v1/**", "/external-api-admin/v1/**", "/notification-admin/v1/**", "/webhook-admin/v1/**", "/currency-admin/v1/**");
    }

    /** Controller 方法是否声明 RequestBody。 */
    private static boolean hasRequestBody(HandlerMethod method) {
        for (var parameter : method.getMethodParameters())
            if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) return true;
        return false;
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
            return value != null && value.getSimpleName().endsWith("RequestBody") && (value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.ConsoleResourceModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.PermissionManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.TenantAccessManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.NotificationManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.WebhookManagementModels.class || value.getEnclosingClass() == com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.class)
                && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            // Webhook 写 Body 上限 8192 字节，其余配置 65536 字节。
            int limit = type.resolve().getEnclosingClass() == com.paytech.operations.console.bff.service.model.WebhookManagementModels.class ? 8192 : 65536;
            byte[] body = input.getBody().readNBytes(limit + 1);
            if (body.length > limit) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try {
                var tree = getMapper().readTree(body);
                if (tree == null || !tree.isObject()) throw new HttpMessageNotReadableException("配置 Body 必须为对象", input);
                if (type.resolve() == com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.UpdateApiScopeRequestBody.class && !tree.has("description"))
                    { throw new HttpMessageNotReadableException("修改说明必须显式传值或 null", input); }
                // 普通 class 模型以包装类型加 @NotNull 表达必填，只有 record 需要检查缺失的基本类型字段。
                if (type.resolve().isRecord())
                    for (var field : type.resolve().getRecordComponents())
                        if (field.getType().isPrimitive() && !tree.has(field.getName()))
                            throw new HttpMessageNotReadableException("配置 Body 缺少必填字段", input);
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

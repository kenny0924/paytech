package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.operations.console.bff.service.model.ClientReviewModels;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client 入驻审核请求的严格边界
 * <li>Query 只接受 Controller 声明的参数，不能重复或为空；GET 不接受 Body。</li>
 * <li>写 Body 最多 64 KiB，必须为对象，拒绝重复键、未知字段和标量隐式转换。</li>
 */
@Configuration(proxyBeanMethods = false)
public class ClientReviewRequestConfiguration implements WebMvcConfigurer {
    /** 写 Body 上限。 */
    static final int BODY_LIMIT = 65536;

    private final JsonMapper mapper;

    /** 只为审核请求派生严格解析器。 */
    public ClientReviewRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 拒绝未知、重复、空 Query 和查询携带 Body。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                if (!(handler instanceof HandlerMethod method)) {
                    return true;
                }
                Set<String> keys = queryKeys(method);
                for (var parameter : request.getParameterMap().entrySet()) {
                    if (!keys.contains(parameter.getKey()) || parameter.getValue().length != 1 || parameter.getValue()[0].isBlank()) {
                        throw new InvalidParameterException("Query 参数未知、重复或为空");
                    }
                }
                if ("GET".equals(request.getMethod()) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("查询请求不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/client-onboarding-admin/v1/**");
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new ReviewRequestConverter(mapper)));
    }

    /** Controller 声明的 Query 名。 */
    static Set<String> queryKeys(HandlerMethod method) {
        Set<String> keys = new HashSet<>();
        for (var parameter : method.getMethodParameters()) {
            RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
            if (query == null) {
                continue;
            }
            keys.add(!query.name().isBlank() ? query.name() : !query.value().isBlank() ? query.value() : parameter.getParameterName());
        }
        return keys;
    }

    /** 只读取审核请求记录的有界严格转换器。 */
    static final class ReviewRequestConverter extends JacksonJsonHttpMessageConverter {
        ReviewRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            Class<?> value = type.resolve();
            return value != null && value.getEnclosingClass() == ClientReviewModels.class && value.getSimpleName().endsWith("RequestBody")
                && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(BODY_LIMIT + 1);
            if (body.length > BODY_LIMIT) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
            }
            try {
                var tree = getMapper().readTree(body);
                if (tree == null || !tree.isObject()) {
                    throw new HttpMessageNotReadableException("审核请求 Body 必须为对象", input);
                }
                return getMapper().readValue(body, type.resolve());
            } catch (tools.jackson.core.JacksonException failure) {
                throw new HttpMessageNotReadableException("审核请求不符合 JSON 协议", input);
            }
        }

        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
            return false;
        }
    }
}

package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * /finance 请求体的严格边界
 * <li>只读取 Finance*Models 内的 *RequestBody：拒绝重复键、未知属性、尾随内容与标量强转，写 Body 上限 8192 字节（超限 413）。</li>
 * <li>GET 与未声明 RequestBody 的命令（开放会计期间）携带 Body 时返回 400；拦截器排在 PLT-I02 授权之后执行。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class FinanceRequestConfiguration implements WebMvcConfigurer {
    private static final int BODY_LIMIT = 8192;
    private static final Set<Class<?>> MODEL_CONTAINERS = Set.of(FinanceSetupModels.class,
        FinanceLedgerBookModels.class, FinanceBalanceModels.class);

    private final JsonMapper mapper;

    /** 只为财务请求体派生严格解析器，不改变其他业务的 JSON 策略。 */
    public FinanceRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, c -> c.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .build();
    }

    /** 查询与无 Body 命令不接受请求体；排在全局授权拦截器之后。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
                if (!(handler instanceof HandlerMethod method)) {
                    return true;
                }
                if ("GET".equals(request.getMethod()) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("查询请求不接受 Body");
                }
                if (!hasRequestBody(method) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("该请求不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/finance/**").order(Ordered.LOWEST_PRECEDENCE);
    }

    /** Controller 方法是否声明 RequestBody。 */
    private static boolean hasRequestBody(HandlerMethod method) {
        for (var parameter : method.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    /** 写 Body 有界严格解析，响应序列化继续走原有转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new FinanceRequestConverter(mapper)));
    }

    /** 只读取财务页面请求体的转换器。 */
    private static final class FinanceRequestConverter extends JacksonJsonHttpMessageConverter {
        private FinanceRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            Class<?> value = type.resolve();
            // 顶层请求类没有外部类；不可变 Set 的 contains(null) 会抛 NPE，必须先排除。
            return value != null && value.getEnclosingClass() != null && value.getSimpleName().endsWith("RequestBody")
                && MODEL_CONTAINERS.contains(value.getEnclosingClass()) && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(BODY_LIMIT + 1);
            if (body.length > BODY_LIMIT) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            }
            try {
                var tree = getMapper().readTree(body);
                if (tree == null || !tree.isObject()) {
                    throw new HttpMessageNotReadableException("财务请求 Body 必须为 JSON 对象", input);
                }
                return getMapper().readValue(body, type.resolve());
            } catch (JacksonException failure) {
                throw new HttpMessageNotReadableException("财务请求不符合 JSON 协议", input);
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

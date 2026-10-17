package com.paytech.risk.configuration;

import java.io.IOException;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.request.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.configuration.RiskVariableRequestConfiguration.VariableRequestConverter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TEST Candidate 协议严格读取：准确 Query、无 Body 查询、字符串 ID、整数版本及重复键拒绝。 */
@Configuration(proxyBeanMethods = false)
public class DecisionReleaseRequestConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    /** 复制应用 JSON 配置，开启重复键检测并避免解析异常携带原始内容。 */
    public DecisionReleaseRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    /** 拒绝发布接口（Candidate 与执行发布）的未知、重复、空 Query 以及查询 Body。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 不合法时在进入业务入口前返回 400 INVALID_REQUEST。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                Set<String> keys = keys(request.getMethod(), request.getRequestURI());
                for (var entry : request.getParameterMap().entrySet()) {
                    if (!keys.contains(entry.getKey()) || entry.getValue().length != 1 || entry.getValue()[0].isEmpty()) {
                        throw new InvalidParameterException("Candidate Query 参数未知、重复或为空");
                    }
                }
                if ("GET".equals(request.getMethod()) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("Candidate 查询不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/decision-releases/v1/**");
    }

    /** 优先注册只处理 Candidate 创建与校验 Body 的严格转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new ReleaseRequestConverter(mapper)));
    }

    /** 查询名单来自 DecisionReleaseManagementApi 与 ExecutionReleaseManagementApi 的列表声明；详情与写操作不接受 Query。 */
    static Set<String> keys(String method, String path) {
        if ("GET".equals(method) && (path.endsWith("/candidates") || path.endsWith("/executions"))) {
            return Set.of("page", "size", "pointCode", "status");
        }
        return Set.of();
    }

    /** 两个 Candidate 写请求的有界严格 JSON 转换器。 */
    private static final class ReleaseRequestConverter extends JacksonJsonHttpMessageConverter {
        private static final Set<Class<?>> REQUESTS = Set.of(CreateTestReleaseRequestBody.class,
            ValidateTestReleaseRequestBody.class);

        /** 绑定开启重复键检测的解析器。 */
        private ReleaseRequestConverter(JsonMapper mapper) { super(mapper); }

        /** 只接管 Candidate 写请求，其他请求保持原 Reader。 */
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return REQUESTS.contains(type.resolve()) && super.canRead(type, mediaType);
        }

        /**
         * 最多读取 16 KiB，要求单个对象、准确键集及声明类型。
         * <li>ID 只接受 JSON String，expectedVersion 只接受 INT 范围的 JSON 整数；显式 null 交由 Bean Validation 返回 422。</li>
         * <li>超限返回 413，其余形状或类型错误返回 400。</li>
         */
        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] bytes = input.getBody().readNBytes(16385);
            if (bytes.length > 16384) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            }
            try (var parser = getMapper().createParser(bytes)) {
                JsonNode tree = getMapper().readTree(parser);
                VariableRequestConverter.validateRecord(tree, type.resolve());
                if (parser.nextToken() != null) {
                    throw new IllegalArgumentException();
                }
                return getMapper().treeToValue(tree, type.resolve());
            } catch (JacksonException | IllegalArgumentException failure) {
                throw new HttpMessageNotReadableException("Candidate 请求不符合 JSON 字段或类型合同", input);
            }
        }

        /** 普通类型读取入口复用同一检查。 */
        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        /** 本转换器不接管响应写出。 */
        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }
    }
}

package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 数据查询请求体的严格解析：拒绝未知属性、重复键（含 parameters / params 内部）、尾随内容与标量强转
 * <li>Body 上限 65,536 字节，与 Gateway 路由绝对上限一致；只作用于数据查询请求体类型，不改变其他业务的 JSON 策略。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class DataQueryRequestConfiguration implements WebMvcConfigurer {

    /** 与 Gateway 写路由的请求体上限一致。 */
    static final int MAX_BODY_BYTES = 65_536;

    private static final String REQUEST_PACKAGE = "com.paytech.operations.console.bff.service.model.dataquery.request";

    private final JsonMapper mapper;

    /** 从宿主 JsonMapper 派生严格解析器。 */
    public DataQueryRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .build();
    }

    /** 放在首位，只读取数据查询请求体；响应仍由原有转换器写出。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new DataQueryRequestConverter(mapper)));
    }

    private static final class DataQueryRequestConverter extends JacksonJsonHttpMessageConverter {

        private DataQueryRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            Class<?> value = type.resolve();
            return value != null && value.getPackageName().equals(REQUEST_PACKAGE)
                && value.getSimpleName().endsWith("RequestBody") && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            }
            try {
                var tree = getMapper().readTree(body);
                if (tree == null || !tree.isObject()) {
                    throw new HttpMessageNotReadableException("数据查询 Body 必须为对象", input);
                }
                return getMapper().readValue(body, type.resolve());
            } catch (JacksonException failure) {
                throw new HttpMessageNotReadableException("数据查询请求不符合 JSON 协议", input);
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

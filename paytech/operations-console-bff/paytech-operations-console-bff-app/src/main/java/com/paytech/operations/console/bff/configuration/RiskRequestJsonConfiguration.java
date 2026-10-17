package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 对 Risk 命令安装独立严格 Reader，不修改既有 MVC JSON Reader/Writer。 */
@Configuration(proxyBeanMethods = false)
public class RiskRequestJsonConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    public RiskRequestJsonConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> converters.addFirst(new RiskCommandJsonConverter(mapper)));
    }

    private static final class RiskCommandJsonConverter extends JacksonJsonHttpMessageConverter {
        private final RiskRequestShapeValidator validator = new RiskRequestShapeValidator();

        private RiskCommandJsonConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return validator.supports(type) && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            try (var parser = getMapper().createParser(input.getBody())) {
                var tree = getMapper().readTree(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException("Risk 请求必须只有一个 JSON 根");
                validator.validate(type, tree);
                return getMapper().treeToValue(tree, getMapper().constructType(type.getType()));
            } catch (JacksonException | IllegalArgumentException failure) {
                // Jackson 的原始错误及 Cause 可能携带策略字段或 Literal，不让它进入共享错误日志。
                throw new HttpMessageNotReadableException("Risk 请求不符合 JSON 协议", input);
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

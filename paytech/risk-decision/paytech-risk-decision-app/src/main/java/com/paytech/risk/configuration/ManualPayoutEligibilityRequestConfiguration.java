package com.paytech.risk.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.risk.decision.api.evaluation.request.ManualPayoutEligibilityRequestBody;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/** Manual Payout 资格评估请求拒绝重复键，避免同一字段两个值时 inputHash 与实际绑定值出现歧义。 */
@Configuration(proxyBeanMethods = false)
public class ManualPayoutEligibilityRequestConfiguration implements WebMvcConfigurer {
    private final JsonMapper mapper;

    /** 复制应用 JSON 配置，开启重复键检测并避免解析异常携带原始内容。 */
    public ManualPayoutEligibilityRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    /** 优先注册只处理评估请求 Body 的严格转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new EligibilityRequestConverter(mapper)));
    }

    /** 评估请求的重复键拒绝转换器；解析失败由框架转换为 400。 */
    private static final class EligibilityRequestConverter extends JacksonJsonHttpMessageConverter {
        /** 绑定开启重复键检测的解析器。 */
        private EligibilityRequestConverter(JsonMapper mapper) { super(mapper); }

        /** 只接管评估请求，其他请求保持原 Reader。 */
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return type.resolve() == ManualPayoutEligibilityRequestBody.class && super.canRead(type, mediaType);
        }

        /** 本转换器不接管响应写出。 */
        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) { return false; }
    }
}

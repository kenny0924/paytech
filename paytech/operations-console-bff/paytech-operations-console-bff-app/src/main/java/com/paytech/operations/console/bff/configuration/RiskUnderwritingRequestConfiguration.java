package com.paytech.operations.console.bff.configuration;

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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.operations.console.bff.service.model.riskunderwriting.CreateUnderwritingDraftPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.ReviseUnderwritingPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.SaveUnderwritingDraftPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.SubmitUnderwritingPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingPreviewPageRequestBody;
import com.paytech.operations.console.bff.service.model.risklimittier.RequestTierChangePageRequestBody;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * /risk-underwriting（REQ-03）与 /risk-limit-tiers（REQ-04）页面请求体的严格边界
 * <li>只读取承保的六个与限额档位的三个页面写请求体：拒绝重复键、未知属性（如页面伪造 input、复核清单系统项或操作人）、
 * 尾随内容与标量强转。</li>
 * <li>Body 上限 16384 字节（不低于块 143 / 144 写路由的 Gateway 上限，超限 413）。</li>
 */
@Configuration(proxyBeanMethods = false)
public class RiskUnderwritingRequestConfiguration implements WebMvcConfigurer {
    private static final int BODY_LIMIT = 16384;
    private static final Set<Class<?>> REQUEST_TYPES = Set.of(CreateUnderwritingDraftPageRequestBody.class,
        SaveUnderwritingDraftPageRequestBody.class, SubmitUnderwritingPageRequestBody.class,
        ReviseUnderwritingPageRequestBody.class, UnderwritingPreviewPageRequestBody.class, ReviewUnderwritingRequestBody.class,
        RequestTierChangePageRequestBody.class, WithdrawClientTierChangeRequestBody.class, DecideClientTierChangeRequestBody.class);

    private final JsonMapper mapper;

    /** 只为承保页面请求体派生严格解析器，不改变其他业务的 JSON 策略。 */
    public RiskUnderwritingRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, c -> c.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .withCoercionConfig(LogicalType.Integer, c -> c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .build();
    }

    /** 写 Body 有界严格解析，响应序列化继续走原有转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new UnderwritingRequestConverter(mapper)));
    }

    /** 只读取承保页面请求体的转换器。 */
    private static final class UnderwritingRequestConverter extends JacksonJsonHttpMessageConverter {
        private UnderwritingRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            Class<?> value = type.resolve();
            return value != null && REQUEST_TYPES.contains(value) && super.canRead(type, mediaType);
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
                    throw new HttpMessageNotReadableException("承保请求 Body 必须为 JSON 对象", input);
                }
                return getMapper().readValue(body, type.resolve());
            } catch (JacksonException failure) {
                throw new HttpMessageNotReadableException("承保请求不符合 JSON 协议", input);
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

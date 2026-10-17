package com.paytech.acquiring.configuration;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.acquiring.api.checkout.v1.payment.request.SubmitPaymentRequestBody;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.json.JsonMapper;

/** 含卡请求独立严格 JSON 边界，使用普通类属性绑定和 Bean Validation。 */
@Configuration(proxyBeanMethods = false)
public class CustomerPaymentJsonConfiguration implements WebMvcConfigurer {
    /** 仅为含卡请求设置未知字段、重复字段与尾随输入检查，不改变其他接口兼容性。 */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.addFirst(new CardRequestConverter());
    }

    /** 只读含卡请求，解析错误不携带可能包含原始输入的 Jackson Cause。 */
    static final class CardRequestConverter extends AbstractHttpMessageConverter<SubmitPaymentRequestBody> {
        private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)).build();

        /** 只接收 application/json。 */
        CardRequestConverter() { super(MediaType.APPLICATION_JSON); }

        /** 类型严格限定到支付提交顶层模型。 */
        protected boolean supports(Class<?> type) { return type == SubmitPaymentRequestBody.class; }

        /** 此转换器不承担任何响应序列化。 */
        @Override
        public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }

        /** 读取流受请求 Filter 的 8 KiB 上限约束。 */
        protected SubmitPaymentRequestBody readInternal(Class<? extends SubmitPaymentRequestBody> type,
            HttpInputMessage input) throws IOException {
            try {
                return json.readValue(input.getBody(), SubmitPaymentRequestBody.class);
            } catch (JacksonException invalid) {
                throw new HttpMessageNotReadableException("支付请求 JSON 格式无效", input);
            }
        }

        /** 不允许将敏感请求作为响应输出。 */
        protected void writeInternal(SubmitPaymentRequestBody body, HttpOutputMessage output) {
            throw new HttpMessageNotWritableException("支付请求不可作为响应");
        }
    }
}

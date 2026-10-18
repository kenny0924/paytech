package com.paytech.platform.support.configuration;

import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/** Webhook 事件内部提交的 Body 边界：限制 8 KiB、拒绝重复键，解析错误不回显 Body 片段；其他 JSON 策略沿用应用默认。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookRequestConfiguration implements WebMvcConfigurer {

    /** 提交 Body 字节上限。 */
    static final int MAX_BODY_BYTES = 8 * 1024;

    private final JsonMapper mapper;

    /** 关闭错误位置中的源内容；重复键会改变摘要语义，直接拒绝。 */
    public WebhookRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    /** 仅为事件提交 Body 安装有界转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new WebhookEventRequestConverter(mapper)));
    }

    /** 读取不超过上限的字节后再反序列化；超限返回 413。 */
    private static final class WebhookEventRequestConverter extends JacksonJsonHttpMessageConverter {

        /** 使用已关闭源内容、开启重复键检测的映射器。 */
        private WebhookEventRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        /** 只接管事件提交 Body，其他类型交给默认转换器。 */
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return type.resolve() == SubmitWebhookEventRequestBody.class && super.canRead(type, mediaType);
        }

        /** 读取不超过 8 KiB 的字节再反序列化；超限 413，解析失败只给固定提示。 */
        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
            }
            try {
                return getMapper().readValue(body, SubmitWebhookEventRequestBody.class);
            } catch (JacksonException malformed) {
                // 原异常消息可能包含字段值，只保留固定提示。
                throw new HttpMessageNotReadableException("Webhook 事件请求不符合 JSON 协议", input);
            }
        }

        /** 与带类型参数的读取共用同一边界。 */
        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
            return read(ResolvableType.forClass(type), input, Map.of());
        }

        /** 只用于读取请求，不参与响应写出。 */
        @Override
        public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
            return false;
        }
    }
}

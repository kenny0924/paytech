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

import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/** 通知内部提交的 Body 边界：限制 1 MB，解析错误不回显 Body 片段；其他 JSON 策略沿用应用默认。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationRequestConfiguration implements WebMvcConfigurer {

    /** 提交 Body 字节上限。 */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final JsonMapper mapper;

    /** 只关闭错误位置中的源内容，避免邮箱进入异常消息。 */
    public NotificationRequestConfiguration(JsonMapper mapper) {
        this.mapper = mapper.rebuild().disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    }

    /** 仅为通知提交 Body 安装有界转换器。 */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(list -> list.addFirst(new NotificationRequestConverter(mapper)));
    }

    /** 读取不超过上限的字节后再反序列化；超限返回 413。 */
    private static final class NotificationRequestConverter extends JacksonJsonHttpMessageConverter {

        private NotificationRequestConverter(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return type.resolve() == SubmitNotificationRequestBody.class && super.canRead(type, mediaType);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
            byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
            }
            try {
                return getMapper().readValue(body, SubmitNotificationRequestBody.class);
            } catch (JacksonException malformed) {
                // 原异常消息可能包含字段值，只保留固定提示。
                throw new HttpMessageNotReadableException("通知请求不符合 JSON 协议", input);
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

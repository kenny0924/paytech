package com.paytech.gateway.idempotency;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.paytech.gateway.web.GatewayErrorCode;

/** 读取并校验恰好一个 Idempotency-Key；值区分大小写，不做规范化。 */
@Component
public class IdempotencyKeyPolicy {
    public static final String HEADER_NAME = "Idempotency-Key";
    private static final int MAX_LENGTH = 255;
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    /** 返回校验结果；失败时只携带错误码，不回显 Key 原值。 */
    public KeyResolution resolve(HttpHeaders headers) {
        List<String> values = headers.getOrEmpty(HEADER_NAME);
        if (values.isEmpty()) {
            return KeyResolution.rejected(GatewayErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String value = values.getFirst();
        if (values.size() != 1 || value == null || value.isEmpty() || value.length() > MAX_LENGTH
            || !KEY_PATTERN.matcher(value).matches()) {
            return KeyResolution.rejected(GatewayErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        return new KeyResolution(value, null);
    }

    /**
     * @param key 通过校验的 Key 原值
     * @param error 校验失败的错误码
     */
    public record KeyResolution(String key, GatewayErrorCode error) {
        static KeyResolution rejected(GatewayErrorCode error) {
            return new KeyResolution(null, error);
        }

        public boolean valid() {
            return error == null;
        }

        @Override
        public String toString() {
            return "KeyResolution[valid=" + valid() + ", error=" + error + "]";
        }
    }
}

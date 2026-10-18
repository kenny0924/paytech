package com.paytech.platform.support.api.onboardingfiles.response;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 对原对象目标签发的临时 PUT 授权；不允许延长原 Upload 生命周期。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadAuthorization {

    /**
     * HTTP 方法
     * <li>固定为 PUT。</li>
     */
    private String method;

    /** 浏览器直传使用的临时 URL。 */
    private String url;

    /** 浏览器必须原样携带的请求头。 */
    private Map<String, String> headers;

    /**
     * 本次授权到期时间（UTC）
     * <li>可早于 Upload 到期时间，不会晚于它。</li>
     */
    private Instant expiresAt;

    /** 临时 URL 和签名 Header 不可被默认打印。 */
    @Override
    public String toString() {
        return "UploadAuthorization[REDACTED]";
    }
}

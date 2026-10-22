package com.paytech.platform.support.api.files.response;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 对原对象目标签发的临时 PUT 授权；不允许延长原上传的期限。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadAuthorization {

    /**
     * HTTP 方法
     * <li>固定为 PUT。</li>
     */
    private String method;

    /** 浏览器直传使用的临时 HTTPS 地址。 */
    private String url;

    /** 浏览器必须原样携带的请求头（Content-Type、If-None-Match 等）。 */
    private Map<String, String> headers;

    /**
     * 本次授权到期时间（UTC）
     * <li>不晚于上传到期时间。</li>
     */
    private Instant expiresAt;

    /** 临时地址和签名 Header 不可被默认打印。 */
    @Override
    public String toString() {
        return "FileUploadAuthorization[REDACTED]";
    }
}

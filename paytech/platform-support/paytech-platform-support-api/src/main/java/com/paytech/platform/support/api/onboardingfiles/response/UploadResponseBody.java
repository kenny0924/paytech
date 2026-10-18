package com.paytech.platform.support.api.onboardingfiles.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 开始上传的结果：稳定上传身份与对原对象目标签发的临时 PUT 授权。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponseBody {

    /** 稳定上传 ID，十进制字符串。 */
    private String uploadId;

    /** 含临时 URL、必需 Header 与本次授权到期时间。 */
    private UploadAuthorization authorization;

    /**
     * 原上传的到期时间（UTC）
     * <li>不可延长。</li>
     */
    private Instant expiresAt;

    /** 临时授权不可进入普通日志。 */
    @Override
    public String toString() {
        return "UploadResponseBody[REDACTED]";
    }
}

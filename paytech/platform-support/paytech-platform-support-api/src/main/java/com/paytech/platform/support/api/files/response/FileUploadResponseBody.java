package com.paytech.platform.support.api.files.response;

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
public class FileUploadResponseBody {

    /** 稳定上传 ID，十进制字符串。 */
    private String uploadId;

    /** 临时 PUT 授权。 */
    private FileUploadAuthorization authorization;

    /**
     * 上传到期时间（UTC）
     * <li>由写入路由的上传期限决定，不可延长；到期后不能完成。</li>
     */
    private Instant expiresAt;

    /** 临时授权不可进入普通日志。 */
    @Override
    public String toString() {
        return "FileUploadResponseBody[REDACTED]";
    }
}

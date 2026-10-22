package com.paytech.platform.support.service.processor.files.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 签发文件下载授权的内部请求；内部系统之间不做文件级权限。 */
@Getter
@AllArgsConstructor
public class AuthorizeFileDownloadRequest {

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;
}

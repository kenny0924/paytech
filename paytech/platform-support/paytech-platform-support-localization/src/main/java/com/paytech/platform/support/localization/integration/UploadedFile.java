package com.paytech.platform.support.localization.integration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已上传并通过检查的导入文件内容。 */
@Getter
@AllArgsConstructor
public class UploadedFile {

    /** 文件 ID。 */
    private final long fileId;

    /** 文件服务登记的内容 SHA-256，小写十六进制；读取的字节已与它核对。 */
    private final String contentHash;

    /** 文件字节。 */
    private final byte[] content;
}

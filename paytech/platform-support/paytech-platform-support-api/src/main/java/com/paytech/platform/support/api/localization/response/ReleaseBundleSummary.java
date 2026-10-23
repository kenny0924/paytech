package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 发布中的一个语言包文件。 */
@Getter
@Setter
public class ReleaseBundleSummary {

    /** 语言代码。 */
    private String locale;

    /** 命名空间编码。 */
    private String namespaceCode;

    /** 文件服务地址 /files/v1/{fileId}。 */
    private String fileAddress;

    /** 内容 SHA-256，小写十六进制。 */
    private String contentHash;

    /** 字节数。 */
    private int byteSize;
}

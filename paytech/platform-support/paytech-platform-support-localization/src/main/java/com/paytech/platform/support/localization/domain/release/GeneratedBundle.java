package com.paytech.platform.support.localization.domain.release;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 生成的一个语言包：内容字节与摘要；上传后由文件服务返回地址。 */
@Getter
@AllArgsConstructor
public class GeneratedBundle {

    /** 语言代码。 */
    private final String locale;

    /** 命名空间编码。 */
    private final String namespaceCode;

    /** 扁平 {"key":"text"} JSON 的 UTF-8 字节；key 按字典序，无多余空白。 */
    private final byte[] content;

    /** 内容 SHA-256，小写十六进制。 */
    private final String sha256;

    /** 包含的 key 数。 */
    private final int keyCount;
}

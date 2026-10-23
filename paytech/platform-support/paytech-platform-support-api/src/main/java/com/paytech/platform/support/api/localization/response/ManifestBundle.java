package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** manifest 中一个语言包的公开地址与摘要。 */
@Getter
@Setter
public class ManifestBundle {

    /** 内容 SHA-256，小写十六进制。 */
    private String hash;

    /** 浏览器可直接拉取的公开地址；内容永不变化，可长期缓存。 */
    private String url;
}

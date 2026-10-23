package com.paytech.platform.support.localization.domain.release;

import java.util.Map;

/**
 * 一次发布全部语言包的公开地址与摘要；每次读取 manifest 时计算。
 *
 * @param bundles 语言代码 → 命名空间编码 → 语言包
 */
public record ReleaseBundleLinks(Map<String, Map<String, BundleLink>> bundles) {

    /** 复制为不可变映射。 */
    public ReleaseBundleLinks {
        bundles = Map.copyOf(bundles);
    }

    /**
     * 一个语言包。
     *
     * @param hash 内容 SHA-256
     * @param url 公开地址
     */
    public record BundleLink(String hash, String url) {
    }
}

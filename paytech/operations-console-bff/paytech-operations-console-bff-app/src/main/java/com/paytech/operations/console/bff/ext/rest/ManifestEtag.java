package com.paytech.operations.console.bff.ext.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.paytech.platform.support.api.localization.response.ManifestBundle;
import com.paytech.platform.support.api.localization.response.ManifestLocale;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;

/**
 * manifest 的强 ETag："r{发布号}-{语言}-{内容摘要前 16 位}"；无 READY 发布时发布号为 0，未指定语言时为 default
 * <li>摘要覆盖语言列表与默认语言：它们可在两次发布之间变化，只用发布号与语言会让浏览器以 304 保留过期内容。</li>
 * <li>语言已按 BCP 47 格式校验，不含引号或控制字符。</li>
 */
final class ManifestEtag {

    private ManifestEtag() { }

    /** 按 manifest 内容与请求语言生成 ETag（含引号）。 */
    static String of(ManifestResponseBody manifest, String locale) {
        String release = manifest.getReleaseNo() == null ? "0" : manifest.getReleaseNo().toString();
        return "\"r" + release + "-" + (locale == null ? "default" : locale) + "-" + digest(canonical(manifest)) + "\"";
    }

    /** 与字段顺序、Map 迭代顺序无关的规范文本。 */
    private static String canonical(ManifestResponseBody manifest) {
        var text = new StringBuilder().append(manifest.getApplication()).append('\n').append(manifest.getReleaseNo())
            .append('\n').append(manifest.getPublishedAt()).append('\n').append(manifest.getDefaultLocale()).append('\n');
        for (ManifestLocale locale : Objects.requireNonNullElse(manifest.getLocales(), List.<ManifestLocale>of())) {
            text.append("L\t").append(locale.getCode()).append('\t').append(locale.getNativeName()).append('\t')
                .append(locale.getDirection()).append('\n');
        }
        var bundles = new TreeMap<String, Map<String, ManifestBundle>>(
            Objects.requireNonNullElse(manifest.getBundles(), Map.of()));
        bundles.forEach((locale, namespaces) -> new TreeMap<>(namespaces).forEach((namespace, bundle) -> text
            .append("B\t").append(locale).append('\t').append(namespace).append('\t').append(bundle.getHash())
            .append('\t').append(bundle.getUrl()).append('\n')));
        return text.toString();
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", impossible);
        }
    }
}

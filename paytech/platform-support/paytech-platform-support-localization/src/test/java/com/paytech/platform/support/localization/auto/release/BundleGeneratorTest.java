package com.paytech.platform.support.localization.auto.release;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.Sha256;
import com.paytech.platform.support.localization.domain.release.BundleContent;
import com.paytech.platform.support.localization.domain.release.BundleGenerator;
import com.paytech.platform.support.localization.domain.release.GeneratedBundle;
import com.paytech.platform.support.localization.test.builder.LocalizationFixtures;

/**
 * 语言包生成（技术方案 3.3 第 1 步）：en 来自基准，其他语言只含已填写翻译；按语言、命名空间排序；扁平 JSON 的 key 按字典序、UTF-8、
 * 无多余空白；同一输入的字节与 SHA-256 稳定。
 *
 * @see BundleGenerator#generate(List, Map, java.util.Collection, java.util.Collection)
 * @see BundleContent#write(Map)
 * @see BundleContent#read(byte[])
 */
class BundleGeneratorTest {

    private static final Map<Long, String> NAMESPACES = Map.of(LocalizationFixtures.TEAM_NAMESPACE_ID, "team",
        LocalizationFixtures.COMMON_NAMESPACE_ID, "common");

    private static final List<I18nKey> KEYS = List.of(
        LocalizationFixtures.key(1, LocalizationFixtures.TEAM_NAMESPACE_ID, "team.z", "Zed", KeyStatus.ACTIVE),
        LocalizationFixtures.key(2, LocalizationFixtures.TEAM_NAMESPACE_ID, "team.a", "Hello \"{name}\"",
            KeyStatus.ACTIVE),
        LocalizationFixtures.key(3, LocalizationFixtures.COMMON_NAMESPACE_ID, "common.ok", "OK", KeyStatus.ACTIVE));

    /** en 与有翻译的语言各按命名空间生成；未翻译的 key 与停用语言的翻译不进入语言包，基准已变更的翻译仍包含。 */
    @Test
    void test_bundles() {
        List<I18nTranslation> translations = List.of(
            LocalizationFixtures.translation(1, "zh-CN", "泽", 0),
            LocalizationFixtures.translation(3, "zh-CN", "好", 1),
            LocalizationFixtures.translation(3, "ar", "حسنا", 1),
            LocalizationFixtures.translation(99, "zh-CN", "不在用", 1));

        List<GeneratedBundle> bundles = BundleGenerator.generate(KEYS, NAMESPACES, translations, List.of("zh-CN"));

        assertAll(
            () -> assertEquals(List.of("en/common", "en/team", "zh-CN/common", "zh-CN/team"), bundles.stream()
                .map(bundle -> bundle.getLocale() + "/" + bundle.getNamespaceCode()).toList()),
            () -> assertEquals("{\"team.a\":\"Hello \\\"{name}\\\"\",\"team.z\":\"Zed\"}", text(bundles.get(1))),
            () -> assertEquals("{\"team.z\":\"泽\"}", text(bundles.get(3))),
            () -> assertEquals(1, bundles.get(3).getKeyCount()),
            () -> assertEquals(Sha256.hex(bundles.get(2).getContent()), bundles.get(2).getSha256()));
    }

    /** 同一输入（顺序不同）得到相同字节与摘要；读取后还原同一映射。 */
    @Test
    void test_stableBytes() {
        List<GeneratedBundle> first = BundleGenerator.generate(KEYS, NAMESPACES, List.of(), List.of());
        List<GeneratedBundle> second = BundleGenerator.generate(KEYS.reversed(), NAMESPACES, List.of(), List.of());
        byte[] written = BundleContent.write(Map.of("b", "2", "a", "1"));
        assertAll(
            () -> assertArrayEquals(first.get(1).getContent(), second.get(1).getContent()),
            () -> assertEquals(first.get(1).getSha256(), second.get(1).getSha256()),
            () -> assertEquals("{\"a\":\"1\",\"b\":\"2\"}", new String(written, StandardCharsets.UTF_8)),
            () -> assertEquals(Map.of("a", "1", "b", "2"), BundleContent.read(written)));
    }

    /** UTF-8 文本。 */
    static String text(GeneratedBundle bundle) {
        return new String(bundle.getContent(), StandardCharsets.UTF_8);
    }
}

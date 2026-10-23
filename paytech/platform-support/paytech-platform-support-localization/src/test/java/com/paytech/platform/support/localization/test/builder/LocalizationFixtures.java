package com.paytech.platform.support.localization.test.builder;

import java.nio.charset.StandardCharsets;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;

/** 纯 Java 测试的数据对象构造：只组装对象，不访问数据库或外部资源。 */
public final class LocalizationFixtures {

    /** 测试命名空间 team 的 ID。 */
    public static final long TEAM_NAMESPACE_ID = 11L;

    /** 测试命名空间 common 的 ID。 */
    public static final long COMMON_NAMESPACE_ID = 12L;

    private LocalizationFixtures() {
    }

    /**
     * key：基准修订号 1。
     *
     * @param keyId key ID
     * @param namespaceId 命名空间 ID
     * @param keyCode 完整 key
     * @param sourceText 英文基准
     * @param status 状态
     * @return key
     */
    public static I18nKey key(long keyId, long namespaceId, String keyCode, String sourceText, KeyStatus status) {
        I18nKey key = new I18nKey();
        key.setKeyId(keyId);
        key.setApplicationId(1L);
        key.setNamespaceId(namespaceId);
        key.setKeyCode(keyCode);
        key.setSourceText(sourceText);
        key.setSourceRevision(1);
        key.setStatus(status);
        key.setVersion(1);
        return key;
    }

    /**
     * 翻译：依据给定的基准修订号。
     *
     * @param keyId key ID
     * @param locale 语言
     * @param text 文案
     * @param basedOnSourceRevision 依据的基准修订号
     * @return 翻译
     */
    public static I18nTranslation translation(long keyId, String locale, String text, int basedOnSourceRevision) {
        I18nTranslation translation = new I18nTranslation();
        translation.setTranslationId(keyId * 100);
        translation.setKeyId(keyId);
        translation.setLocaleCode(locale);
        translation.setText(text);
        translation.setBasedOnSourceRevision(basedOnSourceRevision);
        translation.setVersion(1);
        return translation;
    }

    /** UTF-8 字节。 */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}

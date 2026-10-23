package com.paytech.platform.support.localization.domain;

import java.util.regex.Pattern;

/** 多语言依赖的稳定代码与格式规则。 */
public final class LocalizationCodes {

    /** 全系统基准语言。 */
    public static final String BASE_LOCALE = "en";

    /** key 导入文件格式。 */
    public static final String SOURCE_FORMAT = "code-mor-i18n-source/1";

    /** 翻译导出与导入文件格式。 */
    public static final String TRANSLATION_FORMAT = "code-mor-i18n-translation/1";

    /** key 导入文件的上传原因码。 */
    public static final String KEY_IMPORT_REASON = "I18N_KEY_IMPORT";

    /** 翻译导入文件的上传原因码。 */
    public static final String TRANSLATION_IMPORT_REASON = "I18N_TRANSLATION_IMPORT";

    /** 语言包文件的上传原因码，路由到公开桶。 */
    public static final String RELEASE_BUNDLE_REASON = "I18N_RELEASE_BUNDLE";

    /** 文案最大字符数。 */
    public static final int MAX_TEXT_LENGTH = 8000;

    /** 开发者说明最大字符数。 */
    public static final int MAX_DESCRIPTION_LENGTH = 512;

    /** 命名空间编码：小写字母开头的小写字母、数字与连字符，不含点号。 */
    public static final Pattern NAMESPACE_CODE = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** 完整 key：点号分隔的段，每段为字母、数字、下划线或连字符，最长 191 字符。 */
    public static final Pattern KEY_CODE = Pattern.compile("(?=.{3,191}$)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)+");

    private LocalizationCodes() {
    }

    /**
     * key 所属的命名空间编码：第一个点号之前的部分。
     *
     * @param keyCode 完整 key
     * @return 命名空间编码
     */
    public static String namespaceOf(String keyCode) {
        int dot = keyCode.indexOf('.');
        return dot < 0 ? keyCode : keyCode.substring(0, dot);
    }
}

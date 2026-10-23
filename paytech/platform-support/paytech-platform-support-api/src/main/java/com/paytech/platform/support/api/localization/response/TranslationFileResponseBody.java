package com.paytech.platform.support.api.localization.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 一个语言的翻译导出文件；导入使用同一结构。 */
@Getter
@Setter
public class TranslationFileResponseBody {

    /** 文件格式，固定 code-mor-i18n-translation/1。 */
    private String format;

    /** 应用编码。 */
    private String application;

    /** 语言代码。 */
    private String locale;

    /**
     * 命名空间编码 → (完整 key → 翻译)
     * <li>包含全部 ACTIVE key，未翻译的值为空字符串；命名空间与 key 均按字典序。</li>
     */
    private Map<String, Map<String, String>> namespaces;
}

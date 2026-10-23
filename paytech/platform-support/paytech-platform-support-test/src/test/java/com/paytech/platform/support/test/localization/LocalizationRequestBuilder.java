package com.paytech.platform.support.test.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.SaveTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.TranslationInput;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.TextDirection;
import com.paytech.platform.support.localization.domain.LocalizationCodes;

/** 多语言请求体与导入文件内容的测试构造：给出合法默认值，允许用例覆盖为负例；不访问外部资源。 */
public final class LocalizationRequestBuilder {

    private LocalizationRequestBuilder() {
    }

    /** 新增语言。 */
    public static CreateLocaleRequestBody createLocale(String code, String nativeName) {
        CreateLocaleRequestBody body = new CreateLocaleRequestBody();
        body.setLocaleCode(code);
        body.setNativeName(nativeName);
        body.setEnglishName(nativeName);
        body.setTextDirection(TextDirection.LTR.getCode());
        body.setSortOrder(20);
        return body;
    }

    /** 修改语言状态，排序值保持新增时的 20，其余字段取给定值。 */
    public static UpdateLocaleRequestBody updateLocale(int expectedVersion, String nativeName, LocaleStatus status) {
        UpdateLocaleRequestBody body = new UpdateLocaleRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setNativeName(nativeName);
        body.setEnglishName(nativeName);
        body.setTextDirection(TextDirection.LTR.getCode());
        body.setStatus(status.getCode());
        body.setSortOrder(20);
        return body;
    }

    /** 新增应用：默认语言 en，阈值 100。 */
    public static CreateApplicationRequestBody createApplication(String code, String... requiredLocales) {
        CreateApplicationRequestBody body = new CreateApplicationRequestBody();
        body.setApplicationCode(code);
        body.setApplicationName(code);
        body.setDefaultLocale(LocalizationCodes.BASE_LOCALE);
        body.setRequiredLocales(List.of(requiredLocales));
        body.setCoverageThresholdPercent(100);
        return body;
    }

    /** 新建命名空间，排序值 10（排在测试夹具排序 0 的命名空间之后）。 */
    public static CreateNamespaceRequestBody createNamespace(String code) {
        CreateNamespaceRequestBody body = new CreateNamespaceRequestBody();
        body.setNamespaceCode(code);
        body.setNamespaceName(code);
        body.setSortOrder(10);
        return body;
    }

    /** 一条翻译。 */
    public static TranslationInput translation(String keyCode, String text, int expectedVersion) {
        TranslationInput input = new TranslationInput();
        input.setKeyCode(keyCode);
        input.setText(text);
        input.setExpectedVersion(expectedVersion);
        return input;
    }

    /** 批量保存。 */
    public static SaveTranslationsRequestBody save(String locale, TranslationInput... translations) {
        SaveTranslationsRequestBody body = new SaveTranslationsRequestBody();
        body.setLocale(locale);
        body.setTranslations(new ArrayList<>(List.of(translations)));
        return body;
    }

    /**
     * key 导入文件 code-mor-i18n-source/1：命名空间 → (key → 英文基准)，值为字符串。
     *
     * @param application 应用编码
     * @param namespaces 命名空间到条目
     * @return JSON 文本
     */
    public static String sourceFile(String application, Map<String, Map<String, String>> namespaces) {
        return "{\"format\":\"" + LocalizationCodes.SOURCE_FORMAT + "\",\"application\":\"" + application
            + "\",\"namespaces\":" + object(namespaces) + "}";
    }

    /** 嵌套对象；文案中不使用需要转义的字符。 */
    static String object(Map<String, Map<String, String>> namespaces) {
        return namespaces.entrySet().stream().map(namespace -> "\"" + namespace.getKey() + "\":"
            + namespace.getValue().entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\""
                + entry.getValue() + "\"").collect(Collectors.joining(",", "{", "}")))
            .collect(Collectors.joining(",", "{", "}"));
    }
}

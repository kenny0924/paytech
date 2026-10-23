package com.paytech.platform.support.localization.domain.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.LocalizationProblem;

import lombok.Getter;

/**
 * 翻译文件 code-mor-i18n-translation/1：{format, application, locale, namespaces: {命名空间: {完整 key: 翻译}}}；导出与导入共用。
 * 解析时收集格式问题，不抛出。纯 Java。
 */
@Getter
public final class TranslationFile {

    /** 文件声明的应用编码。 */
    private String application;

    /** 文件声明的语言代码。 */
    private String locale;

    /** 命名空间编码 → (完整 key → 翻译)，保持文件顺序；值不是字符串的条目不出现。 */
    private final Map<String, Map<String, String>> namespaces = new LinkedHashMap<>();

    /** 格式问题。 */
    private final List<LocalizationProblem> problems = new ArrayList<>();

    private TranslationFile() {
    }

    /**
     * 解析文件。
     *
     * @param content UTF-8 字节
     * @return 解析结果
     */
    public static TranslationFile parse(byte[] content) {
        TranslationFile file = new TranslationFile();
        JsonNode root = JsonFiles.read(content);
        if (root == null || !root.isObject()) {
            file.problems.add(new LocalizationProblem("file", "文件不是合法的 JSON 对象（或含重复的键）"));
            return file;
        }
        if (!LocalizationCodes.TRANSLATION_FORMAT.equals(JsonFiles.text(root, "format"))) {
            file.problems.add(new LocalizationProblem("format", "format 必须为 " + LocalizationCodes.TRANSLATION_FORMAT));
        }
        file.application = JsonFiles.text(root, "application");
        file.locale = JsonFiles.text(root, "locale");
        JsonNode namespaces = root.get("namespaces");
        if (namespaces == null || !namespaces.isObject()) {
            file.problems.add(new LocalizationProblem("namespaces", "namespaces 必须是对象"));
            return file;
        }
        namespaces.properties().forEach(entry -> file.readNamespace(entry.getKey(), entry.getValue()));
        return file;
    }

    /** 读取一个命名空间的翻译。 */
    void readNamespace(String namespace, JsonNode keys) {
        if (keys == null || !keys.isObject()) {
            problems.add(new LocalizationProblem("namespaces." + namespace, "命名空间的值必须是对象"));
            return;
        }
        Map<String, String> entries = new LinkedHashMap<>();
        namespaces.put(namespace, entries);
        keys.properties().forEach(entry -> {
            if (entry.getValue() == null || !entry.getValue().isString()) {
                problems.add(new LocalizationProblem("namespaces." + namespace + "." + entry.getKey(), "翻译必须是字符串"));
                return;
            }
            entries.put(entry.getKey(), entry.getValue().asString());
        });
    }
}

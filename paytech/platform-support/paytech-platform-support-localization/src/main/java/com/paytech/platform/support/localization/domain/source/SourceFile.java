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
 * key 导入文件 code-mor-i18n-source/1（技术方案 3.5）：{format, application, namespaces: {命名空间: {完整 key: 文案 | {text,
 * description}}}}。解析时收集格式问题，不抛出；问题的 field 为文件内路径。纯 Java。
 */
@Getter
public final class SourceFile {

    /** 文件声明的应用编码；缺失时为 null。 */
    private String application;

    /** 命名空间编码 → (完整 key → 条目)，保持文件顺序；结构不合法的条目不出现。 */
    private final Map<String, Map<String, SourceEntry>> namespaces = new LinkedHashMap<>();

    /** 格式问题。 */
    private final List<LocalizationProblem> problems = new ArrayList<>();

    private SourceFile() {
    }

    /**
     * 解析文件。
     *
     * @param content UTF-8 字节
     * @return 解析结果；有问题时 problems 非空
     */
    public static SourceFile parse(byte[] content) {
        SourceFile file = new SourceFile();
        JsonNode root = JsonFiles.read(content);
        if (root == null || !root.isObject()) {
            file.problems.add(new LocalizationProblem("file", "文件不是合法的 JSON 对象（或含重复的键）"));
            return file;
        }
        if (!LocalizationCodes.SOURCE_FORMAT.equals(JsonFiles.text(root, "format"))) {
            file.problems.add(new LocalizationProblem("format", "format 必须为 " + LocalizationCodes.SOURCE_FORMAT));
        }
        file.application = JsonFiles.text(root, "application");
        JsonNode namespaces = root.get("namespaces");
        if (namespaces == null || !namespaces.isObject()) {
            file.problems.add(new LocalizationProblem("namespaces", "namespaces 必须是对象"));
            return file;
        }
        namespaces.properties().forEach(entry -> file.readNamespace(entry.getKey(), entry.getValue()));
        return file;
    }

    /** 读取一个命名空间；编码与 key 前缀问题记入 problems。 */
    void readNamespace(String namespace, JsonNode keys) {
        String path = "namespaces." + namespace;
        if (!LocalizationCodes.NAMESPACE_CODE.matcher(namespace).matches()) {
            problems.add(new LocalizationProblem(path, "命名空间编码只能使用小写字母、数字与连字符，且以字母开头"));
            return;
        }
        if (keys == null || !keys.isObject()) {
            problems.add(new LocalizationProblem(path, "命名空间的值必须是对象"));
            return;
        }
        Map<String, SourceEntry> entries = new LinkedHashMap<>();
        namespaces.put(namespace, entries);
        keys.properties().forEach(entry -> readEntry(namespace, entry.getKey(), entry.getValue(), entries));
    }

    /** 读取一个 key；不合法时记入 problems 且不放入条目。 */
    void readEntry(String namespace, String key, JsonNode value, Map<String, SourceEntry> entries) {
        String path = "namespaces." + namespace + "." + key;
        if (!LocalizationCodes.KEY_CODE.matcher(key).matches()) {
            problems.add(new LocalizationProblem(path, "key 只能由点号分隔的字母、数字、下划线或连字符组成，最长 191 字符"));
            return;
        }
        if (!key.startsWith(namespace + ".")) {
            problems.add(new LocalizationProblem(path, "key 必须以所在命名空间编码加点号开头：" + namespace + "."));
            return;
        }
        SourceEntry entry = entry(value);
        String problem = entryProblem(entry);
        if (problem != null) {
            problems.add(new LocalizationProblem(path, problem));
            return;
        }
        entries.put(key, entry);
    }

    /** 文案可以是字符串，或带 text 与可选 description 的对象；其他形态返回 null。 */
    static SourceEntry entry(JsonNode value) {
        if (value != null && value.isString()) {
            return new SourceEntry(value.asString(), null);
        }
        if (value == null || !value.isObject()) {
            return null;
        }
        JsonNode description = value.get("description");
        if (description != null && !description.isNull() && !description.isString()) {
            return null;
        }
        return new SourceEntry(JsonFiles.text(value, "text"), description == null || description.isNull() ? null
            : description.asString());
    }

    /** 条目内容问题；没有问题返回 null。 */
    static String entryProblem(SourceEntry entry) {
        if (entry == null || entry.text() == null) {
            return "文案必须是字符串，或带 text 与可选 description 的对象";
        }
        if (entry.text().isBlank()) {
            return "英文基准不能为空";
        }
        if (entry.text().length() > LocalizationCodes.MAX_TEXT_LENGTH) {
            return "英文基准不能超过 " + LocalizationCodes.MAX_TEXT_LENGTH + " 个字符";
        }
        if (entry.description() != null && entry.description().length() > LocalizationCodes.MAX_DESCRIPTION_LENGTH) {
            return "说明不能超过 " + LocalizationCodes.MAX_DESCRIPTION_LENGTH + " 个字符";
        }
        return null;
    }
}

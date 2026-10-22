package com.paytech.platform.support.dataquery.template;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板中的取值路径：参数名、循环变量或 __ 系统参数，后跟可选的字段名。 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TemplatePath {

    private static final Pattern PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** 路径各段，至少一段。 */
    private final List<String> segments;

    /**
     * 解析路径文本。
     *
     * @param text 如 c.minAmount
     * @return 路径；文本不是合法路径时返回 null
     */
    public static TemplatePath parse(String text) {
        if (text == null || !PATH.matcher(text).matches()) {
            return null;
        }
        return new TemplatePath(List.of(text.split("\\.")));
    }

    /**
     * 判断文本是否为单段名称（参数名、循环变量名、片段名）。
     *
     * @param text 文本
     * @return true 表示合法名称
     */
    public static boolean isName(String text) {
        return text != null && NAME.matcher(text).matches();
    }

    /**
     * 首段名称。
     *
     * @return 参数名、循环变量名或系统参数名
     */
    public String root() {
        return segments.getFirst();
    }

    /**
     * 首段之后的字段名。
     *
     * @return 字段名，可能为空
     */
    public List<String> fields() {
        return segments.subList(1, segments.size());
    }

    /**
     * 原始文本。
     *
     * @return 以点连接的路径
     */
    public String text() {
        return String.join(".", segments);
    }

    /**
     * 由各段直接构造，供内部合成系统参数路径使用。
     *
     * @param segments 各段
     * @return 路径
     */
    static TemplatePath of(String... segments) {
        return new TemplatePath(Arrays.asList(segments));
    }
}

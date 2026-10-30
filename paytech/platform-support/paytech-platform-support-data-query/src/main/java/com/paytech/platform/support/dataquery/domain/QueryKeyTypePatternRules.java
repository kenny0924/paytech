package com.paytech.platform.support.dataquery.domain;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 键类型入口正则的语法子集（B13）：只允许 Java 与 JavaScript 行为一致的写法，页面与服务端用同一正则整串匹配。
 * <li>先要求 Java 可编译，再拒绝反向引用、命名引用、前后断言、内联标志、原子组、所有格量词、Unicode 属性、
 * 字符类交集与嵌套，以及 \A \Z \z \G \Q \E \h \v \R \X \e \a \0 等只在一侧有意义的转义。</li>
 */
public final class QueryKeyTypePatternRules {

    private static final String UNSUPPORTED_ESCAPES = "123456789kpPAZzGQEhHvVRXea0";

    private QueryKeyTypePatternRules() {
    }

    /**
     * 检查正则。
     *
     * @param pattern 正则，不含 ^$
     * @return 不合法时的中文原因；合法为 null
     */
    public static String violation(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException failure) {
            return "正则无法编译：" + failure.getDescription();
        }
        return scan(pattern);
    }

    /** 逐字符扫描，区分转义、字符类与分组。 */
    static String scan(String pattern) {
        boolean inClass = false;
        for (int index = 0; index < pattern.length(); index++) {
            char current = pattern.charAt(index);
            char next = index + 1 < pattern.length() ? pattern.charAt(index + 1) : 0;
            if (current == '\\') {
                if (UNSUPPORTED_ESCAPES.indexOf(next) >= 0) {
                    return "正则不能使用 \\" + next + "（Java 与 JavaScript 含义不同或不支持）";
                }
                index++;
                continue;
            }
            if (inClass) {
                if (current == ']') {
                    inClass = false;
                } else if (current == '[' || current == '&' && next == '&') {
                    return "正则的字符类不能嵌套或使用 && 交集";
                }
                continue;
            }
            if (current == '[') {
                inClass = true;
                index = next == '^' ? index + 1 : index;
                index = index + 1 < pattern.length() && pattern.charAt(index + 1) == ']' ? index + 1 : index;
                continue;
            }
            String violation = current == '(' && next == '?' ? group(pattern, index + 2) : quantifier(current, next);
            if (violation != null) {
                return violation;
            }
        }
        return null;
    }

    /** “(?” 之后只允许非捕获组 (?: 与命名组 (?<name>。 */
    static String group(String pattern, int index) {
        char kind = index < pattern.length() ? pattern.charAt(index) : 0;
        char after = index + 1 < pattern.length() ? pattern.charAt(index + 1) : 0;
        if (kind == ':' || kind == '<' && Character.isLetter(after)) {
            return null;
        }
        if (kind == '=' || kind == '!' || kind == '<') {
            return "正则不能使用前后断言";
        }
        if (kind == '>') {
            return "正则不能使用原子组";
        }
        return "正则不能使用内联标志";
    }

    /** 量词之后紧跟 + 为 Java 所有格量词，JavaScript 不支持。 */
    static String quantifier(char current, char next) {
        boolean quantifier = current == '*' || current == '+' || current == '?' || current == '}';
        return quantifier && next == '+' ? "正则不能使用所有格量词" : null;
    }
}

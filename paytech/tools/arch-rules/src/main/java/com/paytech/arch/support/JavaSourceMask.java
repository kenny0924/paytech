package com.paytech.arch.support;

/**
 * 把 Java 源码中的注释、字符串、字符与文本块替换为空格（保留换行与偏移），用于在代码结构上做文本规则。
 *
 * @author Claude
 */
public final class JavaSourceMask {

    private JavaSourceMask() {
    }

    /**
     * 返回与原文等长的掩码文本。
     *
     * @param source Java 源码
     * @return 只保留代码字符的文本
     */
    public static String mask(String source) {
        char[] result = source.toCharArray();
        int index = 0;
        while (index < result.length) {
            if (startsWith(source, index, "//")) {
                index = blank(result, index, source.indexOf('\n', index) < 0 ? source.length()
                    : source.indexOf('\n', index));
            } else if (startsWith(source, index, "/*")) {
                int end = source.indexOf("*/", index + 2);
                index = blank(result, index, end < 0 ? source.length() : end + 2);
            } else if (startsWith(source, index, "\"\"\"")) {
                int end = source.indexOf("\"\"\"", index + 3);
                index = blank(result, index, end < 0 ? source.length() : end + 3);
            } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
                index = blank(result, index, literalEnd(source, index));
            } else {
                index++;
            }
        }
        return new String(result);
    }

    private static int literalEnd(String source, int start) {
        char quote = source.charAt(start);
        int index = start + 1;
        while (index < source.length() && source.charAt(index) != quote && source.charAt(index) != '\n') {
            index += source.charAt(index) == '\\' ? 2 : 1;
        }
        return Math.min(index + 1, source.length());
    }

    private static int blank(char[] result, int start, int end) {
        for (int index = start; index < end; index++) {
            if (result[index] != '\n') {
                result[index] = ' ';
            }
        }
        return end;
    }

    private static boolean startsWith(String source, int index, String prefix) {
        return source.startsWith(prefix, index);
    }
}

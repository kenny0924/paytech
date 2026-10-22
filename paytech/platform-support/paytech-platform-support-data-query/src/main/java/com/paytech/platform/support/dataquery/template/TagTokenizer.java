package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** 把标签参数或条件表达式原文切成词法单元，位置换算回模板正文。 */
final class TagTokenizer {

    private static final List<String> SYMBOLS = List.of("==", "!=", ">=", "<=", "&&", "||", ">", "<", "!", "(", ")",
        ",", "=");

    private final String text;

    private final SourcePosition origin;

    private final List<TagToken> tokens = new ArrayList<>();

    private int index;

    /**
     * 以原文及其首字符位置构造。
     *
     * @param text 标签参数原文
     * @param origin 原文首字符在模板中的位置
     */
    TagTokenizer(String text, SourcePosition origin) {
        this.text = text;
        this.origin = origin;
    }

    /**
     * 切分全部原文。
     *
     * @return 词法单元
     * @throws TemplateCheckException 出现不支持的字符或字符串未闭合
     */
    List<TagToken> tokenize() {
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
            } else if (current == '\'' || current == '"') {
                tokens.add(readString(current));
            } else if (Character.isDigit(current) || (current == '-' && nextIsDigit())) {
                tokens.add(readWhile(TagToken.Kind.NUMBER, index + 1, TagTokenizer::isNumberPart));
            } else if (Character.isLetter(current) || current == '_') {
                tokens.add(readWhile(TagToken.Kind.WORD, index + 1, TagTokenizer::isWordPart));
            } else {
                tokens.add(readSymbol());
            }
        }
        return tokens;
    }

    /**
     * 当前下标之后是否为数字，用于识别负数。
     *
     * @return true 表示下一个字符是数字
     */
    private boolean nextIsDigit() {
        return index + 1 < text.length() && Character.isDigit(text.charAt(index + 1));
    }

    /**
     * 读取连续的同类字符。
     *
     * @param kind 种类
     * @param from 首字符之后的下标
     * @param part 判断后续字符是否属于本单元
     * @return 词法单元
     */
    private TagToken readWhile(TagToken.Kind kind, int from, IntPredicate part) {
        int end = from;
        while (end < text.length() && part.test(text.charAt(end))) {
            end++;
        }
        TagToken token = new TagToken(kind, text.substring(index, end), positionAt(index));
        index = end;
        return token;
    }

    /**
     * 读取引号字符串，支持 \' \" \\ 转义。
     *
     * @param quote 开始的引号字符
     * @return 字符串单元
     */
    private TagToken readString(char quote) {
        SourcePosition start = positionAt(index);
        StringBuilder value = new StringBuilder();
        int cursor = index + 1;
        while (cursor < text.length() && text.charAt(cursor) != quote) {
            char current = text.charAt(cursor);
            if (current == '\\' && cursor + 1 < text.length()) {
                cursor++;
                current = text.charAt(cursor);
            }
            value.append(current);
            cursor++;
        }
        if (cursor >= text.length()) {
            throw new TemplateCheckException(start, "字符串缺少结尾引号 " + quote);
        }
        index = cursor + 1;
        return new TagToken(TagToken.Kind.STRING, value.toString(), start);
    }

    /**
     * 读取运算符或标点，长符号优先。
     *
     * @return 符号单元
     */
    private TagToken readSymbol() {
        for (String symbol : SYMBOLS) {
            if (text.startsWith(symbol, index)) {
                TagToken token = new TagToken(TagToken.Kind.SYMBOL, symbol, positionAt(index));
                index += symbol.length();
                return token;
            }
        }
        throw new TemplateCheckException(positionAt(index), "不支持的字符：" + text.charAt(index));
    }

    /**
     * 原文下标对应的模板位置。
     *
     * @param offset 原文下标
     * @return 模板位置
     */
    private SourcePosition positionAt(int offset) {
        return origin.advance(text, 0, offset);
    }

    /**
     * 名称或路径的后续字符。
     *
     * @param value 字符
     * @return true 表示字母、数字、下划线或点
     */
    private static boolean isWordPart(int value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '.';
    }

    /**
     * 数字的后续字符。
     *
     * @param value 字符
     * @return true 表示数字或小数点
     */
    private static boolean isNumberPart(int value) {
        return Character.isDigit(value) || value == '.';
    }
}

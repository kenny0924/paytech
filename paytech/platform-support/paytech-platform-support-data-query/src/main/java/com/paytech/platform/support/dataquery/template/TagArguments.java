package com.paytech.platform.support.dataquery.template;

import java.util.List;

/** 标签参数词法单元的顺序读取器，错误定位到具体单元。 */
final class TagArguments {

    private final List<TagToken> tokens;

    private final SourcePosition end;

    private final String tagName;

    private int index;

    /**
     * 切分并包装标签参数原文。
     *
     * @param tagName 标签名，用于报错，如 #each
     * @param text 参数原文
     * @param origin 原文首字符位置
     */
    TagArguments(String tagName, String text, SourcePosition origin) {
        this.tagName = tagName;
        this.tokens = new TagTokenizer(text, origin).tokenize();
        this.end = origin.advance(text, 0, text.length());
    }

    /**
     * 是否已读完。
     *
     * @return true 表示没有剩余单元
     */
    boolean atEnd() {
        return index >= tokens.size();
    }

    /**
     * 查看下一个单元。
     *
     * @return 单元；已读完时为 null
     */
    TagToken peek() {
        return atEnd() ? null : tokens.get(index);
    }

    /**
     * 读取下一个单元。
     *
     * @param expected 缺失时报错用的期望描述
     * @return 单元
     */
    TagToken next(String expected) {
        if (atEnd()) {
            throw new TemplateCheckException(end, "{{" + tagName + "}} 缺少" + expected);
        }
        return tokens.get(index++);
    }

    /**
     * 读取指定种类的单元。
     *
     * @param kind 期望种类
     * @param expected 期望描述
     * @return 单元
     */
    TagToken expect(TagToken.Kind kind, String expected) {
        TagToken token = next(expected);
        if (token.getKind() != kind) {
            throw unexpected(token, expected);
        }
        return token;
    }

    /**
     * 读取指定符号。
     *
     * @param symbol 符号
     */
    void expectSymbol(String symbol) {
        TagToken token = next(" " + symbol);
        if (!token.isSymbol(symbol)) {
            throw unexpected(token, " " + symbol);
        }
    }

    /**
     * 要求已读完。
     */
    void expectEnd() {
        if (!atEnd()) {
            throw new TemplateCheckException(peek().getPosition(),
                "{{" + tagName + "}} 多余的内容：" + peek().display());
        }
    }

    /**
     * 构造“遇到意外单元”的错误。
     *
     * @param token 意外单元
     * @param expected 期望描述
     * @return 异常
     */
    TemplateCheckException unexpected(TagToken token, String expected) {
        return new TemplateCheckException(token.getPosition(),
            "{{" + tagName + "}} 期望" + expected + "，遇到 " + token.display());
    }
}

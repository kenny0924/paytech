package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标签参数与条件表达式中的词法单元。 */
@Getter
@AllArgsConstructor
final class TagToken {

    /** 词法单元种类；只在解析器内部使用，不持久化。 */
    enum Kind {

        /** 名称或路径，如 c.minAmount、as、true。 */
        WORD,

        /** 引号字符串，text 为去掉引号并还原转义后的值。 */
        STRING,

        /** 十进制数字。 */
        NUMBER,

        /** 运算符或标点：== != &gt;= &lt;= &gt; &lt; ! &amp;&amp; || ( ) , =。 */
        SYMBOL
    }

    /** 种类。 */
    private final Kind kind;

    /** 文本或字符串值。 */
    private final String text;

    /** 起始位置。 */
    private final SourcePosition position;

    /**
     * 是否为指定符号。
     *
     * @param symbol 符号文本
     * @return true 表示匹配
     */
    boolean isSymbol(String symbol) {
        return kind == Kind.SYMBOL && text.equals(symbol);
    }

    /**
     * 是否为指定单词。
     *
     * @param word 单词
     * @return true 表示匹配
     */
    boolean isWord(String word) {
        return kind == Kind.WORD && text.equals(word);
    }

    /**
     * 报错时的展示文本。
     *
     * @return 带引号的字符串或原文
     */
    String display() {
        return kind == Kind.STRING ? "\"" + text + "\"" : text;
    }
}

package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板词法扫描：切出文本、#{…}、@{…}、{{…}}，丢弃 {{!-- --}} 注释并还原转义。
 *
 * <p>标签内的引号字符串可以包含 }}；#{ 与 @{ 必须在同一行内闭合。</p>
 */
final class TemplateScanner {

    private static final String COMMENT_OPEN = "{{!--";

    private static final String COMMENT_CLOSE = "--}}";

    private final String body;

    private final List<TemplateToken> tokens = new ArrayList<>();

    private final StringBuilder pendingText = new StringBuilder();

    private SourcePosition pendingStart;

    private int index;

    private SourcePosition position = new SourcePosition(1, 1);

    /**
     * 以模板原文构造。
     *
     * @param body 模板原文
     */
    TemplateScanner(String body) {
        this.body = body;
    }

    /**
     * 扫描全部正文。
     *
     * @return 词法单元，按出现顺序
     * @throws TemplateCheckException 注释、标签或占位未闭合
     */
    List<TemplateToken> scan() {
        while (index < body.length()) {
            scanNext();
        }
        flushText();
        return tokens;
    }

    /** 识别当前位置开始的一个词法单元或一个文本字符。 */
    private void scanNext() {
        String escaped = escapedMarker();
        if (escaped != null) {
            appendText(escaped, 3);
        } else if (body.startsWith(COMMENT_OPEN, index)) {
            flushText();
            skipComment();
        } else if (body.startsWith("{{", index)) {
            flushText();
            tokens.add(readTag());
        } else if (body.startsWith("#{", index)) {
            flushText();
            tokens.add(readPlaceholder(TemplateToken.Kind.VALUE));
        } else if (body.startsWith("@{", index)) {
            flushText();
            tokens.add(readPlaceholder(TemplateToken.Kind.FRAGMENT));
        } else {
            appendText(String.valueOf(body.charAt(index)), 1);
        }
    }

    /**
     * 当前位置是否为转义序列 \#{、\@{、\{{。
     *
     * @return 还原后的文本；不是转义时返回 null
     */
    private String escapedMarker() {
        if (body.charAt(index) != '\\') {
            return null;
        }
        for (String marker : List.of("#{", "@{", "{{")) {
            if (body.startsWith(marker, index + 1)) {
                return marker;
            }
        }
        return null;
    }

    /**
     * 追加输出文本并前进。
     *
     * @param text 输出文本
     * @param consumed 消耗的原文字符数
     */
    private void appendText(String text, int consumed) {
        if (pendingText.isEmpty()) {
            pendingStart = position;
        }
        pendingText.append(text);
        advance(consumed);
    }

    /** 把累计的文本作为一个文本单元输出。 */
    private void flushText() {
        if (pendingText.isEmpty()) {
            return;
        }
        tokens.add(new TemplateToken(TemplateToken.Kind.TEXT, pendingStart, pendingText.toString(), pendingStart));
        pendingText.setLength(0);
    }

    /** 跳过注释；未闭合时报错。 */
    private void skipComment() {
        SourcePosition start = position;
        int end = body.indexOf(COMMENT_CLOSE, index + COMMENT_OPEN.length());
        if (end < 0) {
            throw new TemplateCheckException(start, "注释 {{!-- 缺少结尾 --}}");
        }
        advance(end + COMMENT_CLOSE.length() - index);
    }

    /**
     * 读取 {{…}} 标签；引号内的 }} 不结束标签。
     *
     * @return 标签单元
     */
    private TemplateToken readTag() {
        SourcePosition start = position;
        advance(2);
        SourcePosition contentStart = position;
        int contentBegin = index;
        int end = findTagEnd(start);
        String content = body.substring(contentBegin, end);
        advance(end + 2 - index);
        return new TemplateToken(TemplateToken.Kind.TAG, start, content, contentStart);
    }

    /**
     * 从当前位置找标签结尾 }}，跳过引号字符串。
     *
     * @param start 标签起始位置，用于报错
     * @return }} 的下标
     */
    private int findTagEnd(SourcePosition start) {
        char quote = 0;
        for (int cursor = index; cursor < body.length(); cursor++) {
            char current = body.charAt(cursor);
            if (quote != 0) {
                cursor += current == '\\' ? 1 : 0;
                quote = current == quote ? 0 : quote;
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (body.startsWith("}}", cursor)) {
                return cursor;
            }
        }
        throw new TemplateCheckException(start, "标签缺少结尾 }}");
    }

    /**
     * 读取 #{…} 或 @{…}；必须在同一行内闭合。
     *
     * @param kind VALUE 或 FRAGMENT
     * @return 占位单元，content 已去掉首尾空白
     */
    private TemplateToken readPlaceholder(TemplateToken.Kind kind) {
        SourcePosition start = position;
        String opening = body.substring(index, index + 2);
        advance(2);
        SourcePosition contentStart = position;
        int end = body.indexOf('}', index);
        int lineEnd = body.indexOf('\n', index);
        if (end < 0 || (lineEnd >= 0 && lineEnd < end)) {
            throw new TemplateCheckException(start, opening + " 缺少结尾 }");
        }
        String content = body.substring(index, end).strip();
        advance(end + 1 - index);
        return new TemplateToken(kind, start, content, contentStart);
    }

    /**
     * 前进若干字符并更新行列。
     *
     * @param count 字符数
     */
    private void advance(int count) {
        position = position.advance(body, index, index + count);
        index += count;
    }
}

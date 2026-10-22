package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 渲染中的输出缓冲：正文、绑定值与来源标记；join 的每段先写入子缓冲再合并。 */
final class RenderOutput {

    private final StringBuilder text = new StringBuilder();

    private final List<Object> bindings = new ArrayList<>();

    private final List<SourceMark> marks = new ArrayList<>();

    /**
     * 追加逐字来自模板的文本。
     *
     * @param value 文本
     * @param position 文本首字符的模板位置
     */
    void appendLiteral(String value, SourcePosition position) {
        append(value, position, true);
    }

    /**
     * 追加整段指向同一模板位置的文本（占位符、片段、分隔符）。
     *
     * @param value 文本
     * @param position 来源节点的位置
     */
    void appendFixed(String value, SourcePosition position) {
        append(value, position, false);
    }

    /**
     * 追加文本并记录来源。
     *
     * @param value 文本
     * @param position 来源位置
     * @param literal 是否逐字来自模板
     */
    private void append(String value, SourcePosition position, boolean literal) {
        if (value.isEmpty()) {
            return;
        }
        marks.add(new SourceMark(text.length(), position, literal));
        text.append(value);
    }

    /**
     * 绑定值列表，供值绑定器追加。
     *
     * @return 可修改的绑定值列表
     */
    List<Object> bindings() {
        return bindings;
    }

    /**
     * 当前输出是否全为空白。
     *
     * @return true 表示没有可见内容
     */
    boolean isBlank() {
        return text.toString().isBlank();
    }

    /**
     * 合并子缓冲，去掉其首尾空白；标记随之平移，绑定值按顺序追加。
     *
     * @param segment 子缓冲
     */
    void appendTrimmed(RenderOutput segment) {
        String value = segment.text.toString();
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        int base = text.length() - start;
        for (SourceMark mark : segment.marks) {
            int offset = mark.getOutputOffset();
            if (offset < end) {
                SourcePosition position = offset >= start || !mark.isLiteral() ? mark.getPosition()
                    : mark.getPosition().advance(value, offset, start);
                marks.add(new SourceMark(base + Math.max(offset, start), position, mark.isLiteral()));
            }
        }
        text.append(value, start, end);
        bindings.addAll(segment.bindings);
    }

    /**
     * 生成不可变的渲染结果。
     *
     * @return 渲染结果
     */
    RenderedQuery toRenderedQuery() {
        return new RenderedQuery(text.toString(), Collections.unmodifiableList(new ArrayList<>(bindings)),
            List.copyOf(marks));
    }
}

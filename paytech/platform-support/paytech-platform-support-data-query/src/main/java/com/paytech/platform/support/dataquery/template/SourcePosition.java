package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板正文中的位置，行号与列号都从 1 开始。 */
@Getter
@AllArgsConstructor
public final class SourcePosition {

    /** 行号，从 1 开始。 */
    private final int line;

    /** 列号，从 1 开始，按字符计数。 */
    private final int column;

    /**
     * 从本位置起越过一段文本后的位置。
     *
     * @param text 文本
     * @param from 起始下标（含）
     * @param to 结束下标（不含）
     * @return 新位置
     */
    public SourcePosition advance(String text, int from, int to) {
        int currentLine = line;
        int currentColumn = column;
        for (int index = from; index < to; index++) {
            if (text.charAt(index) == '\n') {
                currentLine++;
                currentColumn = 1;
            } else {
                currentColumn++;
            }
        }
        return new SourcePosition(currentLine, currentColumn);
    }

    /**
     * 中文描述，用于错误信息前缀。
     *
     * @return 如“第 3 行第 5 列”
     */
    public String describe() {
        return "第 " + line + " 行第 " + column + " 列";
    }
}

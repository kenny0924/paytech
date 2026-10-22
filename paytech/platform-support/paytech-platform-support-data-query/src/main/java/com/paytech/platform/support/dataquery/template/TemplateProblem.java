package com.paytech.platform.support.dataquery.template;

import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条模板校验问题；编辑器按行列定位，参数声明问题按 field 定位。 */
@Getter
@AllArgsConstructor
public final class TemplateProblem {

    /** 级别。 */
    private final TemplateProblemLevel level;

    /** 正文行号；与正文位置无关时为 null。 */
    private final Integer line;

    /** 正文列号；与正文位置无关时为 null。 */
    private final Integer column;

    /** 参数声明中的字段路径，如 parameters[2].choices；与声明无关时为 null。 */
    private final String field;

    /** 中文原因。 */
    private final String message;

    /**
     * 正文中某位置的错误。
     *
     * @param position 位置；未知时为 null
     * @param message 原因
     * @return 问题
     */
    public static TemplateProblem error(SourcePosition position, String message) {
        return at(TemplateProblemLevel.ERROR, position, message);
    }

    /**
     * 正文中某位置的问题。
     *
     * @param level 级别
     * @param position 位置；未知时为 null
     * @param message 原因
     * @return 问题
     */
    public static TemplateProblem at(TemplateProblemLevel level, SourcePosition position, String message) {
        if (position == null) {
            return new TemplateProblem(level, null, null, null, message);
        }
        return new TemplateProblem(level, position.getLine(), position.getColumn(), null, message);
    }

    /**
     * 参数声明字段上的问题。
     *
     * @param level 级别
     * @param field 字段路径
     * @param message 原因
     * @return 问题
     */
    public static TemplateProblem field(TemplateProblemLevel level, String field, String message) {
        return new TemplateProblem(level, null, null, field, message);
    }

    /**
     * 带定位前缀的中文描述，用于异常消息。
     *
     * @return 如“第 3 行第 5 列：标签不成对”
     */
    public String describe() {
        if (line != null) {
            return "第 " + line + " 行第 " + column + " 列：" + message;
        }
        if (field != null) {
            return field + "：" + message;
        }
        return message;
    }
}

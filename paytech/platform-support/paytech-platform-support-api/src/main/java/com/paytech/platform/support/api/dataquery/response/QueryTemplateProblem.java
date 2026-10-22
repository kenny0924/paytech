package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 一条模板校验问题；编辑器按行列定位，参数声明与约束问题按 field 定位。 */
@Getter
@Setter
public class QueryTemplateProblem {

    /** 级别：ERROR 阻止保存；WARNING 只提示（如声明了但未使用的参数、组合较多已按覆盖集校验）。 */
    private String level;

    /** 正文行号，从 1 开始；与正文位置无关时为 null。 */
    private Integer line;

    /** 正文列号，从 1 开始；与正文位置无关时为 null。 */
    private Integer column;

    /** 字段路径，如 parameters[2].choices、constraints.maxRows、dataSourceId；与字段无关时为 null。 */
    private String field;

    /** 中文原因。 */
    private String message;
}

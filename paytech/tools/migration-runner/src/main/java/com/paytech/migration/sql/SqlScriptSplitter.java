package com.paytech.migration.sql;

import com.paytech.migration.model.SqlStatement;

import java.util.List;

/**
 * 把一份 MySQL 8.4 评审脚本切分为逐条执行的语句。
 *
 * <p>
 * 只在顶层分号处切分；单引号与双引号字符串（含反斜杠转义与重复引号）、反引号标识符、{@code --}（其后须为空白或行尾）与
 * {@code #} 行注释、块注释内的分号不作为分隔符。只含注释或空白的片段不产生语句。不支持 {@code DELIMITER}：它会被
 * 当作普通文本留在语句中，由调用方按语句类型拒绝。
 *
 * @author Claude
 */
public interface SqlScriptSplitter {

    /**
     * 切分脚本。
     *
     * @param script 脚本全文（换行可为 LF 或 CRLF）
     * @return 按出现顺序排列的语句
     * @throws SqlScriptSyntaxException 字符串、标识符或块注释未闭合
     */
    List<SqlStatement> split(String script);
}

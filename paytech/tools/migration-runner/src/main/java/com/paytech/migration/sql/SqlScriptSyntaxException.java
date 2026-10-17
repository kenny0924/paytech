package com.paytech.migration.sql;

/**
 * 脚本无法切分：字符串、反引号标识符或块注释未闭合。
 *
 * @author Claude
 */
public class SqlScriptSyntaxException extends RuntimeException {

    /**
     * 未闭合片段的起始行号。
     */
    private final int line;

    /**
     * 构造切分失败。
     *
     * @param line 未闭合片段的起始行号
     * @param message 中文说明
     */
    public SqlScriptSyntaxException(int line, String message) {
        super("第 " + line + " 行: " + message);
        this.line = line;
    }

    /**
     * 未闭合片段的起始行号。
     *
     * @return 行号，从 1 起
     */
    public int getLine() {
        return line;
    }
}

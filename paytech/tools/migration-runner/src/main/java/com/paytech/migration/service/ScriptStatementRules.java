package com.paytech.migration.service;

import com.paytech.migration.model.SqlStatement;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 评审脚本的语句类型规则：迁移脚本与初始化数据共用的禁止语句，以及初始化数据的 DDL 识别。
 *
 * @author Claude
 */
final class ScriptStatementRules {

    private static final Pattern FORBIDDEN = Pattern.compile(
        "^(?:DELIMITER\\b|USE\\b|CALL\\b"
            + "|(?:CREATE|ALTER|DROP)\\s+(?:OR\\s+REPLACE\\s+)?(?:DEFINER\\s*=\\s*\\S+\\s+)?"
            + "(?:PROCEDURE|FUNCTION|TRIGGER|EVENT)\\b)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DDL = Pattern.compile("^(?:CREATE|ALTER|DROP|TRUNCATE|RENAME)\\b",
        Pattern.CASE_INSENSITIVE);

    private ScriptStatementRules() {
    }

    /**
     * 找出第一条禁止语句。
     *
     * @param statements 语句
     * @return 第一条 DELIMITER、USE、CALL 或存储程序语句
     */
    static Optional<SqlStatement> firstForbidden(List<SqlStatement> statements) {
        return statements.stream().filter(statement -> FORBIDDEN.matcher(statement.code()).find()).findFirst();
    }

    /**
     * 找出第一条 DDL 语句。
     *
     * @param statements 语句
     * @return 第一条 CREATE、ALTER、DROP、TRUNCATE 或 RENAME 语句
     */
    static Optional<SqlStatement> firstDdl(List<SqlStatement> statements) {
        return statements.stream().filter(statement -> DDL.matcher(statement.code()).find()).findFirst();
    }

    /**
     * 语句的短摘要，用于说明。
     *
     * @param statement 语句
     * @return 行号与前 60 个字符
     */
    static String describe(SqlStatement statement) {
        String code = statement.code();
        return "第 " + statement.line() + " 行 \"" + (code.length() > 60 ? code.substring(0, 60) + "…" : code) + "\"";
    }
}

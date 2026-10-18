package com.paytech.migration.auto;

import com.paytech.migration.model.SqlStatement;
import com.paytech.migration.sql.SqlScriptSplitter;
import com.paytech.migration.sql.SqlScriptSplitterImpl;
import com.paytech.migration.sql.SqlScriptSyntaxException;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 按顶层分号切分：字符串、注释、反引号内的分号不切分，多行语句保留起始行号。
 *
 * @see SqlScriptSplitter#split(String)
 * @author Claude
 */
class SqlScriptSplitterAutoTest {

    private final SqlScriptSplitter splitter = new SqlScriptSplitterImpl();

    @Test
    void test_semicolonInsideStrings() {
        List<SqlStatement> statements = splitter.split(
            "INSERT INTO t VALUES ('a;b', \"c;d\", 'it''s;', 'back\\';slash');SELECT 2");

        Assertions.assertEquals(List.of("INSERT INTO t VALUES ('a;b', \"c;d\", 'it''s;', 'back\\';slash')", "SELECT 2"),
            codes(statements));
    }

    @Test
    void test_semicolonInsideComments() {
        List<SqlStatement> statements = splitter.split(
            "-- header; not a statement\nSELECT 1; # hash; comment\n/* block; comment */ SELECT /* inline; */ 2;");

        Assertions.assertEquals(List.of("SELECT 1", "SELECT 2"), codes(statements));
        Assertions.assertEquals(List.of(2, 3), statements.stream().map(SqlStatement::line).toList());
        Assertions.assertEquals("-- header; not a statement\nSELECT 1", statements.get(0).sql());
        Assertions.assertEquals("# hash; comment\n/* block; comment */ SELECT /* inline; */ 2", statements.get(1).sql());
    }

    @Test
    void test_backtickIdentifier() {
        List<SqlStatement> statements = splitter.split("CREATE TABLE `a;b` (`c``;d` INT);SELECT 1;");

        Assertions.assertEquals(List.of("CREATE TABLE `a;b` (`c``;d` INT)", "SELECT 1"), codes(statements));
    }

    @Test
    void test_multiLineStatement() {
        List<SqlStatement> statements = splitter.split(
            "SET NAMES utf8mb4;\r\n\r\nCREATE TABLE t (\r\n  id BIGINT NOT NULL,\r\n  PRIMARY KEY (id)\r\n);\r\n"
                + "INSERT INTO t VALUES (1)");

        Assertions.assertEquals(3, statements.size());
        Assertions.assertEquals(List.of(1, 2, 3), statements.stream().map(SqlStatement::index).toList());
        Assertions.assertEquals(List.of(1, 3, 7), statements.stream().map(SqlStatement::line).toList());
        Assertions.assertEquals("CREATE TABLE t (\n  id BIGINT NOT NULL,\n  PRIMARY KEY (id)\n)", statements.get(1).sql());
        Assertions.assertEquals("CREATE TABLE t ( id BIGINT NOT NULL, PRIMARY KEY (id) )", statements.get(1).code());
        Assertions.assertEquals("INSERT INTO t VALUES (1)", statements.get(2).sql());
    }

    @Test
    void test_doubleDashWithoutWhitespaceIsNotComment() {
        Assertions.assertEquals(List.of("SELECT 1--1", "SELECT 2"), codes(splitter.split("SELECT 1--1;SELECT 2;")));
        Assertions.assertEquals(List.of("SELECT 1"), codes(splitter.split("SELECT 1 --")));
    }

    @Test
    void test_noStatement() {
        Assertions.assertEquals(List.of(), splitter.split("-- only; comments\n/* and; blocks */\n;;\n"));
    }

    @Test
    void test_unterminatedTokens() {
        SqlScriptSyntaxException string = Assertions.assertThrows(SqlScriptSyntaxException.class,
            () -> splitter.split("SELECT 1;\nSELECT 'open;\n"));
        Assertions.assertEquals(2, string.getLine());
        Assertions.assertTrue(string.getMessage().contains("单引号字符串未闭合"), string.getMessage());

        SqlScriptSyntaxException comment = Assertions.assertThrows(SqlScriptSyntaxException.class,
            () -> splitter.split("SELECT 1;\n\n/* open; comment"));
        Assertions.assertEquals(3, comment.getLine());
        Assertions.assertTrue(comment.getMessage().contains("块注释未闭合"), comment.getMessage());

        Assertions.assertThrows(SqlScriptSyntaxException.class, () -> splitter.split("SELECT `open"));
    }

    /**
     * 各语句去注释后的文本。
     *
     * @param statements 语句
     * @return code
     */
    private static List<String> codes(List<SqlStatement> statements) {
        return statements.stream().map(SqlStatement::code).toList();
    }
}

package com.paytech.migration.sql;

import com.paytech.migration.model.SqlStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * 单遍扫描的词法切分：维护当前所处的词法区域，只在顶层分号处结束一条语句。
 *
 * @author Claude
 */
public class SqlScriptSplitterImpl implements SqlScriptSplitter {

    @Override
    public List<SqlStatement> split(String script) {
        return new Scanner(script.replace("\r\n", "\n").replace('\r', '\n')).scan();
    }

    /**
     * 词法区域。
     */
    private enum Region {

        /**
         * 语句正文。
         */
        CODE,

        /**
         * 单引号字符串。
         */
        SINGLE_QUOTED,

        /**
         * 双引号字符串。
         */
        DOUBLE_QUOTED,

        /**
         * 反引号标识符。
         */
        BACKTICK,

        /**
         * 行注释。
         */
        LINE_COMMENT,

        /**
         * 块注释。
         */
        BLOCK_COMMENT
    }

    /**
     * 一次切分的可变状态；每次 {@link #split(String)} 新建，不跨调用共享。
     */
    private static final class Scanner {

        private final String text;

        private final List<SqlStatement> statements = new ArrayList<>();

        private final StringBuilder sql = new StringBuilder();

        private final StringBuilder code = new StringBuilder();

        private Region region = Region.CODE;

        private int position;

        private int line = 1;

        private int statementLine;

        private int regionLine;

        /**
         * 绑定待切分文本。
         *
         * @param text 已把换行归一化为 LF 的脚本
         */
        Scanner(String text) {
            this.text = text;
        }

        /**
         * 扫描全文并在结尾检查未闭合区域。
         *
         * @return 语句
         */
        List<SqlStatement> scan() {
            while (position < text.length()) {
                char current = text.charAt(position);
                switch (region) {
                    case CODE -> scanCode(current);
                    case SINGLE_QUOTED -> scanQuoted(current, '\'');
                    case DOUBLE_QUOTED -> scanQuoted(current, '"');
                    case BACKTICK -> scanBacktick(current);
                    case LINE_COMMENT -> scanLineComment(current);
                    case BLOCK_COMMENT -> scanBlockComment(current);
                }
            }
            if (region != Region.CODE && region != Region.LINE_COMMENT) {
                throw new SqlScriptSyntaxException(regionLine, describe(region) + "未闭合");
            }
            finishStatement();
            return List.copyOf(statements);
        }

        /**
         * 处理语句正文中的一个字符：识别分号、引号与注释起点。
         *
         * @param current 当前字符
         */
        private void scanCode(char current) {
            if (current == ';') {
                advance();
                finishStatement();
                return;
            }
            if (startsLineComment(current)) {
                enter(Region.LINE_COMMENT);
                return;
            }
            if (current == '/' && peek(1) == '*') {
                enter(Region.BLOCK_COMMENT);
                copy(2, false);
                return;
            }
            Region quoted = current == '\'' ? Region.SINGLE_QUOTED
                : current == '"' ? Region.DOUBLE_QUOTED : current == '`' ? Region.BACKTICK : null;
            if (quoted != null) {
                markStatementStart();
                enter(quoted);
            }
            if (!Character.isWhitespace(current)) {
                markStatementStart();
            }
            copy(1, true);
        }

        /**
         * 判断当前位置是否开始一个 MySQL 行注释：{@code #}，或 {@code --} 后紧跟空白、控制字符或文件结尾。
         *
         * @param current 当前字符
         * @return 是行注释起点返回 true
         */
        private boolean startsLineComment(char current) {
            if (current == '#') {
                return true;
            }
            if (current != '-' || peek(1) != '-') {
                return false;
            }
            char next = peek(2);
            return next == 0 || Character.isWhitespace(next) || Character.isISOControl(next);
        }

        /**
         * 处理字符串内的一个字符：反斜杠转义下一个字符，连续两个同种引号表示引号本身。
         *
         * @param current 当前字符
         * @param quote 引号字符
         */
        private void scanQuoted(char current, char quote) {
            if (current == '\\' && position + 1 < text.length()) {
                copy(2, true);
                return;
            }
            if (current == quote && peek(1) == quote) {
                copy(2, true);
                return;
            }
            if (current == quote) {
                region = Region.CODE;
            }
            copy(1, true);
        }

        /**
         * 处理反引号标识符内的一个字符：连续两个反引号表示反引号本身。
         *
         * @param current 当前字符
         */
        private void scanBacktick(char current) {
            if (current == '`' && peek(1) == '`') {
                copy(2, true);
                return;
            }
            if (current == '`') {
                region = Region.CODE;
            }
            copy(1, true);
        }

        /**
         * 处理行注释内的一个字符：换行结束注释，注释文本不进入 code。
         *
         * @param current 当前字符
         */
        private void scanLineComment(char current) {
            if (current == '\n') {
                region = Region.CODE;
                code.append(' ');
            }
            copy(1, false);
        }

        /**
         * 处理块注释内的一个字符：{@code *}{@code /} 结束注释。
         *
         * @param current 当前字符
         */
        private void scanBlockComment(char current) {
            if (current == '*' && peek(1) == '/') {
                copy(2, false);
                region = Region.CODE;
                code.append(' ');
                return;
            }
            copy(1, false);
        }

        /**
         * 进入新的词法区域并记录其起始行。
         *
         * @param next 新区域
         */
        private void enter(Region next) {
            region = next;
            regionLine = line;
        }

        /**
         * 第一次遇到有效字符时记录语句起始行。
         */
        private void markStatementStart() {
            if (statementLine == 0) {
                statementLine = line;
            }
        }

        /**
         * 复制若干字符到原文缓冲区，并按需复制到 code 缓冲区；同时维护行号。
         *
         * @param count 字符数
         * @param intoCode 是否同时进入 code
         */
        private void copy(int count, boolean intoCode) {
            int end = Math.min(position + count, text.length());
            for (int index = position; index < end; index++) {
                char character = text.charAt(index);
                sql.append(character);
                if (intoCode) {
                    code.append(character);
                }
                if (character == '\n') {
                    line++;
                }
            }
            position = end;
        }

        /**
         * 跳过当前字符（语句分隔符），不进入任何缓冲区。
         */
        private void advance() {
            position++;
        }

        /**
         * 读取当前位置之后第 offset 个字符。
         *
         * @param offset 偏移
         * @return 字符；越界返回 0
         */
        private char peek(int offset) {
            int index = position + offset;
            return index < text.length() ? text.charAt(index) : 0;
        }

        /**
         * 结束当前语句：有有效字符时产生一条语句，并重置缓冲区。
         */
        private void finishStatement() {
            String normalizedCode = code.toString().trim().replaceAll("\\s+", " ");
            if (!normalizedCode.isEmpty()) {
                statements.add(new SqlStatement(statements.size() + 1, statementLine, sql.toString().trim(),
                    normalizedCode));
            }
            sql.setLength(0);
            code.setLength(0);
            statementLine = 0;
        }

        /**
         * 区域的中文名称。
         *
         * @param unclosed 未闭合区域
         * @return 名称
         */
        private static String describe(Region unclosed) {
            return switch (unclosed) {
                case SINGLE_QUOTED -> "单引号字符串";
                case DOUBLE_QUOTED -> "双引号字符串";
                case BACKTICK -> "反引号标识符";
                default -> "块注释";
            };
        }
    }
}

package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 在字符串字面量与反引号标识符之外扫描 SQL 文本：找注释符号，并切出单词供关键字查找。
 *
 * <p>引号内支持反斜杠转义与重复引号；MySQL 的可执行注释 /*! … *&#47; 也属于注释，一并拒绝。</p>
 */
final class SqlTextScanner {

    private static final List<String> COMMENT_MARKERS = List.of("/*", "*/", "--", "#");

    private final String sql;

    private final List<String> words = new ArrayList<>();

    private final List<Integer> wordOffsets = new ArrayList<>();

    private int commentOffset = -1;

    /**
     * 扫描 SQL 文本。
     *
     * @param sql SQL
     */
    SqlTextScanner(String sql) {
        this.sql = sql;
        int index = 0;
        while (index < sql.length()) {
            index = scanFrom(index);
        }
    }

    /**
     * 从下标处识别一个字面量、单词、注释符号或单个字符。
     *
     * @param index 下标
     * @return 下一个下标
     */
    private int scanFrom(int index) {
        char current = sql.charAt(index);
        if (current == '\'' || current == '"' || current == '`') {
            return skipQuoted(index, current);
        }
        if (Character.isLetterOrDigit(current) || current == '_' || current == '$') {
            int end = index + 1;
            while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_'
                || sql.charAt(end) == '$')) {
                end++;
            }
            words.add(sql.substring(index, end).toUpperCase(Locale.ROOT));
            wordOffsets.add(index);
            return end;
        }
        if (commentOffset < 0 && COMMENT_MARKERS.stream().anyMatch(marker -> sql.startsWith(marker, index))) {
            commentOffset = index;
        }
        return index + 1;
    }

    /**
     * 跳过引号内容；未闭合时跳到末尾，由解析器报错。
     *
     * @param start 开始引号的下标
     * @param quote 引号字符
     * @return 结束引号之后的下标
     */
    private int skipQuoted(int start, char quote) {
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\\' && quote != '`') {
                index += 2;
            } else if (current == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                index += 2;
            } else if (current == quote) {
                return index + 1;
            } else {
                index++;
            }
        }
        return sql.length();
    }

    /**
     * 第一处注释符号的下标。
     *
     * @return 下标；没有注释时为 -1
     */
    int commentOffset() {
        return commentOffset;
    }

    /**
     * 第一处连续出现指定单词的位置，不区分大小写。
     *
     * @param sequence 单词序列，大写
     * @return 首个单词的下标；没有时为 -1
     */
    int findSequence(String... sequence) {
        for (int start = 0; start + sequence.length <= words.size(); start++) {
            if (matchesAt(start, sequence)) {
                return wordOffsets.get(start);
            }
        }
        return -1;
    }

    /**
     * 最后一处出现任一单词的位置，用于定位顶层 LIMIT。
     *
     * @param candidates 候选单词，大写
     * @return 下标；没有时为 -1
     */
    int findLast(String... candidates) {
        for (int index = words.size() - 1; index >= 0; index--) {
            if (List.of(candidates).contains(words.get(index))) {
                return wordOffsets.get(index);
            }
        }
        return -1;
    }

    /**
     * 单词序列是否从 start 开始出现。
     *
     * @param start 单词下标
     * @param sequence 单词序列
     * @return true 表示匹配
     */
    private boolean matchesAt(int start, String... sequence) {
        for (int offset = 0; offset < sequence.length; offset++) {
            if (!words.get(start + offset).equals(sequence[offset])) {
                return false;
            }
        }
        return true;
    }
}

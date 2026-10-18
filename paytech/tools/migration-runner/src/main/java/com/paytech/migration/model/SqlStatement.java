package com.paytech.migration.model;

/**
 * 脚本中按分号切分出的一条语句。
 *
 * @param index 在脚本中的序号，从 1 起
 * @param line 语句第一个有效字符所在行号，从 1 起
 * @param sql 原文（含语句内注释，首尾空白已去除，不含结尾分号）
 * @param code 去掉注释并把连续空白折叠为单个空格后的文本，用于识别语句类型；字符串与标识符内容保持原样
 * @author Claude
 */
public record SqlStatement(int index, int line, String sql, String code) {
}

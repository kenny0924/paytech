package com.paytech.migration.model;

/**
 * 一次执行失败。
 *
 * @param version 失败的版本号；与具体版本无关（如取锁超时、记录表读写失败）时为 null
 * @param script 失败的脚本或初始化数据文件名；与文件无关时为 null
 * @param statementIndex 失败语句序号，从 1 起；与语句无关时为 0
 * @param line 失败语句起始行号；与语句无关时为 0
 * @param sqlState 数据库 SQLState；非数据库错误为 null
 * @param message 中文说明与数据库错误摘要
 * @author Claude
 */
public record ExecutionFailure(Integer version, String script, int statementIndex, int line, String sqlState,
    String message) {
}

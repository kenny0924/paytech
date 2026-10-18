package com.paytech.migration.model;

/**
 * 本次运行成功执行的一个版本。
 *
 * @param version 版本号
 * @param script 文件名
 * @param executionMs 耗时毫秒
 * @author Claude
 */
public record ExecutedMigration(int version, String script, int executionMs) {
}

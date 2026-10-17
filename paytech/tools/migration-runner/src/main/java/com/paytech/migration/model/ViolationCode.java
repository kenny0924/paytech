package com.paytech.migration.model;

/**
 * 迁移校验失败类别。
 *
 * @author Claude
 */
public enum ViolationCode {

    /**
     * 迁移目录中的文件名不符合 {@code V<version>__<kebab-description>.sql}。
     */
    INVALID_SCRIPT_NAME,

    /**
     * 同一版本号对应多个文件。
     */
    DUPLICATE_VERSION,

    /**
     * 版本号不从 1 起连续。
     */
    VERSION_GAP,

    /**
     * 目录中存在高于清单目标版本的超前文件。
     */
    SCRIPT_AHEAD_OF_TARGET,

    /**
     * 清单目标版本在目录中没有对应文件。
     */
    TARGET_SCRIPT_MISSING,

    /**
     * 脚本文件无法按 UTF-8 读取。
     */
    SCRIPT_UNREADABLE,

    /**
     * 脚本存在未闭合的字符串、标识符或块注释。
     */
    SQL_SYNTAX,

    /**
     * 脚本不含任何语句。
     */
    EMPTY_SCRIPT,

    /**
     * 脚本含 DELIMITER、USE、存储程序（过程、函数、触发器、事件）或 CALL。
     */
    FORBIDDEN_STATEMENT,

    /**
     * 已成功执行版本的记录校验和与当前文件不一致。
     */
    CHECKSUM_MISMATCH,

    /**
     * 已成功执行版本的记录文件名与当前文件不一致。
     */
    SCRIPT_NAME_MISMATCH,

    /**
     * 已成功执行的版本在目录中没有文件。
     */
    APPLIED_SCRIPT_MISSING,

    /**
     * 低于当前已执行最高版本的某个版本没有成功记录。
     */
    APPLIED_VERSION_GAP,

    /**
     * 存在执行失败的记录；需人工处理后才能继续迁移。
     */
    FAILED_MIGRATION,

    /**
     * 初始化数据目录存在约定文件之外的 SQL 文件。
     */
    SEED_FILE_UNEXPECTED,

    /**
     * 初始化数据含 DDL；初始化数据只允许 DML，以便在一个事务中原子执行。
     */
    SEED_DDL_FORBIDDEN,

    /**
     * 已记录的初始化数据校验和与当前文件不一致。
     */
    SEED_CHECKSUM_MISMATCH,

    /**
     * Schema 尚未迁移到目标版本，不能执行初始化数据。
     */
    SEED_BEFORE_MIGRATION
}

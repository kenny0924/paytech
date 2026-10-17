package com.paytech.migration.model;

import java.time.Instant;

/**
 * {@code t_schema_migration} 中的一行执行记录。
 *
 * @author Claude
 */
public class AppliedMigration {

    /**
     * Schema 版本号。
     */
    private final int version;

    /**
     * 脚本描述。
     */
    private final String description;

    /**
     * 脚本文件名。
     */
    private final String script;

    /**
     * 执行时脚本的 SHA-256。
     */
    private final String checksum;

    /**
     * 执行账号。
     */
    private final String installedBy;

    /**
     * 执行结束的 UTC 时间。
     */
    private final Instant installedAt;

    /**
     * 执行耗时。
     * <li>单位：毫秒</li>
     */
    private final int executionMs;

    /**
     * 是否全部语句成功。
     */
    private final boolean success;

    /**
     * 失败摘要。
     * <li>成功时为 null</li>
     */
    private final String errorMessage;

    /**
     * 构造一行执行记录。
     *
     * @param version 版本号
     * @param description 描述
     * @param script 文件名
     * @param checksum 校验和
     * @param installedBy 执行账号
     * @param installedAt 执行时间；写入前为 null，由数据库生成
     * @param executionMs 耗时毫秒
     * @param success 是否成功
     * @param errorMessage 失败摘要
     */
    public AppliedMigration(int version, String description, String script, String checksum, String installedBy,
        Instant installedAt, int executionMs, boolean success, String errorMessage) {
        this.version = version;
        this.description = description;
        this.script = script;
        this.checksum = checksum;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
        this.executionMs = executionMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * 版本号。
     *
     * @return 版本号
     */
    public int getVersion() {
        return version;
    }

    /**
     * 脚本描述。
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 脚本文件名。
     *
     * @return 文件名
     */
    public String getScript() {
        return script;
    }

    /**
     * 执行时脚本的校验和。
     *
     * @return SHA-256 十六进制
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * 执行账号。
     *
     * @return 数据库账号
     */
    public String getInstalledBy() {
        return installedBy;
    }

    /**
     * 执行结束时间。
     *
     * @return UTC 时间
     */
    public Instant getInstalledAt() {
        return installedAt;
    }

    /**
     * 执行耗时。
     *
     * @return 毫秒
     */
    public int getExecutionMs() {
        return executionMs;
    }

    /**
     * 是否全部语句成功。
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 失败摘要。
     *
     * @return 失败摘要；成功时为 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}

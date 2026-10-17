package com.paytech.migration.model;

import java.time.Instant;

/**
 * {@code t_schema_seed} 中的一行初始化数据记录。
 *
 * @author Claude
 */
public class AppliedSeed {

    /**
     * 初始化数据文件名。
     */
    private final String seedFile;

    /**
     * 执行时文件的 SHA-256。
     */
    private final String checksum;

    /**
     * 执行完成的 UTC 时间。
     */
    private final Instant installedAt;

    /**
     * 构造一行初始化数据记录。
     *
     * @param seedFile 文件名
     * @param checksum 校验和
     * @param installedAt 执行时间
     */
    public AppliedSeed(String seedFile, String checksum, Instant installedAt) {
        this.seedFile = seedFile;
        this.checksum = checksum;
        this.installedAt = installedAt;
    }

    /**
     * 初始化数据文件名。
     *
     * @return 文件名
     */
    public String getSeedFile() {
        return seedFile;
    }

    /**
     * 执行时文件的校验和。
     *
     * @return SHA-256 十六进制
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * 执行完成时间。
     *
     * @return UTC 时间
     */
    public Instant getInstalledAt() {
        return installedAt;
    }
}

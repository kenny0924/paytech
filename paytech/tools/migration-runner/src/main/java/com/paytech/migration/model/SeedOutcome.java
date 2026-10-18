package com.paytech.migration.model;

/**
 * {@code seed} 子命令的结果。
 *
 * @author Claude
 */
public enum SeedOutcome {

    /**
     * 本次首次执行并记录。
     */
    APPLIED,

    /**
     * 已执行过且校验和一致，本次未执行。
     */
    ALREADY_APPLIED,

    /**
     * 服务没有初始化数据文件。
     */
    NO_SEED_FILE
}

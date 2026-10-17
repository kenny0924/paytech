package com.paytech.migration.cli;

import java.util.Arrays;
import java.util.Optional;

/**
 * 子命令。
 *
 * @author Claude
 */
public enum MigrationCommand {

    /**
     * 列出当前版本、目标版本与待执行文件；只读。
     */
    PLAN("plan"),

    /**
     * 校验目录、已执行版本与初始化数据的校验和；只读。
     */
    VALIDATE("validate"),

    /**
     * 按版本顺序执行待执行脚本。
     */
    MIGRATE("migrate"),

    /**
     * 首次且仅一次执行初始化数据。
     */
    SEED("seed");

    private final String code;

    /**
     * 绑定命令行名称。
     *
     * @param code 命令行名称
     */
    MigrationCommand(String code) {
        this.code = code;
    }

    /**
     * 命令行名称。
     *
     * @return 小写名称
     */
    public String getCode() {
        return code;
    }

    /**
     * 按命令行名称查找。
     *
     * @param code 命令行名称
     * @return 子命令
     */
    public static Optional<MigrationCommand> fromCode(String code) {
        return Arrays.stream(values()).filter(command -> command.code.equals(code)).findFirst();
    }
}

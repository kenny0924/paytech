package com.paytech.data.domain.whitelist;

import java.util.List;

/**
 * CDC 白名单中的一张表：进入白名单即允许同步到数仓（D25）。
 *
 * @param service 表所属逻辑服务名，如 {@code ledger}
 * @param database 来源 MySQL 库名，必须以 {@code mor_} 开头
 * @param name 物理表名
 * @param maskedColumns 同步前必须屏蔽的列（含 PII 的列必须登记于此）
 * @param excludedColumns 不得同步的列
 * @author Claude
 */
public record WhitelistedTable(String service, String database, String name, List<String> maskedColumns,
    List<String> excludedColumns) {

    /**
     * 防御性复制列清单。
     */
    public WhitelistedTable {
        maskedColumns = List.copyOf(maskedColumns);
        excludedColumns = List.copyOf(excludedColumns);
    }

    /**
     * 返回 {@code database.table} 形式的限定名，与 Flink CDC pipeline 的 {@code tables} 写法一致。
     *
     * @return 限定名
     */
    public String qualifiedName() {
        return database + "." + name;
    }
}

package com.paytech.data.domainservice;

import com.paytech.data.domain.whitelist.WhitelistedTable;

import java.util.List;
import java.util.Optional;

/**
 * CDC 白名单目录：读取并校验公开契约文件 {@code cdc/whitelist.yaml}。
 *
 * <p>
 * 未列出的表不得同步到数仓；目录在构造时完成校验，非法文件使应用启动失败。
 *
 * @author Claude
 */
public interface WhitelistCatalog {

    /**
     * 返回白名单中的全部表，顺序与文件一致。
     *
     * @return 白名单表
     */
    List<WhitelistedTable> tables();

    /**
     * 按来源库与表名查找白名单表。
     *
     * @param database 来源库名
     * @param table 物理表名
     * @return 白名单表；未登记时为空
     */
    Optional<WhitelistedTable> find(String database, String table);
}

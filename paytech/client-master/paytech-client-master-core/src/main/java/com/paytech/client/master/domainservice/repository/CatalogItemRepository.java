package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import java.util.Collection;
import java.util.List;

/** t_catalog_item 仓储。 */
public interface CatalogItemRepository {
    /**
     * 指定目录在辖区与全局（*）下的 ACTIVE 项
     * 
     * @param catalogCodes 目录代码
     * @param jurisdictionCode 辖区代码；全局目录查询传 *
     * @return 目录项；空集合不访问数据库
     */
    List<CatalogItem> findActive(Collection<String> catalogCodes, String jurisdictionCode);

    /**
     * 某目录全部辖区、全部状态的项，供运营只读查看
     * 
     * @param catalogCode 目录代码
     * @return 目录项
     */
    List<CatalogItem> findByCatalog(String catalogCode);
}

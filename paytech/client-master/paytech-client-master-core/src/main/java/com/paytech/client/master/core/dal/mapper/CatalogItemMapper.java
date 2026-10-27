package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_catalog_item 只读 SQL。 */
public interface CatalogItemMapper {
    /** 指定目录在辖区与全局下的 ACTIVE 项。 */
    List<CatalogItem> findActive(@Param("catalogCodes") Collection<String> catalogCodes, @Param("jurisdictionCode") String jurisdictionCode);

    /** 某目录全部项。 */
    List<CatalogItem> findByCatalog(@Param("catalogCode") String catalogCode);
}

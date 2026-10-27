package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import com.paytech.client.master.core.dal.mapper.CatalogItemMapper;
import com.paytech.client.master.domainservice.repository.CatalogItemRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 CatalogItemMapper 访问本表。 */
@AllArgsConstructor
public class CatalogItemRepositoryImpl implements CatalogItemRepository {
    private final CatalogItemMapper mapper;

    /** 空集合不访问数据库。 */
    @Override
    public List<CatalogItem> findActive(Collection<String> catalogCodes, String jurisdictionCode) {
        return catalogCodes.isEmpty() ? List.of() : List.copyOf(mapper.findActive(catalogCodes, jurisdictionCode));
    }

    /** 某目录全部项。 */
    @Override
    public List<CatalogItem> findByCatalog(String catalogCode) {
        return List.copyOf(mapper.findByCatalog(catalogCode));
    }
}

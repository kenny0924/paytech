package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import com.paytech.client.master.core.dal.mapper.AddressFormatComponentMapper;
import com.paytech.client.master.domainservice.repository.AddressFormatComponentRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 AddressFormatComponentMapper 访问本表。 */
@AllArgsConstructor
public class AddressFormatComponentRepositoryImpl implements AddressFormatComponentRepository {
    private final AddressFormatComponentMapper mapper;

    /** 空集合不访问数据库。 */
    @Override
    public List<AddressFormatComponent> findByFormats(Collection<String> formatCodes) {
        return formatCodes.isEmpty() ? List.of() : List.copyOf(mapper.findByFormats(formatCodes));
    }
}

package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import com.paytech.client.master.core.dal.mapper.PartyAttributeMapper;
import com.paytech.client.master.domainservice.repository.PartyAttributeRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 PartyAttributeMapper 访问本表。 */
@AllArgsConstructor
public class PartyAttributeRepositoryImpl implements PartyAttributeRepository {
    private final PartyAttributeMapper mapper;

    /** 空集合不访问数据库。 */
    @Override
    public List<PartyAttribute> findByProfiles(Collection<Long> profileIds) {
        return profileIds.isEmpty() ? List.of() : List.copyOf(mapper.findByProfiles(profileIds));
    }

    /** INSERT 为 1 行、覆盖为 2 行或取值未变为 0 行，均表示已写入。 */
    @Override
    public void save(PartyAttribute row) {
        mapper.upsert(row);
    }

    /** 清空属性。 */
    @Override
    public void delete(long profileId, String attributeCode) {
        mapper.delete(profileId, attributeCode);
    }
}

package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.core.dal.mapper.OnboardingSchemaFieldMapper;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaFieldRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 OnboardingSchemaFieldMapper 访问本表。 */
@AllArgsConstructor
public class OnboardingSchemaFieldRepositoryImpl implements OnboardingSchemaFieldRepository {
    private final OnboardingSchemaFieldMapper mapper;

    /** 空集合不访问数据库。 */
    @Override
    public List<OnboardingSchemaField> findBySchemas(Collection<Long> schemaIds) {
        return schemaIds.isEmpty() ? List.of() : List.copyOf(mapper.findBySchemas(schemaIds));
    }
}

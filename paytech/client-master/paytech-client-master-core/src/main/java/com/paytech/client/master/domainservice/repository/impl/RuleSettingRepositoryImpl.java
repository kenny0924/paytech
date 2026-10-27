package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.RuleSetting;
import com.paytech.client.master.core.dal.mapper.RuleSettingMapper;
import com.paytech.client.master.domainservice.repository.RuleSettingRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 RuleSettingMapper 访问本表。 */
@AllArgsConstructor
public class RuleSettingRepositoryImpl implements RuleSettingRepository {
    private final RuleSettingMapper mapper;

    /** 空集合不访问数据库。 */
    @Override
    public List<RuleSetting> findActive(Collection<String> scopeCodes) {
        return scopeCodes.isEmpty() ? List.of() : List.copyOf(mapper.findActive(scopeCodes));
    }

    /** 全部规则。 */
    @Override
    public List<RuleSetting> findAll() {
        return List.copyOf(mapper.findAll());
    }
}

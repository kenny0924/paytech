package com.paytech.risk.control.domainservice.repository.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleVersion;
import com.paytech.risk.control.core.dal.mapper.UnderwritingRuleVersionMapper;
import com.paytech.risk.control.domainservice.repository.UnderwritingRuleVersionRepository;

import lombok.AllArgsConstructor;

/** 自动承保规则版本读取。 */
@Repository
@AllArgsConstructor
public class UnderwritingRuleVersionRepositoryImpl implements UnderwritingRuleVersionRepository {
    private final UnderwritingRuleVersionMapper mapper;

    /** 读取 ACTIVE 版本。 */
    @Override
    public Optional<UnderwritingRuleVersion> findActive() {
        return Optional.ofNullable(mapper.selectActive());
    }

    /** 读取全部版本。 */
    @Override
    public List<UnderwritingRuleVersion> findAll() {
        return mapper.selectAll();
    }
}

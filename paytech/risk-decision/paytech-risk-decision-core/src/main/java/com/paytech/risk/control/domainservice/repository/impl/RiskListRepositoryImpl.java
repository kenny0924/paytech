package com.paytech.risk.control.domainservice.repository.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.mapper.RiskListMapper;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;

import lombok.AllArgsConstructor;

/** 风险名单定义持久化。 */
@Repository
@AllArgsConstructor
public class RiskListRepositoryImpl implements RiskListRepository {
    private final RiskListMapper mapper;

    /** 插入名单；唯一编码冲突转为并发冲突。 */
    @Override
    public void insert(RiskList row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("名单编码已存在: " + row.getListCode());
        }
    }

    /** 按 ID 读取名单。 */
    @Override
    public Optional<RiskList> findById(long listId) {
        return Optional.ofNullable(mapper.selectById(listId));
    }

    /** 按编码读取名单。 */
    @Override
    public Optional<RiskList> findByCode(String listCode) {
        return Optional.ofNullable(mapper.selectByCode(listCode));
    }

    /** 按编码稳定排序分页读取名单。 */
    @Override
    public List<RiskList> findPage(int limit, long offset) {
        return mapper.selectPage(limit, offset);
    }

    /** 统计名单数量。 */
    @Override
    public long count() {
        return mapper.count();
    }
}

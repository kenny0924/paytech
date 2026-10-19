package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.core.dal.mapper.PricingProfileCondMapper;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 PricingProfileCondMapper 访问 t_pricing_profile_cond。 */
@AllArgsConstructor
public class PricingProfileCondRepositoryImpl implements PricingProfileCondRepository {
    private final PricingProfileCondMapper mapper;

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<PricingProfileCond> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (PricingProfileCond row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("定价画像条件插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public PricingProfileCond findById(long profileConditionId) {
        return mapper.selectById(profileConditionId);
    }

    /** 读取一个画像的全部条件。 */
    @Override
    public List<PricingProfileCond> findByProfileId(long pricingProfileId) {
        return List.copyOf(mapper.selectByProfileId(pricingProfileId));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<PricingProfileCond> findByProfileIds(Collection<Long> pricingProfileIds) {
        if (pricingProfileIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByProfileIds(pricingProfileIds));
    }

    /** 删除一个画像的全部条件，用于 DRAFT 内整体替换。 */
    @Override
    public int deleteByProfileId(long pricingProfileId) {
        return mapper.deleteByProfileId(pricingProfileId);
    }
}

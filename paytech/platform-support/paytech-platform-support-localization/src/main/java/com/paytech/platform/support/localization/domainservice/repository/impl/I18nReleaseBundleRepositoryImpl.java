package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.dal.mapper.I18nReleaseBundleMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseBundleRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的语言包仓储。 */
@Repository
@AllArgsConstructor
public class I18nReleaseBundleRepositoryImpl implements I18nReleaseBundleRepository {

    private final I18nReleaseBundleMapper mapper;

    /** 一条语句插入全部语言包；数量受语言数 × 命名空间数限制。 */
    @Override
    public void insertAll(List<I18nReleaseBundle> bundles) {
        RowChecks.requireInserted(mapper.insertBatch(bundles), bundles.size(), "语言包");
    }

    /** 发布的全部语言包。 */
    @Override
    public List<I18nReleaseBundle> findByRelease(long releaseId) {
        return mapper.selectByRelease(releaseId);
    }
}

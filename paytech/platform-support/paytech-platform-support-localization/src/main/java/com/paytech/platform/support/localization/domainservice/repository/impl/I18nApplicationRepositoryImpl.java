package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.mapper.I18nApplicationMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的应用仓储；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nApplicationRepositoryImpl implements I18nApplicationRepository {

    private final I18nApplicationMapper mapper;

    /** 只把应用编码唯一键冲突转换为业务拒绝。 */
    @Override
    public void insert(I18nApplication application) {
        try {
            RowChecks.requireInserted(mapper.insert(application), 1, application.getApplicationCode());
        } catch (DuplicateKeyException failure) {
            throw RowChecks.duplicate(failure, "uk_i18n_app_code", "应用编码已存在: " + application.getApplicationCode());
        }
    }

    /** 按应用编码查询。 */
    @Override
    public I18nApplication findByCode(String applicationCode) {
        return mapper.selectByCode(applicationCode);
    }

    /** 排他锁读取。 */
    @Override
    public I18nApplication lockById(long applicationId) {
        return mapper.selectByIdForUpdate(applicationId);
    }

    /** 全部应用。 */
    @Override
    public List<I18nApplication> findAll() {
        return mapper.selectAll();
    }

    /** 引用某语言的应用。 */
    @Override
    public List<String> findCodesUsingLocale(String localeCode) {
        return mapper.selectCodesUsingLocale(localeCode);
    }

    /** 条件更新成功后把目标设置写回原对象。 */
    @Override
    public void updateSettings(I18nApplication application, I18nApplication next) {
        RowChecks.requireVersionAvailable(application.getVersion(), application.getApplicationCode());
        RowChecks.requireUpdated(mapper.updateSettings(application, next), "应用 " + application.getApplicationCode());
        application.setApplicationName(next.getApplicationName());
        application.setDefaultLocale(next.getDefaultLocale());
        application.setRequiredLocalesJson(next.getRequiredLocalesJson());
        application.setCoverageThresholdPercent(next.getCoverageThresholdPercent());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(next.getUpdatedBy());
        application.setUpdateTime(next.getUpdateTime());
    }

    /** 两个修订号同时加一。 */
    @Override
    public void advanceKeySet(I18nApplication application, Instant now) {
        RowChecks.requireUpdated(mapper.advanceKeySet(application.getApplicationId(), application.getKeySetRevision(),
            now), "应用 key 集合 " + application.getApplicationCode());
        application.setKeySetRevision(application.getKeySetRevision() + 1);
        application.setContentRevision(application.getContentRevision() + 1);
        application.setUpdateTime(now);
    }

    /** 文案修订号加一。 */
    @Override
    public void advanceContent(I18nApplication application, Instant now) {
        RowChecks.requireUpdated(mapper.advanceContent(application.getApplicationId(), application.getContentRevision(),
            now), "应用文案 " + application.getApplicationCode());
        application.setContentRevision(application.getContentRevision() + 1);
        application.setUpdateTime(now);
    }

    /** 当前发布按原值比较后切换。 */
    @Override
    public void switchCurrentRelease(I18nApplication application, long releaseId, Instant now) {
        RowChecks.requireUpdated(mapper.updateCurrentRelease(application.getApplicationId(),
            application.getCurrentReleaseId(), releaseId, now), "应用当前发布 " + application.getApplicationCode());
        application.setCurrentReleaseId(releaseId);
        application.setUpdateTime(now);
    }
}

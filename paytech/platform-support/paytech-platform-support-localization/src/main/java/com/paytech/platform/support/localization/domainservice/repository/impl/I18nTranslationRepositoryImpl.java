package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.dal.mapper.I18nTranslationMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的翻译仓储；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nTranslationRepositoryImpl implements I18nTranslationRepository {

    private final I18nTranslationMapper mapper;

    /** 同一 key 与语言已存在说明调用方读取的版本过期，按并发冲突处理。 */
    @Override
    public void insert(I18nTranslation translation) {
        try {
            RowChecks.requireInserted(mapper.insert(translation), 1, "翻译");
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_i18n_translation")) {
                throw failure;
            }
            throw new ConcurrentRequestException("翻译已被其他操作创建，请刷新后重试", failure);
        }
    }

    /** 条件更新成功后把目标值写回原对象。 */
    @Override
    public void update(I18nTranslation translation, I18nTranslation next) {
        RowChecks.requireVersionAvailable(translation.getVersion(), "翻译");
        RowChecks.requireUpdated(mapper.update(translation, next), "翻译");
        translation.setText(next.getText());
        translation.setBasedOnSourceRevision(next.getBasedOnSourceRevision());
        translation.setVersion(translation.getVersion() + 1);
        translation.setUpdatedBy(next.getUpdatedBy());
        translation.setUpdateTime(next.getUpdateTime());
    }

    /** 空集合直接返回空列表。 */
    @Override
    public List<I18nTranslation> findByKeys(Collection<Long> keyIds, String locale) {
        return keyIds.isEmpty() ? List.of() : mapper.selectByKeys(keyIds, locale);
    }

    /** 应用 ACTIVE key 的全部翻译。 */
    @Override
    public List<I18nTranslation> findActiveByApplication(long applicationId) {
        return mapper.selectActiveByApplication(applicationId);
    }
}

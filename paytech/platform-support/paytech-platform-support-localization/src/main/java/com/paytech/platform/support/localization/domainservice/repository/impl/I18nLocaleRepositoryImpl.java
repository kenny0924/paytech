package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.mapper.I18nLocaleMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nLocaleRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的语言仓储；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nLocaleRepositoryImpl implements I18nLocaleRepository {

    private final I18nLocaleMapper mapper;

    /** 只把语言代码唯一键冲突转换为业务拒绝。 */
    @Override
    public void insert(I18nLocale locale) {
        try {
            RowChecks.requireInserted(mapper.insert(locale), 1, locale.getLocaleCode());
        } catch (DuplicateKeyException failure) {
            throw RowChecks.duplicate(failure, "uk_i18n_locale_code", "语言代码已存在: " + locale.getLocaleCode());
        }
    }

    /** 按语言代码查询。 */
    @Override
    public I18nLocale findByCode(String localeCode) {
        return mapper.selectByCode(localeCode);
    }

    /** 空集合不发 SQL。 */
    @Override
    public List<I18nLocale> lockForShare(Collection<String> localeCodes) {
        return localeCodes.isEmpty() ? List.of() : mapper.selectByCodesForShare(localeCodes);
    }

    /** 全部语言。 */
    @Override
    public List<I18nLocale> findAll() {
        return mapper.selectAll();
    }

    /** 条件更新成功后把目标值写回原对象。 */
    @Override
    public void update(I18nLocale locale, I18nLocale next) {
        RowChecks.requireVersionAvailable(locale.getVersion(), locale.getLocaleCode());
        RowChecks.requireUpdated(mapper.update(locale, next), "语言 " + locale.getLocaleCode());
        locale.setNativeName(next.getNativeName());
        locale.setEnglishName(next.getEnglishName());
        locale.setTextDirection(next.getTextDirection());
        locale.setStatus(next.getStatus());
        locale.setSortOrder(next.getSortOrder());
        locale.setVersion(locale.getVersion() + 1);
        locale.setUpdatedBy(next.getUpdatedBy());
        locale.setUpdateTime(next.getUpdateTime());
    }
}

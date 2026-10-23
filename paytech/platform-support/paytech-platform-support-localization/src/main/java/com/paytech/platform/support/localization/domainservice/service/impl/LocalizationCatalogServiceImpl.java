package com.paytech.platform.support.localization.domainservice.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nLocaleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;

import lombok.AllArgsConstructor;

/** 目录写入与引用重证：语言停用与应用引用之间以语言行的共享锁 / 排他锁串行，命名空间删除与导入以应用行锁串行。 */
@Service
@AllArgsConstructor
public class LocalizationCatalogServiceImpl implements LocalizationCatalogService {

    private final I18nKeyRepository keys;
    private final I18nLocaleRepository locales;
    private final I18nNamespaceRepository namespaces;
    private final I18nApplicationRepository applications;

    /** 唯一键为最终保护。 */
    @Override
    public void createLocale(I18nLocale locale) {
        locales.insert(locale);
    }

    /** 先更新（取得排他锁），再读取引用该语言的应用。 */
    @Override
    public void updateLocale(I18nLocale locale, I18nLocale next) {
        locales.update(locale, next);
        if (next.getStatus() != LocaleStatus.DISABLED) {
            return;
        }
        List<String> using = applications.findCodesUsingLocale(locale.getLocaleCode());
        if (!using.isEmpty()) {
            throw new BizCheckFailException("语言仍被应用用作默认语言或必须覆盖语言，不能停用: " + locale.getLocaleCode()
                + "，应用 " + String.join("、", using));
        }
    }

    /** 引用的语言加共享锁后插入。 */
    @Override
    public void createApplication(I18nApplication application, List<String> localeCodes) {
        requireEnabled(localeCodes);
        applications.insert(application);
    }

    /** 引用的语言加共享锁后按原版本更新。 */
    @Override
    public void updateApplication(I18nApplication application, I18nApplication next, List<String> localeCodes) {
        requireEnabled(localeCodes);
        applications.updateSettings(application, next);
    }

    /** 唯一键为最终保护。 */
    @Override
    public void createNamespace(I18nNamespace namespace) {
        namespaces.insert(namespace);
    }

    /** 按原版本更新。 */
    @Override
    public void updateNamespace(I18nNamespace namespace, I18nNamespace next) {
        namespaces.update(namespace, next);
    }

    /** 应用行锁与 key 导入互斥，锁后计数为 0 才删除。 */
    @Override
    public void deleteNamespace(I18nApplication application, I18nNamespace namespace) {
        if (applications.lockById(application.getApplicationId()) == null) {
            throw new ConcurrentRequestException("应用已不存在: " + application.getApplicationCode());
        }
        long count = keys.countInNamespace(namespace.getNamespaceId());
        if (count > 0) {
            throw new BizCheckFailException("命名空间下还有 " + count + " 个 key（含已废弃），不能删除: "
                + namespace.getNamespaceCode());
        }
        namespaces.delete(namespace);
    }

    /** 语言都存在且 ENABLED；共享锁阻止并发停用。 */
    void requireEnabled(List<String> localeCodes) {
        Set<String> enabled = locales.lockForShare(Set.copyOf(localeCodes)).stream()
            .filter(locale -> locale.getStatus() == LocaleStatus.ENABLED).map(I18nLocale::getLocaleCode)
            .collect(Collectors.toSet());
        List<String> unavailable = localeCodes.stream().filter(code -> !enabled.contains(code)).distinct().toList();
        if (!unavailable.isEmpty()) {
            throw new BizCheckFailException("语言不存在或未启用: " + String.join("、", unavailable));
        }
    }
}

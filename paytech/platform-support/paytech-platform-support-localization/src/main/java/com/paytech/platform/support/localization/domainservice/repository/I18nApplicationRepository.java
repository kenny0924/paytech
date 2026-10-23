package com.paytech.platform.support.localization.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;

/** t_i18n_application 仓储；修订号与当前发布只经条件更新推进。 */
public interface I18nApplicationRepository {

    /**
     * 插入新应用。
     *
     * @param application 已准备主键、版本、修订号与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 应用编码已存在
     */
    void insert(I18nApplication application);

    /** 按应用编码查询；不存在返回 null。 */
    I18nApplication findByCode(String applicationCode);

    /**
     * 在调用方事务内加排他锁重新读取：同一应用的文案写入、命名空间删除与发布登记在数据库侧串行。
     *
     * @param applicationId 应用 ID
     * @return 当前已提交的应用；不存在返回 null
     */
    I18nApplication lockById(long applicationId);

    /** 全部应用，按编码升序。 */
    List<I18nApplication> findAll();

    /** 以某语言为默认语言或必须覆盖语言的应用编码。 */
    List<String> findCodesUsingLocale(String localeCode);

    /** 按原版本更新设置，成功后同步原对象。 */
    void updateSettings(I18nApplication application, I18nApplication next);

    /** 按原 key 集合修订号把 key 集合与文案修订号各加一，成功后同步原对象。 */
    void advanceKeySet(I18nApplication application, Instant now);

    /** 按原文案修订号把文案修订号加一，成功后同步原对象。 */
    void advanceContent(I18nApplication application, Instant now);

    /** 按原当前发布切换当前发布，成功后同步原对象。 */
    void switchCurrentRelease(I18nApplication application, long releaseId, Instant now);
}

package com.paytech.platform.support.localization.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;

/** t_i18n_locale 仓储。 */
public interface I18nLocaleRepository {

    /**
     * 插入新语言。
     *
     * @param locale 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 语言代码已存在
     */
    void insert(I18nLocale locale);

    /** 按语言代码查询；不存在返回 null。 */
    I18nLocale findByCode(String localeCode);

    /**
     * 在调用方事务内加共享锁读取：引用语言的应用写入与语言停用在数据库侧串行。
     *
     * @param localeCodes 语言代码；空集合返回空列表
     * @return 存在的语言
     */
    List<I18nLocale> lockForShare(Collection<String> localeCodes);

    /** 全部语言，按排序值、语言代码升序。 */
    List<I18nLocale> findAll();

    /**
     * 按原版本更新，成功后同步原对象。
     *
     * @param locale 当前对象
     * @param next 目标名称、方向、状态、排序、修改人与时间
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本已变化
     */
    void update(I18nLocale locale, I18nLocale next);
}

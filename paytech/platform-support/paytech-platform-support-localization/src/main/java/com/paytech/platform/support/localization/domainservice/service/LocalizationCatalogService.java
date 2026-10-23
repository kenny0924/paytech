package com.paytech.platform.support.localization.domainservice.service;

import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;

/**
 * 语言、应用与命名空间的本地保存；不访问外部能力。
 * <li>写方法须在调用方的本地事务内调用；引用关系在事务内以共享锁或应用行锁重证。</li>
 */
public interface LocalizationCatalogService {

    /** 插入新语言。 */
    void createLocale(I18nLocale locale);

    /**
     * 按原版本修改语言；停用时在事务内重证没有应用引用它。
     *
     * @param locale 当前语言
     * @param next 目标值
     */
    void updateLocale(I18nLocale locale, I18nLocale next);

    /**
     * 插入新应用；所引用的语言在事务内加共享锁并须为 ENABLED。
     *
     * @param application 新应用
     * @param locales 默认语言与必须覆盖语言
     */
    void createApplication(I18nApplication application, List<String> locales);

    /**
     * 按原版本修改应用设置；所引用的语言在事务内加共享锁并须为 ENABLED。
     *
     * @param application 当前应用
     * @param next 目标设置
     * @param locales 默认语言与必须覆盖语言
     */
    void updateApplication(I18nApplication application, I18nApplication next, List<String> locales);

    /** 插入新命名空间。 */
    void createNamespace(I18nNamespace namespace);

    /** 按原版本修改命名空间。 */
    void updateNamespace(I18nNamespace namespace, I18nNamespace next);

    /**
     * 删除命名空间：锁应用行后重证其下没有任何 key，再按原版本删除。
     *
     * @param application 所属应用
     * @param namespace 命名空间
     */
    void deleteNamespace(I18nApplication application, I18nNamespace namespace);
}

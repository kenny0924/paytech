package com.paytech.platform.support.localization.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;

/** t_i18n_namespace 仓储。 */
public interface I18nNamespaceRepository {

    /**
     * 插入新命名空间。
     *
     * @param namespace 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 应用内编码已存在
     */
    void insert(I18nNamespace namespace);

    /** 应用的全部命名空间，按排序值、编码升序。 */
    List<I18nNamespace> findByApplication(long applicationId);

    /** 按应用与编码查询；不存在返回 null。 */
    I18nNamespace findByCode(long applicationId, String namespaceCode);

    /** 按原版本更新，成功后同步原对象。 */
    void update(I18nNamespace namespace, I18nNamespace next);

    /** 按原版本删除。 */
    void delete(I18nNamespace namespace);
}

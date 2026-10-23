package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.dal.mapper.I18nNamespaceMapper;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的命名空间仓储；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nNamespaceRepositoryImpl implements I18nNamespaceRepository {

    private final I18nNamespaceMapper mapper;

    /** 只把应用内编码唯一键冲突转换为业务拒绝。 */
    @Override
    public void insert(I18nNamespace namespace) {
        try {
            RowChecks.requireInserted(mapper.insert(namespace), 1, namespace.getNamespaceCode());
        } catch (DuplicateKeyException failure) {
            throw RowChecks.duplicate(failure, "uk_i18n_namespace", "命名空间编码已存在: " + namespace.getNamespaceCode());
        }
    }

    /** 应用的全部命名空间。 */
    @Override
    public List<I18nNamespace> findByApplication(long applicationId) {
        return mapper.selectByApplication(applicationId);
    }

    /** 按应用与编码查询。 */
    @Override
    public I18nNamespace findByCode(long applicationId, String namespaceCode) {
        return mapper.selectByCode(applicationId, namespaceCode);
    }

    /** 条件更新成功后把目标值写回原对象。 */
    @Override
    public void update(I18nNamespace namespace, I18nNamespace next) {
        RowChecks.requireVersionAvailable(namespace.getVersion(), namespace.getNamespaceCode());
        RowChecks.requireUpdated(mapper.update(namespace, next), "命名空间 " + namespace.getNamespaceCode());
        namespace.setNamespaceName(next.getNamespaceName());
        namespace.setDescription(next.getDescription());
        namespace.setSortOrder(next.getSortOrder());
        namespace.setVersion(namespace.getVersion() + 1);
        namespace.setUpdatedBy(next.getUpdatedBy());
        namespace.setUpdateTime(next.getUpdateTime());
    }

    /** 按原版本删除，未命中视为并发修改。 */
    @Override
    public void delete(I18nNamespace namespace) {
        RowChecks.requireUpdated(mapper.delete(namespace), "命名空间 " + namespace.getNamespaceCode());
    }
}

package com.paytech.platform.support.localization.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;

/** t_i18n_namespace 的 MyBatis 映射；SQL 在 mapper/localization/I18nNamespaceMapper.xml。 */
public interface I18nNamespaceMapper {

    /** 插入新命名空间。 */
    int insert(@Param("row") I18nNamespace row);

    /** 应用的全部命名空间，按排序值、编码升序。 */
    List<I18nNamespace> selectByApplication(@Param("applicationId") long applicationId);

    /** 按应用与编码查询。 */
    I18nNamespace selectByCode(@Param("applicationId") long applicationId, @Param("namespaceCode") String namespaceCode);

    /** 按原版本更新名称、说明与排序，版本加一。 */
    int update(@Param("row") I18nNamespace row, @Param("next") I18nNamespace next);

    /** 按原版本删除。 */
    int delete(@Param("row") I18nNamespace row);
}

package com.paytech.platform.support.localization.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;

/** t_i18n_release_bundle 的 MyBatis 映射；SQL 在 mapper/localization/I18nReleaseBundleMapper.xml。 */
public interface I18nReleaseBundleMapper {

    /** 批量插入语言包。 */
    int insertBatch(@Param("rows") Collection<I18nReleaseBundle> rows);

    /** 发布的全部语言包，按语言、命名空间排列。 */
    List<I18nReleaseBundle> selectByRelease(@Param("releaseId") long releaseId);
}

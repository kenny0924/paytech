package com.paytech.platform.support.localization.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseChange;

/** t_i18n_release_change 的 MyBatis 映射；SQL 在 mapper/localization/I18nReleaseChangeMapper.xml。 */
public interface I18nReleaseChangeMapper {

    /** 批量插入文案变化。 */
    int insertBatch(@Param("rows") Collection<I18nReleaseChange> rows);

    /** 发布号不大于给定值时，每个语言、key 的最后一条变化中不是 REMOVED 的（只含语言、key 与文案）。 */
    List<I18nReleaseChange> selectContent(@Param("applicationId") long applicationId,
        @Param("releaseNo") int releaseNo);
}

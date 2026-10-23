package com.paytech.platform.support.localization.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;

/** t_i18n_release 的 MyBatis 映射；SQL 在 mapper/localization/I18nReleaseMapper.xml。 */
public interface I18nReleaseMapper {

    /** 插入新发布。 */
    int insert(@Param("row") I18nRelease row);

    /** 按主键查询。 */
    I18nRelease selectById(@Param("releaseId") long releaseId);

    /** 按应用与发布号查询。 */
    I18nRelease selectByNo(@Param("applicationId") long applicationId, @Param("releaseNo") int releaseNo);

    /** 应用内最大发布号；没有发布时为 null。 */
    Integer selectMaxNo(@Param("applicationId") long applicationId);

    /** 小于给定发布号的最大发布号；没有时为 null。 */
    Integer selectPreviousNo(@Param("applicationId") long applicationId, @Param("releaseNo") int releaseNo);

    /** 分页查询，按发布号倒序。 */
    List<I18nRelease> selectPage(@Param("applicationId") long applicationId, @Param("offset") int offset,
        @Param("size") int size);

    /** 应用的发布总数。 */
    long count(@Param("applicationId") long applicationId);

    /** PENDING_CHECK 变为 READY。 */
    int markReady(@Param("releaseId") long releaseId, @Param("now") Instant now);

    /** PENDING_CHECK 变为 FAILED。 */
    int markFailed(@Param("releaseId") long releaseId, @Param("reason") String reason, @Param("now") Instant now);
}

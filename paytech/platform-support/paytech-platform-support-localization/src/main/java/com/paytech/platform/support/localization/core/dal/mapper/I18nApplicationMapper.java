package com.paytech.platform.support.localization.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;

/** t_i18n_application 的 MyBatis 映射；SQL 在 mapper/localization/I18nApplicationMapper.xml。 */
public interface I18nApplicationMapper {

    /** 插入新应用。 */
    int insert(@Param("row") I18nApplication row);

    /** 按应用编码查询。 */
    I18nApplication selectByCode(@Param("applicationCode") String applicationCode);

    /** 按主键加排他锁读取，与同一应用的其他文案写入串行。 */
    I18nApplication selectByIdForUpdate(@Param("applicationId") long applicationId);

    /** 全部应用，按编码升序。 */
    List<I18nApplication> selectAll();

    /** 以某语言为默认语言或必须覆盖语言的应用编码。 */
    List<String> selectCodesUsingLocale(@Param("localeCode") String localeCode);

    /** 按原版本更新设置，版本加一。 */
    int updateSettings(@Param("row") I18nApplication row, @Param("next") I18nApplication next);

    /** 按原 key 集合修订号推进 key 集合与文案修订号。 */
    int advanceKeySet(@Param("applicationId") long applicationId, @Param("keySetRevision") int keySetRevision,
        @Param("now") Instant now);

    /** 按原文案修订号推进文案修订号。 */
    int advanceContent(@Param("applicationId") long applicationId, @Param("contentRevision") int contentRevision,
        @Param("now") Instant now);

    /** 按原当前发布切换当前发布；原值为 null 时按 IS NULL 比较。 */
    int updateCurrentRelease(@Param("applicationId") long applicationId,
        @Param("expectedReleaseId") Long expectedReleaseId, @Param("releaseId") long releaseId,
        @Param("now") Instant now);
}

package com.paytech.platform.support.localization.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;

/** t_i18n_translation 的 MyBatis 映射；SQL 在 mapper/localization/I18nTranslationMapper.xml。 */
public interface I18nTranslationMapper {

    /** 插入新翻译。 */
    int insert(@Param("row") I18nTranslation row);

    /** 按原版本更新文案与依据的基准修订号，版本加一。 */
    int update(@Param("row") I18nTranslation row, @Param("next") I18nTranslation next);

    /** 一组 key 在某语言下的翻译。 */
    List<I18nTranslation> selectByKeys(@Param("keyIds") Collection<Long> keyIds, @Param("locale") String locale);

    /** 应用 ACTIVE key 的全部翻译。 */
    List<I18nTranslation> selectActiveByApplication(@Param("applicationId") long applicationId);
}

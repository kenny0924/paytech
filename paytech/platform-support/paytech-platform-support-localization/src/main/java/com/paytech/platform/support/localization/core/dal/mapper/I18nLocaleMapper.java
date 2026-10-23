package com.paytech.platform.support.localization.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;

/** t_i18n_locale 的 MyBatis 映射；SQL 在 mapper/localization/I18nLocaleMapper.xml。 */
public interface I18nLocaleMapper {

    /** 插入新语言。 */
    int insert(@Param("row") I18nLocale row);

    /** 按语言代码查询。 */
    I18nLocale selectByCode(@Param("localeCode") String localeCode);

    /** 按语言代码加共享锁读取，阻止并发停用直到本事务结束。 */
    List<I18nLocale> selectByCodesForShare(@Param("localeCodes") Collection<String> localeCodes);

    /** 全部语言，按排序值、语言代码升序。 */
    List<I18nLocale> selectAll();

    /** 按原版本更新名称、方向、状态与排序，版本加一。 */
    int update(@Param("row") I18nLocale row, @Param("next") I18nLocale next);
}

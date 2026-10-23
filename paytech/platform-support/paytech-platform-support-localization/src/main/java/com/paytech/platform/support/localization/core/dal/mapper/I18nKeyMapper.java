package com.paytech.platform.support.localization.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.TranslationState;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domain.TranslationEntryView;

/** t_i18n_key 的 MyBatis 映射；SQL 在 mapper/localization/I18nKeyMapper.xml。 */
public interface I18nKeyMapper {

    /** 批量插入新 key。 */
    int insertBatch(@Param("rows") Collection<I18nKey> rows);

    /** 应用的全部 key（含已废弃），按 key 升序。 */
    List<I18nKey> selectByApplication(@Param("applicationId") long applicationId);

    /** 应用某状态的 key，按 key 升序。 */
    List<I18nKey> selectByStatus(@Param("applicationId") long applicationId, @Param("status") KeyStatus status);

    /** 按应用与完整 key 查询。 */
    List<I18nKey> selectByCodes(@Param("applicationId") long applicationId, @Param("keyCodes") Collection<String> keyCodes);

    /** 各命名空间各状态的 key 数。 */
    List<NamespaceKeyCount> countByNamespace(@Param("applicationId") long applicationId);

    /** 命名空间下的 key 数（含已废弃）。 */
    long countInNamespace(@Param("namespaceId") long namespaceId);

    /** key 行或其任一翻译行在给定时间之后写入过的 key 数（含已废弃）。 */
    long countChangedSince(@Param("applicationId") long applicationId, @Param("since") Instant since);

    /** 按原版本更新导入字段，版本加一。 */
    int updateImported(@Param("row") I18nKey row, @Param("next") I18nKey next);

    /** 翻译工作台分页：ACTIVE key 与目标语言翻译。 */
    List<TranslationEntryView> selectEntries(@Param("applicationId") long applicationId,
        @Param("locale") String locale, @Param("namespaceId") Long namespaceId, @Param("state") TranslationState state,
        @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long countEntries(@Param("applicationId") long applicationId, @Param("locale") String locale,
        @Param("namespaceId") Long namespaceId, @Param("state") TranslationState state,
        @Param("keyword") String keyword);
}

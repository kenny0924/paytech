package com.paytech.platform.support.localization.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.TranslationState;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domain.TranslationEntryView;

/** t_i18n_key 仓储；翻译工作台的组合查询以 key 为主表。 */
public interface I18nKeyRepository {

    /** 批量插入新 key；空集合不发 SQL。 */
    void insertAll(List<I18nKey> keys);

    /** 应用的全部 key（含已废弃）。 */
    List<I18nKey> findByApplication(long applicationId);

    /** 应用某状态的 key，按 key 升序。 */
    List<I18nKey> findByStatus(long applicationId, KeyStatus status);

    /** 按应用与完整 key 查询；空集合返回空列表。 */
    List<I18nKey> findByCodes(long applicationId, Collection<String> keyCodes);

    /** 各命名空间各状态的 key 数。 */
    List<NamespaceKeyCount> countByNamespace(long applicationId);

    /** 命名空间下的 key 数（含已废弃）。 */
    long countInNamespace(long namespaceId);

    /**
     * key 行或其任一翻译行在给定时间之后写入过的 key 数（含已废弃）；用于“未发布变更数”。
     * <li>沿用 idx_i18n_key_app（application_id 前缀）与 uk_i18n_translation（key_id 前缀），不新增索引。</li>
     *
     * @param applicationId 应用 ID
     * @param since 当前发布的登记时间
     * @return key 数
     */
    long countChangedSince(long applicationId, Instant since);

    /** 按原版本更新导入字段（基准、说明、基准修订号、状态），成功后同步原对象。 */
    void updateImported(I18nKey key, I18nKey next);

    /** 翻译工作台分页；关键字中的 LIKE 通配符按字面匹配。 */
    List<TranslationEntryView> findEntries(long applicationId, String locale, Long namespaceId, TranslationState state,
        String keyword, PagingRequestBody paging);

    /** 与分页相同条件的总数。 */
    long countEntries(long applicationId, String locale, Long namespaceId, TranslationState state, String keyword);
}

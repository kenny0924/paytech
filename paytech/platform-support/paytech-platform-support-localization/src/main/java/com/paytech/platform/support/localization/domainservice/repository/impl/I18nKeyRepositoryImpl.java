package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.mapper.I18nKeyMapper;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.TranslationState;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domain.TranslationEntryView;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的 key 仓储；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nKeyRepositoryImpl implements I18nKeyRepository {

    /** 单条批量插入语句的最大行数。 */
    private static final int BATCH_SIZE = 500;

    private final I18nKeyMapper mapper;

    /** 按每批最多 500 行插入。 */
    @Override
    public void insertAll(List<I18nKey> keys) {
        for (int from = 0; from < keys.size(); from += BATCH_SIZE) {
            List<I18nKey> batch = keys.subList(from, Math.min(keys.size(), from + BATCH_SIZE));
            RowChecks.requireInserted(mapper.insertBatch(batch), batch.size(), "key 批量插入");
        }
    }

    /** 应用的全部 key。 */
    @Override
    public List<I18nKey> findByApplication(long applicationId) {
        return mapper.selectByApplication(applicationId);
    }

    /** 应用某状态的 key。 */
    @Override
    public List<I18nKey> findByStatus(long applicationId, KeyStatus status) {
        return mapper.selectByStatus(applicationId, status);
    }

    /** 空集合直接返回空列表，不发出 IN () 语句。 */
    @Override
    public List<I18nKey> findByCodes(long applicationId, Collection<String> keyCodes) {
        return keyCodes.isEmpty() ? List.of() : mapper.selectByCodes(applicationId, keyCodes);
    }

    /** 各命名空间各状态的 key 数。 */
    @Override
    public List<NamespaceKeyCount> countByNamespace(long applicationId) {
        return mapper.countByNamespace(applicationId);
    }

    /** 命名空间下的 key 数。 */
    @Override
    public long countInNamespace(long namespaceId) {
        return mapper.countInNamespace(namespaceId);
    }

    /** 当前发布之后写入过的 key 数。 */
    @Override
    public long countChangedSince(long applicationId, Instant since) {
        return mapper.countChangedSince(applicationId, since);
    }

    /** 条件更新成功后把导入字段写回原对象。 */
    @Override
    public void updateImported(I18nKey key, I18nKey next) {
        RowChecks.requireVersionAvailable(key.getVersion(), key.getKeyCode());
        RowChecks.requireUpdated(mapper.updateImported(key, next), "key " + key.getKeyCode());
        key.setSourceText(next.getSourceText());
        key.setDescription(next.getDescription());
        key.setSourceRevision(next.getSourceRevision());
        key.setStatus(next.getStatus());
        key.setVersion(key.getVersion() + 1);
        key.setUpdatedBy(next.getUpdatedBy());
        key.setUpdateTime(next.getUpdateTime());
    }

    /** 分页查询。 */
    @Override
    public List<TranslationEntryView> findEntries(long applicationId, String locale, Long namespaceId,
        TranslationState state, String keyword, PagingRequestBody paging) {
        return mapper.selectEntries(applicationId, locale, namespaceId, state, LikePatterns.escape(keyword),
            paging.offset(), paging.size());
    }

    /** 总数。 */
    @Override
    public long countEntries(long applicationId, String locale, Long namespaceId, TranslationState state,
        String keyword) {
        return mapper.countEntries(applicationId, locale, namespaceId, state, LikePatterns.escape(keyword));
    }
}

package com.paytech.platform.support.localization.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;

/** t_i18n_translation 仓储。 */
public interface I18nTranslationRepository {

    /**
     * 插入新翻译。
     *
     * @param translation 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 同一 key 与语言已被并发创建
     */
    void insert(I18nTranslation translation);

    /** 按原版本更新文案与依据的基准修订号，成功后同步原对象。 */
    void update(I18nTranslation translation, I18nTranslation next);

    /** 一组 key 在某语言下的翻译；空集合返回空列表。 */
    List<I18nTranslation> findByKeys(Collection<Long> keyIds, String locale);

    /** 应用 ACTIVE key 的全部翻译。 */
    List<I18nTranslation> findActiveByApplication(long applicationId);
}

package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;

import lombok.AllArgsConstructor;

/** 多语言查询入口共用的本模块查找：当前发布、指定发布、命名空间与 key 数汇总；只读，不开事务。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationQueryLookups {

    private final I18nKeyRepository keys;
    private final I18nReleaseRepository releases;
    private final I18nNamespaceRepository namespaces;

    /** 当前发布；没有时为 null。 */
    I18nRelease currentRelease(I18nApplication application) {
        return application.getCurrentReleaseId() == null ? null : releases.findById(application.getCurrentReleaseId());
    }

    /** 当前发布号；没有时为 null。 */
    Integer currentReleaseNo(I18nApplication application) {
        I18nRelease current = currentRelease(application);
        return current == null ? null : current.getReleaseNo();
    }

    /**
     * 未发布变更数：当前发布登记之后 key 行或其任一翻译行被写入过的不同 key 数；没有当前发布时为 ACTIVE key 数。
     *
     * @param application 应用
     * @return 变更数
     */
    long pendingChangeCount(I18nApplication application) {
        I18nRelease current = currentRelease(application);
        if (current == null) {
            return sum(keys.countByNamespace(application.getApplicationId()), KeyStatus.ACTIVE, null);
        }
        return keys.countChangedSince(application.getApplicationId(), current.getCreateTime());
    }

    /** 指定发布；不存在返回 404。 */
    I18nRelease requireRelease(I18nApplication application, int releaseNo) {
        I18nRelease release = releases.findByNo(application.getApplicationId(), releaseNo);
        if (release == null) {
            throw LocalizationProcessorSupport.notFound();
        }
        return release;
    }

    /** 命名空间编码转 ID；为 null 不限，不存在返回 404。 */
    Long namespaceId(I18nApplication application, String namespaceCode) {
        if (namespaceCode == null) {
            return null;
        }
        I18nNamespace namespace = namespaces.findByCode(application.getApplicationId(), namespaceCode);
        if (namespace == null) {
            throw LocalizationProcessorSupport.notFound();
        }
        return namespace.getNamespaceId();
    }

    /** 某状态的 key 数；namespace 为 null 时统计全部命名空间。 */
    static long sum(List<NamespaceKeyCount> counts, KeyStatus status, I18nNamespace namespace) {
        return counts.stream().filter(count -> count.getStatus() == status)
            .filter(count -> namespace == null || namespace.getNamespaceId().equals(count.getNamespaceId()))
            .mapToLong(NamespaceKeyCount::getKeyCount).sum();
    }
}

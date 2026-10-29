package com.paytech.platform.support.localization.service.processor;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact;
import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseChangeRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;

import lombok.AllArgsConstructor;

/**
 * 按发布变更明细还原任意发布的内容，以及预检与发布共用的“相对线上发布”变更摘要；只查本库，不读语言包文件，不开事务。
 * <li>发布 N 的内容 = 每个语言、key 在发布号不大于 N 的最后一条变化（排除移除）；还原前确认发布号不大于 N 的发布都已登记变化条数，
 * 否则返回服务端错误，不给出不完整的内容。</li>
 * <li>摘要的当前内容由调用方按发布口径从库内文案展开；没有线上发布时与空内容比较。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReleaseChangeSummaries {

    private final LocalizationQueryLookups lookups;
    private final I18nReleaseRepository releases;
    private final I18nReleaseChangeRepository changes;

    /**
     * 还原发布的全部文案。
     *
     * @param application 应用
     * @param releaseNo 发布号；0 表示还没有发布，内容为空
     * @return 语言 → key → 文案
     * @throws ErrorException 发布号不大于它的发布中有未生成变更明细的（发布变更明细缺失）
     */
    Map<String, Map<String, String>> restore(I18nApplication application, int releaseNo) {
        if (releaseNo <= 0) {
            return Map.of();
        }
        int unrecorded = releases.countUnrecorded(application.getApplicationId(), releaseNo);
        if (unrecorded > 0) {
            throw new ErrorException("发布变更明细缺失: 应用 " + application.getApplicationCode() + "，发布号 " + releaseNo
                + "，未登记明细的发布 " + unrecorded + " 个");
        }
        return changes.findContent(application.getApplicationId(), releaseNo);
    }

    /**
     * 当前内容相对应用线上发布的变更摘要。
     *
     * @param application 应用
     * @param current 当前内容：语言 → key → 文案
     * @return 摘要
     */
    ReleaseChangeSummaryFact summarize(I18nApplication application, Map<String, Map<String, String>> current) {
        return summarize(application, current, 0, Map.of());
    }

    /**
     * 当前内容相对应用线上发布的变更摘要；线上发布正是调用方已还原的发布时直接使用，不再还原。
     *
     * @param application 应用
     * @param current 当前内容：语言 → key → 文案
     * @param restoredNo 调用方已还原的发布号；没有时为 0
     * @param restored 该发布的内容
     * @return 摘要
     */
    ReleaseChangeSummaryFact summarize(I18nApplication application, Map<String, Map<String, String>> current,
        int restoredNo, Map<String, Map<String, String>> restored) {
        I18nRelease live = lookups.currentRelease(application);
        if (live == null) {
            return ReleaseChangeSummaryFact.of(ReleaseDiff.between(Map.of(), current), null);
        }
        Map<String, Map<String, String>> base = live.getReleaseNo() == restoredNo ? restored
            : restore(application, live.getReleaseNo());
        return ReleaseChangeSummaryFact.of(ReleaseDiff.between(base, current), live.getReleaseNo());
    }
}

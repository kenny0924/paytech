package com.paytech.platform.support.localization.domain.release;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseChange;
import com.paytech.platform.support.localization.core.enums.ReleaseChangeType;

import lombok.Getter;

/**
 * 一次发布相对上一发布号的文案变化行（t_i18n_release_change）与条数（t_i18n_release.change_count）。纯 Java。
 * <li>比较规则与 {@link ReleaseDiff} 相同：按文字是否相等，改了又改回不算变化；发布号 1 与空内容比较，即全量基线。</li>
 * <li>ADDED、CHANGED 记改后文字，REMOVED 文字为 null；改前文字由更小发布号的最后一条变化给出，不重复存储。</li>
 * <li>主键由仓储按批写入时分配（单批不超过 Snowflake 单毫秒序列上限），这里不生成。</li>
 */
@Getter
public final class ReleaseChanges {

    /** 变化行，按 ADDED、CHANGED、REMOVED 及语言、key 字典序。 */
    private final List<I18nReleaseChange> rows = new ArrayList<>();

    private ReleaseChanges() {
    }

    /**
     * 比较上一发布号的内容与本次发布内容。
     *
     * @param previous 上一发布号的内容：语言 → key → 文案；发布号 1 传空映射
     * @param current 本次发布的内容：语言 → key → 文案
     * @param applicationId 应用 ID
     * @param releaseNo 本次发布号
     * @param now 创建时间
     * @return 变化行与条数
     */
    public static ReleaseChanges between(Map<String, Map<String, String>> previous,
        Map<String, Map<String, String>> current, long applicationId, int releaseNo, Instant now) {
        ReleaseDiff diff = ReleaseDiff.between(previous, current);
        ReleaseChanges changes = new ReleaseChanges();
        diff.getAdded().forEach(change -> changes.add(change, ReleaseChangeType.ADDED, applicationId, releaseNo, now));
        diff.getChanged().forEach(change -> changes.add(change, ReleaseChangeType.CHANGED, applicationId, releaseNo,
            now));
        diff.getRemoved().forEach(change -> changes.add(change, ReleaseChangeType.REMOVED, applicationId, releaseNo,
            now));
        return changes;
    }

    /** 变化条数，即 t_i18n_release.change_count。 */
    public int count() {
        return rows.size();
    }

    /** 追加一行；REMOVED 的文字为 null。 */
    void add(TextChange change, ReleaseChangeType type, long applicationId, int releaseNo, Instant now) {
        I18nReleaseChange row = new I18nReleaseChange();
        row.setApplicationId(applicationId);
        row.setReleaseNo(releaseNo);
        row.setLocaleCode(change.locale());
        row.setKeyCode(change.keyCode());
        row.setChangeType(type);
        row.setText(type == ReleaseChangeType.REMOVED ? null : change.after());
        row.setCreateTime(now);
        rows.add(row);
    }
}

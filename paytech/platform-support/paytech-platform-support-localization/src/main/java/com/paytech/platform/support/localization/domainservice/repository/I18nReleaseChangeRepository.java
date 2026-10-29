package com.paytech.platform.support.localization.domainservice.repository;

import java.util.List;
import java.util.Map;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseChange;

/** t_i18n_release_change 仓储；变化行随发布登记写入，之后不修改、不删除。 */
public interface I18nReleaseChangeRepository {

    /**
     * 插入一次发布的全部变化行：按批分配 Snowflake 主键并写入，成功后主键留在原对象上；没有变化时不执行语句。
     *
     * @param changes 已准备除主键外各字段的新对象
     */
    void insertAll(List<I18nReleaseChange> changes);

    /**
     * 还原发布的全部文案：每个语言、key 取发布号不大于给定值的最后一条变化，排除 REMOVED。
     * <li>调用方须先确认发布号不大于给定值的发布都已登记变化条数，否则还原结果不完整。</li>
     *
     * @param applicationId 应用 ID
     * @param releaseNo 发布号
     * @return 语言 → key → 文案，按字典序
     */
    Map<String, Map<String, String>> findContent(long applicationId, int releaseNo);
}

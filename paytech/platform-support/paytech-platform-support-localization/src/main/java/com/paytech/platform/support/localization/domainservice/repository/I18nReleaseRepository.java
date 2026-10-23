package com.paytech.platform.support.localization.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;

/** t_i18n_release 仓储；状态只从 PENDING_CHECK 走到 READY 或 FAILED。 */
public interface I18nReleaseRepository {

    /**
     * 插入新发布。
     *
     * @param release 已准备主键、发布号与时间的新对象
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 同一发布号已被并发登记
     */
    void insert(I18nRelease release);

    /** 按主键查询；不存在返回 null。 */
    I18nRelease findById(long releaseId);

    /** 按应用与发布号查询；不存在返回 null。 */
    I18nRelease findByNo(long applicationId, int releaseNo);

    /** 应用内最大发布号；没有发布时为 0。 */
    int maxReleaseNo(long applicationId);

    /** 小于给定发布号的最大发布号；没有时为 null。 */
    Integer previousReleaseNo(long applicationId, int releaseNo);

    /** 分页查询，按发布号倒序。 */
    List<I18nRelease> findPage(long applicationId, PagingRequestBody paging);

    /** 应用的发布总数。 */
    long count(long applicationId);

    /** PENDING_CHECK 变为 READY，成功后同步原对象。 */
    void markReady(I18nRelease release, Instant now);

    /** PENDING_CHECK 变为 FAILED，成功后同步原对象。 */
    void markFailed(I18nRelease release, String reason, Instant now);
}

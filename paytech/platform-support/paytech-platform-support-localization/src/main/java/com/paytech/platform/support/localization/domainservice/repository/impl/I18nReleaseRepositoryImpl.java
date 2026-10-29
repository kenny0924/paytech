package com.paytech.platform.support.localization.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.mapper.I18nReleaseMapper;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的发布仓储；状态条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class I18nReleaseRepositoryImpl implements I18nReleaseRepository {

    private final I18nReleaseMapper mapper;

    /** 发布号唯一键冲突说明有并发发布，按并发冲突处理。 */
    @Override
    public void insert(I18nRelease release) {
        try {
            RowChecks.requireInserted(mapper.insert(release), 1, "发布 " + release.getReleaseNo());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_i18n_release")) {
                throw failure;
            }
            throw new ConcurrentRequestException("发布号已被其他发布占用，请重新发布: " + release.getReleaseNo(), failure);
        }
    }

    /** 按主键查询。 */
    @Override
    public I18nRelease findById(long releaseId) {
        return mapper.selectById(releaseId);
    }

    /** 按应用与发布号查询。 */
    @Override
    public I18nRelease findByNo(long applicationId, int releaseNo) {
        return mapper.selectByNo(applicationId, releaseNo);
    }

    /** 没有发布时为 0。 */
    @Override
    public int maxReleaseNo(long applicationId) {
        Integer max = mapper.selectMaxNo(applicationId);
        return max == null ? 0 : max;
    }

    /** 上一个发布号。 */
    @Override
    public Integer previousReleaseNo(long applicationId, int releaseNo) {
        return mapper.selectPreviousNo(applicationId, releaseNo);
    }

    /** 未生成变更明细的发布数。 */
    @Override
    public int countUnrecorded(long applicationId, int releaseNo) {
        return mapper.countUnrecorded(applicationId, releaseNo);
    }

    /** 分页查询。 */
    @Override
    public List<I18nRelease> findPage(long applicationId, PagingRequestBody paging) {
        return mapper.selectPage(applicationId, paging.offset(), paging.size());
    }

    /** 发布总数。 */
    @Override
    public long count(long applicationId) {
        return mapper.count(applicationId);
    }

    /** 只有 PENDING_CHECK 可以变为 READY。 */
    @Override
    public void markReady(I18nRelease release, Instant now) {
        RowChecks.requireUpdated(mapper.markReady(release.getReleaseId(), now), "发布 " + release.getReleaseNo());
        release.setStatus(ReleaseStatus.READY);
        release.setReadyTime(now);
        release.setUpdateTime(now);
    }

    /** 只有 PENDING_CHECK 可以变为 FAILED。 */
    @Override
    public void markFailed(I18nRelease release, String reason, Instant now) {
        RowChecks.requireUpdated(mapper.markFailed(release.getReleaseId(), reason, now), "发布 " + release.getReleaseNo());
        release.setStatus(ReleaseStatus.FAILED);
        release.setFailureReason(reason);
        release.setUpdateTime(now);
    }
}

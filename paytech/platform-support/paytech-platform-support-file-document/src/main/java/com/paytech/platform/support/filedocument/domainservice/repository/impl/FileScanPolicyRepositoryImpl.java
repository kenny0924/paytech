package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRevision;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileScanPolicyMapper;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanPolicyRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的检查策略修订仓储。 */
@Repository
@AllArgsConstructor
public class FileScanPolicyRepositoryImpl implements FileScanPolicyRepository {

    private final FileScanPolicyMapper mapper;

    /** 全部修订。 */
    @Override
    public List<FileScanPolicyRevision> findAll() {
        return mapper.selectAll();
    }

    /** 策略代码的全部修订。 */
    @Override
    public List<FileScanPolicyRevision> findByCode(String policyCode) {
        return mapper.selectByCode(policyCode);
    }

    /** 排他锁定策略代码的全部修订。 */
    @Override
    public List<FileScanPolicyRevision> lockByCode(String policyCode) {
        return mapper.lockByCode(policyCode);
    }

    /** 按修订主键读取。 */
    @Override
    public FileScanPolicyRevision findById(long policyId) {
        return mapper.selectById(policyId);
    }

    /** 排他锁定 ACTIVE 修订。 */
    @Override
    public FileScanPolicyRevision lockActive(String policyCode) {
        return mapper.lockActive(policyCode);
    }

    /** 只把代码与修订号唯一键冲突转换为业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(FileScanPolicyRevision revision) {
        try {
            requireSaved(mapper.insert(revision), revision);
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_file_scan_revision")) {
                throw failure;
            }
            throw new ConcurrentRequestException("检查策略修订已存在: " + code(revision), failure);
        }
    }

    /** 条件更新成功后同步内容、版本与时间。 */
    @Override
    public void updateDraft(FileScanPolicyRevision revision, String allowedMimeRules, long maxSizeBytes, Instant now) {
        requireSaved(mapper.updateDraft(revision, allowedMimeRules, maxSizeBytes, now), revision);
        revision.setAllowedMimeRules(allowedMimeRules);
        revision.setMaxSizeBytes(maxSizeBytes);
        revision.setVersion(revision.getVersion() + 1);
        revision.setUpdateTime(now);
    }

    /** 条件更新成功后同步状态、占位、版本与时间。 */
    @Override
    public void updateStatus(FileScanPolicyRevision revision, String status, Integer activeSlot, Instant now) {
        requireSaved(mapper.updateStatus(revision, status, activeSlot, now), revision);
        revision.setStatus(status);
        revision.setActiveSlot(activeSlot);
        revision.setVersion(revision.getVersion() + 1);
        revision.setUpdateTime(now);
    }

    /** 按原版本删除草稿。 */
    @Override
    public void deleteDraft(FileScanPolicyRevision revision) {
        requireSaved(mapper.deleteDraft(revision), revision);
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次对象。 */
    void requireSaved(int changed, FileScanPolicyRevision revision) {
        if (changed != 1) {
            throw new ConcurrentRequestException("检查策略修订已被其他操作修改，请刷新后重试: " + code(revision));
        }
    }

    /** 修订的审计编码。 */
    static String code(FileScanPolicyRevision revision) {
        return revision.getPolicyCode() + "/" + revision.getRevisionNo();
    }
}

package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRevision;

/** 检查策略修订表仓储；条件更新成功后同步传入的原对象。 */
public interface FileScanPolicyRepository {

    /** 全部修订，按策略代码升序、修订号倒序。 */
    List<FileScanPolicyRevision> findAll();

    /** 策略代码的全部修订，按修订号倒序；不存在为空列表。 */
    List<FileScanPolicyRevision> findByCode(String policyCode);

    /** 排他锁定策略代码的全部修订，按修订号倒序。 */
    List<FileScanPolicyRevision> lockByCode(String policyCode);

    /** 按修订主键读取；不存在为空。 */
    FileScanPolicyRevision findById(long policyId);

    /** 排他锁定策略代码的 ACTIVE 修订；没有为空。 */
    FileScanPolicyRevision lockActive(String policyCode);

    /** 插入修订；代码与修订号重复转换为业务拒绝。 */
    void insert(FileScanPolicyRevision revision);

    /** 按原版本修改 DRAFT 修订内容。 */
    void updateDraft(FileScanPolicyRevision revision, String allowedMimeRules, long maxSizeBytes, Instant now);

    /** 按原版本与原状态切换修订状态与活动占位。 */
    void updateStatus(FileScanPolicyRevision revision, String status, Integer activeSlot, Instant now);

    /** 按原版本删除 DRAFT 修订。 */
    void deleteDraft(FileScanPolicyRevision revision);
}

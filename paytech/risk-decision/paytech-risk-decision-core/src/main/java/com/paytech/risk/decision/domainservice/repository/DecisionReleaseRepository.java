package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

/** Decision Release 根的单表持久化边界。 */
public interface DecisionReleaseRepository {

    /** 按 ID 读取发布根。 */
    Optional<DecisionRelease> findRoot(long releaseId);
    /** 锁定发布根；资源缺失由调用方映射为业务不存在。 */
    Optional<DecisionRelease> lockRoot(long releaseId);
    /** 按稳定顺序读取分页发布根。 */
    List<DecisionRelease> findPage(String pointCode, DecisionReleaseStatus status, int limit, int offset);
    /** 统计同一筛选范围内的发布根。 */
    long count(String pointCode, DecisionReleaseStatus status);
    /** 在 CHECKOUT_PREPARE 点锁内分配全局发布编号。 */
    int nextReleaseNoWhileCheckoutPointLocked();
    /** 插入已构造的初始发布根。 */
    void insert(DecisionRelease release);
    /** 成功执行 DRAFT/version=1 CAS 后同步原对象。 */
    void startValidation(DecisionRelease release, Instant time);
    /** 成功执行 VALIDATING/version=2 CAS 后同步原对象。 */
    void rejectValidation(DecisionRelease release, Instant time);
}

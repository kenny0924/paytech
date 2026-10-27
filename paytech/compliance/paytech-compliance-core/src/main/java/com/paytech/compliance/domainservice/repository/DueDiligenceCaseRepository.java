package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.domain.CaseClosing;

/** t_due_diligence_case 的持久化；所有更新以版本为条件，成功后同步原对象，失败抛出并发冲突且不改原对象。 */
public interface DueDiligenceCaseRepository {
    /** 新增案件；影响行数不为 1 时失败。 */
    void insert(DueDiligenceCase row);

    /** 同一来源提交的全部案件。 */
    List<DueDiligenceCase> findBySubmission(String namespace, long applicationId, String submissionId);

    /** 按案件 ID 读取，不存在返回 null。 */
    DueDiligenceCase findById(long caseId);

    /** 按案件 ID 集合读取；空集合返回空列表。 */
    List<DueDiligenceCase> findByIds(List<Long> caseIds);

    /** 某申请最近一轮的 Client 主案件；没有返回 null。 */
    DueDiligenceCase findLatestClientCase(long applicationId);

    /** 是否已有以该案件为上一轮的后继案件。 */
    boolean hasSuccessor(long caseId);

    /** 补件期限已到、未重提的 RETURNED Client 主案件。 */
    List<DueDiligenceCase> findDueReturned(Instant now, int limit);

    /** Client 入驻主案件分页；platformOnly 为 true 时只看责任方为空的案件。 */
    List<DueDiligenceCase> pageClientCases(String status, String qualificationType, Long applicationId, Long responsiblePartnerId,
        boolean platformOnly, String jurisdictionCode, long offset, int size);

    /** Client 入驻主案件计数。 */
    long countClientCases(String status, String qualificationType, Long applicationId, Long responsiblePartnerId, boolean platformOnly,
        String jurisdictionCode);

    /**
     * 以版本为条件结束进行中案件
     *
     * @param row 读取时的案件
     * @param status 结束状态
     * @param closing 结束时写入的说明、退回期限与操作方
     * @param now 服务端时间
     */
    void close(DueDiligenceCase row, String status, CaseClosing closing, Instant now);

    /** 以版本与当前状态为条件迁移到撤回或过期，释放进行中占位。 */
    void transition(DueDiligenceCase row, String toStatus, Instant now);

    /** 进行中案件版本递增；版本已变化或案件已结束时抛出并发冲突。 */
    void touch(DueDiligenceCase row, Instant now);

    /** SUBMITTED → IN_REVIEW。 */
    void startReview(DueDiligenceCase row, Instant now);

    /** 写入手工评级并清空推荐。 */
    void updateRiskRating(DueDiligenceCase row, String riskRating, String note, long actorId, Instant now);

    /** 写入推荐。 */
    void updateRecommendation(DueDiligenceCase row, String recommendation, String note, long actorId, Instant now);
}

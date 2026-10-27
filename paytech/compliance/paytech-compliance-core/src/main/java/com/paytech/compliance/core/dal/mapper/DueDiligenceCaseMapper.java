package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;

/** t_due_diligence_case 的 SQL；业务 SQL 位于 XML。 */
public interface DueDiligenceCaseMapper {
    /** 新增案件。 */
    int insert(@Param("row") DueDiligenceCase row);

    /** 同一来源提交的全部案件。 */
    List<DueDiligenceCase> findBySubmission(@Param("namespace") String namespace, @Param("applicationId") long applicationId,
        @Param("submissionId") String submissionId);

    /** 按 ID 读取。 */
    DueDiligenceCase findById(@Param("caseId") long caseId);

    /** 按 ID 集合读取。 */
    List<DueDiligenceCase> findByIds(@Param("caseIds") List<Long> caseIds);

    /** 某申请最近一轮的 Client 主案件。 */
    DueDiligenceCase findLatestClientCase(@Param("applicationId") long applicationId);

    /** 以指定案件为上一轮的后继案件数。 */
    long countSuccessors(@Param("caseId") long caseId);

    /** 补件期限已到、仍为 RETURNED 且没有后继的 Client 主案件，按期限升序。 */
    List<DueDiligenceCase> findDueReturned(@Param("now") Instant now, @Param("limit") int limit);

    /** Client 主案件分页。 */
    List<DueDiligenceCase> pageClientCases(@Param("status") String status, @Param("qualificationType") String qualificationType,
        @Param("applicationId") Long applicationId, @Param("responsiblePartnerId") Long responsiblePartnerId,
        @Param("platformOnly") boolean platformOnly, @Param("jurisdictionCode") String jurisdictionCode, @Param("offset") long offset,
        @Param("size") int size);

    /** Client 主案件计数。 */
    long countClientCases(@Param("status") String status, @Param("qualificationType") String qualificationType,
        @Param("applicationId") Long applicationId, @Param("responsiblePartnerId") Long responsiblePartnerId,
        @Param("platformOnly") boolean platformOnly, @Param("jurisdictionCode") String jurisdictionCode);

    /** 进行中案件按版本结束（退回、批准、拒绝、随主案件撤回）。 */
    int close(@Param("row") DueDiligenceCase row, @Param("status") String status, @Param("publicNote") String publicNote,
        @Param("resolutionNote") String resolutionNote, @Param("returnSection") String returnSection,
        @Param("reviewDueTime") Instant reviewDueTime, @Param("operatedBy") String operatedBy,
        @Param("onBehalfOfPartnerId") Long onBehalfOfPartnerId, @Param("now") Instant now);

    /** 按版本与原状态迁移（RETURNED → WITHDRAWN / EXPIRED，进行中 → WITHDRAWN），释放进行中占位。 */
    int transition(@Param("row") DueDiligenceCase row, @Param("toStatus") String toStatus, @Param("now") Instant now);

    /** 进行中案件版本递增。 */
    int touch(@Param("row") DueDiligenceCase row, @Param("now") Instant now);

    /** SUBMITTED → IN_REVIEW。 */
    int startReview(@Param("row") DueDiligenceCase row, @Param("now") Instant now);

    /** 写入手工评级并清空推荐。 */
    int updateRiskRating(@Param("row") DueDiligenceCase row, @Param("riskRating") String riskRating, @Param("note") String note,
        @Param("actorId") long actorId, @Param("now") Instant now);

    /** 写入推荐。 */
    int updateRecommendation(@Param("row") DueDiligenceCase row, @Param("recommendation") String recommendation,
        @Param("note") String note, @Param("actorId") long actorId, @Param("now") Instant now);
}

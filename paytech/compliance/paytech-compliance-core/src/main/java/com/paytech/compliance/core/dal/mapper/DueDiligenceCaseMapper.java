package com.paytech.risk.compliance.core.dal.mapper;

import java.util.List;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

import com.paytech.risk.compliance.core.dal.dataobject.DueDiligenceCase;

/** t_due_diligence_case 的案件 SQL；业务 SQL 位于 XML。 */
public interface DueDiligenceCaseMapper {
    /** 新增案件。 */
    int insert(@Param("row") DueDiligenceCase row);

    /** 同一来源提交的全部案件（主案件与关联人员子案件）。 */
    List<DueDiligenceCase> findBySubmission(@Param("namespace") String namespace, @Param("applicationId") long applicationId,
        @Param("submissionId") String submissionId);

    /** 按案件 ID 读取。 */
    DueDiligenceCase findById(@Param("caseId") long caseId);

    /** 按案件 ID 集合读取。 */
    List<DueDiligenceCase> findByIds(@Param("caseIds") List<Long> caseIds);

    /** Client 入驻主案件分页。 */
    List<DueDiligenceCase> pageClientCases(@Param("status") String status, @Param("qualificationType") String qualificationType,
        @Param("applicationId") Long applicationId, @Param("offset") long offset, @Param("size") int size);

    /** Client 入驻主案件计数，条件与分页一致。 */
    long countClientCases(@Param("status") String status, @Param("qualificationType") String qualificationType,
        @Param("applicationId") Long applicationId);

    /** 以版本和进行中占位为条件结束案件，返回影响行数。 */
    int close(@Param("row") DueDiligenceCase row, @Param("status") String status, @Param("publicNote") String publicNote,
        @Param("resolutionNote") String resolutionNote, @Param("returnSection") String returnSection, @Param("now") Instant now);

    /** 以版本和进行中占位为条件递增版本，串行化同一任务的审核命令，返回影响行数。 */
    int touch(@Param("row") DueDiligenceCase row, @Param("now") Instant now);
}

package com.paytech.compliance.domainservice.repository;

import java.util.List;

import java.time.Instant;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;

/** t_due_diligence_case 的案件持久化。 */
public interface DueDiligenceCaseRepository {
    /** 新增案件；影响行数不为 1 时失败。 */
    void insert(DueDiligenceCase row);

    /** 同一来源提交的全部案件。 */
    List<DueDiligenceCase> findBySubmission(String namespace, long applicationId, String submissionId);

    /** 按案件 ID 读取，不存在返回 null。 */
    DueDiligenceCase findById(long caseId);

    /** 按案件 ID 集合读取；空集合返回空列表。 */
    List<DueDiligenceCase> findByIds(List<Long> caseIds);

    /** Client 入驻主案件分页。 */
    List<DueDiligenceCase> pageClientCases(String status, String qualificationType, Long applicationId, long offset, int size);

    /** Client 入驻主案件计数。 */
    long countClientCases(String status, String qualificationType, Long applicationId);

    /** 以版本为条件结束案件，成功后同步原对象；已变化时并发冲突。 */
    void close(DueDiligenceCase row, String status, String publicNote, String resolutionNote, String returnSection, Instant now);

    /** 进行中任务的版本递增；版本已变化或任务已结束时抛出并发冲突。 */
    void touch(DueDiligenceCase row, Instant now);
}

package com.paytech.compliance.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.dal.mapper.EvidenceRefMapper;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_evidence_ref 单表持久化。 */
@Repository
@AllArgsConstructor
public class EvidenceRefRepositoryImpl implements EvidenceRefRepository {
    private final EvidenceRefMapper mapper;

    /** 新增证据引用。 */
    @Override
    public void insert(EvidenceRef row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("证据引用写入行数异常");
        }
    }

    /** 若干案件下的全部证据引用。 */
    @Override
    public List<EvidenceRef> findByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByCaseIds(caseIds);
    }

    /** 按证据 ID 读取。 */
    @Override
    public EvidenceRef findById(long evidenceId) {
        return mapper.findById(evidenceId);
    }

    /** 条件写入核验结果并同步。 */
    @Override
    public void review(EvidenceRef row, String status, Instant now) {
        if (mapper.review(row, status, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("材料已核验或已变化，请刷新后重试");
        }
        row.setStatus(status);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}

package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.dal.mapper.EvidenceRefMapper;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 证据引用持久化。 */
@Repository
@AllArgsConstructor
public class EvidenceRefRepositoryImpl implements EvidenceRefRepository {
    private final EvidenceRefMapper mapper;

    /** 新增证据。 */
    @Override
    public void insert(EvidenceRef row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("证据写入行数异常");
        }
    }

    /** 若干案件的证据。 */
    @Override
    public List<EvidenceRef> findByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByCaseIds(caseIds);
    }

    /** 条件写入核验结果并同步原对象。 */
    @Override
    public void review(EvidenceRef row, String status, String rejectReasonCode, String note, long reviewerId, Instant now) {
        if (mapper.review(row, status, rejectReasonCode, note, reviewerId, now) != 1) {
            throw new ConcurrentRequestException("材料已被其他操作核验，请刷新后重试");
        }
        row.setStatus(status);
        row.setRejectReasonCode(rejectReasonCode);
        row.setReviewNote(note);
        row.setReviewerId(reviewerId);
        row.setReviewedTime(now);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}

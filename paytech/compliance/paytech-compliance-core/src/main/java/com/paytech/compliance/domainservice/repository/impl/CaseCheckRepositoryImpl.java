package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.mapper.CaseCheckMapper;
import com.paytech.compliance.domainservice.repository.CaseCheckRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 案件检查持久化。 */
@Repository
@AllArgsConstructor
public class CaseCheckRepositoryImpl implements CaseCheckRepository {
    private final CaseCheckMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(CaseCheck row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_dd_check 写入行数异常");
        }
    }

    /** 若干案件的检查。 */
    @Override
    public List<CaseCheck> findByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByCaseIds(caseIds);
    }

    /** 以版本与 REQUESTED 为条件录入人工检查结论，成功后同步原对象。 */
    @Override
    public void record(CaseCheck row, String outcome, String reasonCode, String evidenceRef, String note, long reviewerId, Instant validUntil, String operatedBy, Long onBehalfOfPartnerId, Instant now) {
        if (mapper.record(row, outcome, reasonCode, evidenceRef, note, reviewerId, validUntil, operatedBy, onBehalfOfPartnerId, now) != 1) {
            throw new ConcurrentRequestException("检查已被其他操作录入，请刷新后重试");
        }
        row.setStatus("COMPLETED");
        row.setOutcome(outcome);
        row.setReasonCode(reasonCode);
        row.setEvidenceRef(evidenceRef);
        row.setNote(note);
        row.setReviewerId(reviewerId);
        row.setReviewedTime(now);
        row.setValidUntil(validUntil);
        row.setOperatedBy(operatedBy);
        row.setOnBehalfOfPartnerId(onBehalfOfPartnerId);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}

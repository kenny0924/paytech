package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;

/** 案件检查持久化（t_dd_check）。 */
public interface CaseCheckRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(CaseCheck row);

    /** 若干案件的检查；空集合返回空列表。 */
    List<CaseCheck> findByCaseIds(List<Long> caseIds);

    /** 以版本与 REQUESTED 为条件录入人工检查结论；条件不满足时并发冲突，原对象不变。 */
    void record(CaseCheck row, String outcome, String reasonCode, String evidenceRef, String note, long reviewerId, Instant validUntil, String operatedBy, Long onBehalfOfPartnerId, Instant now);
}

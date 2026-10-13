package com.paytech.risk.compliance.domainservice.repository;

import java.util.List;

import java.time.Instant;
import com.paytech.risk.compliance.core.dal.dataobject.EvidenceRef;

/** t_dd_evidence_ref 的证据引用持久化。 */
public interface EvidenceRefRepository {
    /** 新增证据引用；影响行数不为 1 时失败。 */
    void insert(EvidenceRef row);

    /** 若干案件下的全部证据引用；空集合返回空列表。 */
    List<EvidenceRef> findByCaseIds(List<Long> caseIds);

    /** 按证据 ID 读取，不存在返回 null。 */
    EvidenceRef findById(long evidenceId);

    /** 写入核验结果并同步原对象；已核验或已变化时并发冲突。 */
    void review(EvidenceRef row, String status, Instant now);
}

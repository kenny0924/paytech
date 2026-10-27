package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.EvidenceRef;

/** t_dd_evidence_ref 的证据引用持久化。 */
public interface EvidenceRefRepository {
    /** 新增证据引用；影响行数不为 1 时失败。 */
    void insert(EvidenceRef row);

    /** 若干案件下的全部证据引用；空集合返回空列表。 */
    List<EvidenceRef> findByCaseIds(List<Long> caseIds);

    /**
     * 写入核验结果并同步原对象；已核验或已变化时并发冲突
     *
     * @param row 读取时的证据
     * @param status ACCEPTED 或 REJECTED
     * @param rejectReasonCode 拒绝原因码；接受为空
     * @param note 内部备注
     * @param reviewerId 核验人
     * @param now 服务端时间
     */
    void review(EvidenceRef row, String status, String rejectReasonCode, String note, long reviewerId, Instant now);
}

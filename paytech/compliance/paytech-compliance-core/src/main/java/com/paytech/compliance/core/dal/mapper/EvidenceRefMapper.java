package com.paytech.risk.compliance.core.dal.mapper;

import java.util.List;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

import com.paytech.risk.compliance.core.dal.dataobject.EvidenceRef;

/** t_dd_evidence_ref 的证据引用 SQL；业务 SQL 位于 XML。 */
public interface EvidenceRefMapper {
    /** 新增证据引用。 */
    int insert(@Param("row") EvidenceRef row);

    /** 若干案件下的全部证据引用。 */
    List<EvidenceRef> findByCaseIds(@Param("caseIds") List<Long> caseIds);

    /** 按证据 ID 读取。 */
    EvidenceRef findById(@Param("evidenceId") long evidenceId);

    /** 以版本和 RECEIVED 为条件写入核验结果，返回影响行数。 */
    int review(@Param("row") EvidenceRef row, @Param("status") String status, @Param("now") Instant now);
}
